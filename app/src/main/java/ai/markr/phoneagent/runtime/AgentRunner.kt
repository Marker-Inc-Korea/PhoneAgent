package ai.markr.phoneagent.runtime

import ai.markr.phoneagent.agent.AgentConfig
import ai.markr.phoneagent.agent.AgentLoop
import ai.markr.phoneagent.agent.DeviceController
import ai.markr.phoneagent.agent.model.AgentOutcome
import ai.markr.phoneagent.agent.model.AgentResult
import ai.markr.phoneagent.data.AgentSettings
import ai.markr.phoneagent.data.RunRecord
import ai.markr.phoneagent.llm.LlmClientFactory
import ai.markr.phoneagent.platform.AgentNotifications
import ai.markr.phoneagent.platform.PhoneAccessibilityService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Bridges the UI to the pure [AgentLoop]: resolves settings, runs the loop on a
 * background scope, streams [AgentRunState], persists the result, and drives the
 * on-screen overlay. One run at a time.
 */
class AgentRunner(
    private val controller: DeviceController,
    private val settingsProvider: suspend () -> AgentSettings,
    private val saveRecord: suspend (RunRecord) -> Unit,
    private val llmFactory: (AgentSettings) -> ai.markr.phoneagent.agent.LlmClient = LlmClientFactory::create,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val _state = MutableStateFlow(AgentRunState())
    val state: StateFlow<AgentRunState> = _state.asStateFlow()

    private var job: Job? = null

    fun start(task: String) {
        if (_state.value.isRunning) return
        val trimmed = task.trim()
        if (trimmed.isEmpty()) {
            _state.value = AgentRunState(status = RunStatus.ERROR, message = "작업 내용을 입력하세요.")
            return
        }
        job = scope.launch {
            _state.value = AgentRunState(status = RunStatus.RUNNING, task = trimmed)
            val settings = settingsProvider()
            if (!settings.isConfigured) {
                _state.value = AgentRunState(
                    status = RunStatus.ERROR,
                    task = trimmed,
                    message = "API 키와 모델을 먼저 설정에서 구성하세요.",
                )
                return@launch
            }
            val service = PhoneAccessibilityService.instance
            AgentControl.onStop = { stop() }
            service?.onStopRequested = { stop() }
            service?.showStatus("작업을 시작할게요.")
            notify("작업을 시작할게요: $trimmed")
            try {
                val llm = llmFactory(settings)
                val loop = AgentLoop(
                    controller = controller,
                    llm = llm,
                    config = AgentConfig(
                        maxSteps = settings.maxSteps,
                        voiceConcise = settings.voiceEnabled,
                    ),
                )
                val result = loop.run(trimmed) { step ->
                    _state.value = _state.value.copy(steps = _state.value.steps + step)
                    val line = "단계 ${step.index + 1}: ${step.thought.take(60)}"
                    service?.showStatus(line)
                    notify(line)
                }
                _state.value = result.toRunState(trimmed)
                persist(trimmed, result)
            } catch (e: CancellationException) {
                _state.value = AgentRunState(
                    status = RunStatus.CANCELLED,
                    task = trimmed,
                    steps = _state.value.steps,
                    message = "사용자 중지",
                )
                throw e
            } catch (e: Exception) {
                _state.value = AgentRunState(
                    status = RunStatus.ERROR,
                    task = trimmed,
                    steps = _state.value.steps,
                    message = "오류: ${e.message}",
                )
            } finally {
                service?.hideStatus()
                AgentControl.onStop = null
                PhoneAccessibilityService.instance?.let { AgentNotifications.dismiss(it) }
            }
        }
    }

    private fun notify(text: String) {
        PhoneAccessibilityService.instance?.let { AgentNotifications.showRunning(it, text) }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    fun reset() {
        if (!_state.value.isRunning) _state.value = AgentRunState()
    }

    private suspend fun persist(task: String, result: AgentResult) {
        saveRecord(
            RunRecord(
                task = task,
                outcome = result.outcome.name,
                answer = result.answer,
                stepCount = result.steps.size,
                createdAt = clock(),
            ),
        )
    }

    private fun AgentResult.toRunState(task: String) = AgentRunState(
        status = when (outcome) {
            AgentOutcome.DONE -> RunStatus.DONE
            AgentOutcome.ABORTED -> RunStatus.ABORTED
            AgentOutcome.CANCELLED -> RunStatus.CANCELLED
            AgentOutcome.MAX_STEPS, AgentOutcome.ERROR -> RunStatus.ERROR
        },
        task = task,
        steps = steps,
        answer = answer,
        message = if (outcome == AgentOutcome.DONE) "" else answer,
    )
}

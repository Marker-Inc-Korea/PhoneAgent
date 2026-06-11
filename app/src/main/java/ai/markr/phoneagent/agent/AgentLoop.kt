package ai.markr.phoneagent.agent

import ai.markr.phoneagent.agent.model.Action
import ai.markr.phoneagent.agent.model.AgentOutcome
import ai.markr.phoneagent.agent.model.AgentResult
import ai.markr.phoneagent.agent.model.AgentStep
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * Orchestrates the observe -> think -> act loop. Pure Kotlin: depends only on
 * the [DeviceController] and [LlmClient] interfaces, so the whole scenario is
 * unit-testable with fakes.
 */
class AgentLoop(
    private val controller: DeviceController,
    private val llm: LlmClient,
    private val parser: ActionParser = ActionParser(),
    private val dispatcher: ActionDispatcher = ActionDispatcher(controller),
    private val config: AgentConfig = AgentConfig(),
) {
    suspend fun run(
        task: String,
        onStep: (AgentStep) -> Unit = {},
    ): AgentResult {
        if (!controller.isConnected) {
            return AgentResult(AgentOutcome.ERROR, "접근성 서비스가 연결되지 않았습니다.", emptyList())
        }
        val system = PromptBuilder.systemPrompt(task, visionAvailable = llm.supportsVision)
        val history = ArrayList<LlmMessage>()
        val steps = ArrayList<AgentStep>()
        var pendingImage: ByteArray? = null
        var requestVision = false
        var lastResult: String? = null
        val recentActions = ArrayDeque<String>()

        try {
            for (index in 0 until config.maxSteps) {
                coroutineContext.ensureActive()

                val rawSnapshot = controller.snapshot()
                val snapshot = SnapshotPolicy.apply(rawSnapshot)

                val useVision = llm.supportsVision && (requestVision || snapshot.needsVision)
                if (useVision) {
                    pendingImage = controller.screenshotJpeg()
                }
                requestVision = false

                val loopWarning = recentActions.size >= 3 &&
                    recentActions.toSet().size == 1
                // Fold the previous action's result into this observation so the
                // conversation stays strictly user/assistant-alternating (Anthropic requires it).
                val observation = buildString {
                    lastResult?.let { appendLine("이전 행동 결과: $it").appendLine() }
                    append(PromptBuilder.observation(snapshot, loopWarning))
                }
                history.add(LlmMessage(LlmMessage.Role.USER, observation))

                val turn = requestTurn(system, history, pendingImage)
                pendingImage = null
                if (turn == null) {
                    return finalize(AgentOutcome.ERROR, "LLM 응답을 해석하지 못했습니다.", steps)
                }
                history.add(LlmMessage(LlmMessage.Role.ASSISTANT, turn.assistantRaw))

                val action = turn.parsed.action
                when (action) {
                    is Action.Done -> {
                        recordStep(steps, index, turn, "완료", useVision, onStep)
                        return finalize(AgentOutcome.DONE, action.answer, steps)
                    }
                    is Action.Abort -> {
                        recordStep(steps, index, turn, "중단", useVision, onStep)
                        return finalize(AgentOutcome.ABORTED, action.reason, steps)
                    }
                    is Action.Screenshot -> {
                        requestVision = true
                        val result = if (llm.supportsVision) "다음 관측에 화면 이미지를 포함합니다."
                        else "비전 모델이 설정되지 않아 스크린샷을 사용할 수 없습니다."
                        recordStep(steps, index, turn, result, useVision, onStep)
                    }
                    is Action.Wait -> {
                        delay(action.ms.toLong().coerceIn(0, 5000))
                        recordStep(steps, index, turn, "대기 완료", useVision, onStep)
                    }
                    else -> {
                        val result = dispatcher.dispatch(action)
                        recordStep(steps, index, turn, result, useVision, onStep)
                        delay(config.settleMs)
                    }
                }
                lastResult = steps.last().actionResult
                trimHistory(history)
                pushRecent(recentActions, action)
            }
            return finalize(AgentOutcome.MAX_STEPS, "최대 단계(${config.maxSteps})에 도달했습니다.", steps)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return finalize(AgentOutcome.ERROR, "오류: ${e.message}", steps)
        }
    }

    private suspend fun requestTurn(
        system: String,
        history: List<LlmMessage>,
        image: ByteArray?,
    ): TurnResult? {
        repeat(2) { attempt ->
            val raw = try {
                llm.complete(system, history, if (attempt == 0) image else null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return TurnResult(
                    assistantRaw = "{\"thought\":\"오류\",\"action\":{\"type\":\"abort\",\"reason\":\"LLM 호출 실패: ${e.message}\"}}",
                    parsed = ParsedTurn("오류", Action.Abort("LLM 호출 실패: ${e.message}")),
                )
            }
            val parsed = parser.parse(raw)
            if (parsed.isSuccess) return TurnResult(raw, parsed.getOrThrow())
        }
        return null
    }

    private fun recordStep(
        steps: MutableList<AgentStep>,
        index: Int,
        turn: TurnResult,
        result: String,
        usedVision: Boolean,
        onStep: (AgentStep) -> Unit,
    ) {
        val step = AgentStep(index, turn.parsed.thought, turn.parsed.action, result, usedVision)
        steps.add(step)
        onStep(step)
    }

    private fun finalize(outcome: AgentOutcome, answer: String, steps: List<AgentStep>) =
        AgentResult(outcome, answer, steps)

    private fun trimHistory(history: MutableList<LlmMessage>) {
        val max = config.maxHistoryMessages
        // Drop oldest user/assistant pairs so the history still begins with a
        // user turn (required by the Anthropic messages API).
        while (history.size > max && history.size >= 2) {
            history.removeAt(0)
            history.removeAt(0)
        }
    }

    private fun pushRecent(recent: ArrayDeque<String>, action: Action) {
        recent.addLast(action::class.simpleName + actionKey(action))
        while (recent.size > 3) recent.removeFirst()
    }

    private fun actionKey(action: Action): String = when (action) {
        is Action.Tap -> ":${action.id}"
        is Action.Scroll -> ":${action.direction}"
        is Action.SetText -> ":${action.id}"
        else -> ""
    }
}

private data class TurnResult(val assistantRaw: String, val parsed: ParsedTurn)

data class AgentConfig(
    val maxSteps: Int = 20,
    val settleMs: Long = 600,
    val maxHistoryMessages: Int = 16,
)

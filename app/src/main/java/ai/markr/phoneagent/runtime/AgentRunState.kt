package ai.markr.phoneagent.runtime

import ai.markr.phoneagent.agent.model.AgentStep

enum class RunStatus { IDLE, RUNNING, DONE, ABORTED, ERROR, CANCELLED }

data class AgentRunState(
    val status: RunStatus = RunStatus.IDLE,
    val task: String = "",
    val steps: List<AgentStep> = emptyList(),
    val answer: String = "",
    val message: String = "",
) {
    val isRunning: Boolean get() = status == RunStatus.RUNNING
}

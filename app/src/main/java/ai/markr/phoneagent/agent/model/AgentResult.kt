package ai.markr.phoneagent.agent.model

enum class AgentOutcome { DONE, ABORTED, MAX_STEPS, CANCELLED, ERROR }

/** Final result of an agent run. */
data class AgentResult(
    val outcome: AgentOutcome,
    val answer: String,
    val steps: List<AgentStep>,
) {
    val success: Boolean get() = outcome == AgentOutcome.DONE
}

package ai.markr.phoneagent.agent.model

/** One completed turn of the agent loop, for logging and UI display. */
data class AgentStep(
    val index: Int,
    val thought: String,
    val action: Action,
    val actionResult: String,
    val usedVision: Boolean = false,
)

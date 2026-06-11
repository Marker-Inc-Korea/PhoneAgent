package ai.markr.phoneagent.runtime

/**
 * Process-wide stop bus so out-of-Activity surfaces (overlay button, ongoing
 * notification action) can cancel the active run without holding a reference to
 * the runner.
 */
object AgentControl {
    @Volatile
    var onStop: (() -> Unit)? = null

    fun requestStop() {
        onStop?.invoke()
    }
}

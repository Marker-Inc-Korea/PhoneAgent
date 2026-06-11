package ai.markr.phoneagent.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ai.markr.phoneagent.runtime.AgentControl

/** Handles the "중지" action from the ongoing notification. */
class StopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == AgentNotifications.ACTION_STOP) {
            AgentControl.requestStop()
            AgentNotifications.dismiss(context)
        }
    }
}

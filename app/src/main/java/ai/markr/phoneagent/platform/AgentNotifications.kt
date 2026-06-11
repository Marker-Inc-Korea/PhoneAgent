package ai.markr.phoneagent.platform

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ai.markr.phoneagent.R

/** Ongoing "agent is running" notification with a Stop action. */
object AgentNotifications {
    const val CHANNEL_ID = "phoneagent_run"
    const val NOTIF_ID = 1001
    const val ACTION_STOP = "ai.markr.phoneagent.ACTION_STOP"

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "에이전트 실행 상태",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "PhoneAgent가 작업을 수행하는 동안의 진행 상태" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun showRunning(context: Context, status: String) {
        ensureChannel(context)
        val stopIntent = Intent(context, StopReceiver::class.java).setAction(ACTION_STOP)
        val stopPending = PendingIntent.getBroadcast(
            context, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_agent)
            .setContentTitle("PhoneAgent 실행 중")
            .setContentText(status)
            .setStyle(NotificationCompat.BigTextStyle().bigText(status))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "중지", stopPending)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIF_ID, notification) }
    }

    fun dismiss(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(NOTIF_ID) }
    }
}

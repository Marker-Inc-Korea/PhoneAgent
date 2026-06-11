package ai.markr.phoneagent.platform

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/** Resolves an app name or package to a launch intent and starts it. */
class AppLauncher(private val context: Context) {

    /** Common aliases so the LLM can say "Gmail" instead of the package id. */
    private val aliases = mapOf(
        "gmail" to "com.google.android.gm",
        "지메일" to "com.google.android.gm",
        "chrome" to "com.android.chrome",
        "크롬" to "com.android.chrome",
        "settings" to "com.android.settings",
        "설정" to "com.android.settings",
        "play store" to "com.android.vending",
        "youtube" to "com.google.android.youtube",
        "유튜브" to "com.google.android.youtube",
        "messages" to "com.google.android.apps.messaging",
        "메시지" to "com.google.android.apps.messaging",
    )

    fun launch(appOrPackage: String): String {
        val pm = context.packageManager
        val pkg = resolvePackage(pm, appOrPackage)
            ?: return "앱을 찾을 수 없습니다: '$appOrPackage'. ${suggestions(pm, appOrPackage)}"
        val intent = pm.getLaunchIntentForPackage(pkg)
            ?: return "실행할 수 없는 앱입니다: $pkg"
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        context.startActivity(intent)
        return "앱 실행: $pkg"
    }

    /** On a miss, surface launchable app labels so the LLM can retry with a valid name. */
    private fun suggestions(pm: PackageManager, query: String): String {
        val labels = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .asSequence()
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .map { pm.getApplicationLabel(it).toString() }
            .distinct()
        val q = query.trim()
        val ranked = (labels.filter { it.contains(q, ignoreCase = true) }.toList() +
            labels.toList()).distinct().take(12)
        return "설치된 앱 예시: ${ranked.joinToString(", ")}. 정확한 이름으로 다시 open_app 하세요."
    }

    private fun resolvePackage(pm: PackageManager, query: String): String? {
        val q = query.trim()
        aliases[q.lowercase()]?.let { return it }
        // Already a package id we can launch?
        if (q.contains('.') && pm.getLaunchIntentForPackage(q) != null) return q
        // Match by visible application label.
        val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        return installed.firstOrNull {
            pm.getApplicationLabel(it).toString().equals(q, ignoreCase = true)
        }?.packageName
            ?: installed.firstOrNull {
                pm.getApplicationLabel(it).toString().contains(q, ignoreCase = true) &&
                    pm.getLaunchIntentForPackage(it.packageName) != null
            }?.packageName
    }
}

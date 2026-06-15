package ai.markr.phoneagent.platform

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo

/**
 * Resolves an app name or package to a launch intent and starts it.
 *
 * Enumerates apps via the launcher intent (declared in <queries>) instead of
 * QUERY_ALL_PACKAGES — that permission strongly triggers Play Protect and Play
 * policy review, and the launcher query gives us exactly the launchable apps we
 * need for open_app.
 */
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
        val apps = launchableApps(pm)
        val pkg = resolvePackage(pm, apps, appOrPackage)
            ?: return "앱을 찾을 수 없습니다: '$appOrPackage'. ${suggestions(apps, appOrPackage)}"
        val intent = pm.getLaunchIntentForPackage(pkg)
            ?: return "실행할 수 없는 앱입니다: $pkg"
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        context.startActivity(intent)
        return "앱 실행: $pkg"
    }

    private data class LaunchApp(val label: String, val pkg: String)

    /** Every app with a launcher icon — visible to us via the <queries> declaration. */
    private fun launchableApps(pm: PackageManager): List<LaunchApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { ri: ResolveInfo ->
                LaunchApp(
                    label = ri.loadLabel(pm).toString(),
                    pkg = ri.activityInfo.packageName,
                )
            }
            .distinctBy { it.pkg }
    }

    private fun resolvePackage(pm: PackageManager, apps: List<LaunchApp>, query: String): String? {
        val q = query.trim()
        aliases[q.lowercase()]?.let { return it }
        if (q.contains('.') && apps.any { it.pkg == q }) return q
        return apps.firstOrNull { it.label.equals(q, ignoreCase = true) }?.pkg
            ?: apps.firstOrNull { it.label.contains(q, ignoreCase = true) }?.pkg
    }

    /** On a miss, surface launchable app names so the LLM can retry with a valid one. */
    private fun suggestions(apps: List<LaunchApp>, query: String): String {
        val q = query.trim()
        val labels = apps.map { it.label }.distinct()
        val ranked = (labels.filter { it.contains(q, ignoreCase = true) } + labels)
            .distinct()
            .take(12)
        return "설치된 앱 예시: ${ranked.joinToString(", ")}. 정확한 이름으로 다시 open_app 하세요."
    }
}

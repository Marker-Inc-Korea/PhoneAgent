package ai.markr.phoneagent.agent

import ai.markr.phoneagent.agent.model.Action
import ai.markr.phoneagent.agent.model.GlobalAction
import ai.markr.phoneagent.agent.model.ScrollDirection
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

/**
 * Turns raw LLM text into an [Action]. Tolerant of markdown fences and prose
 * around the JSON: it extracts the first balanced `{...}` object, then maps it.
 */
class ActionParser(moshi: Moshi = Moshi.Builder().build()) {

    private val mapAdapter = moshi.adapter<Map<String, Any?>>(
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java),
    )

    fun parse(raw: String): Result<ParsedTurn> {
        val json = extractJsonObject(raw)
            ?: return Result.failure(IllegalArgumentException("응답에서 JSON 객체를 찾지 못했습니다."))
        val map = runCatching { mapAdapter.fromJson(json) }.getOrNull()
            ?: return Result.failure(IllegalArgumentException("JSON 파싱 실패: $json"))
        val thought = (map["thought"] as? String).orEmpty()
        @Suppress("UNCHECKED_CAST")
        val actionMap = map["action"] as? Map<String, Any?>
            ?: return Result.failure(IllegalArgumentException("'action' 필드가 없습니다."))
        return toAction(actionMap).map { ParsedTurn(thought, it) }
    }

    private fun toAction(a: Map<String, Any?>): Result<Action> {
        val type = (a["type"] as? String)?.trim()?.lowercase()
            ?: return Result.failure(IllegalArgumentException("action.type 누락"))
        return runCatching {
            when (type) {
                "open_app" -> Action.OpenApp(str(a, "app"))
                "tap" -> Action.Tap(int(a, "id"))
                "tap_xy" -> Action.TapXy(int(a, "x"), int(a, "y"))
                "set_text" -> Action.SetText(int(a, "id"), str(a, "text"))
                "enter", "submit" -> Action.Enter((a["id"] as? Number)?.toInt())
                "scroll" -> Action.Scroll(
                    direction = ScrollDirection.valueOf(str(a, "direction").uppercase()),
                    id = (a["id"] as? Number)?.toInt(),
                )
                "swipe" -> Action.Swipe(
                    int(a, "x1"), int(a, "y1"), int(a, "x2"), int(a, "y2"),
                    (a["duration"] as? Number)?.toInt() ?: (a["duration_ms"] as? Number)?.toInt() ?: 300,
                )
                "global" -> Action.Global(GlobalAction.valueOf(str(a, "name").uppercase()))
                "screenshot" -> Action.Screenshot
                "wait" -> Action.Wait((a["ms"] as? Number)?.toInt() ?: 500)
                "done" -> Action.Done(str(a, "answer"))
                "abort" -> Action.Abort((a["reason"] as? String) ?: "사유 미지정")
                else -> throw IllegalArgumentException("알 수 없는 action.type: $type")
            }
        }
    }

    private fun str(m: Map<String, Any?>, k: String): String =
        (m[k] as? String) ?: throw IllegalArgumentException("'$k' 문자열 필드 누락")

    private fun int(m: Map<String, Any?>, k: String): Int =
        (m[k] as? Number)?.toInt() ?: throw IllegalArgumentException("'$k' 숫자 필드 누락")

    /** Scans for the first top-level balanced JSON object, ignoring braces in strings. */
    private fun extractJsonObject(raw: String): String? {
        val start = raw.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escape = false
        for (i in start until raw.length) {
            val c = raw[i]
            if (inString) {
                when {
                    escape -> escape = false
                    c == '\\' -> escape = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return raw.substring(start, i + 1)
                }
            }
        }
        return null
    }
}

data class ParsedTurn(val thought: String, val action: Action)

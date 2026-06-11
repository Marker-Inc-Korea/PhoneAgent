package ai.markr.phoneagent.agent

import ai.markr.phoneagent.agent.model.Snapshot

/** Builds the system prompt and per-turn observation text for the LLM. */
object PromptBuilder {

    fun systemPrompt(task: String, visionAvailable: Boolean): String = buildString {
        appendLine("당신은 안드로이드 폰을 직접 조작하는 범용 GUI 에이전트입니다.")
        appendLine("특정 앱에 한정되지 않습니다. 어떤 앱이든 open_app 으로 열어 사람이 화면을 보고 손가락으로")
        appendLine("조작하듯, 주어진 화면(SCREEN)을 읽고 한 번에 하나의 행동으로 목표를 끝까지 수행하세요.")
        appendLine()
        appendLine("[목표]")
        appendLine(task)
        appendLine()
        appendLine("[규칙]")
        appendLine("- 매 턴 정확히 하나의 JSON 객체만 출력합니다. 그 외 설명/마크다운/코드펜스 금지.")
        appendLine("- 형식: {\"thought\": \"왜 이 행동을 하는지 한국어로\", \"action\": { ... }}")
        appendLine("- 화면의 각 요소는 [id]로 표시됩니다. tap/set_text는 그 id를 사용합니다.")
        appendLine("- 목표를 달성했으면 즉시 done 으로 끝내고, 불가능하면 abort 합니다.")
        appendLine("- done.answer 에는 사용자에게 보고할 한국어 요약을 담습니다.")
        appendLine()
        appendLine("[사용 가능한 action]")
        appendLine("{\"type\":\"open_app\",\"app\":\"Gmail\"}            앱 실행(이름 또는 패키지)")
        appendLine("{\"type\":\"tap\",\"id\":3}                          요소 탭")
        appendLine("{\"type\":\"tap_xy\",\"x\":540,\"y\":1200}           좌표 탭")
        appendLine("{\"type\":\"set_text\",\"id\":2,\"text\":\"안녕\"}    입력란에 텍스트 입력")
        appendLine("{\"type\":\"enter\",\"id\":2}                       입력란에서 엔터(검색/전송) — id 생략 가능")
        appendLine("{\"type\":\"scroll\",\"direction\":\"down\",\"id\":5} 스크롤(id 생략 가능)")
        appendLine("{\"type\":\"swipe\",\"x1\":..,\"y1\":..,\"x2\":..,\"y2\":..} 스와이프")
        appendLine("{\"type\":\"global\",\"name\":\"back\"}              back|home|recents|notifications")
        if (visionAvailable) {
            appendLine("{\"type\":\"screenshot\"}                          텍스트로 부족할 때 화면을 이미지로 확인")
        }
        appendLine("{\"type\":\"wait\",\"ms\":500}                       화면 안정화 대기")
        appendLine("{\"type\":\"done\",\"answer\":\"...\"}               작업 완료 보고")
        appendLine("{\"type\":\"abort\",\"reason\":\"...\"}              수행 불가")
    }

    fun observation(snapshot: Snapshot, loopWarning: Boolean): String = buildString {
        if (loopWarning) {
            appendLine("[주의] 같은 행동을 반복하고 있습니다. 다른 접근을 시도하거나 done/abort 하세요.")
        }
        if (snapshot.error != null) {
            appendLine("SCREEN 오류: ${snapshot.error}")
            return@buildString
        }
        append("SCREEN package=").append(snapshot.packageName)
        append(" activity=").append(snapshot.activity).appendLine()
        if (snapshot.needsVision) {
            appendLine("(텍스트 요소가 거의 없습니다. 필요하면 screenshot 으로 화면을 확인하세요.)")
        }
        for (n in snapshot.nodes) {
            append("[").append(n.id).append("] ").append(n.role)
            if (n.text.isNotBlank()) append(" \"").append(n.text).append("\"")
            val flags = buildList {
                if (n.clickable) add("clickable")
                if (n.editable) add("editable")
                if (n.scrollable) add("scrollable")
            }
            if (flags.isNotEmpty()) append(" (").append(flags.joinToString(",")).append(")")
            appendLine()
        }
    }
}

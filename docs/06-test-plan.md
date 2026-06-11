# PhoneAgent — 테스트 계획 & 결과

## 테스트 피라미드
| 층위 | 위치 | 대상 | 실행 환경 |
|---|---|---|---|
| 단위 (순수) | `src/test` | model/parser/policy/prompt/dispatcher | JVM, 결정적 |
| 시나리오 (순수) | `src/test` AgentLoopTest | 에이전트 루프 전체(가짜 LLM+컨트롤러) | JVM |
| 통합 (순수) | `src/test` AgentRunnerTest | Runner↔Loop↔설정↔기록 | JVM |
| 클라이언트 | `src/test` LlmClientTest | Anthropic/OpenAI/Gemini 요청·응답 | JVM + MockWebServer |
| 계측 (디바이스) | 수동 | 접근성/제스처/Keystore/스크린샷 | 실제 폰 |

## 자동 테스트 매핑 (수용 기준 → 테스트)
- AC-1 시나리오 완주 → `AgentLoopTest.completes_gmail_scenario`, `AgentRunnerTest.successful_run_reaches_done_and_persists`
- AC-2 tap→click → `ActionDispatcherTest.tap_calls_click`
- AC-3 노드 상한·절단 → `SnapshotPolicyTest.caps_node_count`, `truncates_long_text_to_max`
- AC-4 펜스 JSON 파싱 → `ActionParserTest.parses_inside_markdown_fence`, `ignores_braces_inside_strings`
- AC-5 키 미설정 차단 → `AgentRunnerTest.blocks_when_not_configured`
- AC-6 비전 폴백 → `SnapshotPolicyTest.flags_vision_when_sparse`, `AgentLoopTest.uses_vision_when_screenshot_requested`
- AC-7 키 암호화 → 계측(디바이스): KeystoreCrypto round-trip 및 DataStore 원시값≠평문
- AC-8 루프 경고 → `AgentLoopTest.injects_loop_warning_after_three_repeats`, `PromptBuilderTest.observation_injects_loop_warning`

## 디바이스 수동 검증 절차 (자고 일어나서 5분)
1. APK 설치 → 앱 실행.
2. 온보딩에서 "접근성 설정 열기" → PhoneAgent 접근성 ON. (+ 알림 허용)
3. 설정에서 제공자 선택, API 키 입력, 텍스트 모델(+선택 비전 모델) 입력 → "연결 테스트" 성공 확인 → 저장.
4. 홈에서 칩 "Gmail에서 새 메일 확인하고 요약해줘" 탭 → 실행.
5. 상단 오버레이에 단계 표시, Gmail이 열리고 목록을 읽은 뒤 홈 화면 답변 카드에 요약 표시 확인.
6. 중간에 "중지" 동작 확인.

## 빌드 게이트
- `./gradlew testDebugUnitTest` — 모든 JVM 테스트 통과.
- `./gradlew assembleDebug` / `assembleRelease` — APK 산출.

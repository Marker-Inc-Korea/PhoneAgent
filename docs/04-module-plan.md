# PhoneAgent — 모듈별 개발 계획 (초소형 단위)

원칙: **1 모듈 = 1 책임 = 1~2 파일 ≤ ~150 LOC**. 각 모듈은 인터페이스 경계로 격리되어
가짜(fake)로 단독 테스트 가능. 순수 코틀린 모듈을 먼저(JVM 테스트), 안드로이드 의존 모듈을 나중에.

## 빌드 순서 & 의존
```
M0  Gradle 스캐폴드  ────────────────┐
M1  model(Action/Observation/Step)  │ (의존 없음, 순수)
M2  ActionParser (M1)               │
M3  PromptBuilder (M1)              │  ← JVM 테스트 가능
M4  DeviceController/LlmClient IF(M1)│
M5  ActionDispatcher (M1,M4)        │
M6  SnapshotPolicy (M1)             │
M7  AgentLoop (M1..M6)              ┘  ← 시나리오 테스트(가짜 LLM+가짜 컨트롤러)
M8  KeystoreCrypto + AgentSettings + SettingsRepository
M9  LLM clients (Anthropic/OpenAI/Gemini) + Factory  ← MockWebServer 테스트
M10 Accessibility: TreeSerializer / GestureHelper / ScreenshotCapture / AppLauncher
M11 PhoneAccessibilityService + AccessibilityController (M10) + OverlayStatus
M12 RunHistory(Room) + AgentRunner(runtime)
M13 DI(Hilt) + Application + MainActivity + 테마 + Nav
M14 UI 화면: Onboarding / Home / Settings (+components)
```

## 모듈 명세
### M0 — Gradle 스캐폴드
- 산출: settings/build gradle, libs.versions.toml, wrapper, gradle.properties, manifest 뼈대.
- 검증: `./gradlew help` (또는 tasks) 성공.

### M1 — Domain Model (순수)
- `Action`(sealed: OpenApp, Tap, TapXy, SetText, Scroll, Swipe, Global, Screenshot, Wait, Done, Abort)
- `UiNode`(id, role, text, clickable, editable, bounds), `Snapshot`(pkg, activity, nodes, needsVision)
- `AgentStep`(index, thought, action, actionResult), `AgentResult`(success, answer, steps)
- 검증: 데이터 클래스 동등성/기본값 테스트.

### M2 — ActionParser (순수)
- 입력: LLM 원문 문자열. 출력: `Result<Action>`.
- 관용 처리: 마크다운 펜스 제거, 첫 균형 중괄호 JSON 추출, 알 수 없는 타입→실패.
- 검증(AC-4): 펜스/잡텍스트 포함·각 액션 타입·잘못된 타입 케이스.

### M3 — PromptBuilder (순수)
- system 프롬프트(역할·액션 스펙·JSON 강제), 관측 텍스트, 단계 히스토리, 루프 경고(AC-8) 조립.
- 검증: 관측 포함·루프 경고 주입·이미지 모드 분기.

### M4 — DeviceController / LlmClient 인터페이스 (순수)
- `DeviceController`: snapshot, click(id), setText(id,text), scroll(dir,id?), tapXy, swipe,
  global(name), screenshotJpeg(), openApp(name), isConnected.
- `LlmClient`: suspend complete(system, messages, imageJpeg?) → String; supportsVision.
- 검증: 컴파일/시그니처(별도 테스트 불필요, M5/M7에서 fake로 사용).

### M5 — ActionDispatcher (순수)
- Action → DeviceController 호출 매핑, 결과 문자열(성공/실패 사유) 반환. done/abort/screenshot는 루프가 처리.
- 검증(AC-2): 각 액션이 올바른 컨트롤러 메서드 호출(가짜 컨트롤러 호출 기록).

### M6 — SnapshotPolicy (순수)
- 노드 상한(120)·텍스트 절단(80, 코드포인트 경계)·비가시/빈 노드 제외·needsVision 판단(의미노드<3).
- 검증(AC-3,AC-6): 200노드→≤120, 절단, 폴백 플래그.

### M7 — AgentLoop (순수, 핵심)
- 의존: DeviceController, LlmClient, PromptBuilder, ActionParser, ActionDispatcher, SnapshotPolicy.
- 흐름: 관측→프롬프트→LLM→파싱(실패시 재요청1회)→디스패치→스텝기록→종료조건(done/abort/max/취소).
- VLM 폴백: needsVision 또는 screenshot 액션 시 다음 complete에 이미지 첨부.
- 검증(AC-1,AC-8): 스크립트 LLM으로 open_app→snapshot→tap→done 완주, 루프경고, max 중단, 취소.

### M8 — Settings & Crypto
- `KeystoreCrypto`: AndroidKeyStore AES/GCM encrypt/decrypt(base64). (계측 또는 robolectric 경계 — JVM에선 인터페이스로 우회, 로컬 테스트는 평문우회 fake)
- `AgentSettings`(provider, apiKey, textModel, visionModel, baseUrl, maxSteps).
- `SettingsRepository`: DataStore 저장/로드, 키 암호화 적용. 검증(AC-7): 저장 원시값≠평문(계측).

### M9 — LLM Clients
- 공통 HTTP(OkHttp) + JSON 빌드/파싱. 제공자별 엔드포인트·헤더·메시지·이미지(base64) 포맷.
- `LlmClientFactory(settings)` → 적절한 클라이언트. 검증: MockWebServer로 요청 본문/응답 파싱.

### M10 — Accessibility helpers
- `TreeSerializer`: AccessibilityNodeInfo 트리 → List<UiNode> (id 부여, bounds).
- `GestureHelper`: dispatchGesture tap/swipe. `ScreenshotCapture`: takeScreenshot→JPEG.
- `AppLauncher`: 앱이름/패키지 → Intent 실행. (계측 테스트 또는 로직 단위 분리)

### M11 — Service & Controller
- `PhoneAccessibilityService`: 노드 루트 보관, 제스처/스크린샷 API 위임, 오버레이.
- `AccessibilityController`: DeviceController 구현(서비스 위임). `OverlayStatus`: 상태바+중지.

### M12 — RunHistory & Runner
- Room: `RunRecord`, `RunHistoryDao`, `AppDatabase`.
- `AgentRunner`: 코루틴 스코프에서 AgentLoop 실행, 상태 Flow, 취소, 기록 저장.

### M13 — DI & App shell
- Hilt 모듈(SettingsRepo, Factory, Runner, DB 제공), `PhoneAgentApp`, `MainActivity`, 테마, Nav.

### M14 — UI
- Onboarding(권한), Home(작업입력·실행·로그·답변), Settings(제공자/키/모델/연결테스트).
- 검증: ViewModel 상태 전이 단위테스트(가짜 Runner/Repo) + Compose 프리뷰.

## 테스트 전략
- **단위/시나리오(JVM, src/test)**: M1~M9, ViewModel — fake 주입. 빠르고 결정적. ★메인 검증 축.
- **계측(androidTest)**: KeystoreCrypto, 접근성/제스처 — 환경 의존이라 빌드만 보장, 디바이스 시 실행.
- **빌드 게이트**: `./gradlew testDebugUnitTest assembleDebug` 통과 = 완료.

# PhoneAgent — 아키텍처 & 파일 계획

## 아키텍처 개요 (레이어)
```
┌─────────────────────────── UI (Compose) ───────────────────────────┐
│ HomeScreen   SettingsScreen   OnboardingScreen   RunDetailScreen     │
│        ↑ ViewModel(StateFlow)         ↑ Hilt 주입                     │
├──────────────────────────── Agent Core ────────────────────────────┤
│ AgentLoop ── PromptBuilder ── ActionParser ── ActionDispatcher       │
│      │            │                                  │               │
│   LlmClient (provider 추상)                    DeviceController(IF)   │
├──────────────────── Platform / Accessibility ──────────────────────┤
│ PhoneAccessibilityService ⇒ AccessibilityController(DeviceController)│
│   TreeSerializer   GestureHelper   ScreenshotCapture   AppLauncher   │
├──────────────────────────── Data ──────────────────────────────────┤
│ SettingsRepository(DataStore)  KeystoreCrypto  RunHistory(Room)      │
└─────────────────────────────────────────────────────────────────────┘
```

핵심 분리: **AgentLoop는 순수 코틀린**(안드로이드 의존 없음)으로 `DeviceController`와
`LlmClient` 인터페이스에만 의존 → JVM 단위테스트로 시나리오 전체를 검증 가능.

## 패키지 루트
`ai.markr.phoneagent`

## 파일 트리 (구현 대상)
```
settings.gradle.kts
build.gradle.kts                      # 루트
gradle.properties
gradle/wrapper/gradle-wrapper.properties (+ jar)
gradle/libs.versions.toml
gradlew  gradlew.bat
app/
  build.gradle.kts
  proguard-rules.pro
  src/main/AndroidManifest.xml
  src/main/res/...                    # 문자열, 테마, 아이콘, 접근성 설정 xml
  src/main/java/ai/markr/phoneagent/
    PhoneAgentApp.kt                  # @HiltAndroidApp
    di/AppModule.kt                   # Hilt 제공
    # ---- model (순수) ----
    agent/model/Action.kt             # sealed Action + 직렬화 무관 모델
    agent/model/Observation.kt        # Snapshot/UiNode/Observation
    agent/model/AgentStep.kt          # 한 단계 기록
    agent/model/AgentResult.kt        # 최종 결과
    # ---- agent core (순수 코틀린) ----
    agent/DeviceController.kt         # interface: snapshot/click/setText/...
    agent/LlmClient.kt               # interface: complete(messages, image?)
    agent/ActionParser.kt            # JSON(관용) → Action
    agent/PromptBuilder.kt           # system+관측 → 메시지
    agent/ActionDispatcher.kt        # Action → DeviceController 호출
    agent/AgentLoop.kt               # 루프 오케스트레이션
    agent/SnapshotPolicy.kt          # 노드 상한/절단/VLM 폴백 판단
    # ---- platform / accessibility ----
    platform/PhoneAccessibilityService.kt
    platform/AccessibilityController.kt   # DeviceController 구현
    platform/TreeSerializer.kt
    platform/GestureHelper.kt
    platform/ScreenshotCapture.kt
    platform/AppLauncher.kt
    platform/OverlayStatus.kt
    platform/ServiceLocatorBridge.kt # 서비스 인스턴스 ↔ Hilt 연결
    # ---- llm clients ----
    llm/AnthropicClient.kt
    llm/OpenAiClient.kt
    llm/GeminiClient.kt
    llm/LlmClientFactory.kt
    llm/Json.kt                      # Moshi/직접 JSON 유틸
    # ---- data ----
    data/SettingsRepository.kt
    data/KeystoreCrypto.kt
    data/AgentSettings.kt            # 설정 데이터 클래스
    data/RunHistoryDao.kt  data/RunRecord.kt  data/AppDatabase.kt
    # ---- runtime glue ----
    runtime/AgentRunner.kt           # ViewModel↔AgentLoop, 코루틴/취소
    runtime/AgentRunState.kt
    # ---- ui ----
    ui/MainActivity.kt
    ui/theme/{Color,Theme,Type}.kt
    ui/nav/AppNav.kt
    ui/home/{HomeScreen,HomeViewModel}.kt
    ui/settings/{SettingsScreen,SettingsViewModel}.kt
    ui/onboarding/{OnboardingScreen}.kt
    ui/components/{StepLogList,PrimaryButton,...}.kt
  src/test/java/...                  # JVM 단위/시나리오 테스트
  src/androidTest/...                # (선택) 계측 테스트
```

## 의존성 (sibling 프로젝트에서 검증된 캐시 버전 재사용)
AGP 8.7.3 / Kotlin 2.0.21 / KSP 2.0.21-1.0.27 / Hilt 2.52 / Compose BOM 2025.01.00 /
Room 2.7.1 / DataStore 1.1.1 / security-crypto 1.1.0-alpha06 / OkHttp 4.12 / Moshi 1.15.1 /
coroutines 1.10.1 / JUnit4 / Turbine / MockWebServer. (전부 ~/.gradle 캐시에 존재 → 오프라인성 빌드 가능)

## 데이터 흐름 (S1)
HomeVM.run(task) → AgentRunner.start(task) → AgentLoop.run:
  loop: controller.snapshot() → SnapshotPolicy(트리/VLM) → PromptBuilder → LlmClient.complete()
       → ActionParser → ActionDispatcher.dispatch() → 상태 emit → (done/abort/max로 종료)
결과 → RunHistory 저장 → HomeScreen에 최종 답변·단계 로그 표시.

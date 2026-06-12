# PhoneAgent

안드로이드 **접근성 서비스 기반 온디바이스 GUI 에이전트**. 자연어 지시를 받아, 사람이 화면을
보고 조작하듯 **어떤 앱이든** 열어서 텍스트(접근성 트리)로 읽고 조작한다. 트리가 빈약하면
**스크린샷 + 비전 모델(VLM)** 로 자동 폴백한다. 특정 앱 전용이 아니며, Gmail 새 메일 요약은
대표 검증 시나리오일 뿐이다.

## 다운로드 후 바로 쓰기 (API 키만 설정)
1. **Releases** 탭에서 최신 `PhoneAgent-x.y.z-release.apk` 를 폰으로 다운로드.
2. 파일 관리자에서 APK를 탭해 설치(“출처를 알 수 없는 앱” 설치 허용).
   - PC에서 설치하려면: `adb install -r PhoneAgent-x.y.z-release.apk`
3. 앱 실행 → 온보딩에서 **접근성 권한** 허용(+선택: 알림 허용).
4. **설정**에서 제공자(Anthropic / OpenAI호환 / Gemini) 선택 → **API 키만 입력** →
   (모델은 기본값이 채워져 있음) → **연결 테스트** 성공 확인 → 저장.
5. 홈에서 예시 칩을 탭하거나 직접 지시 입력 → **실행**.

> 그게 전부다. 코드를 빌드할 필요 없이 APK만 받아 API 키를 넣으면 동작한다.

## 할 수 있는 일 (앱 무관)
- "Gmail에서 새 메일 확인하고 요약해줘"
- "유튜브에서 lofi 검색해줘"
- "크롬 열어서 내일 날씨 검색해줘"
- "설정에서 와이파이 화면 열어줘"
- "지금 화면에 뭐가 있는지 읽어줘"

설치된 **모든 앱**이 대상이다. 에이전트는 `open_app` 으로 이름/패키지로 앱을 찾아 열고,
`tap·set_text·enter·scroll·swipe·global(back/home/recents)·screenshot` 으로 조작하며,
목표 달성 시 한국어로 요약 보고한다.

## 음성 대화 (핸즈프리)
마이크 버튼을 탭해 **말로 지시**하고, 에이전트의 **답변을 음성으로** 듣는다.
- **STT/TTS는 기기 내장 엔진 사용**(Android `SpeechRecognizer` + `TextToSpeech`) — 온디바이스,
  무료, 한국어 지원, 모델 번들 불필요. 내장 엔진이 없거나 언어 미지원이면 오픈소스
  **sherpa-onnx(Kokoro-82M / Piper)** 엔진을 끼울 수 있게 인터페이스(`SpeechSynthesizer`/
  `SpeechTranscriber`)로 분리되어 있다.
- **끼어들기(barge-in)**: 답변을 읽는 도중 마이크를 탭하거나 말을 시작하면 **즉시 말이 끊기고**
  바로 듣기로 전환된다(`VoiceCoordinator` 상태기계, 단위 테스트로 검증).
- **음성용 짧은 답변**: 음성 모드에서는 시스템 프롬프트가 LLM에게 답변을 1~2문장으로
  짧게 말하도록 지시한다(장황 금지).
- 설정에서 음성 on/off와 말하기 속도(0.5~2.0x)를 조절한다.

## 동작 방식
```
관측(접근성 트리 → 압축 텍스트)  →  LLM(JSON 액션 1개)  →  디스패처(탭/입력/엔터/스크롤/제스처)  →  반복
                              ↘ 트리 빈약 시 스크린샷 + VLM 폴백 ↗
```
- 에이전트 코어(`agent/`)는 **순수 코틀린** — 안드로이드 의존 없이 `DeviceController`/`LlmClient`
  인터페이스에만 의존하므로 시나리오 전체를 JVM 단위테스트로 검증한다.
- 조작은 접근성 노드 액션 우선, 실패 시 좌표 제스처로 폴백.
- 실행 중 **상단 오버레이 + 진행 알림**(중지 버튼 포함)으로 상태를 보여주고 언제든 중단 가능.
- 모든 실행은 **기록** 화면에 저장된다(작업/결과/단계 수/시각).
- API 키는 AndroidKeyStore(AES-GCM)로 암호화되어 저장된다.

## 모델/키 설정
앱 **설정** 화면에서 구성한다(코드/저장소에 키를 넣지 않는다).
- **Anthropic**: 기본 `claude-sonnet-4-6`, 키 `sk-ant-...`.
- **OpenAI 호환**: Base URL 변경 가능(사내 게이트웨이·로컬 서버 등). 로컬 `http://`
  엔드포인트(localhost/사설 IP)도 허용된다. 기본 `gpt-4o`.
- **Gemini**: 기본 `gemini-2.0-flash`, 키는 URL 쿼리로 전달.
- **비전 모델**을 비우면 스크린샷/VLM 폴백이 비활성화되고 텍스트 트리만으로 동작한다.

## 소스에서 빌드
```
./gradlew testDebugUnitTest      # JVM 단위/시나리오/통합 테스트
./gradlew assembleDebug          # 디버그 APK
./gradlew assembleRelease        # 릴리스 APK
```
- 필요 도구: JDK 17, Android SDK(platform 35, build-tools 35). Gradle은 wrapper로 자동.
- **서명**: 저장소에는 서명 키가 포함되지 않는다. 본인 키로 서명하려면 루트에
  `keystore.properties` 를 만들고(`storeFile/storePassword/keyAlias/keyPassword`) `*.jks` 를 둔다.
  없으면 릴리스 빌드는 디버그 키로 서명되어 그대로 설치 가능하다.

## 문서
- `docs/00-concept.md` · `01-scenarios.md` · `02-prd.md` · `03-architecture-fileplan.md`
  · `04-module-plan.md` · `05-gui-ux.md` · `06-test-plan.md`

## 프라이버시
화면 텍스트와 (폴백 시) 스크린샷은 **사용자가 설정한 LLM 제공자에게만** 전송된다(원격 제공자는
HTTPS, 로컬 엔드포인트만 평문 허용). 그 외 서버 전송·텔레메트리·로그 수집은 없다.

## 라이선스
MIT — `LICENSE` 참고.

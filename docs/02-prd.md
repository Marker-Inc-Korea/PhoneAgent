# PhoneAgent — PRD (Product Requirements Document)

## 1. 개요
PhoneAgent는 안드로이드 접근성 서비스 기반 온디바이스 GUI 에이전트다. 사용자의 자연어 지시를
받아, 사람이 화면을 보고 조작하듯 임의의 앱을 텍스트(접근성 트리) + 시각(스크린샷·VLM)으로
이해하고 조작한다. v1의 검증 시나리오는 Gmail 새 이메일 요약이다.

## 2. 페르소나
- **주 사용자**: 개발자/파워유저. 자기 폰에서 반복 작업을 자연어로 자동화하고 싶다.
- **기술 수준**: API 키 발급·입력 가능. 접근성 권한 부여 동의 가능.

## 3. 기능 요구사항 (FR)
| ID | 요구사항 | 우선순위 |
|---|---|---|
| FR-1 | 접근성 서비스로 현재 화면의 노드 트리를 구조화 텍스트로 수집 | P0 |
| FR-2 | 노드 단위 액션(tap, set_text, scroll, global back/home/recents) 수행 | P0 |
| FR-3 | 좌표 제스처(tap_xy, swipe) 폴백 수행 | P0 |
| FR-4 | 스크린샷 캡처 후 VLM에 이미지로 전달(폴백) | P0 |
| FR-5 | 패키지/앱이름으로 대상 앱 실행(open_app) | P0 |
| FR-6 | LLM 에이전트 루프: 관측→사고→액션→관측 반복, done/abort로 종료 | P0 |
| FR-7 | 제공자(Anthropic/OpenAI 호환/Gemini)·모델·키를 설정에서 구성 | P0 |
| FR-8 | API 키 Keystore 암호화 저장, 연결 테스트 | P0 |
| FR-9 | 홈 화면에서 작업 입력·실행, 단계별 진행 로그·최종 답변 표시 | P0 |
| FR-10 | 실행 중 오버레이 상태/중지 + 알림 | P1 |
| FR-11 | 권한 온보딩(접근성/알림) | P0 |
| FR-12 | 실행 기록 저장(작업·결과·단계 로그) | P1 |

## 4. 비기능 요구사항 (NFR)
- **보안**: API 키는 AndroidKeyStore AES-GCM으로 암호화. 로그·UI에 평문 노출 금지(마스킹).
- **프라이버시**: 화면 텍스트·스크린샷은 사용자가 설정한 제공자에게만 전송. 외부 전송 사실을 UI에 명시.
- **성능**: 스냅샷 직렬화 < 300ms(노드 120 상한). 단계당 LLM 왕복 외 오버헤드 < 500ms.
- **호환성**: minSdk 30(스크린샷 API), targetSdk 35. 휴대폰 세로 기준.
- **신뢰성**: 네트워크 오류 재시도. 단계 상한·동일동작 루프 차단으로 무한루프 방지.

## 5. 액션 프로토콜 (LLM ↔ 에이전트 계약)
LLM은 매 턴 정확히 하나의 JSON 객체를 반환한다:
```json
{"thought": "왜 이 행동을 하는지", "action": {"type": "tap", "id": 12}}
```
지원 action.type:
- `open_app` {app} — 앱 이름 또는 패키지
- `tap` {id} | `tap_xy` {x,y}
- `set_text` {id, text}
- `scroll` {direction: up|down|left|right, id?}
- `swipe` {x1,y1,x2,y2,duration?}
- `global` {name: back|home|recents|notifications}
- `screenshot` {} — 다음 관측에 이미지 포함 요청
- `wait` {ms}
- `done` {answer} — 작업 완료, 사용자에게 보고할 최종 답변
- `abort` {reason} — 수행 불가

관측(Observation)은 다음 텍스트로 LLM에 제공:
```
SCREEN package=com.google.android.gm activity=ConversationListActivity
[0] <FrameLayout>
  [1] "받은편지함" (clickable)
  [2] EditText "메일 검색"
  [3] "홍길동 · 회의 일정 안내 · 내일 오후..." (clickable)
  ...
```

## 6. 수용 기준 (Given-When-Then, 모두 테스트 가능)
- AC-1: *Given* 접근성 서비스 활성·키 설정됨, *When* "Gmail 새 메일 요약" 실행, *Then*
  에이전트가 open_app→snapshot→(tap/scroll)*→done 순으로 진행하고 done.answer가 비어있지 않다.
- AC-2: *Given* 스크립트 LLM이 tap(id=3) 반환, *When* 디스패처가 처리, *Then* 해당 노드의
  ACTION_CLICK가 호출된다(가짜 컨트롤러로 검증).
- AC-3: *Given* 노드 200개 화면, *When* 스냅샷 직렬화, *Then* 출력 노드 ≤120, 각 텍스트 ≤80자.
- AC-4: *Given* LLM이 마크다운 펜스로 감싼 JSON 반환, *When* 파싱, *Then* 액션이 정상 추출된다.
- AC-5: *Given* 키 미설정, *When* 실행 시도, *Then* 실행이 시작되지 않고 설정 유도 메시지 반환.
- AC-6: *Given* 의미 노드 2개뿐, *When* 스냅샷, *Then* 컨트롤러가 스크린샷 폴백을 트리거한다.
- AC-7: *Given* 평문 API 키 저장, *When* DataStore 원시값 조회, *Then* 평문과 다르다(암호화됨).
- AC-8: *Given* 동일 액션 3연속, *When* 다음 프롬프트 구성, *Then* 루프 경고가 주입된다.

## 7. 마일스톤 → 모듈 매핑
docs/04-module-plan.md의 M0~M14 모듈로 분해. 각 모듈은 단일 책임·소형(≤~150 LOC 목표)·
독립 테스트 가능하도록 설계한다.

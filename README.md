# 에이전트 브라우저

Claude에게 말로 시키면 페이지를 읽고 대신 조작해 주는 안드로이드 브라우저. GeckoView(파이어폭스 엔진) 위에서 Anthropic API를 직접 호출한다.

## 빌드와 설치

- Android Studio(JBR 포함)와 Android SDK 37.2가 필요하다. 없는 플랫폼은 첫 빌드 때 자동으로 받는다.
- 폰을 USB 디버깅으로 연결한 뒤:

```
gradlew.bat :app:installDebug
```

- 테스트: `gradlew.bat :app:testDebugUnitTest` (PC), `gradlew.bat :app:connectedDebugAndroidTest` (연결된 폰)
- Gradle이 `Unable to establish loopback connection`으로 실패하면 임시 폴더 경로 문제다. `TEMP`, `TMP`와 `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=<공백 없는 ASCII 경로> -Djava.io.tmpdir=<같은 경로>`를 설정하고 다시 실행한다.

## 쓰는 법

1. 설정 → Anthropic API 키를 입력하고 저장한다. 키는 Android Keystore 키로 암호화해 이 기기에만 저장된다.
2. 하단의 Claude 버튼을 누르고 할 일을 적는다. 예: "이 페이지 요약해줘", "운동화 검색해줘".
3. 결제, 주문, 전송, 삭제, 계정 설정 변경은 "진행"을 눌러야 실행된다.
4. 실행 중에는 언제든 "정지"로 멈출 수 있다. 한 번의 지시에서 동작은 최대 25회다.

## Chrome에서 가져오기

- 북마크: PC Chrome → 북마크 관리자 → 북마크 내보내기 → HTML 파일을 폰으로 옮긴 뒤, 설정 → 북마크 가져오기.
- 비밀번호: Chrome → 설정 → Google 비밀번호 관리자 → 설정 → 비밀번호 내보내기 → 설정 → 비밀번호 가져오기. 가져온 뒤 원본 CSV 파일은 지운다.
- 자동 동기화는 없다. Chrome에서 바뀐 내용은 다시 가져와야 한다.

## 안전장치

- 비밀번호 입력창의 값은 Claude에게 보내는 페이지 내용에서 항상 비운다. 비밀번호 입력창이 보이는 화면은 캡처하지 않는다.
- Claude는 비밀번호 입력창에 입력할 수 없고, 저장된 비밀번호를 읽는 도구도 없다.
- 페이지 안의 문장은 지시가 아니라 데이터로 다루도록 시스템 프롬프트에 명시했다. 프롬프트 인젝션을 완전히 막지는 못하므로 확인 절차가 마지막 방어선이다.
- 페이지 내용은 Claude에게 시킨 동안에만 API로 전송된다.

## 알려진 한계

- 탭은 하나다. 방문 기록, 다운로드 관리, 광고 차단이 없다.
- 페이지가 띄우는 창 중 로그인 저장/계정 선택만 처리한다. 자바스크립트 alert/confirm, `<select>` 드롭다운 선택 창, 파일 업로드 선택 창은 뜨지 않는다.
- 카카오 로그인이나 결제 앱을 부르는 `intent://` 링크는 처리하지 않는다.
- iframe 안의 요소는 Claude가 읽거나 조작하지 못한다.
- 스토어 배포용이 아니다. 배포하려면 API 키를 앱에 두지 않는 중계 서버가 필요하다.

## 구조

| 위치 | 역할 |
| --- | --- |
| `browser/` | GeckoView 세션과 브라우저 화면 |
| `assets/agent/` | 페이지를 읽고 조작하는 내장 확장 프로그램 |
| `page/` | 확장 프로그램과 통신하는 PageDriver |
| `agent/` | Anthropic API 클라이언트, 도구 정의, 조작 루프, 확인 판별 |
| `chat/` | 채팅 패널 |
| `bookmarks/`, `passwords/` | Chrome 파일 파서, 저장소, 화면, 자동 입력 연결 |
| `settings/` | API 키 암호화 보관, 모델 선택, 가져오기 |

설계 문서: https://claude.ai/artifact/Jjk5HtMriXYkjowb4ym9kk · 구현 계획: `docs/superpowers/plans/`

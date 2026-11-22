<div align="center">

<img src="docs/logo.svg" width="112" height="112" alt="Agentic Browser logo">

# Agentic Browser

**An Android browser you can hand over to Claude.**<br>
It reads the page, clicks, types and navigates for you.

**English** · [한국어](#한국어)

[![Release](https://img.shields.io/github/v/release/HPotty36/claude-android-browser?color=0F6B5C)](https://github.com/HPotty36/claude-android-browser/releases/latest)
[![License: MIT](https://img.shields.io/badge/license-MIT-0F6B5C)](LICENSE)
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-0F6B5C?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.4-0F6B5C?logo=kotlin&logoColor=white)
![Engine: GeckoView](https://img.shields.io/badge/engine-GeckoView-0F6B5C?logo=firefoxbrowser&logoColor=white)

<img src="docs/screenshots/browser.png" width="250" alt="The browser">&nbsp;
<img src="docs/screenshots/chat.png" width="250" alt="The Claude panel">&nbsp;
<img src="docs/screenshots/tabs.png" width="250" alt="The tab list">

[Install](#install) · [Usage](#usage) · [How it works](#how-it-works) · [Safety](#safety) · [Build](#build)

</div>

---

On a desktop you can hand browser work to Claude. On a phone there has been no good way to. This app is an ordinary browser until you tap the ring-shaped **Claude button** in the bottom bar and type something like "Summarize this page" or "Search for running shoes". All it needs is an Anthropic API key; there is no server in between.

> [!NOTE]
> A personal project. It is unofficial and not affiliated with Anthropic. API usage is billed to your own account.

## Features

| | |
| --- | --- |
| **Page control** | Read, click, type, scroll, navigate, screenshot. The page wears a teal border while Claude has the controls. |
| **Confirmation** | Paying, ordering, sending, deleting and changing account settings run only after you tap Continue. |
| **Resizable panel** | Drag the top of the Claude panel to any height. Pull it to the top, or tap the button, for full screen. |
| **Always stoppable** | A stop button stays on screen while Claude works, and one request is capped at 25 actions. |
| **One-handed** | The address, back, forward, bookmark, Claude, tabs and menu all sit in one bar at the bottom. |
| **Tabs** | Keep several pages open; switch or close them from the list. Links that ask for a new window open a new tab. |
| **Search** | DuckDuckGo by default; Google, Naver, Bing and Brave Search in settings. |
| **Ad blocking** | uBlock Origin is built in and on by default; a switch in settings turns it off. |
| **Encrypted DNS** | Site lookups go over HTTPS (Cloudflare) by default, which gets past DNS-based blocking. Sites that support ECH also hide their name while connecting. |
| **Desktop site** | Tablets, unfolded foldables and external displays get the desktop version of pages; phones get the mobile one. Settings can force either. |
| **Translation** | Pages that are in neither Korean nor English are translated into Korean by themselves, through the Anthropic API (Claude Haiku). ⋮ → Translate page does it for any page, and the icon in the address bar brings the original back. Settings can turn the automatic part off or translate into English instead. |
| **Keyboard and wide windows** | With a keyboard attached: Ctrl+L address, Ctrl+T / Ctrl+W new and close tab, Ctrl+Tab switch tab, Ctrl+R or F5 reload, Alt+← / Alt+→ back and forward, Ctrl+D bookmark, Ctrl+J Claude (the cursor lands in its message box), Esc cancels. In the message box Enter sends and Shift+Enter starts a new line. Right-click a link to open it in a new tab or copy it. On a window wider than it is tall (a monitor, a tablet, a phone on its side) Claude sits beside the page. |
| **Hands off to apps** | A link meant for an app, such as a sign-in returning to its app or a payment app, asks before it opens there. |
| **Google sign-in** | Built on the Firefox engine (GeckoView), so the Google sign-in that WebView apps are refused works. |
| **Import from Chrome** | Bookmarks (HTML) and passwords (CSV), with saved logins filled in on sign-in pages. |
| **Kept on the device** | The API key and passwords are encrypted with an Android Keystore key and never leave the phone. |
| **Updates itself** | Checks GitHub for a new release once a day and from settings, then downloads and installs it in the app. |
| **English and Korean** | Follows the system language, or pick one in settings. Claude answers in the same language. |

## Install

1. Download `agentic-browser-*.apk` from the [latest release](https://github.com/HPotty36/claude-android-browser/releases/latest).
2. Open the file on your phone. The first time, Android asks you to allow installs from that source.

Runs on arm64 devices with Android 8.0 or later. The APK is about 100 MB because it carries the browser engine.

Later versions install from inside the app: **Settings → Update**. Android asks once to let the app install updates, and accepts only an APK signed with the same key as the one installed.

## Usage

1. Save your key under **Settings → Anthropic API key**. Keys are created in the [Claude Console](https://console.anthropic.com/).
2. Tap the ring-shaped **Claude button** in the bottom bar and type a task.
3. When an action needs confirming, choose Continue or Cancel on the bar that appears.

Bring your Chrome data over under **Settings → Import from Chrome**.

- **Bookmarks:** the HTML file from ⋮ > Export bookmarks in the bookmark manager of Chrome on a computer
- **Passwords:** the CSV file from Chrome Settings > Google Password Manager > Settings > Export passwords. Delete the CSV once it is imported.

Nothing syncs. Changes made in Chrome later need importing again.

## How it works

```mermaid
sequenceDiagram
    participant U as You
    participant A as App (AgentLoop)
    participant C as Claude API
    participant P as Page (built-in extension)
    U->>A: "Search for running shoes"
    loop until done or stopped (25 actions at most)
        A->>C: conversation + tools
        C-->>A: tool call (read_page, click, type …)
        opt paying, sending, deleting …
            A->>U: Go ahead?
            U-->>A: Continue / Cancel
        end
        A->>P: run it
        P-->>A: result
    end
    A-->>U: answer
```

Claude is sent a numbered text list of the buttons, links and inputs rather than screenshots. It costs fewer tokens and clicks land where they should.

```
[1] link "Log in"
[2] textbox "Search" value=""
[3] button "Search"
--- page text ---
…
```

| Where | What |
| --- | --- |
| `browser/` | Tabs, GeckoView sessions and the browser screen |
| `assets/agent/` | The built-in extension that reads and operates the page |
| `page/` | `PageDriver`, which talks to the extension |
| `agent/` | API client, tool definitions, the action loop, the confirmation check |
| `chat/` | The Claude panel |
| `bookmarks/`, `passwords/` | Chrome file parsers, storage, autofill |
| `settings/` | Encrypted API key, model, search engine, language, import |
| `update/` | Release check, APK download, hand-off to the system installer |
| `ui/` | Theme and every string in both languages |

## Safety

- **Translation sends page text to Anthropic.** With automatic translation on (the default), the visible text of a page in a foreign language goes to the Anthropic API as soon as the page opens, and is billed to your key. A page with a password field is never translated by itself. Turn it off in Settings → Translation if you do not want that.
- **Passwords never reach Claude.** The value of a password field is always blanked in what Claude reads, and such a page is never captured. Claude cannot type into a password field and has no tool that reads saved passwords.
- **Confirmation has two layers.** An action is confirmed when Claude marks it risky, and also when the button's label says pay, order, send, delete or the like.
- **Text on the page is not treated as instructions.** No defence against prompt injection is complete, so on accounts that matter, read the confirmation before you continue.
- **Page content is sent to the Anthropic API only while Claude is working** on your request.
- **The install permission is used only for the app's own updates**, downloaded from this repository's releases and checked against the size and SHA-256 GitHub lists.

## Known limits

- Translation covers the text on the page when it finished loading, up to about 60,000 characters. Text a page adds later (endless feeds, comments that load on scroll) stays as it was until you translate again.
- No history or download manager. Open tabs are gone when the app is closed.
- Claude works in the tab you are looking at. Switch tabs mid-task and it carries on in the new one.
- JavaScript alert/confirm dialogs, `<select>` dropdowns and file pickers do not appear.
- Sign-ins that return to their app through an ordinary `https://` link (an app link) stay in the browser. Right-click menus and dragging the side panel wider are not there yet.
- Claude cannot read or operate elements inside iframes.
- Encrypted DNS does not get past blocking that reads the site name from the connection (SNI) unless the site supports ECH. The ad blocker has no per-site switch yet.
- Password autofill and the import screens are covered by automated tests down to storage; they have not had much use on real sites.

## Build

Android Studio (with its bundled JBR) is required. Missing SDK platforms download on the first build.

```bash
./gradlew :app:installDebug              # install on a connected phone
./gradlew :app:testDebugUnitTest         # unit tests
./gradlew :app:connectedDebugAndroidTest # tests that run on the phone
./gradlew :app:assembleRelease           # release APK
```

Debug builds install as a separate app, `dev.agentbrowser.debug`, and leave the data of a release install alone. Release signing reads `keystore.properties` in the project root (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`); without it the debug key is used.

<details>
<summary><code>Unable to establish loopback connection</code> on Windows</summary>

The temp folder path is the problem. Point `TEMP` and `TMP` at a path without spaces or non-ASCII characters, set the following, and run again.

```
JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=<path> -Djava.io.tmpdir=<path>
```

</details>

## License

[MIT](LICENSE). The browser engine, [GeckoView](https://mozilla.github.io/geckoview/), is under the Mozilla Public License 2.0, and the bundled ad blocker, [uBlock Origin](https://github.com/gorhill/uBlock), is under the GPLv3. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

---

<div align="center">

# 한국어

**Claude가 페이지를 읽고 대신 조작해 주는 안드로이드 브라우저**

[English](#agentic-browser) · **한국어**

[설치](#설치) · [쓰는 법](#쓰는-법) · [동작 방식](#동작-방식) · [안전장치](#안전장치) · [직접 빌드](#직접-빌드)

</div>

앱 이름은 **에이전틱 브라우저**입니다. 데스크톱에서는 브라우저 작업을 Claude에게 맡길 수 있지만 폰에서는 방법이 마땅치 않습니다. 이 앱은 평소에는 평범한 브라우저이고, 필요할 때 하단 바의 고리 모양 **Claude 버튼**을 눌러 "이 페이지 요약해 줘", "운동화 검색해 줘"처럼 시키면 됩니다. Anthropic API 키만 있으면 되고 별도 서버는 없습니다.

> [!NOTE]
> 개인 프로젝트이며 Anthropic과 관련이 없는 비공식 앱입니다. API 사용 요금은 본인 계정에 청구됩니다.

## 기능

| | |
| --- | --- |
| **페이지 조작** | 읽기, 클릭, 입력, 스크롤, 이동, 화면 캡처. Claude가 조작하는 동안 페이지에 청록색 테두리가 둘러집니다. |
| **확인 절차** | 결제, 주문, 전송, 삭제, 계정 설정 변경은 "진행"을 눌러야 실행됩니다. |
| **패널 크기 조절** | Claude 패널의 위쪽을 끌어 원하는 높이로 조절합니다. 끝까지 올리거나 버튼을 누르면 전체 화면이 됩니다. |
| **언제든 중지** | 작업 중에는 중지 버튼이 항상 화면에 있고, 한 번의 지시에서 동작은 최대 25회입니다. |
| **한 손 조작** | 주소창과 뒤로, 앞으로, 북마크, Claude 버튼, 탭, 메뉴가 화면 아래의 바 하나에 모여 있습니다. |
| **탭** | 여러 페이지를 열어 두고 목록에서 바꾸거나 닫습니다. 새 창으로 열리는 링크는 새 탭이 됩니다. |
| **검색** | 기본 검색 엔진은 DuckDuckGo이고, 설정에서 Google, 네이버, Bing, Brave Search로 바꿀 수 있습니다. |
| **광고 차단** | uBlock Origin이 내장돼 기본으로 켜져 있고, 설정에서 끌 수 있습니다. |
| **암호화 DNS** | 주소 조회를 기본으로 HTTPS(Cloudflare)로 보내 DNS 차단을 지나갑니다. ECH를 지원하는 사이트는 접속할 때 이름도 가려집니다. |
| **데스크톱 사이트** | 태블릿, 펼친 폴더블, 외부 디스플레이에서는 데스크톱 버전으로, 폰에서는 모바일 버전으로 엽니다. 설정에서 한쪽으로 고정할 수 있습니다. |
| **번역** | 한국어도 영어도 아닌 페이지는 Anthropic API(Claude Haiku)로 알아서 한국어로 번역합니다. ⋮ → 페이지 번역으로 어느 페이지든 번역할 수 있고, 주소창의 아이콘을 누르면 원문으로 돌아갑니다. 설정에서 자동 번역을 끄거나 영어로 번역하게 바꿀 수 있습니다. |
| **키보드와 넓은 창** | 키보드를 연결하면 Ctrl+L 주소창, Ctrl+T / Ctrl+W 새 탭과 탭 닫기, Ctrl+Tab 탭 전환, Ctrl+R 또는 F5 새로고침, Alt+← / Alt+→ 뒤로와 앞으로, Ctrl+D 북마크, Ctrl+J Claude(커서가 입력창으로 갑니다), Esc 취소를 씁니다. Claude 입력창에서는 Enter로 보내고 Shift+Enter로 줄을 바꿉니다. 링크를 오른쪽 클릭하면 새 탭에서 열거나 주소를 복사합니다. 가로가 세로보다 긴 창(모니터, 태블릿, 가로로 돌린 폰)에서는 Claude 패널이 페이지 옆에 놓입니다. |
| **앱으로 넘기기** | 로그인 후 앱으로 돌아가는 링크나 결제 앱 링크처럼 다른 앱을 여는 링크는 확인을 받고 그 앱에서 엽니다. |
| **구글 로그인** | 파이어폭스 엔진(GeckoView)을 써서 WebView 앱에서 막히는 구글 로그인이 됩니다. |
| **Chrome에서 가져오기** | 북마크(HTML)와 비밀번호(CSV)를 가져오고, 로그인 화면에서 자동으로 채웁니다. |
| **기기 안에 보관** | API 키와 비밀번호는 Android Keystore 키로 암호화해 기기에만 저장합니다. |
| **앱 안에서 업데이트** | 하루에 한 번, 그리고 설정에서 누를 때 GitHub의 새 릴리스를 확인하고 앱 안에서 내려받아 설치합니다. |
| **한국어와 영어** | 시스템 언어를 따르거나 설정에서 고릅니다. Claude도 같은 언어로 답합니다. |

## 설치

1. [최신 릴리스](https://github.com/HPotty36/claude-android-browser/releases/latest)에서 `agentic-browser-*.apk`를 내려받습니다.
2. 폰에서 파일을 열어 설치합니다. 처음이면 "출처를 알 수 없는 앱 설치"를 허용해야 합니다.

Android 8.0 이상의 arm64 기기에서 동작합니다. 브라우저 엔진이 들어 있어 APK가 100MB 정도입니다.

이후 버전은 앱 안의 **설정 → 업데이트**에서 설치합니다. 처음 한 번 이 앱의 업데이트 설치를 허용해야 하고, 설치된 앱과 같은 키로 서명된 APK만 설치됩니다.

## 쓰는 법

1. **설정 → Anthropic API 키**에 키를 저장합니다. 키는 [Claude Console](https://console.anthropic.com/)에서 만듭니다.
2. 하단 바의 고리 모양 **Claude 버튼**을 누르고 할 일을 적습니다.
3. 확인이 필요한 동작이 나오면 화면에 뜨는 "진행" 또는 "취소"를 고릅니다.

Chrome 데이터는 **설정 → Chrome에서 가져오기**에서 옮깁니다.

- **북마크:** PC Chrome의 북마크 관리자에서 ⋮ > 북마크 내보내기로 만든 HTML 파일
- **비밀번호:** Chrome 설정 > Google 비밀번호 관리자 > 설정 > 비밀번호 내보내기로 만든 CSV 파일. 가져온 뒤 CSV 파일은 삭제하세요.

자동 동기화는 없습니다. Chrome에서 바뀐 내용은 다시 가져와야 합니다.

## 동작 방식

흐름 다이어그램과 코드 구조는 [영어 설명](#how-it-works)과 같습니다. Claude에게는 스크린샷 대신 버튼, 링크, 입력창에 번호를 붙인 텍스트 목록을 보냅니다. 토큰이 적게 들고 클릭이 정확합니다.

## 안전장치

- **번역은 페이지의 글을 Anthropic으로 보냅니다.** 자동 번역이 켜져 있으면(기본값) 외국어 페이지를 열 때 화면에 보이는 글이 바로 Anthropic API로 가고, 요금은 내 키에 청구됩니다. 비밀번호 입력창이 있는 페이지는 알아서 번역하지 않습니다. 원하지 않으면 설정 → 번역에서 끄세요.
- **비밀번호는 Claude에게 가지 않습니다.** 비밀번호 입력창의 값은 페이지 내용에서 항상 비우고, 그런 화면은 캡처하지 않습니다. Claude는 비밀번호 입력창에 입력할 수 없고 저장된 비밀번호를 읽는 도구도 없습니다.
- **확인 절차는 두 겹입니다.** Claude가 위험하다고 표시한 동작과, 버튼 글자에 결제·주문·전송·삭제 같은 단어가 있는 동작 모두 확인을 받습니다.
- **페이지 안의 문장은 지시로 다루지 않습니다.** 다만 프롬프트 인젝션을 완전히 막는 방법은 없으므로, 중요한 계정에서는 확인 창의 내용을 읽고 진행하세요.
- **페이지 내용은 Claude에게 시킨 동안에만** Anthropic API로 전송됩니다.
- **설치 권한은 이 앱의 업데이트에만 씁니다.** 이 저장소의 릴리스에서 내려받고, GitHub에 적힌 크기와 SHA-256과 맞는지 확인한 뒤 설치합니다.

## 알려진 한계

- 번역은 페이지가 다 열린 시점의 글을 약 6만 자까지 다룹니다. 그 뒤에 페이지가 덧붙이는 글(끝없이 이어지는 피드, 스크롤하면 불러오는 댓글)은 다시 번역하기 전까지 그대로입니다.
- 방문 기록과 다운로드 관리가 없습니다. 열어 둔 탭은 앱을 종료하면 사라집니다.
- Claude는 지금 보고 있는 탭에서만 동작합니다. 작업 중에 탭을 바꾸면 바꾼 탭에서 이어 갑니다.
- 자바스크립트 alert/confirm, `<select>` 드롭다운, 파일 업로드 선택 창이 뜨지 않습니다.
- 일반 `https://` 주소(앱 링크)로 앱에 돌아가는 로그인은 브라우저에 머뭅니다. 마우스 오른쪽 클릭 메뉴와 옆 패널 너비 조절은 아직 없습니다.
- iframe 안의 요소는 Claude가 읽거나 조작하지 못합니다.
- 암호화 DNS는 접속 과정에서 사이트 이름(SNI)을 읽는 차단은 지나가지 못합니다. 사이트가 ECH를 지원할 때만 예외입니다. 광고 차단을 사이트별로 끄는 기능은 아직 없습니다.
- 비밀번호 자동 입력과 가져오기 화면은 자동 테스트로 저장소까지만 검증했고, 실제 사이트에서의 동작은 충분히 써 보지 못했습니다.

## 직접 빌드

명령과 서명 설정은 [영어 설명](#build)과 같습니다. 디버그 빌드는 `dev.agentbrowser.debug`라는 별도 앱으로 설치되어 릴리스로 설치한 앱의 데이터를 건드리지 않습니다.

## 라이선스

[MIT](LICENSE). 브라우저 엔진 [GeckoView](https://mozilla.github.io/geckoview/)는 Mozilla Public License 2.0을, 함께 들어 있는 광고 차단기 [uBlock Origin](https://github.com/gorhill/uBlock)은 GPLv3를 따릅니다. [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)를 참고하세요.

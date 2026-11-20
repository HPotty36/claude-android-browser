package dev.agentbrowser.ui

import androidx.compose.runtime.staticCompositionLocalOf

/** What the user picked in settings. */
enum class Language(val id: String) { SYSTEM("system"), ENGLISH("en"), KOREAN("ko") }

fun languageById(id: String?): Language = Language.entries.firstOrNull { it.id == id } ?: Language.SYSTEM

/** The strings for the chosen language; "system" means Korean on a Korean device and English anywhere else. */
fun stringsFor(language: Language, deviceLanguage: String): Strings = when (language) {
    Language.ENGLISH -> En
    Language.KOREAN -> Ko
    Language.SYSTEM -> if (deviceLanguage == "ko") Ko else En
}

val LocalStrings = staticCompositionLocalOf<Strings> { En }

/** Every piece of text the app shows. What goes to Claude (tool descriptions, tool results) is not here. */
interface Strings {
    /** The language Claude is told to answer in. */
    val replyLanguage: String

    // Browser
    val searchHint: String
    val back: String
    val forward: String
    val reload: String
    val stopLoading: String
    val clear: String
    val editAddress: String
    val addBookmark: String
    val removeBookmark: String
    val menu: String
    val claudePanel: String
    val openChat: String
    val newTab: String
    val bookmarks: String
    val home: String
    val settings: String
    val closeTab: String
    val claudeWorking: String
    val stop: String
    val suggestSummary: String
    val suggestPrice: String
    fun tabCount(count: Int): String

    // Chat
    val newChat: String
    val expandPanel: String
    val shrinkPanel: String
    val closePanel: String
    val resizePanel: String
    val send: String
    val taskHint: String
    val working: String
    val confirmTitle: String
    val cancel: String
    val proceed: String
    val openSettings: String
    val checkApiKey: String
    val connectionLost: String
    val stepLimitReached: String
    val stopped: String

    // What Claude is doing, as shown in the chat
    val actionReadPage: String
    val actionScrollUp: String
    val actionScrollDown: String
    val actionScreenshot: String
    fun actionClick(label: String): String
    fun actionType(label: String, text: String, submit: Boolean): String
    fun actionNavigate(target: String): String
    fun unnamedElement(ref: Int): String

    // Settings
    val apiKeyTitle: String
    val apiKeyNote: String
    val save: String
    val delete: String
    val showKey: String
    val hideKey: String
    val model: String
    val customModel: String
    val modelId: String
    val searchEngine: String
    val naver: String
    val language: String
    val languageSystem: String
    val languageEnglish: String
    val languageKorean: String
    fun apiKeySaved(lastFour: String): String

    // Desktop site
    val desktopSite: String
    val desktopSiteAuto: String
    val desktopSiteAlways: String
    val desktopSiteNever: String

    // Protection
    val protection: String
    val adBlock: String
    val adBlockNote: String
    val encryptedDns: String
    val encryptedDnsNote: String

    // Update
    val update: String
    val checkForUpdates: String
    val checkingForUpdates: String
    val upToDate: String
    val installingUpdate: String
    val updateFailed: String
    val retry: String
    val updateMenu: String
    val updateDebugNote: String
    fun currentVersion(version: String): String
    fun updateAvailable(version: String): String
    fun downloadUpdate(megabytes: Int): String
    fun downloadingUpdate(percent: Int): String

    // Import from Chrome
    val importTitle: String
    val importBookmarks: String
    val importBookmarksNote: String
    val importBookmarksHelp: String
    val importPasswords: String
    val importPasswordsNote: String
    val importPasswordsHelp: String
    val savedPasswords: String
    val howToExport: String
    val noBookmarksInFile: String
    val notAPasswordCsv: String
    val deleteCsvTitle: String
    val deleteCsvBody: String
    val ok: String
    fun importedBookmarks(added: Int, skipped: Int): String
    fun importedPasswords(added: Int, skipped: Int): String

    // Bookmarks and passwords
    val noBookmarks: String
    val bookmarkDeleted: String
    val undo: String
    val confirmIdentity: String
    val noSavedPasswords: String
    val noUsername: String
    val showPassword: String
    val copyPassword: String
    val copied: String
    val deletePasswordTitle: String
    val savePasswordTitle: String
    val notNow: String
    val chooseAccount: String
}

object En : Strings {
    override val replyLanguage = "English"

    override val searchHint = "Search or enter address"
    override val back = "Back"
    override val forward = "Forward"
    override val reload = "Reload"
    override val stopLoading = "Stop loading"
    override val clear = "Clear"
    override val editAddress = "Edit address"
    override val addBookmark = "Add bookmark"
    override val removeBookmark = "Remove bookmark"
    override val menu = "Menu"
    override val claudePanel = "Claude panel"
    override val openChat = "Open chat"
    override val newTab = "New tab"
    override val bookmarks = "Bookmarks"
    override val home = "Home"
    override val settings = "Settings"
    override val closeTab = "Close tab"
    override val claudeWorking = "Claude is working"
    override val stop = "Stop"
    override val suggestSummary = "Summarize this page"
    override val suggestPrice = "Find the price here"
    override fun tabCount(count: Int) = if (count == 1) "1 tab" else "$count tabs"

    override val newChat = "New chat"
    override val expandPanel = "Expand to full screen"
    override val shrinkPanel = "Shrink panel"
    override val closePanel = "Close panel"
    override val resizePanel = "Drag to resize the panel"
    override val send = "Send"
    override val taskHint = "Give Claude a task"
    override val working = "Working"
    override val confirmTitle = "Go ahead?"
    override val cancel = "Cancel"
    override val proceed = "Continue"
    override val openSettings = "Open settings"
    override val checkApiKey = "Check your API key."
    override val connectionLost = "Connection lost."
    override val stepLimitReached = "Stopped after the most actions one request allows."
    override val stopped = "Stopped."

    override val actionReadPage = "Read the page"
    override val actionScrollUp = "Scroll up"
    override val actionScrollDown = "Scroll down"
    override val actionScreenshot = "Take a screenshot"
    override fun actionClick(label: String) = "Click '$label'"
    override fun actionType(label: String, text: String, submit: Boolean) =
        "Type '$text' into '$label'" + if (submit) " and submit" else ""
    override fun actionNavigate(target: String) = "Go to $target"
    override fun unnamedElement(ref: Int) = "element [$ref]"

    override val apiKeyTitle = "Anthropic API key"
    override val apiKeyNote = "Stored encrypted, on this device only."
    override val save = "Save"
    override val delete = "Delete"
    override val showKey = "Show key"
    override val hideKey = "Hide key"
    override val model = "Model"
    override val customModel = "Custom"
    override val modelId = "Model ID"
    override val searchEngine = "Search engine"
    override val naver = "Naver"
    override val language = "Language"
    override val languageSystem = "System"
    override val languageEnglish = "English"
    override val languageKorean = "한국어"
    override fun apiKeySaved(lastFour: String) = "Saved · ····$lastFour"

    override val desktopSite = "Desktop site"
    override val desktopSiteAuto = "Large screens"
    override val desktopSiteAlways = "Always"
    override val desktopSiteNever = "Never"

    override val protection = "Protection"
    override val adBlock = "Block ads"
    override val adBlockNote = "uBlock Origin blocks ads and trackers"
    override val encryptedDns = "Encrypted DNS"
    override val encryptedDnsNote =
        "Looks up sites over HTTPS through Cloudflare, which gets past DNS blocking. Sites that support ECH also keep their name hidden while connecting."

    override val update = "Update"
    override val checkForUpdates = "Check for updates"
    override val checkingForUpdates = "Checking…"
    override val upToDate = "You have the latest version"
    override val installingUpdate = "Opening the installer…"
    override val updateFailed = "The update didn't go through"
    override val retry = "Try again"
    override val updateMenu = "Update available"
    override val updateDebugNote = "Debug builds don't update themselves"
    override fun currentVersion(version: String) = "Version $version"
    override fun updateAvailable(version: String) = "Version $version is available"
    override fun downloadUpdate(megabytes: Int) = "Download and install ($megabytes MB)"
    override fun downloadingUpdate(percent: Int) = "Downloading… $percent%"

    override val importTitle = "Import from Chrome"
    override val importBookmarks = "Import bookmarks"
    override val importBookmarksNote = "An HTML file exported from Chrome"
    override val importBookmarksHelp =
        "In Chrome on a computer, open the bookmark manager, choose ⋮ > Export bookmarks, and move the file to this device."
    override val importPasswords = "Import passwords"
    override val importPasswordsNote = "A CSV file exported from Chrome"
    override val importPasswordsHelp = "Chrome Settings > Google Password Manager > Settings > Export passwords"
    override val savedPasswords = "Saved passwords"
    override val howToExport = "How to export"
    override val noBookmarksInFile = "No bookmarks in that file. Pick an HTML file exported from Chrome."
    override val notAPasswordCsv = "That is not a password CSV exported from Chrome."
    override val deleteCsvTitle = "Delete the CSV file"
    override val deleteCsvBody = "It holds your passwords unencrypted."
    override val ok = "OK"
    override fun importedBookmarks(added: Int, skipped: Int) =
        "Imported $added ${if (added == 1) "bookmark" else "bookmarks"}" + if (skipped > 0) " ($skipped skipped)" else ""
    override fun importedPasswords(added: Int, skipped: Int) =
        "Imported $added ${if (added == 1) "password" else "passwords"}" + if (skipped > 0) " ($skipped skipped)" else ""

    override val noBookmarks = "No bookmarks"
    override val bookmarkDeleted = "Bookmark deleted"
    override val undo = "Undo"
    override val confirmIdentity = "Confirm it's you with your screen lock."
    override val noSavedPasswords = "No saved passwords."
    override val noUsername = "No username"
    override val showPassword = "Show password"
    override val copyPassword = "Copy password"
    override val copied = "Copied"
    override val deletePasswordTitle = "Delete this password?"
    override val savePasswordTitle = "Save this password?"
    override val notNow = "Not now"
    override val chooseAccount = "Choose an account"
}

object Ko : Strings {
    override val replyLanguage = "Korean"

    override val searchHint = "검색어 또는 주소 입력"
    override val back = "뒤로"
    override val forward = "앞으로"
    override val reload = "새로고침"
    override val stopLoading = "로딩 중지"
    override val clear = "지우기"
    override val editAddress = "주소 편집"
    override val addBookmark = "북마크에 추가"
    override val removeBookmark = "북마크에서 삭제"
    override val menu = "메뉴"
    override val claudePanel = "Claude 패널"
    override val openChat = "대화 열기"
    override val newTab = "새 탭"
    override val bookmarks = "북마크"
    override val home = "홈"
    override val settings = "설정"
    override val closeTab = "탭 닫기"
    override val claudeWorking = "Claude가 작업 중"
    override val stop = "중지"
    override val suggestSummary = "이 페이지 요약해 줘"
    override val suggestPrice = "여기서 가격 찾아 줘"
    override fun tabCount(count: Int) = "탭 ${count}개"

    override val newChat = "새 대화"
    override val expandPanel = "전체 화면으로 키우기"
    override val shrinkPanel = "패널 줄이기"
    override val closePanel = "패널 접기"
    override val resizePanel = "끌어서 패널 크기 조절"
    override val send = "보내기"
    override val taskHint = "시킬 일 입력"
    override val working = "작업 중"
    override val confirmTitle = "진행할까요?"
    override val cancel = "취소"
    override val proceed = "진행"
    override val openSettings = "설정 열기"
    override val checkApiKey = "API 키를 확인하세요."
    override val connectionLost = "연결이 끊겼어요."
    override val stepLimitReached = "한 번에 할 수 있는 동작 수를 넘어 멈췄어요."
    override val stopped = "중지했어요."

    override val actionReadPage = "페이지 읽기"
    override val actionScrollUp = "위로 스크롤"
    override val actionScrollDown = "아래로 스크롤"
    override val actionScreenshot = "화면 캡처"
    override fun actionClick(label: String) = "'$label' 클릭"
    override fun actionType(label: String, text: String, submit: Boolean) =
        "'$label'에 '$text' 입력" + if (submit) " 후 제출" else ""
    override fun actionNavigate(target: String) = "이동: $target"
    override fun unnamedElement(ref: Int) = "요소 [$ref]"

    override val apiKeyTitle = "Anthropic API 키"
    override val apiKeyNote = "이 기기에만 암호화해 저장해요."
    override val save = "저장"
    override val delete = "삭제"
    override val showKey = "키 보기"
    override val hideKey = "키 숨기기"
    override val model = "모델"
    override val customModel = "직접 입력"
    override val modelId = "모델 ID"
    override val searchEngine = "검색 엔진"
    override val naver = "네이버"
    override val language = "언어"
    override val languageSystem = "시스템"
    override val languageEnglish = "English"
    override val languageKorean = "한국어"
    override fun apiKeySaved(lastFour: String) = "저장됨 · ····$lastFour"

    override val desktopSite = "데스크톱 사이트"
    override val desktopSiteAuto = "큰 화면에서"
    override val desktopSiteAlways = "항상"
    override val desktopSiteNever = "안 함"

    override val protection = "보호"
    override val adBlock = "광고 차단"
    override val adBlockNote = "uBlock Origin으로 광고와 추적기를 막습니다"
    override val encryptedDns = "암호화 DNS"
    override val encryptedDnsNote =
        "Cloudflare를 거쳐 HTTPS로 주소를 조회해 DNS 차단을 지나갑니다. ECH를 지원하는 사이트는 접속할 때 이름도 가려집니다."

    override val update = "업데이트"
    override val checkForUpdates = "업데이트 확인"
    override val checkingForUpdates = "확인하는 중…"
    override val upToDate = "최신 버전입니다"
    override val installingUpdate = "설치 화면을 여는 중…"
    override val updateFailed = "업데이트하지 못했습니다"
    override val retry = "다시 시도"
    override val updateMenu = "업데이트 있음"
    override val updateDebugNote = "디버그 빌드는 업데이트하지 않습니다"
    override fun currentVersion(version: String) = "버전 $version"
    override fun updateAvailable(version: String) = "새 버전 $version"
    override fun downloadUpdate(megabytes: Int) = "다운로드 후 설치 (${megabytes}MB)"
    override fun downloadingUpdate(percent: Int) = "다운로드 중… $percent%"

    override val importTitle = "Chrome에서 가져오기"
    override val importBookmarks = "북마크 가져오기"
    override val importBookmarksNote = "Chrome에서 내보낸 HTML 파일"
    override val importBookmarksHelp = "PC Chrome의 북마크 관리자에서 ⋮ > 북마크 내보내기로 만든 파일을 이 기기로 옮기세요."
    override val importPasswords = "비밀번호 가져오기"
    override val importPasswordsNote = "Chrome에서 내보낸 CSV 파일"
    override val importPasswordsHelp = "Chrome 설정 > Google 비밀번호 관리자 > 설정 > 비밀번호 내보내기"
    override val savedPasswords = "저장된 비밀번호"
    override val howToExport = "내보내는 방법"
    override val noBookmarksInFile = "북마크가 없는 파일이에요. Chrome에서 내보낸 HTML 파일을 골라 주세요."
    override val notAPasswordCsv = "Chrome에서 내보낸 비밀번호 CSV 파일이 아니에요."
    override val deleteCsvTitle = "CSV 파일을 삭제하세요"
    override val deleteCsvBody = "이 파일에는 비밀번호가 암호화되지 않은 채 들어 있어요."
    override val ok = "확인"
    override fun importedBookmarks(added: Int, skipped: Int) =
        "북마크 ${added}개를 가져왔어요" + if (skipped > 0) " (${skipped}개 건너뜀)" else ""
    override fun importedPasswords(added: Int, skipped: Int) =
        "비밀번호 ${added}개를 가져왔어요" + if (skipped > 0) " (${skipped}개 건너뜀)" else ""

    override val noBookmarks = "북마크가 없어요"
    override val bookmarkDeleted = "북마크를 삭제했어요"
    override val undo = "실행 취소"
    override val confirmIdentity = "화면 잠금으로 본인 확인을 해 주세요."
    override val noSavedPasswords = "저장된 비밀번호가 없어요."
    override val noUsername = "아이디 없음"
    override val showPassword = "비밀번호 보기"
    override val copyPassword = "비밀번호 복사"
    override val copied = "복사했어요"
    override val deletePasswordTitle = "이 비밀번호를 삭제할까요?"
    override val savePasswordTitle = "비밀번호를 저장할까요?"
    override val notNow = "저장 안 함"
    override val chooseAccount = "로그인할 계정 선택"
}

# In-app APK Update Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The app finds a newer GitHub release, downloads its APK and hands it to Android's installer.

**Architecture:** A JVM-only `Updater` holds a state flow and does the check and the download with OkHttp; version
comparison and release JSON parsing are pure functions beside it. An Android-only `ApkInstaller` feeds the file
to a `PackageInstaller` session, and a manifest receiver launches the confirmation the system asks for. Settings
renders the state; the browser menu shows an entry when an update is waiting.

**Tech Stack:** Kotlin, OkHttp 4.12, kotlinx-serialization-json, coroutines, Compose Material 3, JUnit 4 + MockWebServer.

**Spec:** `docs/superpowers/specs/2026-11-15-in-app-update-design.md`

## Global Constraints

- Release source: `https://api.github.com/repos/HPotty36/claude-android-browser/releases/latest`.
- No new dependencies.
- Every user-visible string goes in `ui/Strings.kt`, in both `En` and `Ko`. No Hangul in `En` (a test enforces it).
- Debug builds never check, download or install; settings shows the version only.
- The automatic check runs at most once per 24 hours and fails silently.
- Package for new code: `dev.agentbrowser.update`.

## Review Focus

- A tag that is not a version (`nightly`): treated as "no update", never a crash.
- A release with no `.apk` asset: "up to date".
- A download cut off part-way: state is `Failed`, the partial file is deleted, and a retry starts clean.
- GitHub answering 403/404/500 or with non-JSON: manual check shows `Failed`; automatic check stays `Idle`.
- A downloaded file whose size or SHA-256 differs from what the release lists: `Failed`, file deleted, nothing installed.

---

### Task 1: Version comparison and release parsing

**Files:**
- Create: `app/src/main/java/dev/agentbrowser/update/Version.kt`
- Create: `app/src/main/java/dev/agentbrowser/update/Release.kt`
- Test: `app/src/test/java/dev/agentbrowser/update/VersionTest.kt`, `.../update/ReleaseTest.kt`

**Interfaces:**
- Produces:
  - `fun isNewer(candidate: String, installed: String): Boolean` — a leading `v` and anything from the first `-`
    or `+` are ignored; missing parts count as 0; false when either side does not parse.
  - `data class Release(val version: String, val notes: String, val apkUrl: String, val apkSize: Long, val sha256: String?)`
    — `version` is the tag without the leading `v`.
  - `fun parseRelease(json: String): Release?` — null for malformed JSON, a missing tag, or no asset whose name
    ends in `.apk`. `sha256` comes from the asset's `digest` field (`"sha256:<hex>"`), lowercased, null when absent.

- [ ] **Step 1: Write the failing tests**

```kotlin
class VersionTest {
    @Test fun higherPatchIsNewer() = assertTrue(isNewer("v0.4.1", "0.4.0"))
    @Test fun comparesNumericallyNotAsText() = assertTrue(isNewer("0.10.0", "0.9.9"))
    @Test fun sameVersionIsNotNewer() = assertFalse(isNewer("v0.4.0", "0.4.0"))
    @Test fun olderIsNotNewer() = assertFalse(isNewer("0.3.9", "0.4.0"))
    @Test fun missingPartsCountAsZero() { assertFalse(isNewer("1.0", "1.0.0")); assertTrue(isNewer("1.1", "1.0.5")) }
    @Test fun suffixIsIgnored() = assertFalse(isNewer("0.4.0-beta", "0.4.0"))
    @Test fun tagThatIsNotAVersionIsNotNewer() = assertFalse(isNewer("nightly", "0.4.0"))
    @Test fun unknownInstalledVersionIsNotUpdated() = assertFalse(isNewer("0.5.0", ""))
}

class ReleaseTest {
    private val json = """{"tag_name":"v0.5.0","body":"Fixes","assets":[
        {"name":"notes.txt","browser_download_url":"https://x/notes.txt","size":3},
        {"name":"agentic-browser-0.5.0.apk","browser_download_url":"https://x/a.apk","size":104857600,"digest":"sha256:ABCDEF"}]}"""
    @Test fun picksTheApkAsset() =
        assertEquals(Release("0.5.0", "Fixes", "https://x/a.apk", 104857600, "abcdef"), parseRelease(json))
    @Test fun digestIsOptionalAndNotesMayBeNull() = assertEquals(
        Release("0.5.0", "", "https://x/a.apk", 5, null),
        parseRelease("""{"tag_name":"0.5.0","body":null,"assets":[{"name":"a.apk","browser_download_url":"https://x/a.apk","size":5}]}"""))
    @Test fun noApkMeansNoRelease() = assertNull(parseRelease("""{"tag_name":"v0.5.0","assets":[]}"""))
    @Test fun garbageMeansNoRelease() { assertNull(parseRelease("<html>")); assertNull(parseRelease("""{"message":"rate limited"}""")) }
}
```

- [ ] **Step 2:** Run `./gradlew :app:testDebugUnitTest --tests 'dev.agentbrowser.update.*'` — expect compile failure.
- [ ] **Step 3:** Implement both files. `parseRelease` uses `Json { ignoreUnknownKeys = true }.parseToJsonElement` inside `runCatching`.
- [ ] **Step 4:** Run the same command — expect PASS.
- [ ] **Step 5:** Commit `feat: version comparison and release parsing for updates`.

### Task 2: Updater (check and download)

**Files:**
- Create: `app/src/main/java/dev/agentbrowser/update/Updater.kt`
- Test: `app/src/test/java/dev/agentbrowser/update/UpdaterTest.kt`

**Interfaces:**
- Consumes: `isNewer`, `parseRelease`, `Release` (Task 1).
- Produces:

```kotlin
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Release) : UpdateState
    data class Downloading(val release: Release, val fraction: Float) : UpdateState
    data class Installing(val release: Release) : UpdateState
    /** `release` is set when the failure came after one was found, so the UI can offer a retry. */
    data class Failed(val release: Release?) : UpdateState
}
/** True while an update is found and not yet installed: Available, Downloading, Installing, or Failed with a release. */
val UpdateState.hasUpdate: Boolean

class Updater(
    private val installedVersion: String,
    private val enabled: Boolean,
    private val dir: File,                       // downloads go here; the updater owns the directory
    private val install: suspend (File) -> Unit, // throws on failure
    private val lastCheck: () -> Long,
    private val saveLastCheck: (Long) -> Unit,
    private val releaseUrl: String = RELEASE_URL,
    private val http: OkHttpClient = OkHttpClient(),
) {
    val state: StateFlow<UpdateState>
    suspend fun check(silent: Boolean = false)   // silent: any failure leaves Idle instead of Failed(null)
    suspend fun checkIfDue(now: Long)            // check(silent = true) when now - lastCheck() >= 24h; saves `now` first
    suspend fun downloadAndInstall()             // no-op unless state is Available or Failed(release != null)
    fun installFailed()                          // called by the receiver: Installing -> Failed(release)
}
const val RELEASE_URL = "https://api.github.com/repos/HPotty36/claude-android-browser/releases/latest"
```

Behaviour the tests pin: every method is a no-op when `enabled` is false; `check` is a no-op while state is
`Checking`, `Downloading` or `Installing`; requests carry `Accept: application/vnd.github+json`; blocking work
runs on `Dispatchers.IO`; the file is `dir/agentic-browser-<version>.apk`, written as `<name>.part` and renamed
on success; other files in `dir` are deleted before a download; a complete file of the right size already there
is reused; `fraction` is bytes read over `release.apkSize`; size and (when present) SHA-256 are verified before
`install` is called.

- [ ] **Step 1: Write the failing tests** in `UpdaterTest` with `MockWebServer`, a `TemporaryFolder` rule and
  `runBlocking`. A helper builds the release JSON with the asset URL pointing back at the server
  (`server.url("/a.apk")`), `size` and `digest` computed from the body. Tests and their assertions:
  - `newerReleaseIsAvailable`: installed `0.4.0`, tag `v0.5.0` → `state.value == Available(release)`; request header `Accept` is `application/vnd.github+json`.
  - `sameVersionIsUpToDate`: tag `v0.4.0` → `UpToDate`.
  - `tagThatIsNotAVersionIsUpToDate`: tag `nightly` → `UpToDate`.
  - `releaseWithoutApkIsUpToDate`: `assets: []` → `UpToDate`.
  - `serverErrorFailsAManualCheck`: 403 → `Failed(null)`.
  - `serverErrorIsSilentOnAnAutomaticCheck`: 500 with `check(silent = true)` → `Idle`.
  - `automaticCheckRunsOncePerDay`: `checkIfDue(now)` twice with `lastCheck` backed by a var → `server.requestCount == 1`; then `checkIfDue(now + 24h)` → 2.
  - `disabledNeverTouchesTheNetwork`: `enabled = false`, `check()`, `checkIfDue(…)` → `requestCount == 0`, `Idle`.
  - `downloadsVerifiesAndInstalls`: body of 4096 bytes → `install` receives a file named `agentic-browser-0.5.0.apk` with those bytes; state `Installing(release)`; a stale `old.apk` placed in `dir` beforehand is gone.
  - `wrongChecksumIsNotInstalled`: digest of other bytes → `Failed(release)`, `install` never called, `dir` has no files.
  - `truncatedDownloadFailsAndCleansUp`: `MockResponse().setBody(…).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)` → `Failed(release)`, no files left; enqueue a good response, `downloadAndInstall()` again → `Installing`.
  - `installFailureCanBeRetried`: `install` throws → `Failed(release)`; and `installFailed()` from `Installing` → `Failed(release)`.
  - `hasUpdateCoversEveryWaitingState`: true for `Available`, `Downloading`, `Installing`, `Failed(release)`; false for `Idle`, `Checking`, `UpToDate`, `Failed(null)`.
- [ ] **Step 2:** Run `./gradlew :app:testDebugUnitTest --tests 'dev.agentbrowser.update.UpdaterTest'` — expect compile failure.
- [ ] **Step 3:** Implement `Updater.kt`.
- [ ] **Step 4:** Run again — expect PASS.
- [ ] **Step 5:** Commit `feat: updater that checks GitHub releases and downloads the APK`.

### Task 3: Installer, receiver, wiring

**Files:**
- Create: `app/src/main/java/dev/agentbrowser/update/ApkInstaller.kt` (holds `UpdateReceiver` too)
- Modify: `app/src/main/AndroidManifest.xml`, `app/build.gradle.kts`, `app/src/main/java/dev/agentbrowser/App.kt`,
  `app/src/main/java/dev/agentbrowser/MainActivity.kt`

**Interfaces:**
- Consumes: `Updater` (Task 2).
- Produces: `App.updater: Updater`; `suspend fun installApk(context: Context, apk: File)`.

- [ ] **Step 1:** `build.gradle.kts`: add `buildConfig = true` to `buildFeatures`.
- [ ] **Step 2:** Manifest: `<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />` and
  `<receiver android:name=".update.UpdateReceiver" android:exported="false" />`.
- [ ] **Step 3:** `installApk`: on `Dispatchers.IO`, create a `PackageInstaller` session
  (`MODE_FULL_INSTALL`, `setAppPackageName(context.packageName)`, `setSize`), copy the file into it with `fsync`,
  and `commit` with a `PendingIntent.getBroadcast` to `UpdateReceiver` (`FLAG_UPDATE_CURRENT or FLAG_MUTABLE`,
  mutable flag only on API 31+). Abandon the session if the copy throws.
- [ ] **Step 4:** `UpdateReceiver.onReceive`: on `STATUS_PENDING_USER_ACTION` start the `EXTRA_INTENT` activity
  with `FLAG_ACTIVITY_NEW_TASK`; on `STATUS_SUCCESS` do nothing (the process is replaced); on any other status
  call `(context.applicationContext as App).updater.installFailed()`.
- [ ] **Step 5:** `App`: `val updater by lazy { Updater(BuildConfig.VERSION_NAME.removeSuffix("-debug"), enabled = !BuildConfig.DEBUG, dir = File(cacheDir, "update"), install = { installApk(this, it) }, lastCheck/saveLastCheck backed by AppSettings) }`.
  `AppSettings` gets `var lastUpdateCheck: Long` stored under the key `last_update_check`.
- [ ] **Step 6:** `MainActivity.onCreate`: when `savedInstanceState == null`, `app.scope.launch { app.updater.checkIfDue(System.currentTimeMillis()) }`.
- [ ] **Step 7:** Run `./gradlew :app:assembleDebug :app:testDebugUnitTest` — expect BUILD SUCCESSFUL.
- [ ] **Step 8:** Commit `feat: install downloaded updates through PackageInstaller`.

### Task 4: Settings section, menu entry, strings, README

**Files:**
- Create: `app/src/main/java/dev/agentbrowser/update/UpdateSection.kt`
- Modify: `ui/Strings.kt`, `settings/SettingsScreen.kt` (nothing: the section goes through `extraSections`),
  `MainActivity.kt`, `browser/BrowserScreen.kt`, `README.md`, `app/build.gradle.kts` (version 0.5.0, code 10)
- Test: `app/src/test/java/dev/agentbrowser/ui/StringsTest.kt`

**Interfaces:**
- Consumes: `Updater`, `UpdateState`, `hasUpdate`.
- Produces: `@Composable fun UpdateSection(updater: Updater, installedVersion: String)`; `BrowserScreen` and its
  bar take `updateAvailable: Boolean`.

Strings (English / Korean):

| Name | En | Ko |
| --- | --- | --- |
| `update` | Update | 업데이트 |
| `fun currentVersion(v)` | Version $v | 버전 $v |
| `checkForUpdates` | Check for updates | 업데이트 확인 |
| `checkingForUpdates` | Checking… | 확인하는 중… |
| `upToDate` | You have the latest version | 최신 버전입니다 |
| `fun updateAvailable(v)` | Version $v is available | 새 버전 $v |
| `fun downloadUpdate(mb)` | Download and install ($mb MB) | 다운로드 후 설치 (${mb}MB) |
| `fun downloadingUpdate(percent)` | Downloading… $percent% | 다운로드 중… $percent% |
| `installingUpdate` | Opening the installer… | 설치 화면을 여는 중… |
| `updateFailed` | The update didn't go through | 업데이트하지 못했습니다 |
| `retry` | Try again | 다시 시도 |
| `updateMenu` | Update available | 업데이트 있음 |
| `updateDebugNote` | Debug builds don't update themselves | 디버그 빌드는 업데이트하지 않습니다 |

- [ ] **Step 1: Write the failing test** in `StringsTest`:

```kotlin
@Test fun updateStringsCarryTheirNumbers() {
    assertEquals("Version 0.5.0 is available", En.updateAvailable("0.5.0"))
    assertEquals("새 버전 0.5.0", Ko.updateAvailable("0.5.0"))
    assertEquals("Download and install (100 MB)", En.downloadUpdate(100))
    assertEquals("다운로드 중… 42%", Ko.downloadingUpdate(42))
}
```

- [ ] **Step 2:** Run `./gradlew :app:testDebugUnitTest --tests 'dev.agentbrowser.ui.StringsTest'` — expect compile failure.
- [ ] **Step 3:** Add the strings to the interface, `En` and `Ko`.
- [ ] **Step 4:** `UpdateSection`: `SectionLabel(s.update)`, the version line, then by state — `Idle`: check button;
  `Checking`: disabled button reading `checkingForUpdates`; `UpToDate`: `upToDate` + check button; `Available`:
  `updateAvailable`, the notes (at most 8 lines, ellipsis), `downloadUpdate` button; `Downloading`:
  `LinearProgressIndicator(progress)` + `downloadingUpdate`; `Installing`: `installingUpdate`;
  `Failed`: `updateFailed` in the error colour + `retry` (which downloads when a release is known, checks otherwise).
  In a debug build: the version line and `updateDebugNote` only. Buttons launch on `rememberCoroutineScope()`.
  Match the 16 dp horizontal padding the other sections use.
- [ ] **Step 5:** `MainActivity`: render `UpdateSection` after `ImportSection` in the settings slot; pass
  `updateAvailable = updateState.hasUpdate` to `BrowserScreen`.
- [ ] **Step 6:** `BrowserScreen`: when `updateAvailable`, the menu's first item is `s.updateMenu` with
  `Icons.Outlined.SystemUpdate`, calling `onSettings`; the ⋮ icon is wrapped in a `BadgedBox` with an empty `Badge`.
- [ ] **Step 7:** README, both languages: a Features row ("Updates itself"), a line under Install, the new
  permission under Safety ("install permission is used only for the app's own updates"), `update/` in the
  "Where/What" table.
- [ ] **Step 8:** Bump `versionCode = 10`, `versionName = "0.5.0"`.
- [ ] **Step 9:** Run `./gradlew :app:testDebugUnitTest :app:assembleDebug` — expect BUILD SUCCESSFUL, all tests pass.
- [ ] **Step 10:** Commit `feat: update section in settings and an update entry in the menu`.

# In-app APK update: design

## Intent

Updating today means opening GitHub, downloading the APK and installing it by hand. The app should find a new
release itself, download it and hand it to Android's installer.

## Goals

- Settings gets an "Update" section: the installed version and a "Check for updates" button.
- On launch the app checks at most once a day. When a newer version exists, the menu (⋮) shows an update entry
  that opens settings.
- When a newer version exists, settings shows its version and release notes with a "Download" button, download
  progress, and then Android's install screen.
- All strings in English and Korean.

## Non-goals

- Silent or background installs, delta updates, a release channel choice, downgrade.
- Verifying the APK beyond what Android does: it refuses an update signed with a different key.

## Anti-goals

- Debug builds (`dev.agentbrowser.debug`) must not offer the update: the release APK would install as a second
  app. The section shows the version only.
- A failed or offline check on launch must be silent.

## Approaches

1. **GitHub Releases API (chosen).** `GET /repos/HPotty36/claude-android-browser/releases/latest`, compare
   `tag_name` with the installed version name, download the `.apk` asset. Releases already live there; no server.
2. A version file hosted elsewhere: a second thing to keep in step with each release, for nothing.
3. Play in-app updates: the app is not distributed through Play.

## How

- `update/Version.kt`: parse `v1.2.3` / `1.2.3`, compare numerically. Pure, unit tested.
- `update/ReleaseParser.kt`: release JSON to `Release(version, notes, apkUrl, apkSize)`; null without an `.apk`
  asset. Pure, unit tested.
- `update/Updater.kt`: state flow `Idle | Checking | UpToDate | Available | Downloading(progress) | ReadyToInstall
  | Failed`. Checks with OkHttp (already a dependency), downloads to `cacheDir/update/`, installs through
  `PackageInstaller` sessions (no FileProvider needed). Tested against MockWebServer for check and download.
- Manifest: `REQUEST_INSTALL_PACKAGES`, and a non-exported receiver for the install session's status, which
  launches the confirmation the system asks for.
- The last automatic check time is kept in the settings preferences.

## Left to the builder

Layout details of the section, exact wording, error message granularity.

## Not verifiable in the authoring environment

There was no Android SDK where this was written, so the install flow on a device is untested there.

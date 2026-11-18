# Third-party notices

Agentic Browser's own code is MIT licensed (see `LICENSE`). The APK also carries the works below, each under its own license.

## uBlock Origin

- Version 1.76.4, unmodified, bundled as a built-in browser extension (`assets/ublock` in the APK).
- Copyright (c) Raymond Hill and contributors.
- License: GNU General Public License v3.0. The full text is in the APK at `assets/ublock/LICENSE.txt` and at <https://www.gnu.org/licenses/gpl-3.0.html>.
- Source: <https://github.com/gorhill/uBlock/tree/1.76.4>

The build downloads the release file from the uBlock Origin repository and checks its SHA-256 (`fetchUblock` in `app/build.gradle.kts`); it is not stored in this repository.

## GeckoView

- Mozilla's browser engine. License: Mozilla Public License 2.0.
- Source: <https://github.com/mozilla-firefox/firefox>

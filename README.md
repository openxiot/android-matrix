# android-matrix

Openxiot Generic Application for Android —— the Android client for the Openxiot IoT platform.

**WeMatrix（矩阵）** is an IoT device and space management app for Android, built with Jetpack Compose. It gives mobile access to organizations, projects, spaces, devices, products, and Modbus services inside the Openxiot ecosystem, mirroring the `iphone-matrix` app with an identical UI and shared API contracts.

Package: `cc.openxiot.wematrix` · Current version: **v1.0.10** (derived from git tags)

## Features

- **OAuth 登录 (Login)** — browser OAuth sign-in plus WeChat login via WechatOpenSDK, with a built-in test token for quick evaluation
- **Organization Management** — create / rename / delete organizations, manage members and roles; switch the active organization
- **Project Management** — browse projects and the hierarchical **space tree** (buildings / floors / rooms / zones), pick and switch the active project, manage project members (space access)
- **Device Management** — device list & detail, property view/set, action invocation, remote switch & parameters, add / move devices via QR or 1D barcode scanning, DTU IMEI → device lookup, and an embedded WebView device-operation page
- **Modbus Services** — browse device point tables / service definitions (public + org-private), invoke service methods, and generate raw Modbus **request frames** (`RequestFrame`, byte-exact, covered by JVM tests)
- **Modbus History & Charts** — current values, time-series history with downsampled charting, and acquisition failure lists
- **Modbus Threshold Alarms** — alarm list & summary with tri-state `open` / `handled` filters, per-space or per-service, and one-click alarm handling
- **Statistics Overview** — a one-screen aggregate of device / service / alarm / fault counts
- **Product Catalog** — browse IoT product definition catalogs and details (powered by the `xiot-spec` SDK, with built-in Loach device models for air-conditioners, sensors, switches, and sprinklers)
- **自定义首页看板 (Customizable Home dashboard)** — a read-only, widget-based dashboard whose layout is stored server-side per project: full/half-width cards, per-card data fetch, data-source failure isolation, and an admin-only editor with live preview & optimistic-lock save (`version`), with a server-generated preset when none was ever saved
- **QR / 1D Scanning** — camera scan covering QR, Code128, Code39, DataMatrix, and ITF (interleaved 2 of 5) for device / IMEI workflows
- **Dark Mode** — toggleable light/dark theme
- **多语言 (66 languages, incl. RTL)** — follow the system language, or pick/search a language under Profile → Language; right-to-left scripts (Arabic and friends) are fully adapted
- **应用内自更新 (In-app self-update)** — check, download (with progress), verify, and install new versions from the About page and on launch

## Architecture

- **Jetpack Compose + Material3** with the **MVVM** pattern (`ViewModel` + `StateFlow`), mirroring `iphone-matrix`'s `@Observable` classes
- **Programmatic navigation** using `Navigation Compose` in a 5-tab `HorizontalPager` bottom bar
- **Networking** via **Retrofit + OkHttp + Gson** with a `RetrofitClient` that injects the auth token and `X-Org-Id` header; endpoint interfaces are organized per service (`ApiService`)
- **Persistence** via `SharedPreferences` (`TokenManager` for the session; an independent prefs file for the language so it survives logout)
- **WeChat login** via the official `wechat-sdk-android` SDK; browser OAuth via a custom scheme (`openxiot://oauth/...`) deep-link callback
- **Localization** with per-locale `values-*` resource folders generated from a single source of truth by `tools/i18n/`; `UiText` defers copy to render time so loaded data and navigation survive a language switch
- **Self-update** reads a signed update manifest (`UPDATE_MANIFEST_URL`) and rides the system package installer

## Project Structure

```
android-matrix/
├── settings.gradle.kts         # Gradle plugin management & module set
├── build.gradle.kts            # root plugins (AGP 9.4.1, Kotlin compose 2.4.20)
├── gradle.properties
├── app/
│   ├── build.gradle.kts        # app config, version via git tag, release signing, i18n gates
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── res/
│       │   │   ├── values/                    # default (English) resources
│       │   │   ├── values-zh/, -zh-rHK/, -zh-rTW/   # Chinese + Traditional Chinese
│       │   │   ├── values-*/                  # ~60 more locales (66 languages, incl. RTL)
│       │   │   ├── drawable/  xml/  mipmap-*/  # network security config, icons
│       │   └── java/cc/openxiot/wematrix/
│       │       ├── MainActivity.kt            # OAuth deep-link entry, theme + navigation
│       │       ├── WeMatrixApp.kt             # Application: TokenManager init
│       │       ├── AppState.kt                # global Compose state (login, dark mode)
│       │       ├── AppLanguages.kt / AppLocale.kt   # language list & locale resolution
│       │       ├── AppUpdate.kt               # version update state machine
│       │       ├── data/
│       │       │   ├── api/                   # ApiService, ApiModels, RetrofitClient, UpdateApi
│       │       │   ├── local/TokenManager.kt  # SharedPreferences-backed persistence
│       │       │   └── repository/            # Auth, Organization, Space, Device, Modbus*,
│       │       │                              # MobileDashboard*, Statistics, ProductSpec,
│       │       │                              # UserSettings, Update
│       │       ├── device/loach/              # built-in Loach device models (xiot-spec)
│       │       ├── navigation/AppNavigation.kt
│       │       ├── ui/
│       │       │   ├── theme/                 # Material3 color & typography
│       │       │   ├── core/                  # UiText, SessionState
│       │       │   ├── main/                  # MainScreen — 5-tab bottom bar
│       │       │   ├── home/                  # customizable dashboard + editor (Dashboard*)
│       │       │   ├── project/               # space tree, device tree, members
│       │       │   ├── devices/  device/      # device list, detail, operation
│       │       │   ├── products/              # catalog list & detail
│       │       │   ├── modbus/                # service list/detail, request frames, formats
│       │       │   ├── alarm/                 # threshold alarms
│       │       │   ├── history/               # time-series charts & failure lists
│       │       │   ├── scan/                  # QR/1D camera scanner
│       │       │   ├── organization/          # org picker & detail
│       │       │   ├── profile/               # profile, account, about, language
│       │       │   ├── login/                 # OAuth / WeChat login, Weixin helper
│       │       │   └── components/            # shared views (AppWebView, filters, …)
│       │       ├── util/                      # Constants, Rtl, VersionCompare, ApkInstaller
│       │       └── wxapi/WXEntryActivity.kt   # WeChat callback activity
│       └── test/java/cc/openxiot/wematrix/    # JVM unit tests (no emulator)
│           ├── i18n/          # StringsParity, NoHardcodedChinese, AppLocale, AppLanguages, UiText
│           ├── ui/home/       # DashboardLayout/Folding/Types tests
│           ├── ui/modbus/     # RequestFrame, ModbusFormat byte-exact tests
│           ├── data/api/      # ApiServiceSignatureTest
│           └── util/          # VersionCompareTest
├── tools/i18n/                # localization generator & gates (langs.py = single source of truth)
├── notes/                     # release-note source (zh.txt / en.txt) for the update site
├── .github/workflows/         # build-debug.yml, build-release.yml
└── LICENSE                    # MIT
```

## Localization (66 languages, incl. RTL)

- String resources live in per-locale `values-*` folders, **generated** from `tools/i18n/langs.py` as the single source of truth, with `cldr_check.py` validating plural categories against real CLDR data and `validate.py` as an independent CI gate.
- English is the base (`values/`); Simplified Chinese (`values-zh`) follows, with genuine **Traditional Chinese** variants (`values-zh-rTW` / `values-zh-rHK`) so zh-TW/HK users no longer fall back to English.
- The language (follow system / 中文 / English) is resolved in a pure function (`AppLocale.resolve`) that treats all `zh-*` system preferred languages as Chinese.
- Right-to-left scripts (Arabic, Hebrew, Persian, Urdu, …) are layout-aware (`Rtl.kt`).
- Two i18n gates (`StringsParityTest`, `NoHardcodedChineseTest`) run on every build to catch missing translations or hard-coded Chinese pixels.

## Backend APIs

- **account.openxiot.cn** — OAuth login, organizations, members, user settings
- **matrix.openxiot.cn** — space hierarchy, device management, Modbus point tables / services / alarms / history, statistics, mobile dashboard (`/matrix/v1/...`)
- **product.openxiot.cn** — product catalog
- **ws.dtu.ap.openxiot.cn** — DTU IMEI → device lookup (public, no auth)

## Requirements

- Android minSdk 29 / targetSdk 37
- JDK 17
- Android Gradle Plugin 9.4.1, Kotlin (Compose) 2.4.20
- Android Studio (latest stable)

## Setup

1. Open the project in Android Studio (File → Open → root directory)
2. Build and run on a device or emulator
3. Log in via browser OAuth / WeChat, or tap **test token** on the login screen

The version (`versionName` / `versionCode`) is derived from the nearest `vX.Y.Z` git tag and encoded as `X*10000 + Y*100 + Z`; it can be overridden with `-PappVersionName` / `-PappVersionCode`.

## Continuous Integration

Two GitHub Actions pipelines guard buildability and release integrity:

- **build-debug.yml** (push / PR to `main`): runs the i18n JVM unit tests (`testDebugUnitTest`) → the independent language-directory check (`python3 tools/i18n/validate.py`) → `assembleDebug`, uploading the APK and test report as artifacts.
- **build-release.yml** (git tag `v*`, or `workflow_dispatch`): parses and validates the version (must exceed the max historical versionCode), restores the signing keystore from secrets, runs the same tests + validate gate, builds a **signed** release APK, verifies `versionName`/`versionCode` and the signing SHA-256 fingerprint with `apksigner` as a hard gate, uploads it to Azure Blob (both a versioned URL and a stable `wematrix-latest.apk` alias), probes anonymous reachability, updates the release notes in the official site repo (`openxiot/webapp-matrix-site`) so in-app updates pick up the new build, and writes a job summary.

Both pipelines stop rather than publish broken or unsigned packages — the release path is fully dry-run-able (`dry_run` default `true`).

## Relationship to iOS

This Android app is the sibling of `iphone-matrix`, sharing the same UI and API contracts:

- iOS SwiftUI + custom theme → Android Jetpack Compose + Material3
- iOS `@Observable` classes → Android ViewModel + StateFlow
- iOS `URLSession` + async/await (Retrofit-style service enums) → Android Retrofit + OkHttp + Gson
- iOS `UserDefaults` → Android SharedPreferences
- iOS `NavigationStack` + `TabView` → Android Navigation Compose + `HorizontalPager` bottom bar
- iOS `UiText` + `AppLanguage`/`AppLocale` → Android `UiText` + `AppLanguages`/`AppLocale`

## License

MIT
# iOS Style Launcher (Android)

An original Android home-screen launcher with an iOS-like look and feel, written in Kotlin and Jetpack Compose.
It contains no Apple code, icons, or fonts. Icons come from Material Symbols (Apache 2.0) and the font is
[Inter](https://rsms.me/inter/) (SIL OFL 1.1, see `app/src/main/assets/Inter-LICENSE.txt`).

- minSdk 26 (Android 8.0), targetSdk 34
- Registers as a HOME launcher (`MAIN` + `HOME` + `DEFAULT`)

## Features
- **Home Screen:** 4-column grid of squircle icons with labels, swipeable pages with page dots and a "Search" pill
- **Wallpaper:** original gradient default (light and dark variants), or pick any photo from the gallery
- **Frosted-glass dock** with 4 apps (blurred wallpaper and a tint, works on every API level)
- **Widgets** on page 1: clock/date (taps open Clock) and battery ring with charging state (taps open battery usage)
- **Long-press an app:** quick-actions menu with Edit Home Screen, App Info, Remove App
- **Jiggle / edit mode:** long-press empty space, or long-press an app and drag. Icons wiggle and show "–" badges.
  Drag to rearrange, drag into or out of the dock (swaps when the dock is full), and drag to the screen edge to flip pages.
  "Remove" can Delete App (system uninstall) or Remove from Home Screen (it stays in the App Library).
- **Layout persistence** with DataStore. New apps are added automatically and uninstalled ones removed (LauncherApps callback).
- **Spotlight search:** pull down on the Home Screen or tap "Search". Shows suggestions, top hits, all matches, and "Search the web".
- **App Library** (last page): category folders (Recently Added, Social, Productivity, and so on) with a 2×2 cluster that opens the full
  folder, plus an alphabetical searchable list with letter headers
- **Animations:** icon press scale, jiggle, menu spring, page zoom when returning home, scale-up app launch animation
- **Dark and light mode** (follows the system)
- **Settings** (Edit mode, then Settings): wallpaper, default Home app shortcut, reset layout, launcher PIN,
  **Lock mode** (device owner), **Lost phone mode**

## Lock mode and Lost phone mode
Both are optional, off by default, and protected by the launcher PIN. Full step-by-step guide:
[`LOCK_MODE_SETUP.md`](LOCK_MODE_SETUP.md).

- **Lock mode** (needs `adb shell dpm set-device-owner com.matiassoto.ioslauncher/.admin.AdminReceiver`):
  blocks uninstalling the launcher, forces it as the persistent Home app, and offers an optional kiosk (lock task) whose allowlist is the apps on your Home Screen.
  "Remove device owner" in Settings undoes everything (`clearDeviceOwnerApp`).
- **Lost phone mode:** texting `<codeword> LOCATE` to the phone replies with a Google Maps link, accuracy, and battery %.
  `<codeword> RING` plays a max-volume alarm on the alarm stream (overrides silent) for 60 s with a full-screen Stop button.
  A persistent notification is shown the whole time it's enabled. On Android 13+, sideloaded apps need **App info > ⋮ > Allow restricted settings**
  before SMS permissions can be granted. Keep **Google Find My Device** and **Samsung Find** turned on as your primary backup.

## Build
```
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk   # platforms;android-34, build-tools;34.0.0
./gradlew assembleDebug
# output: app/build/outputs/apk/debug/app-debug.apk
```

## Install on the phone
1. Copy the APK to the phone, open it, and allow **Install unknown apps** for the app you opened it with.
2. Settings > Apps > Choose default apps > **Home app** > iOS Style Launcher.

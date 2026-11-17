# Xachet

A native Android academic & focus companion for Xiangtan University, forked from [Sachet](https://github.com/wyvern1723/sachet) and maintained by **Pluvist**. Thanks to upstream author **wyvern1723** and contributors.

Written in native **Kotlin / Jetpack Compose** since `0.19.78`, and redesigned in **0.19.82** around **Material 3 Expressive** with a new flat geometric sachet app icon, staggered campus timetables, custom course management, built-in Tomato Pomodoro & Long Timer, Live Activities, and Expressive home-screen widgets.

[中文](README.md) · [Build instructions](README_DEV.md) · [Changelog](CHANGELOG.md) · [Release notes](docs/releases/0.19.82.md) · [Third-party notices](THIRD_PARTY_NOTICES.md)

## Download

**Current release: 0.19.82 (`versionCode 58`), requires Android 13 (API 33) or later.**

- [ARM64 APK](https://github.com/Pluvist/Xachet/releases/download/v0.19.82/xachet-v0.19.82-arm64-v8a.apk)
- [ARM v7a APK](https://github.com/Pluvist/Xachet/releases/download/v0.19.82/xachet-v0.19.82-armeabi-v7a.apk)
- [x86_64 APK](https://github.com/Pluvist/Xachet/releases/download/v0.19.82/xachet-v0.19.82-x86_64.apk)

Checksums (`SHA256SUMS.txt`) and the signing certificate fingerprint (`SIGNING-CERTIFICATE.txt`) are attached to the [latest release](https://github.com/Pluvist/Xachet/releases/latest). `0.19.82` uses `com.pluvist.xachet` with the same signing key as `0.19.79–0.19.81` and installs cleanly over them. For Android 12 or earlier, use [0.19.77](https://github.com/Pluvist/Xachet/releases/tag/v0.19.77).

## Highlights

- **Material 3 Expressive UI & New Icon**: Pure-flat Google-palette sachet icon with an arched Xiangtan University ribbon on `#FFFFFF` (with Android 13+ monochrome support), `Google Sans Flex` + `MiSans` variable typography, and expressive shape/spring motion.
- **Themes & UI Scaling**: Built-in **Shaofeng Red** (`#9F181E`), **Xiangshui Blue** (`#014190`), and **Legacy** (`#F5ABB9` / `#5BCFFA`) color presets, Android 12+ wallpaper dynamic color, light/dark modes, and `90% / 100% / 110% / 120%` global UI scaling (defaults to `110%`).
- **Staggered Timetable & Custom Courses**: Automatically shifts sections 1–8 by +10 minutes for Yifu and Information Technology buildings, adds/edits/deletes custom courses, auto-scrolls to the current/next class, hides instructors for denser cards with per-scale font maximization, and animates "Back to this week" with overlapping per-character bounce.
- **Tomato Focus & Long Timer**: Built-in Pomodoro cycle and forward long timer, next-class info bar, focus statistics (daily goals, trends, peak hours), and long-press bottom timer button for a quick-start floating menu and empty-classroom shortcut.
- **Course & Focus Live Activities + Widgets**: Course countdown Live Activity alongside Focus Live Activity with a white stopwatch icon and notification actions (`Stop` / `Skip` for Pomodoro; `Pause` / `Resume` / `Stop` for Long Timer), plus redesigned 2×2 and 4×2 Material 3 Expressive widgets.
- **Academic Services & Offline Cache**: Native Kotlin queries for curriculum, exams, grades, GPA ranking, empty classrooms, textbooks (subject to account permissions), and PDF transcripts with local caching and background session recovery.

## Build & License

Use JDK 21+, Android SDK 37, NDK 27.1.12297006, and CMake 3.22.1. Run `.\native\build-native.ps1` on Windows or `bash native/gradlew -p native :app:assembleDebug :app:testDebugUnitTest --console=plain` on Linux/macOS. See [README_DEV.md](README_DEV.md) for signing and ABI split builds.

Licensed under [AGPL-3.0](LICENSE) with upstream Sachet code under [MIT](LICENSE-MIT). See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for full third-party attributions (`Tomato`, `HyperCeiler`, `Cresto`, `Google Sans Flex`, `MiSans`, etc.).

# Xachet native Android app

Kotlin / Jetpack Compose implementation using the Glasmio components referenced by MeiloX. From 0.19.78 this is the production APK build entry. The original Flutter project remains a historical behavior reference. Validation scope and remaining limits are recorded in [the migration plan](../docs/meilox-migration-plan.md).

## Build

On Windows with Android Studio JBR, SDK 37, NDK 27.1.12297006 and CMake 3.22.1:

```powershell
.\native\build-native.ps1
```

The helper stages sources to an ASCII directory because CMake fails on the original non-ASCII workspace path. It uses ordinary Gradle repositories. `SACHET_LOCAL_MAVEN` optionally points to a local Maven cache; `-Offline` is optional and only works with complete cached dependencies. The staging directory, Java path and Gradle task list can be overridden by parameters.

The default tasks build the debug APK and run JVM tests. The debug package is `com.pluvist.xachet.localtest`; the 0.19.79 production package is `com.pluvist.xachet`. This is a separate installation from the older `com.pluvist.sachet.next`; private app data and widgets do not migrate automatically. A separate optimized `.benchmark` build contains a synthetic timetable and 160-row query fixtures enabled only by the `load_fixture` launch extra. It does not use user accounts or academic query endpoints.

Production requires Android 13/API 33 or later. To package release APKs, set `XACHET_SIGNING_PROPERTIES` to an external Java properties file with `storeFile`, `storePassword`, `keyAlias` and `keyPassword`. The key file and passwords must never be committed. Run `:app:assembleRelease` with `-PxachetReleaseAbis=arm64-v8a,armeabi-v7a,x86_64` for three separate APKs. Missing signing configuration stops release packaging. See [build and signing instructions](../README_DEV.md).

## Portrait animation checks

`tools/performance.py` records individual Android frame-deadline scenarios, with UI inspection outside the measured windows. The coordinate protocol requires the unlocked 1216×2640 OnePlus device. It preserves app data, restores rotation, stops if app focus is lost, and fails if any measured scenario exceeds 5%. Raw frame statistics and JSON are stored in ignored `native/artifacts`.

```powershell
python native/tools/performance.py --package com.pluvist.xachet.localtest --suite focus --tag portrait-focus
python native/tools/performance.py --package com.pluvist.xachet.benchmark --suite controls --tag portrait-controls
```

Use optimized `assemblePreview` / `assembleBenchmark` builds for device measurements. The controls suite uses synthetic populated queries; it does not validate real-account endpoint results. Screen recordings and functional inspections must run separately from these timing windows. Results remain in the ignored artifact directory; these measurements do not establish academic endpoint correctness.

## Sources and licenses

Glasmio source and notices are retained under `third_party/glasmio`. The MIT secure-storage Java implementation is retained for compatibility with the old encrypted account data, without starting Flutter. See `third_party/flutter_secure_storage` and the root `THIRD_PARTY_NOTICES.md`.

The preview and formal app have different Android UIDs. Users must log into the preview separately. The 0.19.79 production package also differs from earlier releases, so an in-place upgrade and automatic private-data migration are unavailable. Users must sign in again and re-add widgets.

## Academic feature verification

The native academic forms and request/response contracts follow the original Dart services individually. See [the repair and live-test report](../docs/native-academic-repair-2026-10-31.md) for verified behavior and the current textbook-permission limitation. An explicit `academic_probe` launch extra in the local `preview` build runs read-only diagnostics and logs only endpoint metadata/counts under `AcademicProbe`; it is disabled in production and benchmark builds. It requires a saved preview login and never prints credentials or query record contents.

## Startup and notification checks

See [startup and notification repair](../docs/startup-and-course-notifications.md). Course reminders use the real per-section timetable: 20 minutes before sections 1/5, 10 minutes before all others. The normal performance fixture disables both live activities and reminders to avoid synthetic alerts during timing runs.

The benchmark APK alone contains the framework-only `NotificationDeliveryProbe` and its minimal foreground host. Allow notifications and exact alarms for that isolated app in system settings, then run:

```sh
adb shell am instrument -w com.pluvist.xachet.benchmark/com.pluvist.sachet.next.NotificationDeliveryProbe
```

It uses synthetic data only, reports case booleans and timing, and restores/cleans its own state. `success=true` and `case_cleanup=true` indicate a pass. No account or other app notifications are inspected. Keep the phone unlocked; the foreground host avoids vendor suspension of a UI-less instrumentation process. This bounded delivery check does not certify every vendor's long-term background/power policy.

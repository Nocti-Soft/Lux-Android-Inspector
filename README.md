# Android Layout Inspector

**Measure your Android UI directly inside your debug app.**

Android Layout Inspector is an in-app layout measurement library for Android Views, Jetpack Compose, and mixed View/Compose screens. A draggable indigo control gives you size, gap, ruler, and bounds tools without attaching an Android Studio inspection session.

> **Debug builds only.** Add the inspector to the app you want to inspect. It is not a standalone tool for inspecting other installed apps, and it does not request permission to draw over other apps.
>
> **Distribution: source integration.** This repository does not currently configure artifact publication. The integration example below uses local source substitution—not a published Maven Central, GitHub Packages, or JitPack dependency.

[Try the sample](#try-the-sample) · [Integrate](#integrate-into-your-app) · [Compose support](#jetpack-compose-and-mixed-screens) · [Usage](#using-the-inspector) · [Troubleshooting](#troubleshooting)

## Preview

<table>
  <tr>
    <th>Idle: Start Inspector</th>
    <th>Active: measurement and Stop</th>
    <th>Settings: Hide and notifications</th>
  </tr>
  <tr>
    <td><img src="docs/images/inspector-idle.jpg" width="250" alt="Indigo inspector quick menu with a magnifying glass and Start Inspector action"></td>
    <td><img src="docs/images/inspector-active.jpg" width="250" alt="Four-sided magenta selection outline, indigo tool menu, and Stop Inspector action"></td>
    <td><img src="docs/images/inspector-settings.jpg" width="250" alt="Light inspector settings panel with Back, Hide Inspector, and Notification Controls"></td>
  </tr>
</table>

Actual sample-app screenshots from Android 16 / API 36. The sample used `targetSdk 35`; these images are not a claim of compatibility with every device or target SDK.

## Features

| Tool | How it works |
| --- | --- |
| **Size** | Tap a captured UI element to display its width and height in dp and px. |
| **Gap** | Tap two elements to display their horizontal and/or vertical separation. Overlapping axis-aligned bounds report a zero gap. |
| **Ruler** | Drag between two points to measure their straight-line distance in dp and px. |
| **Bounds** | Display the rectangles of captured View and tagged Compose nodes. Tap to recapture after a layout change. |

The control can be dragged, docked half-visible at either side, or hidden from Settings. Start/Stop is the final quick-menu action. Selected elements use a continuous magenta outline with a white contrast halo. The floating menu respects system-bar, cutout, and mandatory-gesture insets without padding the measurement canvas.

## Requirements

These are the repository's current build settings, not a promise that every consumer must use these exact versions or that arbitrary combinations are compatible.

| Setting | Current value |
| --- | --- |
| Minimum Android version | Android 7.0 / API 24 |
| Compile SDK | 35 |
| Sample target SDK | 35 |
| JDK / JVM target | 17 |
| Gradle wrapper | 8.13 |
| Android Gradle Plugin | 8.7.3 |
| Kotlin | 2.1.0 |
| Compose BOM | 2024.12.01 |

See the [version catalog](gradle/libs.versions.toml), [library build](inspector/build.gradle.kts), and [sample build](sample/build.gradle.kts) for the source of truth. The library brings AndroidX Startup, Core KTX, and Compose UI dependencies, including in View-only consumers. It does not require your View-only app to convert its UI to Compose.

## Try the sample

Clone or download this repository into a directory named `AndroidLayoutInspector`. Configure JDK 17 and your Android SDK location using Android Studio, `ANDROID_HOME`, or an untracked `local.properties` file. Install Android SDK Platform 35 and the build tools selected by the project.

From the repository root:

```bash
./gradlew :sample:assembleDebug
```

The APK is generated at:

```text
sample/build/outputs/apk/debug/sample-debug.apk
```

To install it on your selected test device or emulator:

```bash
./gradlew :sample:installDebug
```

On Windows, use `gradlew.bat` instead of `./gradlew`. With multiple devices connected, select the intended device in Android Studio or install the APK with `adb -s <device-serial> install -r <apk-path>`.

Open **Inspector Sample**, allow its notification permission when prompted, and shake the device or tap its **Layout Inspector** notification. Tap the floating circle, then choose **Start Inspector** or a tool. The sample includes an XML screen, a Compose screen, and a mixed screen. Its application ID is `com.noctisoft.layoutmeasurement`.

## Integrate into your app

### 1. Include the source build

Keep a checkout of this repository beside your application:

```text
workspace/
├── YourApp/
│   ├── settings.gradle.kts
│   └── app/
└── AndroidLayoutInspector/
    ├── settings.gradle.kts
    ├── gradle/libs.versions.toml
    ├── inspector/
    └── sample/
```

Add this to your application's **`settings.gradle.kts`**, alongside its existing configuration:

```kotlin
includeBuild("../AndroidLayoutInspector") {
    name = "layout-inspector-source"
    dependencySubstitution {
        substitute(module("com.noctisoft.local:layout-inspector"))
            .using(project(":inspector"))
    }
}
```

This uses a [Gradle composite build](https://docs.gradle.org/current/userguide/composite_builds.html). Including the repository root lets the inspector keep its own build configuration and version catalog. Do not point `includeBuild` at the `inspector/` subdirectory.

`com.noctisoft.local:layout-inspector` is only a **local substitution key**. It has no published version and must be used with the `includeBuild` block above. No extra Maven repository is needed for that key. Keep `google()` and `mavenCentral()` available to your application for the inspector's AndroidX dependencies.

### 2. Add the debug-only dependency

In your application's **`app/build.gradle.kts`**:

```kotlin
dependencies {
    debugImplementation("com.noctisoft.local:layout-inspector")
}
```

Do not change your app's `applicationId` or namespace to the inspector's package. The initializer uses the **host application's ID** for its provider authority, and notifications belong to the host app.

The existing [sample module](sample/build.gradle.kts) lives in the same multi-module build as the library, so it uses this form instead:

```kotlin
dependencies {
    debugImplementation(project(":inspector"))
}
```

Use the project form only when `:inspector` is actually a subproject of your build. Simply pointing a new subproject at this repository's module also requires reconciling its `libs` catalog and plugin aliases with the host build; the composite approach avoids that step.

**Custom variants and CI:** explicitly scope the dependency to the intended development variants. A consumer with a nonstandard build type may need a matching fallback to the library's `debug` variant. Ensure CI checks out the inspector at the same relative path, pinned to a chosen commit or tag. The consumer's Gradle invocation runs the composite, so check toolchain and resolved dependency compatibility before adopting it in a different build.

### 3. Initialization is automatic

The library [manifest](inspector/src/main/AndroidManifest.xml) registers [InspectorInitializer](inspector/src/main/java/com/noctisoft/layoutmeasurement/InspectorInitializer.kt) through [AndroidX App Startup](https://developer.android.com/topic/libraries/app-startup). It attaches an in-app overlay as Activities resume. No call from your `Application` and no per-screen overlay setup are required with the default manifest configuration.

Do not remove the initializer's manifest metadata or the Startup provider from the debug variant. If your app intentionally disables App Startup, reconcile that setup before expecting automatic activation.

A new process starts with inspection **stopped** and the control **hidden**. Reveal it by shaking the device or tapping the notification. The saved position and dock side are restored; the previous measurement session and selection are not persisted across process restarts.

### 4. Enable notification recovery

On Android 13 / API 33 and newer, posting the inspector notification requires the user's [notification permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission). The library declares `POST_NOTIFICATIONS` in its manifest, but **does not display the permission prompt for your app**. The sample requests it from its [MainActivity](sample/src/main/java/com/noctisoft/layoutmeasurement/sample/MainActivity.kt).

Use your app's existing permission flow or a developer-triggered debug helper. For example, put the following in **`app/src/debug/java/com/example/app/InspectorPermissions.kt`**, replacing the package with your app's package:

```kotlin
package com.example.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build

fun Activity.requestInspectorNotifications() {
    if (Build.VERSION.SDK_INT >= 33 &&
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 4101)
    }
}
```

Call this from an Activity in response to an explicit developer action. Reserve a request code that does not conflict with your app's permission handling. Do not repeatedly prompt after denial. When calling the helper from shared `src/main` code, supply the same function in **`src/release`** with a no-op body and the same package:

```kotlin
package com.example.app

import android.app.Activity

fun Activity.requestInspectorNotifications() = Unit
```

Add equivalent no-op definitions for any other build types that exclude the debug helper. Your release app does not need this permission just for the inspector.

The **Layout Inspector** channel must also be enabled. Use **Settings → Notification Controls** to open the host app's inspector channel on API 26+, or its app-notification settings on older supported versions. Returning to the app refreshes the notification.

When notification recovery is unavailable, **Hide** is disabled and explains why. Inspection can still be started after a shake; a device without an accelerometer needs the notification or explicit debug-only wiring for activation.

### 5. Verify release isolation

Build both consumer variants:

```bash
./gradlew :app:assembleDebug :app:assembleRelease
./gradlew :app:dependencies --configuration debugRuntimeClasspath
./gradlew :app:dependencies --configuration releaseRuntimeClasspath
```

The substituted inspector project must appear in the debug runtime graph and **not** in the release graph. Check the release APK and merged manifest as well: inspector classes, its initializer metadata, and its action receiver must be absent. Do not remove a shared AndroidX Startup provider or notification permission if another release dependency legitimately needs it.

Keep inspector imports in `src/debug`. Wrapping an inspector reference in `if (BuildConfig.DEBUG)` inside `src/main` is not enough: the release compiler still needs to resolve that reference. Use variant-specific wrappers with release no-ops when shared code needs a debug hook. No release/no-op inspector artifact is provided.

## Jetpack Compose and mixed screens

Compose capture currently exposes **tagged semantics nodes**, not every composable. Add `Modifier.testTag` to the elements whose bounds you want to measure:

```kotlin
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

@Composable
fun CheckoutButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.testTag("checkout_button"),
    ) {
        Text("Checkout")
    }
}
```

Use the tag on the actual element you intend to measure. A content description alone is not a substitute for a `testTag` in this implementation. Untagged Compose content does not become an independently captured Compose node; the enclosing Android/Compose container may still be selected.

The same approach applies to a `ComposeView` hosted in an XML layout. See [ComposeActivity](sample/src/main/java/com/noctisoft/layoutmeasurement/sample/ComposeActivity.kt) and [MixedActivity](sample/src/main/java/com/noctisoft/layoutmeasurement/sample/MixedActivity.kt).

Capture uses the unmerged semantics tree and window-relative bounds. When upgrading your app's Compose dependencies, recheck capture behavior against the resolved version; the pinned sample is not a compatibility guarantee for all Compose releases.

## Using the inspector

| Action | Result |
| --- | --- |
| Shake while the host app is resumed | Reveal the controls; does not start measurement by itself. A deliberately hidden control stays hidden. |
| Tap the Layout Inspector notification | Reveal or restore the controls. It does not launch an inspection overlay over another app. |
| Tap the circle | Open the quick menu. Tap a half-docked circle to undock it first. |
| Choose Size, Gap, Ruler, or Bounds | Start that tool or switch directly from the active tool. |
| Start Inspector | Start the retained tool; Size is the default in a fresh process. |
| Stop Inspector | Stop measurement and clear the selection. The quick-menu action collapses the menu and keeps the circle. Notification Stop does not force a hidden control to reappear. |
| Drag toward the left or right edge | Dock the control half-visible. Docking does not stop measurement. |
| Settings → Hide Inspector | Fully hide the controls while keeping measurement active. Restore through the notification, not another shake. |
| Settings → Notification Controls | Open Android's notification settings for the host app/inspector channel. |

Position and dock side are saved across app restarts. The current tool/session is shared across Activity changes within the process, but an Activity's local captured selection is cleared on detach and must be selected again.

**Hide is not Stop.** Measurement drawings and measurement touch handling remain active when the controls are hidden. For an unobstructed app screenshot, stop inspection first, then hide the idle controls. Capture the screenshot using your normal Android/ADB tooling; the inspector does not include a screenshot-export action.

## Scope and limitations

- **Host app only.** The overlay is attached to the current Activity's decor view. There is no system overlay service, accessibility service, root requirement, or inspection of other processes.
- **Snapshot-based measurement.** Selection uses captured rectangles rather than continuous layout tracking. Stop to interact with or scroll the app, then start and select again after changes. Bounds mode recaptures on a tap.
- **Rectangles, not rendered shapes.** Size, gap, and hit testing use axis-aligned bounds. Overlapping nodes are selected by the smallest captured area containing the tap. Offscreen geometry is clipped rather than replaced with an invented visible border.
- **Compose tagging is required for element granularity.** Web content, custom drawing, and independent windows are not parsed into arbitrary UI elements by this implementation.
- **No process-lifetime guarantee.** Hiding leaves the in-process inspection session active; it does not start a foreground service or keep the app alive after Android ends its process.
- **Validation has a defined scope.** The recorded UI checks use an Android 16 / API 36 emulator with 16 KiB pages and the targetSdk 35 sample. Native Robolectric tests cover rendering and older-API paths. This is not exhaustive physical-device, OEM, or targetSdk 36 validation.

## Troubleshooting

| Problem | Check |
| --- | --- |
| No circle on launch | Hidden startup is intentional. Resume the host Activity, shake the device, or tap its notification. Check the debug dependency and merged initializer metadata. |
| No notification | Allow the host app's notification permission on API 33+, enable its Layout Inspector channel, and return to the app. Posting failures are logged with the `LayoutInspector` tag. |
| Hide is disabled | Enable notification recovery first. The guard prevents deliberately hiding the only available controls. |
| Shake does not restore a hidden control | After Settings → Hide Inspector, restoration is intentionally notification-only. |
| A Compose element cannot be selected | Add a `Modifier.testTag` to the intended element, not only a parent or content description. Inspect the resolved Compose UI version if behavior changes after an upgrade. |
| Taps do not activate the underlying app | An active measurement tool intercepts canvas touches. Stop inspection before normal interaction. |
| A measurement no longer matches a moved element | The selection is a snapshot. Stop, change the UI, restart the tool, and select again. |
| Gradle cannot resolve the local dependency | Check the checkout path, substitution key, and `:inspector` project mapping. It is not a published artifact to fetch from Maven. |
| A consumer build reports variant/toolchain conflicts | Compare the host build with the pinned versions above and inspect the resolved dependency graph. Custom build types need an appropriate variant match. |
| Release compilation references missing inspector classes | Move those references out of `src/main` into `src/debug`, with release no-op wrappers where needed. |

## Build and verify this repository

Run the complete local verification command from the repository root:

```bash
./gradlew :inspector:testDebugUnitTest \
  :sample:testDebugUnitTest \
  :inspector:assembleDebug \
  :sample:assembleDebug \
  :sample:assembleRelease \
  :inspector:lintDebug \
  :sample:lintDebug \
  --rerun-tasks
```

The tests cover geometry, controller state, placement persistence, floating-control interactions, overlay lifecycle behavior, notification handling, native outline rendering, and sample edge-to-edge setup. Consult the generated test and lint reports for current results; a successful build does not mean there are no warnings.

[UI verification report](docs/verification/2026-09-17-inspector-indigo-ui.md) · [Independent review](docs/verification/2026-09-17-inspector-indigo-ui-review.md)

Those reports describe their recorded revisions and test environments. They are historical evidence, not an assertion that every later checkout has been tested on a device.

## Repository structure

```text
inspector/           Android measurement library
sample/              XML, Compose, and mixed-screen demo
  src/main/          Sample app shared code
  src/test/          Sample edge-to-edge regression tests
gradle/              Version catalog and Gradle wrapper
docs/images/         README screenshots from the sample
docs/verification/   Recorded verification and review reports
docs/superpowers/    Design specifications and implementation plans
```

## Contributing

For a bug report, include the commit you tested, Android API level, device/emulator, navigation mode, display density/font scale, selected tool, and a minimal reproduction. For Compose issues, include the relevant modifiers and tags. Remove private app content, tokens, and personal information from screenshots and logs.

Keep the inspector debug-only and its overlay inside the host app. Add regression coverage for behavior changes and run the checks above before proposing a change. Do not treat the pinned sample versions as a request to upgrade another application's toolchain.

## Public GitHub publication checklist

Before the first public push, the repository owner should:

- Select a license and add the corresponding `LICENSE` file. This README does not assign a license.
- Review tracked files **and Git history** for secrets, personal paths, private app screenshots, and internal notes. A new ignore rule does not remove files from earlier commits.
- Exclude local worktrees, agent scratch, SDK settings, signing material, and build outputs. In particular, add `.worktrees/` to the ignore rules before broad staging; it is not covered by the current `.gitignore`. Avoid `git add .` until that review is complete.
- Configure the intended GitHub remote and publish the reviewed branch. Document real artifact coordinates only after a package-publishing workflow and an actual release have been verified.

Creating this README does not create a GitHub repository, change repository visibility, publish a package, or push any commits.

## License

No `LICENSE` file is currently included. A project license has not been declared in this checkout; no license badge or permission grant is implied by this README.

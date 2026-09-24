# Android Layout Inspector

[![JitPack](https://jitpack.io/v/Nocti-Soft/Lux-Android-Inspector.svg)](https://jitpack.io/#Nocti-Soft/Lux-Android-Inspector)

Inspect sizes, gaps, text properties, and colors in Android Views and Jetpack Compose directly inside your app.

> **Debug builds only · Android 7.0 / API 24+.** Inspects your app, not other installed apps.

## Integration

### 1. Add JitPack

Merge this into your app's **`settings.gradle.kts`**, keeping your existing repositories:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://jitpack.io")
            content { includeGroup("com.github.Nocti-Soft") }
        }
    }
}
```

### 2. Add the debug dependency

In **`app/build.gradle.kts`**:

```kotlin
dependencies {
    debugImplementation("com.github.Nocti-Soft:Lux-Android-Inspector:<jitpack-version>")
}
```

Use the JitPack badge above to select a **successfully built tag**. Replace `<jitpack-version>` with that exact version, including the leading `v` when present, and confirm the coordinate shown by **Get it**.

Keep `debugImplementation`, not `implementation`, so the inspector is excluded from release builds. Remove any previous local inspector dependency when switching to JitPack.

### 3. Run your debug app

Sync Gradle and launch the debug build. Initialization is automatic through AndroidX Startup; no `Application` call or per-screen registration is required. Keep the library's Startup initializer enabled in the debug manifest.

For notification access on Android 13+, request `POST_NOTIFICATIONS` through your app's permission flow. The library declares the permission but does not show the prompt. See the [sample Activity](sample/src/main/java/com/noctisoft/layoutmeasurement/sample/MainActivity.kt) for an example.

## Usage

With your app open, **shake the device** or tap its **Layout Inspector notification** to reveal the floating control. Tap the circle and choose a tool:

| Tool | Example usage |
| --- | --- |
| **Size** | Tap a component to read its width and height in dp and px. |
| **Gap** | Tap two components to measure their separation or inner-to-outer edge distances. |
| **Properties** | Tap a component to inspect font size, family, weight, style, letter spacing, and text/background/border colors when available. |
| **Ruler** | Drag between two points to measure their distance. |
| **Bounds** | Show component outlines; tap to refresh after a layout change. |

**Move the Properties panel by dragging its header.** Scroll inside the panel to see more values, tap a color code to copy it, or tap **×** to close it.

Choose **Stop Inspector** before scrolling, pressing buttons, or interacting with your app. To restore controls hidden through **Settings → Hide Inspector**, use the notification rather than shaking.

### XML / Android Views

Views need no extra tags or annotations. For example, select **Properties** and tap a `TextView` to inspect its typography and colors, or select **Size** to measure a button.

### Jetpack Compose

Add `Modifier.testTag` to each Compose element you want to select individually:

```kotlin
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.sp

@Composable
fun InspectableTitle() {
    Text(
        text = "Inspect my properties",
        fontSize = 18.sp,
        modifier = Modifier.testTag("title"),
    )
}
```

Choose **Properties** and tap the title. The same tagging works inside a `ComposeView` in an XML screen. Untagged composables are not independently selectable.

### Parent / inner-view gaps

Choose **Gap**, tap the inner view, then tap the exposed area of its surrounding container. The readout shows **Left, Top, Right, and Bottom** edge distances; either selection order works. These are distances between captured bounds, not declared padding or margins.

### Bottom sheets

Use the same tools inside XML and Compose modal bottom sheets; no per-dialog registration is needed. The inspector returns to the previous window after dismissal. On API 24–28, device restrictions may limit inspection to the Activity.

## Try the sample

Open this repository in Android Studio with **JDK 17** and **Android SDK 35** configured, then run the **sample** debug configuration. Or build from the repository root:

```bash
./gradlew :sample:assembleDebug
```

Install `sample/build/outputs/apk/debug/sample-debug.apk` on your test device or emulator.

The sample includes XML, Compose, mixed screens, **Parent / inner-view gaps**, and **Bottom sheet showcase**. The gap examples use **Left 24 dp, Top 32 dp, Right 40 dp, Bottom 48 dp**. The **Inspect my properties** cards demonstrate typography and color inspection.

[Apache License 2.0](LICENSE)

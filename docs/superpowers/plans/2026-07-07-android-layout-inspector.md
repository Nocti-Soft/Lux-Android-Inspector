# Android Layout Inspector Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A drop-in `debugImplementation` Android library that overlays runtime size/spacing measurement tools (Size, Gap, Ruler, Bounds modes) on any host app, activated by shake or notification toggle, plus a sample app for manual testing.

**Architecture:** An App Startup `Initializer` registers `ActivityLifecycleCallbacks` that attach a transparent overlay to each resumed Activity's decorView. Capture walks the View tree (`getLocationInWindow`) and Compose semantics tree (tagged nodes only), emitting a common `CapturedNode`. Pure geometry math (gaps, px↔dp, hit-pick) is JVM unit-tested; rendering and capture are manually tested via the sample app.

**Tech Stack:** Kotlin 2.1, AGP 8.7, minSdk 24, compileSdk 35, androidx.startup 1.2.0, Compose BOM 2024.12.01 (sample app), JUnit 4 for JVM tests.

## Global Constraints

- Package: `dev.pinij.inspector` (library), `dev.pinij.inspector.sample` (sample).
- All coordinates are **window** coordinates (not screen). View path: `getLocationInWindow`; Compose path: `boundsInWindow`. Overlay is a decorView child at (0,0), so its touch/draw coords equal window coords.
- Labels display `"${dp}dp (${px}px)"`, dp rounded to 1 decimal.
- No runtime permission required except `POST_NOTIFICATIONS` (notification trigger degrades silently if not granted).
- Compose capture: descend ONLY into semantics nodes with a `testTag`. All Compose API usage isolated in `ComposeCapture.kt`.
- The overlay must be fully touch-pass-through when `InspectorController.isActive == false`.
- No new dependencies beyond those listed in Tech Stack.

---

### Task 1: Project scaffold — Gradle, two modules, empty sample activity

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `gradle/libs.versions.toml`
- Create: `inspector/build.gradle.kts`
- Create: `inspector/src/main/AndroidManifest.xml`
- Create: `sample/build.gradle.kts`
- Create: `sample/src/main/AndroidManifest.xml`
- Create: `sample/src/main/java/dev/pinij/inspector/sample/MainActivity.kt`
- Create: `sample/src/main/res/layout/activity_main.xml`
- Create: `sample/src/main/res/values/themes.xml`

**Interfaces:**
- Consumes: nothing (first task).
- Produces: buildable `:inspector` (Android library) and `:sample` (app, `debugImplementation project(":inspector")`). Later tasks add files under `inspector/src/main/java/dev/pinij/inspector/` and tests under `inspector/src/test/java/dev/pinij/inspector/`.

- [ ] **Step 1: Generate Gradle wrapper**

Run: `cd /Volumes/WorkingDisk/projects/AndroidLayoutInspector && gradle wrapper --gradle-version 8.11.1`
Expected: `gradlew`, `gradlew.bat`, `gradle/wrapper/` created. If no system `gradle`, copy wrapper from any existing local Android project, then set `distributionUrl=.../gradle-8.11.1-bin.zip` in `gradle/wrapper/gradle-wrapper.properties`.

- [ ] **Step 2: Write root build files**

`settings.gradle.kts`:
```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "AndroidLayoutInspector"
include(":inspector", ":sample")
```

`build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.compose.compiler) apply false
}
```

`gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx2g
android.useAndroidX=true
```

`gradle/libs.versions.toml`:
```toml
[versions]
agp = "8.7.3"
kotlin = "2.1.0"
startup = "1.2.0"
composeBom = "2024.12.01"
coreKtx = "1.15.0"
appcompat = "1.7.0"
activityCompose = "1.9.3"
junit = "4.13.2"

[libraries]
androidx-startup = { module = "androidx.startup:startup-runtime", version.ref = "startup" }
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
androidx-appcompat = { module = "androidx.appcompat:appcompat", version.ref = "appcompat" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
compose-ui = { module = "androidx.compose.ui:ui" }
compose-material3 = { module = "androidx.compose.material3:material3" }
junit = { module = "junit:junit", version.ref = "junit" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

- [ ] **Step 3: Write `:inspector` module**

`inspector/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "dev.pinij.inspector"
    compileSdk = 35
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(libs.androidx.startup)
    implementation(libs.androidx.core.ktx)
    // Compose semantics access only — no @Composable code, so no compose-compiler plugin.
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    testImplementation(libs.junit)
}
```

`inspector/src/main/AndroidManifest.xml`:
```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
</manifest>
```
(App Startup provider entry added in Task 7; receiver in Task 9.)

- [ ] **Step 4: Write `:sample` module**

`sample/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "dev.pinij.inspector.sample"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.pinij.inspector.sample"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    debugImplementation(project(":inspector"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
}
```

`sample/src/main/AndroidManifest.xml`:
```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:label="Inspector Sample"
        android:theme="@style/Theme.Sample">
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

`sample/src/main/res/values/themes.xml`:
```xml
<resources>
    <style name="Theme.Sample" parent="Theme.AppCompat.DayNight.NoActionBar" />
</resources>
```

`sample/src/main/res/layout/activity_main.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="16dp">

    <TextView
        android:id="@+id/title"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Layout Inspector Sample"
        android:textSize="20sp" />
</LinearLayout>
```

`sample/src/main/java/dev/pinij/inspector/sample/MainActivity.kt`:
```kotlin
package dev.pinij.inspector.sample

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}
```

- [ ] **Step 5: Verify build**

Run: `./gradlew :sample:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "chore: scaffold Gradle project with :inspector library and :sample app"
```

---

### Task 2: Pure geometry — `Bounds`, gaps, px↔dp, hit-pick (TDD)

**Files:**
- Create: `inspector/src/main/java/dev/pinij/inspector/Geometry.kt`
- Test: `inspector/src/test/java/dev/pinij/inspector/GeometryTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `data class Bounds(left: Int, top: Int, right: Int, bottom: Int)` with `width: Int`, `height: Int`, `contains(x: Int, y: Int): Boolean`, `area: Long`
  - `enum class Source { XML, COMPOSE }`
  - `data class CapturedNode(label: String, bounds: Bounds, source: Source)`
  - `object Geometry` with:
    - `horizontalGap(a: Bounds, b: Bounds): Int` — 0 when x-intervals overlap
    - `verticalGap(a: Bounds, b: Bounds): Int` — 0 when y-intervals overlap
    - `pxToDp(px: Int, density: Float): Float`
    - `formatPx(px: Int, density: Float): String` — `"12.0dp (36px)"`
    - `pickAt(nodes: List<CapturedNode>, x: Int, y: Int): CapturedNode?` — smallest-area node containing point (deepest-widget proxy)

- [ ] **Step 1: Write the failing tests**

`inspector/src/test/java/dev/pinij/inspector/GeometryTest.kt`:
```kotlin
package dev.pinij.inspector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeometryTest {

    private fun node(l: Int, t: Int, r: Int, b: Int, label: String = "n") =
        CapturedNode(label, Bounds(l, t, r, b), Source.XML)

    @Test fun `horizontal gap between disjoint rects`() {
        val a = Bounds(0, 0, 100, 50)
        val b = Bounds(140, 0, 200, 50)
        assertEquals(40, Geometry.horizontalGap(a, b))
        assertEquals(40, Geometry.horizontalGap(b, a)) // order-independent
    }

    @Test fun `horizontal gap is zero when x-intervals overlap`() {
        val a = Bounds(0, 0, 100, 50)
        val b = Bounds(90, 60, 200, 100)
        assertEquals(0, Geometry.horizontalGap(a, b))
    }

    @Test fun `vertical gap between disjoint rects`() {
        val a = Bounds(0, 0, 100, 50)
        val b = Bounds(0, 80, 100, 120)
        assertEquals(30, Geometry.verticalGap(a, b))
        assertEquals(30, Geometry.verticalGap(b, a))
    }

    @Test fun `vertical gap is zero when y-intervals overlap`() {
        val a = Bounds(0, 0, 100, 50)
        val b = Bounds(200, 40, 300, 90)
        assertEquals(0, Geometry.verticalGap(a, b))
    }

    @Test fun `nested rects have zero gaps`() {
        val outer = Bounds(0, 0, 200, 200)
        val inner = Bounds(50, 50, 100, 100)
        assertEquals(0, Geometry.horizontalGap(outer, inner))
        assertEquals(0, Geometry.verticalGap(outer, inner))
    }

    @Test fun `pxToDp divides by density`() {
        assertEquals(120f, Geometry.pxToDp(360, 3f), 0.001f)
        assertEquals(360f, Geometry.pxToDp(360, 1f), 0.001f)
    }

    @Test fun `formatPx shows dp and px`() {
        assertEquals("120.0dp (360px)", Geometry.formatPx(360, 3f))
    }

    @Test fun `pickAt returns smallest containing node`() {
        val outer = node(0, 0, 200, 200, "outer")
        val inner = node(50, 50, 100, 100, "inner")
        assertEquals(inner, Geometry.pickAt(listOf(outer, inner), 60, 60))
    }

    @Test fun `pickAt returns null when nothing contains point`() {
        assertNull(Geometry.pickAt(listOf(node(0, 0, 10, 10)), 50, 50))
    }

    @Test fun `bounds contains is inclusive left-top exclusive right-bottom`() {
        val b = Bounds(10, 10, 20, 20)
        assertEquals(true, b.contains(10, 10))
        assertEquals(false, b.contains(20, 20))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :inspector:testDebugUnitTest --tests "dev.pinij.inspector.GeometryTest"`
Expected: FAIL — unresolved references `Bounds`, `CapturedNode`, `Geometry`.

- [ ] **Step 3: Write implementation**

`inspector/src/main/java/dev/pinij/inspector/Geometry.kt`:
```kotlin
package dev.pinij.inspector

import kotlin.math.max
import kotlin.math.min

data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val area: Long get() = width.toLong() * height.toLong()
    fun contains(x: Int, y: Int): Boolean = x in left until right && y in top until bottom
}

enum class Source { XML, COMPOSE }

data class CapturedNode(val label: String, val bounds: Bounds, val source: Source)

object Geometry {
    fun horizontalGap(a: Bounds, b: Bounds): Int =
        max(0, max(a.left, b.left) - min(a.right, b.right))

    fun verticalGap(a: Bounds, b: Bounds): Int =
        max(0, max(a.top, b.top) - min(a.bottom, b.bottom))

    fun pxToDp(px: Int, density: Float): Float = px / density

    fun formatPx(px: Int, density: Float): String {
        val dp = (pxToDp(px, density) * 10).toInt() / 10f
        return "${dp}dp (${px}px)"
    }

    fun pickAt(nodes: List<CapturedNode>, x: Int, y: Int): CapturedNode? =
        nodes.filter { it.bounds.contains(x, y) }.minByOrNull { it.bounds.area }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :inspector:testDebugUnitTest --tests "dev.pinij.inspector.GeometryTest"`
Expected: PASS, 10 tests.

- [ ] **Step 5: Commit**

```bash
git add inspector/src
git commit -m "feat: add Bounds, CapturedNode and pure geometry functions with tests"
```

---

### Task 3: `InspectorController` — activation + mode state (TDD)

**Files:**
- Create: `inspector/src/main/java/dev/pinij/inspector/InspectorController.kt`
- Test: `inspector/src/test/java/dev/pinij/inspector/InspectorControllerTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `enum class MeasureMode { SIZE, GAP, RULER, BOUNDS }`
  - `object InspectorController` with:
    - `var isActive: Boolean` (setter notifies listeners)
    - `var mode: MeasureMode` (default `SIZE`, setter notifies listeners)
    - `fun toggle()`
    - `fun addListener(l: () -> Unit)` / `fun removeListener(l: () -> Unit)`

- [ ] **Step 1: Write the failing tests**

`inspector/src/test/java/dev/pinij/inspector/InspectorControllerTest.kt`:
```kotlin
package dev.pinij.inspector

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class InspectorControllerTest {

    @After fun reset() {
        InspectorController.isActive = false
        InspectorController.mode = MeasureMode.SIZE
    }

    @Test fun `toggle flips isActive`() {
        InspectorController.isActive = false
        InspectorController.toggle()
        assertEquals(true, InspectorController.isActive)
        InspectorController.toggle()
        assertEquals(false, InspectorController.isActive)
    }

    @Test fun `listener fires on activation and mode change`() {
        var fired = 0
        val l = { fired++ }
        InspectorController.addListener(l)
        InspectorController.isActive = true
        InspectorController.mode = MeasureMode.GAP
        InspectorController.removeListener(l)
        InspectorController.isActive = false // must not fire
        assertEquals(2, fired)
    }

    @Test fun `setting same value does not notify`() {
        var fired = 0
        val l = { fired++ }
        InspectorController.isActive = false
        InspectorController.addListener(l)
        InspectorController.isActive = false
        InspectorController.removeListener(l)
        assertEquals(0, fired)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :inspector:testDebugUnitTest --tests "dev.pinij.inspector.InspectorControllerTest"`
Expected: FAIL — unresolved references.

- [ ] **Step 3: Write implementation**

`inspector/src/main/java/dev/pinij/inspector/InspectorController.kt`:
```kotlin
package dev.pinij.inspector

import java.util.concurrent.CopyOnWriteArrayList

enum class MeasureMode { SIZE, GAP, RULER, BOUNDS }

object InspectorController {
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    var isActive: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            notifyListeners()
        }

    var mode: MeasureMode = MeasureMode.SIZE
        set(value) {
            if (field == value) return
            field = value
            notifyListeners()
        }

    fun toggle() { isActive = !isActive }

    fun addListener(l: () -> Unit) = listeners.add(l)
    fun removeListener(l: () -> Unit) = listeners.remove(l)

    private fun notifyListeners() = listeners.forEach { it() }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :inspector:testDebugUnitTest --tests "dev.pinij.inspector.InspectorControllerTest"`
Expected: PASS, 3 tests.

- [ ] **Step 5: Commit**

```bash
git add inspector/src
git commit -m "feat: add InspectorController activation and mode state"
```

---

### Task 4: Capture — `ViewCapture` + `ComposeCapture`

Runtime capture needs a live window — no JVM test. Logic kept thin; the testable selection math already lives in `Geometry.pickAt`.

**Files:**
- Create: `inspector/src/main/java/dev/pinij/inspector/ViewCapture.kt`
- Create: `inspector/src/main/java/dev/pinij/inspector/ComposeCapture.kt`

**Interfaces:**
- Consumes: `Bounds`, `CapturedNode`, `Source` (Task 2).
- Produces:
  - `object ViewCapture { fun captureAll(root: View): List<CapturedNode> }` — every visible view in window coords, Compose islands included; skips the inspector's own overlay (any view whose tag == `InspectorOverlay.TAG`).
  - `object ComposeCapture { fun capture(view: View): List<CapturedNode> }` — tagged semantics nodes of one Compose root; empty list if `view` is not a Compose root or has no tagged nodes. ALL Compose imports live in this file only.
  - `const val TAG = "dev.pinij.inspector.OVERLAY"` on `InspectorOverlay` companion — defined in Task 5; until then `ViewCapture` uses the string literal via its own private const `OVERLAY_TAG` with the same value.

- [ ] **Step 1: Write `ComposeCapture`**

`inspector/src/main/java/dev/pinij/inspector/ComposeCapture.kt`:
```kotlin
package dev.pinij.inspector

import android.view.View
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull

/**
 * The ONLY file touching Compose APIs. Reads the unmerged semantics tree of a
 * Compose root view and returns nodes that carry a testTag (spec: tagged-node
 * granularity only for v1).
 */
object ComposeCapture {

    fun capture(view: View): List<CapturedNode> {
        val owner = (view as? RootForTest)?.semanticsOwner ?: return emptyList()
        val result = mutableListOf<CapturedNode>()
        collect(owner.unmergedRootSemanticsNode, result)
        return result
    }

    private fun collect(node: SemanticsNode, out: MutableList<CapturedNode>) {
        val tag = node.config.getOrNull(SemanticsProperties.TestTag)
        if (tag != null) {
            val b = node.boundsInWindow
            if (b.width > 0f && b.height > 0f) {
                out.add(
                    CapturedNode(
                        label = tag,
                        bounds = Bounds(
                            b.left.toInt(), b.top.toInt(),
                            b.right.toInt(), b.bottom.toInt()
                        ),
                        source = Source.COMPOSE
                    )
                )
            }
        }
        node.children.forEach { collect(it, out) }
    }
}
```

- [ ] **Step 2: Write `ViewCapture`**

`inspector/src/main/java/dev/pinij/inspector/ViewCapture.kt`:
```kotlin
package dev.pinij.inspector

import android.view.View
import android.view.ViewGroup

object ViewCapture {

    private const val OVERLAY_TAG = "dev.pinij.inspector.OVERLAY"

    /** All visible widgets under [root], in window coordinates. */
    fun captureAll(root: View): List<CapturedNode> {
        val out = mutableListOf<CapturedNode>()
        walk(root, out)
        return out
    }

    private fun walk(view: View, out: MutableList<CapturedNode>) {
        if (view.tag == OVERLAY_TAG) return // never measure ourselves
        if (view.visibility != View.VISIBLE || view.width == 0 || view.height == 0) return

        val composeNodes = ComposeCapture.capture(view)
        if (composeNodes.isNotEmpty()) {
            out.add(node(view)) // the island itself
            out.addAll(composeNodes)
            return // spec: don't descend past a Compose root
        }

        out.add(node(view))
        if (view is ViewGroup) {
            // An untagged Compose island: its AndroidComposeView child still walks
            // here and is added as a single leaf rect, which is the spec behavior.
            for (i in 0 until view.childCount) walk(view.getChildAt(i), out)
        }
    }

    private fun node(view: View): CapturedNode {
        val loc = IntArray(2)
        view.getLocationInWindow(loc)
        return CapturedNode(
            label = label(view),
            bounds = Bounds(loc[0], loc[1], loc[0] + view.width, loc[1] + view.height),
            source = Source.XML
        )
    }

    private fun label(view: View): String {
        val cls = view.javaClass.simpleName
        val id = view.id
        if (id == View.NO_ID) return cls
        return runCatching { "$cls/${view.resources.getResourceEntryName(id)}" }
            .getOrDefault(cls)
    }
}
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :inspector:assembleDebug`
Expected: `BUILD SUCCESSFUL`. (Behavior verified on device in Task 10.)

- [ ] **Step 4: Commit**

```bash
git add inspector/src
git commit -m "feat: add View-tree and Compose-semantics capture"
```

---

### Task 5: `InspectorOverlay` — toolbar, touch, measurement rendering

**Files:**
- Create: `inspector/src/main/java/dev/pinij/inspector/InspectorOverlay.kt`

**Interfaces:**
- Consumes: `InspectorController`, `MeasureMode` (Task 3); `ViewCapture.captureAll` (Task 4); `Geometry`, `Bounds`, `CapturedNode` (Task 2).
- Produces:
  - `class InspectorOverlay(context: Context) : FrameLayout` with `companion object { const val TAG = "dev.pinij.inspector.OVERLAY" }`. Sets `tag = TAG` on itself. Task 7 attaches it via `decorView.addView(InspectorOverlay(activity))`.
  - Registers/unregisters its controller listener in `onAttachedToWindow`/`onDetachedFromWindow` — Task 7 needs no wiring beyond addView/removeView.

- [ ] **Step 1: Write the overlay**

`inspector/src/main/java/dev/pinij/inspector/InspectorOverlay.kt`:
```kotlin
package dev.pinij.inspector

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Transparent full-window overlay. Pass-through when inspector inactive.
 * Active: shows a mode toolbar and a drawing canvas handling measurement touch.
 */
class InspectorOverlay(context: Context) : FrameLayout(context) {

    companion object {
        const val TAG = "dev.pinij.inspector.OVERLAY"
    }

    private val canvas = MeasureCanvas(context)
    private val toolbar = buildToolbar(context)
    private val controllerListener: () -> Unit = { post { sync() } }

    init {
        tag = TAG
        setWillNotDraw(true)
        addView(canvas, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(toolbar, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = 48 })
        sync()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        InspectorController.addListener(controllerListener)
        sync()
    }

    override fun onDetachedFromWindow() {
        InspectorController.removeListener(controllerListener)
        super.onDetachedFromWindow()
    }

    private fun sync() {
        val active = InspectorController.isActive
        canvas.visibility = if (active) VISIBLE else GONE
        toolbar.visibility = if (active) VISIBLE else GONE
        if (!active) canvas.clearSelection()
        canvas.invalidate()
    }

    private fun buildToolbar(context: Context): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.argb(220, 30, 30, 30))
            fun btn(text: String, onClick: () -> Unit) = addView(Button(context).apply {
                this.text = text
                textSize = 12f
                setOnClickListener { onClick() }
            })
            btn("Size") { InspectorController.mode = MeasureMode.SIZE }
            btn("Gap") { InspectorController.mode = MeasureMode.GAP }
            btn("Ruler") { InspectorController.mode = MeasureMode.RULER }
            btn("Bounds") { InspectorController.mode = MeasureMode.BOUNDS }
            btn("✕") { InspectorController.isActive = false }
        }

    /** Inner view doing all measurement touch + drawing. */
    private inner class MeasureCanvas(context: Context) : View(context) {

        private val density = resources.displayMetrics.density
        private var nodes: List<CapturedNode> = emptyList()
        private var selectedA: CapturedNode? = null
        private var selectedB: CapturedNode? = null
        private var rulerStart: Pair<Float, Float>? = null
        private var rulerEnd: Pair<Float, Float>? = null
        private var nextIsA = true

        private val boundsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 2f; color = Color.rgb(255, 64, 129)
        }
        private val allBoundsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 1f; color = Color.rgb(0, 176, 255)
        }
        private val gapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeWidth = 3f; color = Color.rgb(255, 196, 0)
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = 12f * density
        }
        private val textBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(200, 0, 0, 0)
        }

        fun clearSelection() {
            selectedA = null; selectedB = null
            rulerStart = null; rulerEnd = null
            nextIsA = true
        }

        private fun recapture() {
            nodes = ViewCapture.captureAll(rootView)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (!InspectorController.isActive) return false
            when (InspectorController.mode) {
                MeasureMode.SIZE -> if (event.action == MotionEvent.ACTION_DOWN) {
                    recapture()
                    selectedA = Geometry.pickAt(nodes, event.x.toInt(), event.y.toInt())
                    selectedB = null
                    invalidate()
                }
                MeasureMode.GAP -> if (event.action == MotionEvent.ACTION_DOWN) {
                    recapture()
                    val hit = Geometry.pickAt(nodes, event.x.toInt(), event.y.toInt())
                    if (hit != null) {
                        if (nextIsA) selectedA = hit else selectedB = hit
                        nextIsA = !nextIsA
                    }
                    invalidate()
                }
                MeasureMode.RULER -> {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> { rulerStart = event.x to event.y; rulerEnd = null }
                        MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> rulerEnd = event.x to event.y
                    }
                    invalidate()
                }
                MeasureMode.BOUNDS -> if (event.action == MotionEvent.ACTION_DOWN) {
                    recapture()
                    invalidate()
                }
            }
            return true
        }

        override fun onDraw(c: Canvas) {
            if (!InspectorController.isActive) return
            when (InspectorController.mode) {
                MeasureMode.SIZE -> selectedA?.let { drawSize(c, it) }
                MeasureMode.GAP -> drawGap(c)
                MeasureMode.RULER -> drawRuler(c)
                MeasureMode.BOUNDS -> drawAllBounds(c)
            }
        }

        private fun drawSize(c: Canvas, n: CapturedNode) {
            drawBounds(c, n.bounds, boundsPaint)
            val text = "${n.label}: ${Geometry.formatPx(n.bounds.width, density)} × " +
                Geometry.formatPx(n.bounds.height, density)
            drawLabel(c, text, n.bounds.left.toFloat(), max(n.bounds.top - 8, 40).toFloat())
        }

        private fun drawGap(c: Canvas) {
            val a = selectedA ?: return
            drawBounds(c, a.bounds, boundsPaint)
            val b = selectedB ?: return
            drawBounds(c, b.bounds, boundsPaint)

            val hGap = Geometry.horizontalGap(a.bounds, b.bounds)
            if (hGap > 0) {
                val x1 = min(a.bounds.right, b.bounds.right).toFloat()
                val x2 = max(a.bounds.left, b.bounds.left).toFloat()
                val y = (max(a.bounds.top, b.bounds.top) +
                    min(a.bounds.bottom, b.bounds.bottom)) / 2f
                c.drawLine(x1, y, x2, y, gapPaint)
                drawLabel(c, Geometry.formatPx(hGap, density), (x1 + x2) / 2, y - 8)
            }
            val vGap = Geometry.verticalGap(a.bounds, b.bounds)
            if (vGap > 0) {
                val y1 = min(a.bounds.bottom, b.bounds.bottom).toFloat()
                val y2 = max(a.bounds.top, b.bounds.top).toFloat()
                val x = (max(a.bounds.left, b.bounds.left) +
                    min(a.bounds.right, b.bounds.right)) / 2f
                c.drawLine(x, y1, x, y2, gapPaint)
                drawLabel(c, Geometry.formatPx(vGap, density), x + 8, (y1 + y2) / 2)
            }
            if (hGap == 0 && vGap == 0) {
                drawLabel(c, "overlapping (gap 0)", a.bounds.left.toFloat(),
                    max(a.bounds.top - 8, 40).toFloat())
            }
        }

        private fun drawRuler(c: Canvas) {
            val s = rulerStart ?: return
            val e = rulerEnd ?: return
            c.drawLine(s.first, s.second, e.first, e.second, gapPaint)
            val len = hypot(e.first - s.first, e.second - s.second).roundToInt()
            drawLabel(c, Geometry.formatPx(len, density),
                (s.first + e.first) / 2, (s.second + e.second) / 2 - 8)
        }

        private fun drawAllBounds(c: Canvas) {
            if (nodes.isEmpty()) recapture()
            nodes.forEach { drawBounds(c, it.bounds, allBoundsPaint) }
        }

        private fun drawBounds(c: Canvas, b: Bounds, p: Paint) {
            c.drawRect(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(), p)
        }

        private fun drawLabel(c: Canvas, text: String, x: Float, y: Float) {
            val w = textPaint.measureText(text)
            val h = textPaint.textSize
            val pad = 6f
            val cx = min(max(x, 0f), width - w - 2 * pad)
            c.drawRect(cx - pad, y - h - pad, cx + w + pad, y + pad, textBgPaint)
            c.drawText(text, cx, y - pad / 2, textPaint)
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :inspector:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Run all unit tests still pass**

Run: `./gradlew :inspector:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add inspector/src
git commit -m "feat: add InspectorOverlay with toolbar and four measure modes"
```

---

### Task 6: `ShakeDetector`

**Files:**
- Create: `inspector/src/main/java/dev/pinij/inspector/ShakeDetector.kt`

**Interfaces:**
- Consumes: nothing from earlier tasks (callback injected).
- Produces: `class ShakeDetector(onShake: () -> Unit)` with `fun start(context: Context)` and `fun stop(context: Context)`. Task 7 creates one per resumed activity, wiring `onShake = { InspectorController.toggle() }`.

- [ ] **Step 1: Write the detector**

`inspector/src/main/java/dev/pinij/inspector/ShakeDetector.kt`:
```kotlin
package dev.pinij.inspector

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/** Accelerometer shake trigger. gForce > 2.7 with a 1s debounce fires [onShake]. */
class ShakeDetector(private val onShake: () -> Unit) : SensorEventListener {

    private var lastShakeMs = 0L

    fun start(context: Context) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop(context: Context) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sm.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val (x, y, z) = event.values
        val gForce = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
        if (gForce > 2.7f) {
            val now = System.currentTimeMillis()
            if (now - lastShakeMs > 1000) {
                lastShakeMs = now
                onShake()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :inspector:assembleDebug`
Expected: `BUILD SUCCESSFUL`. (Threshold behavior tuned on device in Task 10.)

- [ ] **Step 3: Commit**

```bash
git add inspector/src
git commit -m "feat: add accelerometer shake detector"
```

---

### Task 7: Auto-init — `InspectorInitializer` + lifecycle attach/detach

**Files:**
- Create: `inspector/src/main/java/dev/pinij/inspector/InspectorInitializer.kt`
- Create: `inspector/src/main/java/dev/pinij/inspector/InspectorLifecycle.kt`
- Modify: `inspector/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `InspectorOverlay` + `InspectorOverlay.TAG` (Task 5), `ShakeDetector` (Task 6), `InspectorController` (Task 3).
- Produces: zero-config startup. `InspectorLifecycle` also exposes nothing publicly — it is self-contained. Task 9's `NotificationTrigger.show(context)` call is added here later (marked below).

- [ ] **Step 1: Write lifecycle callbacks**

`inspector/src/main/java/dev/pinij/inspector/InspectorLifecycle.kt`:
```kotlin
package dev.pinij.inspector

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.ViewGroup

/** Attaches/detaches the overlay and shake detector as activities resume/pause. */
object InspectorLifecycle : Application.ActivityLifecycleCallbacks {

    private val detectors = mutableMapOf<Activity, ShakeDetector>()

    override fun onActivityResumed(activity: Activity) {
        attachOverlay(activity)
        val detector = ShakeDetector { InspectorController.toggle() }
        detectors[activity] = detector
        detector.start(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        detectors.remove(activity)?.stop(activity)
    }

    private fun attachOverlay(activity: Activity) {
        val decor = activity.window.decorView as? ViewGroup ?: return
        if (decor.findViewWithTag<android.view.View>(InspectorOverlay.TAG) != null) return
        decor.addView(
            InspectorOverlay(activity),
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
```

- [ ] **Step 2: Write the App Startup initializer**

`inspector/src/main/java/dev/pinij/inspector/InspectorInitializer.kt`:
```kotlin
package dev.pinij.inspector

import android.app.Application
import android.content.Context
import androidx.startup.Initializer

class InspectorInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        val app = context.applicationContext as Application
        app.registerActivityLifecycleCallbacks(InspectorLifecycle)
        // NotificationTrigger.show(context) — enabled in Task 9
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
```

- [ ] **Step 3: Register in manifest**

Replace `inspector/src/main/AndroidManifest.xml` with:
```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application>
        <provider
            android:name="androidx.startup.InitializationProvider"
            android:authorities="${applicationId}.androidx-startup"
            android:exported="false"
            tools:node="merge">
            <meta-data
                android:name="dev.pinij.inspector.InspectorInitializer"
                android:value="androidx.startup" />
        </provider>
    </application>
</manifest>
```

- [ ] **Step 4: Verify build + install smoke test**

Run: `./gradlew :sample:assembleDebug`
Expected: `BUILD SUCCESSFUL`.
If device/emulator available: `./gradlew :sample:installDebug`, launch app, shake → toolbar appears at bottom; shake again → disappears.

- [ ] **Step 5: Commit**

```bash
git add inspector/src
git commit -m "feat: zero-config auto-init via App Startup and lifecycle callbacks"
```

---

### Task 8: Sample app screens — XML, Compose, mixed

Built before the notification trigger so manual testing of Task 9 has real screens.

**Files:**
- Modify: `sample/src/main/res/layout/activity_main.xml`
- Modify: `sample/src/main/java/dev/pinij/inspector/sample/MainActivity.kt`
- Create: `sample/src/main/java/dev/pinij/inspector/sample/ComposeActivity.kt`
- Create: `sample/src/main/java/dev/pinij/inspector/sample/MixedActivity.kt`
- Create: `sample/src/main/res/layout/activity_mixed.xml`
- Modify: `sample/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: nothing from the library directly (auto-init does the work).
- Produces: three navigable screens exercising XML, Compose (tagged + untagged), and mixed capture paths.

- [ ] **Step 1: XML screen with measurable widgets + nav buttons**

Replace `sample/src/main/res/layout/activity_main.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="16dp">

    <TextView
        android:id="@+id/title"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="XML screen"
        android:textSize="20sp" />

    <Button
        android:id="@+id/box_a"
        android:layout_width="120dp"
        android:layout_height="48dp"
        android:layout_marginTop="24dp"
        android:text="Box A" />

    <Button
        android:id="@+id/box_b"
        android:layout_width="200dp"
        android:layout_height="64dp"
        android:layout_marginTop="32dp"
        android:text="Box B" />

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="48dp"
        android:orientation="horizontal">

        <Button
            android:id="@+id/open_compose"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Compose screen" />

        <Button
            android:id="@+id/open_mixed"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginStart="16dp"
            android:text="Mixed screen" />
    </LinearLayout>
</LinearLayout>
```

- [ ] **Step 2: Wire navigation + notification permission request**

Replace `sample/src/main/java/dev/pinij/inspector/sample/MainActivity.kt`:
```kotlin
package dev.pinij.inspector.sample

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (Build.VERSION.SDK_INT >= 33) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1
            )
        }
        findViewById<Button>(R.id.open_compose).setOnClickListener {
            startActivity(Intent(this, ComposeActivity::class.java))
        }
        findViewById<Button>(R.id.open_mixed).setOnClickListener {
            startActivity(Intent(this, MixedActivity::class.java))
        }
    }
}
```

- [ ] **Step 3: Compose screen — tagged and untagged nodes**

`sample/src/main/java/dev/pinij/inspector/sample/ComposeActivity.kt`:
```kotlin
package dev.pinij.inspector.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

class ComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ComposeScreen() }
    }
}

@Composable
fun ComposeScreen() {
    Column(Modifier.padding(16.dp)) {
        Text("Compose screen", Modifier.testTag("compose_title"))
        Spacer(Modifier.height(24.dp))
        Button(onClick = {}, Modifier.testTag("compose_button").size(160.dp, 56.dp)) {
            Text("Tagged button")
        }
        Spacer(Modifier.height(32.dp))
        // Deliberately untagged: must NOT appear as its own node (spec).
        Button(onClick = {}) { Text("Untagged button") }
    }
}
```

- [ ] **Step 4: Mixed screen — ComposeView inside XML**

`sample/src/main/res/layout/activity_mixed.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="16dp">

    <TextView
        android:id="@+id/mixed_title"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Mixed screen (XML + Compose island)"
        android:textSize="18sp" />

    <androidx.compose.ui.platform.ComposeView
        android:id="@+id/compose_island"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="24dp" />

    <Button
        android:id="@+id/xml_button"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="24dp"
        android:text="XML button below island" />
</LinearLayout>
```

`sample/src/main/java/dev/pinij/inspector/sample/MixedActivity.kt`:
```kotlin
package dev.pinij.inspector.sample

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

class MixedActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mixed)
        findViewById<ComposeView>(R.id.compose_island).setContent {
            Row {
                Text("island A", Modifier.testTag("island_a").size(120.dp, 40.dp))
                Spacer(Modifier.width(20.dp))
                Text("island B", Modifier.testTag("island_b").size(120.dp, 40.dp))
            }
        }
    }
}
```

- [ ] **Step 5: Register activities**

In `sample/src/main/AndroidManifest.xml`, inside `<application>`, after MainActivity:
```xml
<activity android:name=".ComposeActivity" android:exported="false" />
<activity android:name=".MixedActivity" android:exported="false" />
```

- [ ] **Step 6: Verify build**

Run: `./gradlew :sample:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add sample/src
git commit -m "feat: add XML, Compose and mixed sample screens"
```

---

### Task 9: `NotificationTrigger` — ongoing notification toggle

**Files:**
- Create: `inspector/src/main/java/dev/pinij/inspector/NotificationTrigger.kt`
- Modify: `inspector/src/main/java/dev/pinij/inspector/InspectorInitializer.kt`
- Modify: `inspector/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `InspectorController.toggle()` (Task 3).
- Produces: `object NotificationTrigger { fun show(context: Context) }` and `class ToggleReceiver : BroadcastReceiver`. Called once from `InspectorInitializer.create`.

- [ ] **Step 1: Write the trigger**

`inspector/src/main/java/dev/pinij/inspector/NotificationTrigger.kt`:
```kotlin
package dev.pinij.inspector

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/** Ongoing low-priority notification whose tap toggles the inspector. */
object NotificationTrigger {

    private const val CHANNEL_ID = "layout_inspector"
    private const val NOTIFICATION_ID = 0x1A1
    const val ACTION_TOGGLE = "dev.pinij.inspector.ACTION_TOGGLE"

    fun show(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Layout Inspector",
                    NotificationManager.IMPORTANCE_LOW)
            )
        }
        val pi = PendingIntent.getBroadcast(
            context, 0,
            Intent(ACTION_TOGGLE).setPackage(context.packageName),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_crop)
            .setContentTitle("Layout Inspector")
            .setContentText("Tap to toggle measurement overlay")
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
        // Silently no-op when POST_NOTIFICATIONS not granted (API 33+).
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            runCatching { nm.notify(NOTIFICATION_ID, notification) }
        }
    }
}

class ToggleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == NotificationTrigger.ACTION_TOGGLE) InspectorController.toggle()
    }
}
```

- [ ] **Step 2: Register receiver in library manifest**

In `inspector/src/main/AndroidManifest.xml`, inside `<application>`:
```xml
<receiver
    android:name="dev.pinij.inspector.ToggleReceiver"
    android:exported="false">
    <intent-filter>
        <action android:name="dev.pinij.inspector.ACTION_TOGGLE" />
    </intent-filter>
</receiver>
```

- [ ] **Step 3: Call from initializer**

In `InspectorInitializer.create`, replace the comment line
`// NotificationTrigger.show(context) — enabled in Task 9` with:
```kotlin
NotificationTrigger.show(context)
```

- [ ] **Step 4: Verify build + all tests**

Run: `./gradlew :sample:assembleDebug :inspector:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, all unit tests PASS.

- [ ] **Step 5: Commit**

```bash
git add inspector/src
git commit -m "feat: add ongoing-notification inspector toggle"
```

---

### Task 10: Manual verification on device/emulator

**Files:** none (verification task). Fix bugs found; each fix is its own commit.

- [ ] **Step 1: Install**

Run: `./gradlew :sample:installDebug` (device or emulator connected).
Expected: installs; app launches with XML screen; notification permission prompt on API 33+.

- [ ] **Step 2: Walk the checklist**

| # | Action | Expected |
|---|--------|----------|
| 1 | Grant notification permission | Ongoing "Layout Inspector" notification appears |
| 2 | Tap notification | Toolbar (Size/Gap/Ruler/Bounds/✕) appears at screen bottom |
| 3 | Shake device (emulator: extended controls → virtual sensors) | Overlay toggles off/on |
| 4 | Size mode, tap "Box A" | Pink outline + `Button/box_a: 120.0dp (…px) × 48.0dp (…px)` — dp match layout XML |
| 5 | Gap mode, tap Box A then Box B | Yellow vertical line between them labeled `32.0dp (…px)` |
| 6 | Ruler mode, drag finger | Line follows drag with live length label |
| 7 | Bounds mode, tap anywhere | Blue outlines on every widget |
| 8 | ✕ button | Overlay hides; underlying buttons clickable again (pass-through) |
| 9 | Compose screen, Size mode, tap "Tagged button" | Node labeled `compose_button`, `160dp × 56dp` |
| 10 | Compose screen, tap "Untagged button" | Selects the ComposeView island rect, not the button |
| 11 | Mixed screen, Gap mode, tap `island_a` then `island_b` | Horizontal gap `20.0dp` |
| 12 | Mixed screen, Gap mode, tap `island_a` then XML button | Cross-source gap renders |
| 13 | Rotate device | Overlay reattaches, still works |

- [ ] **Step 3: Fix any failures**

Each fix: reproduce → fix → re-run the failing checklist row → commit `fix: <what>`.

- [ ] **Step 4: Final commit**

```bash
git add -A
git commit -m "docs: mark implementation plan complete"
```

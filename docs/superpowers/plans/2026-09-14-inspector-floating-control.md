# Floating Inspector Control Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the fixed bottom inspector toolbar with a draggable, dockable, hideable in-app floating control that remains safe under Android edge-to-edge layouts while preserving the existing measurement engine.

**Architecture:** Keep `InspectorLifecycle` attaching one full-window `InspectorOverlay` to each resumed Activity. Split the overlay into an unpadded full-window `MeasureCanvas` plus an inset-aware `FloatingInspectorControl`; expand `InspectorController` into the process-wide source of session/control state, persist only placement through a small `SharedPreferences` store, and turn the notification into the recovery/Stop control channel.

**Tech Stack:** Kotlin 2.1.0, AGP 8.7.3, Android Views, AndroidX Core 1.15.0 `WindowInsetsCompat`, Android `SharedPreferences`, JUnit 4.13.2.

**Spec:** `docs/superpowers/specs/2026-09-12-inspector-floating-control-design.md`

## Global Constraints

- Inspector UI stays inside the inspected app's own `decorView`; do not add `SYSTEM_ALERT_WINDOW` or a foreground-service/system-overlay architecture.
- Fresh process state is `session = STOPPED`, `control = HIDDEN`; saved placement is retained for the next reveal.
- Measurement session state is independent from floating-control visibility; docking or hiding must not stop an active tool.
- Exactly one measurement mode may be active at a time: `SIZE`, `GAP`, `RULER`, or `BOUNDS`.
- Selecting a different measurement mode switches directly without an intermediate Stop.
- Full **Hide Inspector** is available only when notification recovery is available.
- A deliberate **Hide Inspector** state is restored only through the inspector notification; startup-hidden state may be revealed by shake or notification, and edge docking remains half-visible and tappable.
- Persist normalized X/Y position and dock side across Activity changes and process restarts; do not persist active session or expanded-menu state.
- `InspectorOverlay` remains full-window and receives no system-bar padding; only the floating-control layer is constrained by safe insets.
- Do not consume or mutate host-app `WindowInsets`.
- Keep the existing capture/geometry semantics and the overlay tag `com.noctisoft.layoutmeasurement.OVERLAY` so inspector-owned UI remains excluded from `ViewCapture`.
- Preserve module names `:inspector` and `:sample`, package `com.noctisoft.layoutmeasurement`, and `debugImplementation(project(":inspector"))`.
- No new runtime dependency is required for this work.

---

## File Structure

The implementation should end with these focused responsibilities:

- `InspectorState.kt` — shared enums/data classes: measurement mode, control state, dock side, normalized placement.
- `InspectorController.kt` — process-wide state transitions and listener notification only; no Android `View` or persistence APIs.
- `FloatingControlGeometry.kt` — pure positioning, clamping, normalization, docking, and menu placement calculations.
- `InspectorPlacementStore.kt` — `SharedPreferences` adapter plus pure string codec for saved placement.
- `FloatingInspectorControl.kt` — draggable circle, expanded menu/settings UI, edge docking interaction, and rendering from controller state.
- `MeasureCanvas.kt` — existing measurement touch/drawing code moved out of `InspectorOverlay` without behavior changes.
- `InspectorOverlay.kt` — full-window composition host, controller-to-view synchronization, safe-inset calculation, and persistence callbacks.
- `NotificationTrigger.kt` — notification channel, Show/Restore action, Stop action, recovery availability, active-mode content.
- `InspectorLifecycle.kt` — one overlay and one shake detector per Activity; shake reveals controls instead of toggling session.
- `InspectorInitializer.kt` — process initialization: restore saved placement, initialize notification observation, register lifecycle callbacks.

---

### Task 1: Introduce explicit inspector session/control state

**Files:**
- Create: `inspector/src/main/java/com/noctisoft/layoutmeasurement/InspectorState.kt`
- Modify: `inspector/src/main/java/com/noctisoft/layoutmeasurement/InspectorController.kt`
- Modify: `inspector/src/test/java/com/noctisoft/layoutmeasurement/InspectorControllerTest.kt`

**Interfaces:**
- Produces:
  - `enum class MeasureMode { SIZE, GAP, RULER, BOUNDS }`
  - `enum class FloatingControlState { HIDDEN, COLLAPSED, EXPANDED, DOCKED_LEFT, DOCKED_RIGHT }`
  - `enum class DockSide { NONE, LEFT, RIGHT }`
  - `enum class RevealSource { SHAKE, NOTIFICATION }`
  - `data class FloatingPlacement(val xFraction: Float, val yFraction: Float, val dockSide: DockSide)`
  - `InspectorController.isActive: Boolean` (read-only outside controller)
  - `InspectorController.mode: MeasureMode` (read-only outside controller)
  - `InspectorController.controlState: FloatingControlState` (read-only outside controller)
  - `InspectorController.placement: FloatingPlacement` (read-only outside controller)
  - `selectMode(mode)`, `stopInspection()`, `revealControls(source)`, `undockControls()`, `expandControls()`, `collapseControls()`, `dock(side)`, `hideControls()`, `hideControlsForStartup()`, `updatePlacement(xFraction, yFraction)`, `restorePlacement(placement)`.
- Consumed later by every UI, lifecycle, persistence, and notification task.

- [ ] **Step 1: Create the shared state types**

Create `InspectorState.kt`:

```kotlin
package com.noctisoft.layoutmeasurement

enum class MeasureMode { SIZE, GAP, RULER, BOUNDS }

enum class FloatingControlState {
    HIDDEN,
    COLLAPSED,
    EXPANDED,
    DOCKED_LEFT,
    DOCKED_RIGHT,
}

enum class DockSide { NONE, LEFT, RIGHT }

enum class RevealSource { SHAKE, NOTIFICATION }

data class FloatingPlacement(
    val xFraction: Float = 0.85f,
    val yFraction: Float = 0.50f,
    val dockSide: DockSide = DockSide.NONE,
) {
    fun sanitized(): FloatingPlacement = copy(
        xFraction = xFraction.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0.85f,
        yFraction = yFraction.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0.50f,
    )
}
```

Remove the old `MeasureMode` declaration from `InspectorController.kt` once this file exists.

- [ ] **Step 2: Replace the controller tests with the new state-transition contract**

Update `InspectorControllerTest.kt` to cover direct switching, Hide-vs-Stop independence, docking, reveal, and listener idempotence:

```kotlin
package com.noctisoft.layoutmeasurement

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class InspectorControllerTest {

    @After
    fun reset() {
        InspectorController.stopInspection()
        InspectorController.restorePlacement(FloatingPlacement())
        InspectorController.hideControlsForStartup()
    }

    @Test
    fun `selectMode starts session and switches directly`() {
        InspectorController.selectMode(MeasureMode.SIZE)
        assertEquals(true, InspectorController.isActive)
        assertEquals(MeasureMode.SIZE, InspectorController.mode)

        InspectorController.selectMode(MeasureMode.GAP)
        assertEquals(true, InspectorController.isActive)
        assertEquals(MeasureMode.GAP, InspectorController.mode)
    }

    @Test
    fun `hide controls does not stop active session`() {
        InspectorController.revealControls(RevealSource.SHAKE)
        InspectorController.selectMode(MeasureMode.RULER)
        InspectorController.hideControls()

        assertEquals(true, InspectorController.isActive)
        assertEquals(MeasureMode.RULER, InspectorController.mode)
        assertEquals(FloatingControlState.HIDDEN, InspectorController.controlState)
    }

    @Test
    fun `stop session does not force controls hidden`() {
        InspectorController.revealControls(RevealSource.SHAKE)
        InspectorController.selectMode(MeasureMode.BOUNDS)
        InspectorController.stopInspection()

        assertEquals(false, InspectorController.isActive)
        assertEquals(FloatingControlState.COLLAPSED, InspectorController.controlState)
    }

    @Test
    fun `dock and tap restore usable control`() {
        InspectorController.revealControls(RevealSource.SHAKE)
        InspectorController.dock(DockSide.RIGHT)
        assertEquals(FloatingControlState.DOCKED_RIGHT, InspectorController.controlState)

        InspectorController.undockControls()
        assertEquals(FloatingControlState.COLLAPSED, InspectorController.controlState)
        assertEquals(DockSide.NONE, InspectorController.placement.dockSide)
    }

    @Test
    fun `startup hidden reveal restores persisted dock`() {
        InspectorController.restorePlacement(
            FloatingPlacement(0.9f, 0.4f, DockSide.LEFT)
        )
        InspectorController.hideControlsForStartup()
        InspectorController.revealControls(RevealSource.SHAKE)

        assertEquals(FloatingControlState.DOCKED_LEFT, InspectorController.controlState)
    }

    @Test
    fun `explicit hide ignores shake and notification restores`() {
        InspectorController.revealControls(RevealSource.SHAKE)
        InspectorController.hideControls()

        InspectorController.revealControls(RevealSource.SHAKE)
        assertEquals(FloatingControlState.HIDDEN, InspectorController.controlState)

        InspectorController.revealControls(RevealSource.NOTIFICATION)
        assertEquals(FloatingControlState.COLLAPSED, InspectorController.controlState)
    }

    @Test
    fun `same state does not notify twice`() {
        var fired = 0
        val listener = { fired++; Unit }
        InspectorController.addListener(listener)

        InspectorController.revealControls(RevealSource.SHAKE)
        InspectorController.collapseControls()

        InspectorController.removeListener(listener)
        assertEquals(1, fired)
    }
}
```

- [ ] **Step 3: Run the controller tests to establish RED**

Run:

```bash
./gradlew :inspector:testDebugUnitTest --tests "com.noctisoft.layoutmeasurement.InspectorControllerTest"
```

Expected: FAIL because the new state types and controller transition methods are not implemented yet.

- [ ] **Step 4: Implement the controller state machine**

Replace `InspectorController.kt` with:

```kotlin
package com.noctisoft.layoutmeasurement

import java.util.concurrent.CopyOnWriteArrayList

object InspectorController {
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    var isActive: Boolean = false
        private set

    var mode: MeasureMode = MeasureMode.SIZE
        private set

    var controlState: FloatingControlState = FloatingControlState.HIDDEN
        private set

    var placement: FloatingPlacement = FloatingPlacement()
        private set

    private var notificationRestoreRequired: Boolean = false

    fun selectMode(newMode: MeasureMode) {
        val changed = !isActive || mode != newMode
        isActive = true
        mode = newMode
        if (changed) notifyListeners()
    }

    fun stopInspection() {
        if (!isActive) return
        isActive = false
        notifyListeners()
    }

    fun revealControls(source: RevealSource) {
        if (
            controlState == FloatingControlState.HIDDEN &&
            notificationRestoreRequired &&
            source != RevealSource.NOTIFICATION
        ) return

        val previousState = controlState
        val previousPlacement = placement
        if (source == RevealSource.NOTIFICATION) notificationRestoreRequired = false

        when (controlState) {
            FloatingControlState.HIDDEN -> {
                controlState = when (placement.dockSide) {
                    DockSide.LEFT -> FloatingControlState.DOCKED_LEFT
                    DockSide.RIGHT -> FloatingControlState.DOCKED_RIGHT
                    DockSide.NONE -> FloatingControlState.COLLAPSED
                }
            }
            FloatingControlState.DOCKED_LEFT,
            FloatingControlState.DOCKED_RIGHT -> undockControls(notify = false)
            FloatingControlState.EXPANDED -> controlState = FloatingControlState.COLLAPSED
            FloatingControlState.COLLAPSED -> Unit
        }
        if (previousState != controlState || previousPlacement != placement) notifyListeners()
    }

    fun undockControls() = undockControls(notify = true)

    private fun undockControls(notify: Boolean) {
        if (
            controlState != FloatingControlState.DOCKED_LEFT &&
            controlState != FloatingControlState.DOCKED_RIGHT
        ) return
        placement = placement.copy(dockSide = DockSide.NONE)
        controlState = FloatingControlState.COLLAPSED
        if (notify) notifyListeners()
    }

    fun hideControlsForStartup() {
        notificationRestoreRequired = false
        if (controlState == FloatingControlState.HIDDEN) return
        controlState = FloatingControlState.HIDDEN
        notifyListeners()
    }

    fun expandControls() {
        if (controlState != FloatingControlState.COLLAPSED) return
        controlState = FloatingControlState.EXPANDED
        notifyListeners()
    }

    fun collapseControls() {
        if (controlState != FloatingControlState.EXPANDED) return
        controlState = FloatingControlState.COLLAPSED
        notifyListeners()
    }

    fun dock(side: DockSide) {
        require(side != DockSide.NONE) { "Dock side must be LEFT or RIGHT" }
        val nextState = if (side == DockSide.LEFT) {
            FloatingControlState.DOCKED_LEFT
        } else {
            FloatingControlState.DOCKED_RIGHT
        }
        val nextPlacement = placement.copy(dockSide = side)
        if (controlState == nextState && placement == nextPlacement) return
        controlState = nextState
        placement = nextPlacement
        notifyListeners()
    }

    fun hideControls() {
        notificationRestoreRequired = true
        if (controlState == FloatingControlState.HIDDEN) return
        controlState = FloatingControlState.HIDDEN
        notifyListeners()
    }

    fun updatePlacement(xFraction: Float, yFraction: Float) {
        val next = FloatingPlacement(
            xFraction = xFraction,
            yFraction = yFraction,
            dockSide = DockSide.NONE,
        ).sanitized()
        if (next == placement) return
        placement = next
        notifyListeners()
    }

    fun restorePlacement(saved: FloatingPlacement) {
        val next = saved.sanitized()
        if (next == placement) return
        placement = next
        notifyListeners()
    }

    fun addListener(listener: () -> Unit) = listeners.add(listener)
    fun removeListener(listener: () -> Unit) = listeners.remove(listener)

    private fun notifyListeners() = listeners.forEach { it() }
}
```

- [ ] **Step 5: Run the controller tests to verify GREEN**

Run the same focused command. Expected: PASS.

- [ ] **Step 6: Run the existing geometry tests to catch accidental shared-type breakage**

Run:

```bash
./gradlew :inspector:testDebugUnitTest --tests "com.noctisoft.layoutmeasurement.GeometryTest"
```

Expected: PASS.

- [ ] **Step 7: Commit Task 1**

```bash
git add inspector/src/main/java/com/noctisoft/layoutmeasurement/InspectorState.kt \
        inspector/src/main/java/com/noctisoft/layoutmeasurement/InspectorController.kt \
        inspector/src/test/java/com/noctisoft/layoutmeasurement/InspectorControllerTest.kt
git commit -m "refactor: model inspector session and control state"
```

---

### Task 2: Add pure floating-control geometry and persistent placement codec

**Files:**
- Create: `inspector/src/main/java/com/noctisoft/layoutmeasurement/FloatingControlGeometry.kt`
- Create: `inspector/src/main/java/com/noctisoft/layoutmeasurement/InspectorPlacementStore.kt`
- Create: `inspector/src/test/java/com/noctisoft/layoutmeasurement/FloatingControlGeometryTest.kt`
- Create: `inspector/src/test/java/com/noctisoft/layoutmeasurement/InspectorPlacementCodecTest.kt`

**Interfaces:**
- Consumes: `FloatingPlacement`, `DockSide` from Task 1.
- Produces:
  - `data class PixelPoint(val x: Int, val y: Int)`
  - `data class SafeArea(val left: Int, val top: Int, val right: Int, val bottom: Int)`
  - `data class ControlSize(val width: Int, val height: Int)`
  - `FloatingControlGeometry.clamp(...)`
  - `FloatingControlGeometry.toNormalized(...)`
  - `FloatingControlGeometry.fromNormalized(...)`
  - `FloatingControlGeometry.chooseDockSide(...)`
  - `FloatingControlGeometry.dockedPosition(...)`
  - `FloatingPlacementCodec.encode/decode`
  - `InspectorPlacementStore.load/save`.

- [ ] **Step 1: Write geometry tests**

Create `FloatingControlGeometryTest.kt`:

```kotlin
package com.noctisoft.layoutmeasurement

import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingControlGeometryTest {
    private val safe = SafeArea(left = 20, top = 40, right = 980, bottom = 1880)
    private val size = ControlSize(width = 100, height = 100)

    @Test
    fun `clamp keeps full control inside safe area`() {
        assertEquals(PixelPoint(20, 40), FloatingControlGeometry.clamp(PixelPoint(-50, -20), safe, size))
        assertEquals(PixelPoint(880, 1780), FloatingControlGeometry.clamp(PixelPoint(950, 1900), safe, size))
    }

    @Test
    fun `normalized position round trips across safe area`() {
        val point = PixelPoint(450, 900)
        val normalized = FloatingControlGeometry.toNormalized(point, safe, size)
        assertEquals(point, FloatingControlGeometry.fromNormalized(normalized, safe, size))
    }

    @Test
    fun `near left and right edges choose dock side`() {
        assertEquals(
            DockSide.LEFT,
            FloatingControlGeometry.chooseDockSide(PixelPoint(25, 500), safe, size, thresholdPx = 80),
        )
        assertEquals(
            DockSide.RIGHT,
            FloatingControlGeometry.chooseDockSide(PixelPoint(870, 500), safe, size, thresholdPx = 80),
        )
        assertEquals(
            DockSide.NONE,
            FloatingControlGeometry.chooseDockSide(PixelPoint(450, 500), safe, size, thresholdPx = 80),
        )
    }

    @Test
    fun `docked position leaves half button visible`() {
        assertEquals(
            PixelPoint(-30, 500),
            FloatingControlGeometry.dockedPosition(DockSide.LEFT, 500, safe, size),
        )
        assertEquals(
            PixelPoint(930, 500),
            FloatingControlGeometry.dockedPosition(DockSide.RIGHT, 500, safe, size),
        )
    }
}
```

- [ ] **Step 2: Write placement codec tests**

Create `InspectorPlacementCodecTest.kt`:

```kotlin
package com.noctisoft.layoutmeasurement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InspectorPlacementCodecTest {
    @Test
    fun `codec round trips normalized placement and dock side`() {
        val source = FloatingPlacement(0.25f, 0.75f, DockSide.RIGHT)
        assertEquals(source, FloatingPlacementCodec.decode(FloatingPlacementCodec.encode(source)))
    }

    @Test
    fun `codec clamps malformed fractions and rejects invalid records`() {
        assertEquals(
            FloatingPlacement(0f, 1f, DockSide.LEFT),
            FloatingPlacementCodec.decode("-2.0|4.0|LEFT"),
        )
        assertNull(FloatingPlacementCodec.decode("not-a-placement"))
        assertNull(FloatingPlacementCodec.decode("0.2|0.4|SIDEWAYS"))
    }
}
```

- [ ] **Step 3: Run both new test classes to establish RED**

```bash
./gradlew :inspector:testDebugUnitTest \
  --tests "com.noctisoft.layoutmeasurement.FloatingControlGeometryTest" \
  --tests "com.noctisoft.layoutmeasurement.InspectorPlacementCodecTest"
```

Expected: FAIL with unresolved geometry/store symbols.

- [ ] **Step 4: Implement pure geometry helpers**

Create `FloatingControlGeometry.kt`:

```kotlin
package com.noctisoft.layoutmeasurement

import kotlin.math.roundToInt

data class PixelPoint(val x: Int, val y: Int)
data class ControlSize(val width: Int, val height: Int)
data class SafeArea(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)
}

object FloatingControlGeometry {
    fun clamp(point: PixelPoint, safe: SafeArea, size: ControlSize): PixelPoint {
        val maxX = (safe.right - size.width).coerceAtLeast(safe.left)
        val maxY = (safe.bottom - size.height).coerceAtLeast(safe.top)
        return PixelPoint(
            x = point.x.coerceIn(safe.left, maxX),
            y = point.y.coerceIn(safe.top, maxY),
        )
    }

    fun toNormalized(point: PixelPoint, safe: SafeArea, size: ControlSize): FloatingPlacement {
        val clamped = clamp(point, safe, size)
        val xRange = (safe.right - safe.left - size.width).coerceAtLeast(1)
        val yRange = (safe.bottom - safe.top - size.height).coerceAtLeast(1)
        return FloatingPlacement(
            xFraction = (clamped.x - safe.left).toFloat() / xRange,
            yFraction = (clamped.y - safe.top).toFloat() / yRange,
            dockSide = DockSide.NONE,
        ).sanitized()
    }

    fun fromNormalized(placement: FloatingPlacement, safe: SafeArea, size: ControlSize): PixelPoint {
        val clean = placement.sanitized()
        val xRange = (safe.right - safe.left - size.width).coerceAtLeast(0)
        val yRange = (safe.bottom - safe.top - size.height).coerceAtLeast(0)
        return clamp(
            PixelPoint(
                x = safe.left + (clean.xFraction * xRange).roundToInt(),
                y = safe.top + (clean.yFraction * yRange).roundToInt(),
            ),
            safe,
            size,
        )
    }

    fun chooseDockSide(
        point: PixelPoint,
        safe: SafeArea,
        size: ControlSize,
        thresholdPx: Int,
    ): DockSide {
        val leftDistance = point.x - safe.left
        val rightDistance = (safe.right - size.width) - point.x
        return when {
            leftDistance <= thresholdPx -> DockSide.LEFT
            rightDistance <= thresholdPx -> DockSide.RIGHT
            else -> DockSide.NONE
        }
    }

    fun dockedPosition(
        side: DockSide,
        y: Int,
        safe: SafeArea,
        size: ControlSize,
    ): PixelPoint {
        val clampedY = y.coerceIn(safe.top, (safe.bottom - size.height).coerceAtLeast(safe.top))
        val x = when (side) {
            DockSide.LEFT -> safe.left - size.width / 2
            DockSide.RIGHT -> safe.right - size.width / 2
            DockSide.NONE -> error("Docked position requires LEFT or RIGHT")
        }
        return PixelPoint(x, clampedY)
    }
}
```

- [ ] **Step 5: Implement the codec and `SharedPreferences` store**

Create `InspectorPlacementStore.kt`:

```kotlin
package com.noctisoft.layoutmeasurement

import android.content.Context

object FloatingPlacementCodec {
    fun encode(placement: FloatingPlacement): String {
        val clean = placement.sanitized()
        return "${clean.xFraction}|${clean.yFraction}|${clean.dockSide.name}"
    }

    fun decode(raw: String?): FloatingPlacement? {
        if (raw.isNullOrBlank()) return null
        val parts = raw.split('|')
        if (parts.size != 3) return null
        val x = parts[0].toFloatOrNull() ?: return null
        val y = parts[1].toFloatOrNull() ?: return null
        val side = runCatching { DockSide.valueOf(parts[2]) }.getOrNull() ?: return null
        return FloatingPlacement(x, y, side).sanitized()
    }
}

class InspectorPlacementStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "layout_measurement_inspector",
        Context.MODE_PRIVATE,
    )

    fun load(): FloatingPlacement =
        FloatingPlacementCodec.decode(preferences.getString(KEY_PLACEMENT, null))
            ?: FloatingPlacement()

    fun save(placement: FloatingPlacement) {
        preferences.edit()
            .putString(KEY_PLACEMENT, FloatingPlacementCodec.encode(placement))
            .apply()
    }

    private companion object {
        const val KEY_PLACEMENT = "floating_control_placement"
    }
}
```

- [ ] **Step 6: Run new tests to verify GREEN**

Run the focused command from Step 3. Expected: PASS.

- [ ] **Step 7: Run all inspector JVM tests**

```bash
./gradlew :inspector:testDebugUnitTest
```

Expected: PASS.

- [ ] **Step 8: Commit Task 2**

```bash
git add inspector/src/main/java/com/noctisoft/layoutmeasurement/FloatingControlGeometry.kt \
        inspector/src/main/java/com/noctisoft/layoutmeasurement/InspectorPlacementStore.kt \
        inspector/src/test/java/com/noctisoft/layoutmeasurement/FloatingControlGeometryTest.kt \
        inspector/src/test/java/com/noctisoft/layoutmeasurement/InspectorPlacementCodecTest.kt
git commit -m "feat: add floating control placement model"
```

---

### Task 3: Upgrade notification into restore and Stop control channel

**Files:**
- Create: `inspector/src/main/java/com/noctisoft/layoutmeasurement/InspectorNotificationModel.kt`
- Create: `inspector/src/test/java/com/noctisoft/layoutmeasurement/InspectorNotificationModelTest.kt`
- Modify: `inspector/src/main/java/com/noctisoft/layoutmeasurement/NotificationTrigger.kt`
- Modify: `inspector/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: controller state from Task 1.
- Produces:
  - `NotificationTrigger.ACTION_SHOW = "com.noctisoft.layoutmeasurement.ACTION_SHOW"`
  - `NotificationTrigger.ACTION_STOP = "com.noctisoft.layoutmeasurement.ACTION_STOP"`
  - `NotificationTrigger.initialize(context)`
  - `NotificationTrigger.show(context)`
  - `NotificationTrigger.isRecoveryAvailable(context): Boolean`
  - `InspectorNotificationModel(contentText, showStopAction)`.

- [ ] **Step 1: Add pure notification-model tests**

Create `InspectorNotificationModelTest.kt`:

```kotlin
package com.noctisoft.layoutmeasurement

import org.junit.Assert.assertEquals
import org.junit.Test

class InspectorNotificationModelTest {
    @Test
    fun `stopped session invites user to show controls`() {
        assertEquals(
            InspectorNotificationModel("Tap to show inspector controls", false),
            buildInspectorNotificationModel(false, MeasureMode.SIZE),
        )
    }

    @Test
    fun `active session shows active mode and stop action`() {
        assertEquals(
            InspectorNotificationModel("Gap active", true),
            buildInspectorNotificationModel(true, MeasureMode.GAP),
        )
    }
}
```

- [ ] **Step 2: Run the focused model test to establish RED**

```bash
./gradlew :inspector:testDebugUnitTest --tests "com.noctisoft.layoutmeasurement.InspectorNotificationModelTest"
```

Expected: FAIL because the model builder does not exist.

- [ ] **Step 3: Implement the pure notification model**

Create `InspectorNotificationModel.kt`:

```kotlin
package com.noctisoft.layoutmeasurement

data class InspectorNotificationModel(
    val contentText: String,
    val showStopAction: Boolean,
)

fun buildInspectorNotificationModel(
    isActive: Boolean,
    mode: MeasureMode,
): InspectorNotificationModel {
    if (!isActive) return InspectorNotificationModel("Tap to show inspector controls", false)
    val name = mode.name.lowercase().replaceFirstChar { it.uppercase() }
    return InspectorNotificationModel("$name active", true)
}
```

- [ ] **Step 4: Run the model test to verify GREEN**

Run Step 2 command. Expected: PASS.

- [ ] **Step 5: Replace toggle notification behavior with Show and Stop actions**

Modify `NotificationTrigger.kt` so it has process-lifetime observation and these semantics:

```kotlin
object NotificationTrigger {
    private const val CHANNEL_ID = "layout_inspector"
    private const val NOTIFICATION_ID = 0x1A1
    const val ACTION_SHOW = "com.noctisoft.layoutmeasurement.ACTION_SHOW"
    const val ACTION_STOP = "com.noctisoft.layoutmeasurement.ACTION_STOP"

    private var appContext: Context? = null
    private val controllerListener: () -> Unit = {
        appContext?.let(::show)
    }

    fun initialize(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        InspectorController.addListener(controllerListener)
        show(context)
    }

    fun isRecoveryAvailable(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun show(context: Context) {
        val app = context.applicationContext
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Layout Inspector", NotificationManager.IMPORTANCE_LOW)
            )
        }
        if (!isRecoveryAvailable(app)) return

        val model = buildInspectorNotificationModel(InspectorController.isActive, InspectorController.mode)
        val showIntent = PendingIntent.getBroadcast(
            app,
            1,
            Intent(ACTION_SHOW).setPackage(app.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_crop)
            .setContentTitle("Layout Inspector")
            .setContentText(model.contentText)
            .setOngoing(true)
            .setContentIntent(showIntent)

        if (model.showStopAction) {
            val stopIntent = PendingIntent.getBroadcast(
                app,
                2,
                Intent(ACTION_STOP).setPackage(app.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(0, "Stop Inspector", stopIntent)
        }

        runCatching { manager.notify(NOTIFICATION_ID, builder.build()) }
    }
}

class InspectorActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            NotificationTrigger.ACTION_SHOW -> InspectorController.revealControls(RevealSource.NOTIFICATION)
            NotificationTrigger.ACTION_STOP -> InspectorController.stopInspection()
        }
        NotificationTrigger.show(context)
    }
}
```

Remove the old `ACTION_TOGGLE` and `ToggleReceiver` implementation.

- [ ] **Step 6: Update the manifest receiver intent filter**

Change the receiver block in `inspector/src/main/AndroidManifest.xml` to:

```xml
<receiver
    android:name="com.noctisoft.layoutmeasurement.InspectorActionReceiver"
    android:exported="false">
    <intent-filter>
        <action android:name="com.noctisoft.layoutmeasurement.ACTION_SHOW" />
        <action android:name="com.noctisoft.layoutmeasurement.ACTION_STOP" />
    </intent-filter>
</receiver>
```

- [ ] **Step 7: Run JVM tests and compile the inspector**

```bash
./gradlew :inspector:testDebugUnitTest :inspector:assembleDebug
```

Expected: PASS / `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit Task 3**

```bash
git add inspector/src/main/java/com/noctisoft/layoutmeasurement/InspectorNotificationModel.kt \
        inspector/src/test/java/com/noctisoft/layoutmeasurement/InspectorNotificationModelTest.kt \
        inspector/src/main/java/com/noctisoft/layoutmeasurement/NotificationTrigger.kt \
        inspector/src/main/AndroidManifest.xml
git commit -m "feat: add inspector restore and stop notification actions"
```

---

### Task 4: Build the draggable floating-control UI

**Files:**
- Create: `inspector/src/main/java/com/noctisoft/layoutmeasurement/FloatingInspectorControl.kt`

**Interfaces:**
- Consumes: Task 1 controller types and Task 2 geometry helpers.
- Produces `internal class FloatingInspectorControl(context: Context) : FrameLayout` with callbacks:

```kotlin
var onModeSelected: (MeasureMode) -> Unit
var onStopRequested: () -> Unit
var onHideRequested: () -> Unit
var onExpandRequested: () -> Unit
var onCollapseRequested: () -> Unit
var onUndockRequested: () -> Unit
var onPlacementChanged: (FloatingPlacement) -> Unit
var onDockRequested: (DockSide, FloatingPlacement) -> Unit

fun setSafeArea(area: SafeArea)
fun render(
    state: FloatingControlState,
    placement: FloatingPlacement,
    activeMode: MeasureMode,
    isActive: Boolean,
    canHide: Boolean,
)
```

- [ ] **Step 1: Create the visual container and callback contract**

Create `FloatingInspectorControl.kt` as a full-window `FrameLayout` with three child surfaces:

```kotlin
internal class FloatingInspectorControl(context: Context) : FrameLayout(context) {
    var onModeSelected: (MeasureMode) -> Unit = {}
    var onStopRequested: () -> Unit = {}
    var onHideRequested: () -> Unit = {}
    var onExpandRequested: () -> Unit = {}
    var onCollapseRequested: () -> Unit = {}
    var onUndockRequested: () -> Unit = {}
    var onPlacementChanged: (FloatingPlacement) -> Unit = {}
    var onDockRequested: (DockSide, FloatingPlacement) -> Unit = { _, _ -> }

    private val density = resources.displayMetrics.density
    private val buttonSizePx = (56 * density).roundToInt()
    private val snapThresholdPx = (48 * density).roundToInt()
    private var safeArea = SafeArea(0, 0, 0, 0)
    private var renderedPlacement = FloatingPlacement()
    private var renderedState = FloatingControlState.HIDDEN

    private val dismissLayer = View(context).apply {
        setBackgroundColor(Color.TRANSPARENT)
        visibility = GONE
        setOnClickListener { onCollapseRequested() }
    }

    private val floatingButton = TextView(context).apply {
        text = "◎"
        gravity = Gravity.CENTER
        textSize = 22f
        setTextColor(Color.WHITE)
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.argb(235, 45, 45, 48))
        }
        elevation = 8 * density
        contentDescription = "Layout inspector controls"
    }

    // Declare the menu-helper fields from Step 2 before this property so buildMenu()
    // never observes uninitialized state.
    private val menu = buildMenu(context)

    init {
        clipChildren = false
        clipToPadding = false
        isClickable = false
        addView(dismissLayer, LayoutParams(MATCH_PARENT, MATCH_PARENT))
        addView(menu, LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        addView(floatingButton, LayoutParams(buttonSizePx, buttonSizePx))
        installDragGesture()
    }
}
```

Use `android.graphics.drawable.GradientDrawable`, `android.view.ViewConfiguration`, and `kotlin.math.roundToInt`; do not add Material or another UI runtime dependency to `:inspector`.

- [ ] **Step 2: Build the main menu and Settings/Stop panel**

Implement `buildMenu(context)` as a vertical `LinearLayout` with a dark rounded background and these exact actions:

```kotlin
private lateinit var hideButton: Button
private lateinit var stopButton: Button
private lateinit var mainPanel: LinearLayout
private lateinit var settingsPanel: LinearLayout
private val modeButtons = linkedMapOf<MeasureMode, Button>()

private fun dp(value: Int): Int = (value * density).roundToInt()

private fun actionButton(label: String, action: () -> Unit): Button =
    Button(context).apply {
        text = label
        isAllCaps = false
        textSize = 12f
        setOnClickListener { action() }
    }

private fun modeButton(label: String, mode: MeasureMode): Button =
    actionButton(label) { onModeSelected(mode) }.also { modeButtons[mode] = it }

private fun buildMenu(context: Context): FrameLayout = FrameLayout(context).apply {
    visibility = GONE
    background = GradientDrawable().apply {
        cornerRadius = 12 * density
        setColor(Color.argb(245, 32, 32, 36))
    }
    setPadding(dp(8), dp(8), dp(8), dp(8))

    mainPanel = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(modeButton("Size", MeasureMode.SIZE))
        addView(modeButton("Gap", MeasureMode.GAP))
        addView(modeButton("Ruler", MeasureMode.RULER))
        addView(modeButton("Bounds", MeasureMode.BOUNDS))
        addView(actionButton("Settings") {
            mainPanel.visibility = GONE
            settingsPanel.visibility = VISIBLE
            menu.post(::positionMenu)
        })
        hideButton = actionButton("Hide Inspector") { onHideRequested() }
        addView(hideButton)
    }

    settingsPanel = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        visibility = GONE
        stopButton = actionButton("Stop Inspector") { onStopRequested() }
        addView(stopButton)
        addView(actionButton("Back") {
            settingsPanel.visibility = GONE
            mainPanel.visibility = VISIBLE
            menu.post(::positionMenu)
        })
    }

    addView(mainPanel)
    addView(settingsPanel)
}
```

These helpers use ordinary Android `Button`/`LinearLayout` widgets; no theme-specific resource or new runtime UI dependency is required.

- [ ] **Step 3: Implement click-vs-drag detection on the circle**

Use the platform touch slop so a tap expands/undocks but a drag repositions:

```kotlin
@SuppressLint("ClickableViewAccessibility")
private fun installDragGesture() {
    val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    var downRawX = 0f
    var downRawY = 0f
    var startX = 0f
    var startY = 0f
    var dragging = false

    floatingButton.setOnTouchListener { view, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                startX = view.x
                startY = view.y
                dragging = false
                true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                if (!dragging && hypot(dx.toDouble(), dy.toDouble()) >= touchSlop.toDouble()) dragging = true
                if (dragging) {
                    val clamped = FloatingControlGeometry.clamp(
                        PixelPoint((startX + dx).roundToInt(), (startY + dy).roundToInt()),
                        safeArea,
                        ControlSize(buttonSizePx, buttonSizePx),
                    )
                    floatingButton.x = clamped.x.toFloat()
                    floatingButton.y = clamped.y.toFloat()
                    menu.visibility = GONE
                    dismissLayer.visibility = GONE
                }
                true
            }
            MotionEvent.ACTION_UP -> {
                if (!dragging) {
                    view.performClick()
                    when (renderedState) {
                        FloatingControlState.DOCKED_LEFT,
                        FloatingControlState.DOCKED_RIGHT -> onUndockRequested()
                        else -> onExpandRequested()
                    }
                } else {
                    finishDrag(PixelPoint(view.x.roundToInt(), view.y.roundToInt()))
                }
                true
            }
            MotionEvent.ACTION_CANCEL -> true
            else -> false
        }
    }
}
```

- [ ] **Step 4: Implement drag completion, persistence callback, and edge docking**

```kotlin
private fun finishDrag(point: PixelPoint) {
    val size = ControlSize(buttonSizePx, buttonSizePx)
    val clamped = FloatingControlGeometry.clamp(point, safeArea, size)
    val normalized = FloatingControlGeometry.toNormalized(clamped, safeArea, size)
    val dockSide = FloatingControlGeometry.chooseDockSide(
        clamped,
        safeArea,
        size,
        snapThresholdPx,
    )
    if (dockSide == DockSide.NONE) {
        onPlacementChanged(normalized)
    } else {
        onDockRequested(dockSide, normalized.copy(dockSide = dockSide))
    }
}
```

- [ ] **Step 5: Implement state rendering and safe menu placement**

Store the most recent render inputs so inset/window-size updates can re-layout without inventing state:

```kotlin
private var renderedMode = MeasureMode.SIZE
private var renderedIsActive = false
private var renderedCanHide = false

fun setSafeArea(area: SafeArea) {
    safeArea = area
    applyButtonPosition()
    if (renderedState == FloatingControlState.EXPANDED) menu.post(::positionMenu)
}

fun render(
    state: FloatingControlState,
    placement: FloatingPlacement,
    activeMode: MeasureMode,
    isActive: Boolean,
    canHide: Boolean,
) {
    renderedState = state
    renderedPlacement = placement
    renderedMode = activeMode
    renderedIsActive = isActive
    renderedCanHide = canHide

    visibility = if (state == FloatingControlState.HIDDEN) GONE else VISIBLE
    if (visibility == GONE) return

    hideButton.isEnabled = canHide
    hideButton.text = if (canHide) "Hide Inspector" else "Hide (notification required)"
    stopButton.isEnabled = isActive

    modeButtons.forEach { (mode, button) ->
        val label = mode.name.lowercase().replaceFirstChar { it.uppercase() }
        button.text = if (isActive && mode == activeMode) "• $label" else label
    }

    dismissLayer.visibility = if (state == FloatingControlState.EXPANDED) VISIBLE else GONE
    menu.visibility = if (state == FloatingControlState.EXPANDED) VISIBLE else GONE
    if (state != FloatingControlState.EXPANDED) {
        settingsPanel.visibility = GONE
        mainPanel.visibility = VISIBLE
    }

    applyButtonPosition()
    if (state == FloatingControlState.EXPANDED) menu.post(::positionMenu)
}

private fun applyButtonPosition() {
    if (safeArea.width <= 0 || safeArea.height <= 0) return
    val size = ControlSize(buttonSizePx, buttonSizePx)
    val ordinary = FloatingControlGeometry.fromNormalized(
        renderedPlacement.copy(dockSide = DockSide.NONE),
        safeArea,
        size,
    )
    val target = when (renderedState) {
        FloatingControlState.DOCKED_LEFT ->
            FloatingControlGeometry.dockedPosition(DockSide.LEFT, ordinary.y, safeArea, size)
        FloatingControlState.DOCKED_RIGHT ->
            FloatingControlGeometry.dockedPosition(DockSide.RIGHT, ordinary.y, safeArea, size)
        else -> ordinary
    }
    floatingButton.x = target.x.toFloat()
    floatingButton.y = target.y.toFloat()
}
```

Then add the safe menu positioning helper:

```kotlin
private fun positionMenu() {
    if (menu.visibility != VISIBLE || menu.measuredWidth == 0) return
    val gap = dp(8)
    val buttonCenter = floatingButton.x + floatingButton.width / 2f
    val safeCenter = (safeArea.left + safeArea.right) / 2f
    val preferredX = if (buttonCenter >= safeCenter) {
        floatingButton.x.roundToInt() - gap - menu.measuredWidth
    } else {
        floatingButton.x.roundToInt() + floatingButton.width + gap
    }
    val maxX = (safeArea.right - menu.measuredWidth).coerceAtLeast(safeArea.left)
    val maxY = (safeArea.bottom - menu.measuredHeight).coerceAtLeast(safeArea.top)
    menu.x = preferredX.coerceIn(safeArea.left, maxX).toFloat()
    menu.y = floatingButton.y.roundToInt().coerceIn(safeArea.top, maxY).toFloat()
}
```

Call `menu.post(::positionMenu)` after making the expanded menu visible.

- [ ] **Step 6: Compile the inspector after adding the Android view**

```bash
./gradlew :inspector:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit Task 4**

```bash
git add inspector/src/main/java/com/noctisoft/layoutmeasurement/FloatingInspectorControl.kt
git commit -m "feat: add draggable inspector floating control"
```

---

### Task 5: Split measurement canvas from overlay and add inset-safe composition

**Files:**
- Create: `inspector/src/main/java/com/noctisoft/layoutmeasurement/MeasureCanvas.kt`
- Modify: `inspector/src/main/java/com/noctisoft/layoutmeasurement/InspectorOverlay.kt`

**Interfaces:**
- Consumes: `FloatingInspectorControl`, `InspectorPlacementStore`, controller state, notification recovery availability.
- Produces an overlay that stays full-window while only the floating UI uses inset-constrained `SafeArea`.

- [ ] **Step 1: Move the existing inner `MeasureCanvas` into its own file without changing measurement behavior**

Create `MeasureCanvas.kt` with the current measurement implementation moved intact into a top-level internal view:

```kotlin
package com.noctisoft.layoutmeasurement

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal class MeasureCanvas(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private var nodes: List<CapturedNode> = emptyList()
    private var selectedA: CapturedNode? = null
    private var selectedB: CapturedNode? = null
    private var rulerStart: Pair<Float, Float>? = null
    private var rulerEnd: Pair<Float, Float>? = null
    private var nextIsA = true

    private val boundsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.rgb(255, 64, 129)
    }
    private val allBoundsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = Color.rgb(0, 176, 255)
    }
    private val gapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 3f
        color = Color.rgb(255, 196, 0)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 12f * density
    }
    private val textBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 0, 0, 0)
    }

    fun clearSelection() {
        selectedA = null
        selectedB = null
        rulerStart = null
        rulerEnd = null
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
            MeasureMode.RULER -> when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    rulerStart = event.x to event.y
                    rulerEnd = null
                }
                MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> rulerEnd = event.x to event.y
            }.also { invalidate() }
            MeasureMode.BOUNDS -> if (event.action == MotionEvent.ACTION_DOWN) {
                recapture()
                invalidate()
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        if (!InspectorController.isActive) return
        when (InspectorController.mode) {
            MeasureMode.SIZE -> selectedA?.let { drawSize(canvas, it) }
            MeasureMode.GAP -> drawGap(canvas)
            MeasureMode.RULER -> drawRuler(canvas)
            MeasureMode.BOUNDS -> drawAllBounds(canvas)
        }
    }

    private fun drawSize(canvas: Canvas, node: CapturedNode) {
        drawBounds(canvas, node.bounds, boundsPaint)
        val text = "${node.label}: ${Geometry.formatPx(node.bounds.width, density)} × " +
            Geometry.formatPx(node.bounds.height, density)
        drawLabel(
            canvas,
            text,
            node.bounds.left.toFloat(),
            max(node.bounds.top - 8, 40).toFloat(),
        )
    }

    private fun drawGap(canvas: Canvas) {
        val a = selectedA ?: return
        drawBounds(canvas, a.bounds, boundsPaint)
        val b = selectedB ?: return
        drawBounds(canvas, b.bounds, boundsPaint)

        val horizontalGap = Geometry.horizontalGap(a.bounds, b.bounds)
        if (horizontalGap > 0) {
            val x1 = min(a.bounds.right, b.bounds.right).toFloat()
            val x2 = max(a.bounds.left, b.bounds.left).toFloat()
            val y = (max(a.bounds.top, b.bounds.top) + min(a.bounds.bottom, b.bounds.bottom)) / 2f
            canvas.drawLine(x1, y, x2, y, gapPaint)
            drawLabel(canvas, Geometry.formatPx(horizontalGap, density), (x1 + x2) / 2, y - 8)
        }

        val verticalGap = Geometry.verticalGap(a.bounds, b.bounds)
        if (verticalGap > 0) {
            val y1 = min(a.bounds.bottom, b.bounds.bottom).toFloat()
            val y2 = max(a.bounds.top, b.bounds.top).toFloat()
            val x = (max(a.bounds.left, b.bounds.left) + min(a.bounds.right, b.bounds.right)) / 2f
            canvas.drawLine(x, y1, x, y2, gapPaint)
            drawLabel(canvas, Geometry.formatPx(verticalGap, density), x + 8, (y1 + y2) / 2)
        }

        if (horizontalGap == 0 && verticalGap == 0) {
            drawLabel(
                canvas,
                "overlapping (gap 0)",
                a.bounds.left.toFloat(),
                max(a.bounds.top - 8, 40).toFloat(),
            )
        }
    }

    private fun drawRuler(canvas: Canvas) {
        val start = rulerStart ?: return
        val end = rulerEnd ?: return
        canvas.drawLine(start.first, start.second, end.first, end.second, gapPaint)
        val length = hypot(end.first - start.first, end.second - start.second).roundToInt()
        drawLabel(
            canvas,
            Geometry.formatPx(length, density),
            (start.first + end.first) / 2,
            (start.second + end.second) / 2 - 8,
        )
    }

    private fun drawAllBounds(canvas: Canvas) {
        if (nodes.isEmpty()) recapture()
        nodes.forEach { drawBounds(canvas, it.bounds, allBoundsPaint) }
    }

    private fun drawBounds(canvas: Canvas, bounds: Bounds, paint: Paint) {
        canvas.drawRect(
            bounds.left.toFloat(),
            bounds.top.toFloat(),
            bounds.right.toFloat(),
            bounds.bottom.toFloat(),
            paint,
        )
    }

    private fun drawLabel(canvas: Canvas, text: String, x: Float, y: Float) {
        val width = textPaint.measureText(text)
        val height = textPaint.textSize
        val padding = 6f
        val clampedX = min(max(x, 0f), this.width - width - 2 * padding)
        canvas.drawRect(
            clampedX - padding,
            y - height - padding,
            clampedX + width + padding,
            y + padding,
            textBgPaint,
        )
        canvas.drawText(text, clampedX, y - padding / 2, textPaint)
    }
}
```

Do not add inset offsets or padding to this class. The touch/draw guards must remain tied only to `InspectorController.isActive`.

- [ ] **Step 2: Replace the fixed toolbar in `InspectorOverlay` with the floating control layer**

Change constructor and fields to:

```kotlin
class InspectorOverlay(
    context: Context,
    private val placementStore: InspectorPlacementStore = InspectorPlacementStore(context),
) : FrameLayout(context) {

    companion object {
        const val TAG = "com.noctisoft.layoutmeasurement.OVERLAY"
    }

    private val canvas = MeasureCanvas(context)
    private val controls = FloatingInspectorControl(context)
    private var lastSafeInsets = androidx.core.graphics.Insets.NONE
    private val controllerListener: () -> Unit = { post { sync() } }
```

Initialize children in this order so controls win touch priority:

```kotlin
init {
    tag = TAG
    setWillNotDraw(true)
    addView(canvas, LayoutParams(MATCH_PARENT, MATCH_PARENT))
    addView(controls, LayoutParams(MATCH_PARENT, MATCH_PARENT))
    wireControlCallbacks()
    installInsetsListener()
    sync()
}
```

Delete the old `toolbar`, `buildToolbar`, `Gravity.BOTTOM`, and fixed `bottomMargin` code.

- [ ] **Step 3: Wire floating-control actions to controller + placement persistence**

Add:

```kotlin
private fun wireControlCallbacks() = with(controls) {
    onModeSelected = { mode ->
        InspectorController.selectMode(mode)
        InspectorController.collapseControls()
    }
    onStopRequested = {
        InspectorController.stopInspection()
        InspectorController.collapseControls()
    }
    onHideRequested = {
        if (NotificationTrigger.isRecoveryAvailable(context)) {
            InspectorController.hideControls()
        }
    }
    onExpandRequested = { InspectorController.expandControls() }
    onCollapseRequested = { InspectorController.collapseControls() }
    onUndockRequested = { InspectorController.undockControls() }
    onPlacementChanged = { placement ->
        InspectorController.updatePlacement(placement.xFraction, placement.yFraction)
        placementStore.save(InspectorController.placement)
    }
    onDockRequested = { side, placement ->
        InspectorController.updatePlacement(placement.xFraction, placement.yFraction)
        InspectorController.dock(side)
        placementStore.save(InspectorController.placement)
    }
}
```

- [ ] **Step 4: Add AndroidX inset handling only for the control layer**

Add imports for `ViewCompat`, `WindowInsetsCompat`, and `androidx.core.graphics.Insets`. Install a non-consuming listener:

```kotlin
private fun installInsetsListener() {
    ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
        lastSafeInsets = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout() or
                WindowInsetsCompat.Type.mandatorySystemGestures()
        )
        updateSafeArea()
        insets
    }
}

private fun updateSafeArea() {
    if (width == 0 || height == 0) return
    controls.setSafeArea(
        SafeArea(
            left = lastSafeInsets.left,
            top = lastSafeInsets.top,
            right = width - lastSafeInsets.right,
            bottom = height - lastSafeInsets.bottom,
        )
    )
}

override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    super.onSizeChanged(w, h, oldw, oldh)
    updateSafeArea()
}
```

Do not call `setPadding()` on `InspectorOverlay` or `MeasureCanvas`.

- [ ] **Step 5: Request current insets when dynamically attached**

Update attach/detach:

```kotlin
override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    InspectorController.addListener(controllerListener)
    ViewCompat.requestApplyInsets(this)
    sync()
}

override fun onDetachedFromWindow() {
    InspectorController.removeListener(controllerListener)
    super.onDetachedFromWindow()
}
```

- [ ] **Step 6: Render measurement and floating states independently**

Replace `sync()` with:

```kotlin
private fun sync() {
    val active = InspectorController.isActive
    canvas.visibility = if (active) VISIBLE else GONE
    if (!active) canvas.clearSelection()
    canvas.invalidate()

    controls.render(
        state = InspectorController.controlState,
        placement = InspectorController.placement,
        activeMode = InspectorController.mode,
        isActive = active,
        canHide = NotificationTrigger.isRecoveryAvailable(context),
    )
}
```

This is the key Hide-vs-Stop separation: `canvas` follows session state; `controls` follows control state.

- [ ] **Step 7: Run inspector tests and compilation**

```bash
./gradlew :inspector:testDebugUnitTest :inspector:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Verify no fixed toolbar remains**

```bash
git grep -nE 'buildToolbar|Gravity\.BOTTOM|bottomMargin = \(16' -- inspector/src/main/java/com/noctisoft/layoutmeasurement
```

Expected: no output / exit status `1`.

- [ ] **Step 9: Commit Task 5**

```bash
git add inspector/src/main/java/com/noctisoft/layoutmeasurement/MeasureCanvas.kt \
        inspector/src/main/java/com/noctisoft/layoutmeasurement/InspectorOverlay.kt
git commit -m "refactor: compose inspector overlay from canvas and floating controls"
```

---

### Task 6: Restore persisted placement at startup and change shake semantics

**Files:**
- Modify: `inspector/src/main/java/com/noctisoft/layoutmeasurement/InspectorInitializer.kt`
- Modify: `inspector/src/main/java/com/noctisoft/layoutmeasurement/InspectorLifecycle.kt`

**Interfaces:**
- Consumes: `InspectorPlacementStore`, `NotificationTrigger.initialize`, `InspectorController.revealControls(source)` and `undockControls()`.
- Produces: fresh-process hidden state with saved placement loaded; Activity transitions preserve controller session/control state; shake reveals/undocks instead of stopping sessions.

- [ ] **Step 1: Initialize placement and notification observation before lifecycle callbacks**

Change `InspectorInitializer.create` to:

```kotlin
override fun create(context: Context) {
    val app = context.applicationContext as Application
    val placementStore = InspectorPlacementStore(app)
    InspectorController.restorePlacement(placementStore.load())
    InspectorController.hideControlsForStartup()
    NotificationTrigger.initialize(app)
    app.registerActivityLifecycleCallbacks(InspectorLifecycle)
}
```

Do not restore `isActive` or `mode` from storage. A new process remains stopped.

- [ ] **Step 2: Change shake behavior from session toggle to control reveal**

In `InspectorLifecycle.onActivityResumed`, replace:

```kotlin
val detector = ShakeDetector { InspectorController.toggle() }
```

with:

```kotlin
val detector = ShakeDetector { InspectorController.revealControls(RevealSource.SHAKE) }
```

The `toggle()` API no longer exists after Task 1.

- [ ] **Step 3: Inject placement persistence into each Activity overlay and keep one overlay per Activity**

Change `attachOverlay` to:

```kotlin
private fun attachOverlay(activity: Activity) {
    val decor = activity.window.decorView as? ViewGroup ?: return
    if (decor.findViewWithTag<android.view.View>(InspectorOverlay.TAG) != null) return
    decor.addView(
        InspectorOverlay(
            context = activity,
            placementStore = InspectorPlacementStore(activity.applicationContext),
        ),
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
    )
}
```

Keep the existing defensive shake-detector removal and `NotificationTrigger.show(activity)` on resume. The latter is required so a notification appears after the user grants notification permission and returns to the Activity.

- [ ] **Step 4: Add controller regression coverage for Activity-independent state assumptions**

Append these tests to `InspectorControllerTest.kt`:

```kotlin
@Test
fun `restoring placement does not start session or reveal control`() {
    InspectorController.stopInspection()
    InspectorController.hideControls()
    InspectorController.restorePlacement(FloatingPlacement(0.1f, 0.8f, DockSide.RIGHT))

    assertEquals(false, InspectorController.isActive)
    assertEquals(FloatingControlState.HIDDEN, InspectorController.controlState)
    assertEquals(DockSide.RIGHT, InspectorController.placement.dockSide)
}

@Test
fun `reveal while running preserves active mode`() {
    InspectorController.selectMode(MeasureMode.GAP)
    InspectorController.hideControls()
    InspectorController.revealControls(RevealSource.NOTIFICATION)

    assertEquals(true, InspectorController.isActive)
    assertEquals(MeasureMode.GAP, InspectorController.mode)
}
```

- [ ] **Step 5: Run tests and build**

```bash
./gradlew :inspector:testDebugUnitTest :sample:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit Task 6**

```bash
git add inspector/src/main/java/com/noctisoft/layoutmeasurement/InspectorInitializer.kt \
        inspector/src/main/java/com/noctisoft/layoutmeasurement/InspectorLifecycle.kt \
        inspector/src/test/java/com/noctisoft/layoutmeasurement/InspectorControllerTest.kt
git commit -m "feat: persist floating inspector placement across activities"
```

---

### Task 7: Make the sample app explicitly edge-to-edge for regression testing

**Files:**
- Modify: `sample/src/main/java/com/noctisoft/layoutmeasurement/sample/MainActivity.kt`
- Modify: `sample/src/main/java/com/noctisoft/layoutmeasurement/sample/ComposeActivity.kt`
- Modify: `sample/src/main/java/com/noctisoft/layoutmeasurement/sample/MixedActivity.kt`

**Interfaces:**
- Produces a deterministic edge-to-edge sample environment on supported Android versions without changing inspector library APIs.

- [ ] **Step 1: Enable edge-to-edge in every sample Activity before content setup**

Add:

```kotlin
import androidx.activity.enableEdgeToEdge
```

Then call `enableEdgeToEdge()` immediately after `super.onCreate(savedInstanceState)` and before `setContentView(...)` or `setContent { ... }` in all three sample Activities.

Example for `MainActivity`:

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContentView(R.layout.activity_main)
    // existing sample code continues unchanged
}
```

Apply the same ordering in `ComposeActivity` and `MixedActivity`.

- [ ] **Step 2: Assemble the sample**

```bash
./gradlew :sample:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit Task 7**

```bash
git add sample/src/main/java/com/noctisoft/layoutmeasurement/sample/MainActivity.kt \
        sample/src/main/java/com/noctisoft/layoutmeasurement/sample/ComposeActivity.kt \
        sample/src/main/java/com/noctisoft/layoutmeasurement/sample/MixedActivity.kt
git commit -m "test: exercise inspector in edge to edge sample screens"
```

---

### Task 8: Perform Android 16+/edge-to-edge manual verification

**Files:**
- No production changes expected unless a verification failure reveals a defect.

**Interfaces:**
- Consumes the completed implementation.
- Produces manual evidence for interaction behavior that JVM tests cannot validate: touch routing, half-visible docking, insets, screenshots, Activity transitions, and notification recovery.

- [ ] **Step 1: Install the debug sample on an Android 16+ emulator/device**

Run:

```bash
./gradlew :sample:installDebug
```

Then launch `com.noctisoft.layoutmeasurement/.sample.MainActivity` from the launcher or Android Studio.

- [ ] **Step 2: Verify initial and reveal behavior**

Confirm all of the following:

```text
[ ] Fresh process: no floating inspector control is visible.
[ ] Shake reveals exactly one floating circle.
[ ] Notification tap reveals exactly one floating circle.
[ ] Repeated shake/notification does not duplicate controls.
```

- [ ] **Step 3: Verify drag, dock, and safe insets**

Confirm:

```text
[ ] Circle can be dragged anywhere inside safe interactive bounds.
[ ] It cannot be left underneath bottom navigation/gesture UI.
[ ] Releasing near the left edge docks it half-visible.
[ ] Releasing near the right edge docks it half-visible.
[ ] Docked visible half remains tappable.
[ ] Tapping docked control restores a full collapsed circle.
[ ] Rotate portrait <-> landscape and repeat docking.
```

- [ ] **Step 4: Verify expanded menu and direct tool switching**

Confirm:

```text
[ ] Tap collapsed circle -> menu expands.
[ ] Tap outside -> menu collapses without stopping current tool.
[ ] Select Size -> session starts.
[ ] Select Gap -> switches directly while session stays active.
[ ] Select Ruler -> switches directly.
[ ] Select Bounds -> switches directly.
[ ] Settings -> Stop Inspector stops measurement but keeps controls available.
```

- [ ] **Step 5: Verify Hide vs Stop independence**

With Gap active:

```text
[ ] Expand menu -> Hide Inspector -> floating UI disappears completely.
[ ] Gap measurement remains active.
[ ] Notification shows "Gap active" and a Stop Inspector action.
[ ] Shake does NOT restore the deliberately hidden control.
[ ] Notification tap restores the controls without restarting/changing mode.
[ ] Notification Stop stops measurement.
```

- [ ] **Step 6: Verify notification-denied recovery guard**

On API 33+, revoke notification permission for the sample and resume it. Confirm:

```text
[ ] Inspector does not crash.
[ ] Hide Inspector is disabled or labeled "Hide (notification required)".
[ ] Docking still works.
[ ] The developer cannot enter an unrecoverable deliberate HIDDEN state.
```

Restore notification permission before continuing.

- [ ] **Step 7: Verify Activity continuity**

Start a tool on MainActivity, move the circle, then navigate to ComposeActivity and MixedActivity. Confirm:

```text
[ ] Active mode remains active across Activity transitions.
[ ] Floating state remains collapsed/expanded/docked as appropriate.
[ ] Normalized position remains visually equivalent after transition.
[ ] Exactly one overlay exists on the currently viewed Activity.
```

- [ ] **Step 8: Verify process-restart persistence**

Dock the control, force-stop the sample, relaunch, then reveal inspector. Confirm:

```text
[ ] Session starts STOPPED.
[ ] Control starts HIDDEN.
[ ] First reveal restores the saved placement/dock side.
[ ] Expanded-menu state was not persisted.
```

- [ ] **Step 9: Verify screenshot use case**

Start any measurement mode, choose **Hide Inspector**, then capture a device screenshot. Confirm no floating control/menu is visible while the measurement session remains active.

- [ ] **Step 10: Record any manual failures before changing code**

If any checkbox fails, capture:

```text
Android version / API level
navigation mode (gesture or 3-button)
orientation/window mode
exact state before failure
expected result
actual result
```

Investigate with `superpowers:systematic-debugging` before applying a fix.

---

### Task 9: Final repository-wide verification and hygiene

**Files:**
- No production changes expected.

**Interfaces:**
- Produces fresh completion evidence for the entire redesign.

- [ ] **Step 1: Run the full required test/build gate**

```bash
./gradlew :inspector:testDebugUnitTest :sample:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Verify inspector-owned UI remains excluded from capture through the overlay tag**

```bash
git grep -n 'com.noctisoft.layoutmeasurement.OVERLAY' -- inspector/src/main
```

Expected at minimum:

```text
InspectorOverlay.kt: const val TAG = "com.noctisoft.layoutmeasurement.OVERLAY"
ViewCapture.kt: private const val OVERLAY_TAG = "com.noctisoft.layoutmeasurement.OVERLAY"
```

The two values must remain identical.

- [ ] **Step 3: Verify old toggle API and fixed toolbar are gone**

```bash
git grep -nE 'ACTION_TOGGLE|ToggleReceiver|InspectorController\.toggle\(\)|buildToolbar|Gravity\.BOTTOM' -- inspector
```

Expected: no output / exit status `1`.

- [ ] **Step 4: Verify no system-overlay permission or foreground-service architecture was introduced**

```bash
git grep -nE 'SYSTEM_ALERT_WINDOW|TYPE_APPLICATION_OVERLAY|startForegroundService|Service\(' -- inspector
```

Expected: no output / exit status `1`.

- [ ] **Step 5: Verify Git hygiene**

```bash
git diff --check
git status --short
```

Expected:

- `git diff --check` prints nothing.
- No generated `build/`, APK, IDE, or Gradle cache output is staged.
- Working tree contains only intentional uncommitted verification notes, if any.

- [ ] **Step 6: Review the cumulative code diff for scope**

```bash
git diff main...HEAD -- inspector sample
```

Expected scope:

- controller/session/control state,
- floating-control geometry/persistence,
- floating UI and measurement-canvas split,
- edge-to-edge inset handling,
- notification Show/Stop actions,
- lifecycle/initializer wiring,
- sample edge-to-edge enablement,
- tests.

There must be no unrelated measurement-math, Compose capture, SDK-version, package-name, or dependency changes.

- [ ] **Step 7: Report completion evidence**

The completion report must include:

```text
Final session/control state model
Persistence behavior across Activity/process restart
Notification Show/Stop behavior
Hide-vs-Stop behavior
Android 16+ manual verification result
Gradle command and BUILD SUCCESSFUL result
Stale fixed-toolbar/toggle scan result
Working-tree status
```

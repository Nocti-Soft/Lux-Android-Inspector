# Android Layout Inspector — Design

**Date:** 2026-07-07
**Status:** Approved (pending user spec review)

## Purpose

An in-app developer tool that measures UI component sizes and the spacing between
widgets at runtime, inside the host app. Ships as a drop-in library (like
LeakCanary): add it as a `debugImplementation` dependency and it self-activates
with zero host-app code. Works on both classic XML `View` hierarchies and Jetpack
Compose UI.

## Scope

**In scope (v1)**
- Reusable `:inspector` library module.
- `:sample` demo app with mixed Compose + XML screens for testing.
- Four measurement modes: single-widget size, two-widget gap, free ruler, show-all-bounds.
- Two activation triggers: shake gesture and an ongoing notification toggle.
- Overlay attached to the Activity decorView (no runtime permissions).
- XML `View` capture: full hit-testing.
- Compose capture: semantics tree, descending only into nodes that carry a
  `testTag`/semantics. Untagged `ComposeView` measured as a single island.

**Out of scope (v1)**
- System-overlay window (`SYSTEM_ALERT_WINDOW`) / cross-activity floating.
- Full arbitrary-Composable hit-testing via reflection into `AndroidComposeView`
  internals. Deferred — add if tagged-node granularity proves insufficient.
- Persisting / exporting measurements.
- Release-build behavior. The library is debug-only.

## Modules

| Module | Type | Depends on |
|--------|------|-----------|
| `:inspector` | Android library | androidx.startup, Compose UI (compileOnly where possible) |
| `:sample` | Android app | `debugImplementation project(':inspector')` |

The sample app is the manual test harness. It contains at least one XML-layout
screen and one Compose screen, plus a screen mixing `ComposeView` inside XML, so
every capture path has a target.

## Auto-Init

1. `androidx.startup.Initializer<Unit>` declared in the library manifest.
2. On init, register `Application.ActivityLifecycleCallbacks`.
3. `onActivityResumed(activity)`: attach a transparent `OverlayView` to
   `activity.window.decorView` (a `FrameLayout`), matching parent bounds, on top.
4. `onActivityPaused`: detach / hide.

No code in the host `Application`. Auto-init is disabled in release by shipping the
Initializer only in the library's debug variant (or guarded so a release consumer
is a no-op).

## Activation

A single `InspectorController` holds `isActive: Boolean` and the current
`MeasureMode`. Two independent triggers flip `isActive`:

- **Shake** — `SensorManager` + `TYPE_ACCELEROMETER`. Compute jerk magnitude,
  fire on a threshold with a debounce window. Registered while an activity is
  resumed.
- **Ongoing notification** — a low-priority ongoing notification with a toggle
  action (`PendingIntent` -> `BroadcastReceiver` -> controller). Posted while the
  inspector is installed.

When `isActive` is true, `OverlayView` becomes touch-interactive and renders a
small toolbar with the mode picker: **Size / Gap / Ruler / Bounds**. When false,
the overlay is fully pass-through (`onTouchEvent` returns false, nothing drawn).

## Capture

The core problem: given a tap point, find the widget bounds under it, in screen
pixel coordinates.

### XML Views
- Walk `decorView` subtree.
- Hit-test with `View.getGlobalVisibleRect(Rect)` (screen coords).
- Deepest hit wins for single-widget selection.
- For show-all-bounds, collect every visible leaf/container rect.

### Compose
- Locate `ComposeView`/`AndroidComposeView` instances in the tree.
- Read the semantics tree: `semanticsOwner.rootSemanticsNode`, walk children,
  use `SemanticsNode.boundsInWindow` for screen-space rects.
- **Only descend into nodes that carry a `testTag` or other semantics.** An
  untagged Compose island is measured as its single `ComposeView` rect.
- Access to `semanticsOwner` uses the least-internal API available; where
  reflection is unavoidable it is isolated in one file (`ComposeCapture`) so a
  Compose version bump touches one place.

Both paths emit a common `CapturedNode(id, label, boundsPx: Rect, source: XML|COMPOSE)`.

## Measurement Math

All bounds normalized to screen pixels (`Rect`). Display converts to dp via
`px / resources.displayMetrics.density`. Labels show both, e.g. `120dp (360px)`.

- **Size**: width/height of one node.
- **Gap**: between two nodes A and B, compute the four directional gaps
  (horizontal gap = clamp of the x-interval distance; vertical likewise). Draw
  only the non-overlapping axes. Overlapping = gap 0 on that axis.

Gap and dp/px conversion are pure functions — unit tested.

## Overlay Render

Single custom `View.onDraw(Canvas)`, driven by `MeasureMode`:

- **Size** — stroke selected bounds, draw a `w × h` label.
- **Gap** — two bounds; draw connector lines across each non-overlapping gap with
  its dp label.
- **Ruler** — track two active touch points; draw a line and its length. No widget
  selection.
- **Bounds** — iterate all `CapturedNode`s, stroke each with a small size label.

Touch handling per mode: Size/Gap/Bounds resolve taps to `CapturedNode`s via the
capture layer; Ruler consumes raw touch coordinates directly.

## Components (isolation boundaries)

| Unit | Responsibility | Depends on |
|------|----------------|-----------|
| `InspectorInitializer` | App Startup entry, registers lifecycle callbacks | Application |
| `InspectorController` | Holds `isActive` + `MeasureMode`, single source of truth | — |
| `ShakeDetector` | Accelerometer -> activation event | SensorManager |
| `NotificationTrigger` | Ongoing notification + toggle | NotificationManager |
| `OverlayView` | Touch + draw, one per activity | Controller, Capture, geometry |
| `ViewCapture` | XML hit-test + collect | decorView |
| `ComposeCapture` | Semantics hit-test + collect (all reflection isolated here) | Compose UI |
| `Geometry` | Pure gap / px<->dp functions | — |

Each communicates through `CapturedNode` and `MeasureMode`; `Geometry` is pure and
independently testable.

## Testing

- **Unit (JUnit, JVM)** — `Geometry`: gap calculation on overlapping /
  non-overlapping / nested rects; px<->dp conversion at several densities. This is
  the one runnable check that fails if the core math breaks.
- **Manual** — the `:sample` app exercises every mode against XML, Compose, and
  mixed screens on a device/emulator. Real hit-testing needs a live window;
  instrumentation harness deferred as not worth it for v1.

## Risks

- **Compose internals fragility** — mitigated by isolating all reflection in
  `ComposeCapture` and limiting v1 to tagged-node granularity.
- **Overlay stealing touches** — mitigated by full pass-through when inactive.
- **Multi-window / dialog decorViews** — v1 attaches to the resumed activity's
  decorView only; dialogs with their own window are out of scope.

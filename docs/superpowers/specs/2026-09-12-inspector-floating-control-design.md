# Floating Inspector Control Design

**Date:** 2026-09-12
**Project:** Android Layout Inspector
**Status:** Approved interaction design, pending written-spec review

## 1. Purpose

Replace the current fixed bottom inspector toolbar with a draggable in-app floating control that works correctly with modern edge-to-edge layouts, especially Android 16+.

The redesigned control must stay unobtrusive, remain recoverable, and allow an active measurement session to continue even when the control itself is docked or hidden.

This is an in-app inspector only. It must not use Android system-overlay permissions and must never appear above other applications.

## 2. Goals

The redesign must:

- Remove the fixed bottom toolbar that can overlap Android navigation/gesture areas under enforced edge-to-edge layouts.
- Provide a draggable circular inspector control inside the inspected app window.
- Allow the control to snap to the left or right edge in a half-visible docked state.
- Allow the control to be fully hidden through an explicit **Hide Inspector** action.
- Restore a fully hidden control only through the existing inspector notification.
- Keep the current measurement session running while the floating control is docked or hidden.
- Allow direct switching between measurement tools without stopping the current session first.
- Persist the floating control position and dock state across Activity changes and app process restarts.
- Preserve the existing full-window measurement coordinate system.
- Keep support for the current measurement tools: Size, Gap, Ruler, and Bounds.

## 3. Non-Goals

This redesign does not:

- Add a system-wide overlay or request `SYSTEM_ALERT_WINDOW` / "Display over other apps" permission.
- Change measurement geometry, hit testing, or view/Compose capture semantics except where required to keep the floating control excluded from capture.
- Introduce multiple simultaneously active measurement modes.
- Make the floating control visible automatically at application startup.
- Stop an active session merely because the floating control is docked or hidden.
- Replace the current Activity lifecycle attachment strategy with a foreground service.

## 4. User Experience

### 4.1 Initial state

When the inspected app starts, the inspector control is hidden.

The developer can reveal the inspector by either:

- shaking the device, or
- tapping the persistent inspector notification.

Revealing the inspector shows the floating circular control in its collapsed state. If a saved position exists, the control is restored to that saved position or dock state, adjusted to fit the current safe window bounds.

### 4.2 Collapsed floating control

The default visible control is a compact circular button rendered above the app content but inside the app's own `decorView`.

The developer can:

- tap it to open the tool menu,
- drag it freely inside the app window,
- drag it close to the left or right edge and release it to dock.

The control must remain inside safe interactive bounds and must not be placed beneath mandatory system gesture/navigation regions or display cutouts.

### 4.3 Expanded tool menu

Tapping the collapsed floating control opens the inspector tool menu.

The menu exposes at least:

- **Size**
- **Gap**
- **Ruler**
- **Bounds**
- **Settings**
- **Hide Inspector**

If a measurement tool is already active and the developer selects a different measurement tool, the inspector switches directly to the selected tool. The session remains running throughout the switch.

The expanded menu is a transient UI state. Tapping outside it collapses it back to the floating circle without changing the active measurement session.

### 4.4 Docked state

When the floating control is dragged near the left or right edge and released, it snaps to that edge and becomes approximately half-visible.

Docking is a convenience state, not a hidden state.

The developer can tap the half-visible docked control to restore it to a fully visible collapsed state. Docking must not stop or pause the active inspector session.

Only horizontal edge docking is required. Top/bottom docking is out of scope for the initial redesign.

### 4.5 Fully hidden state

The **Hide Inspector** action in the expanded menu completely removes the floating control from view.

A fully hidden control:

- has no visible edge handle,
- cannot be restored by tapping or swiping the app edge,
- can be restored only through the inspector notification,
- does not stop an active measurement session.

This state is intended for screenshots or cases where the developer wants no inspector controls obscuring the application UI.

### 4.6 Active session while control is hidden

Inspector session state and floating-control visibility are independent.

For example:

1. Developer activates **Gap** measurement.
2. Developer hides the floating control.
3. Gap measurement remains active and continues to receive/draw measurement interactions.
4. Developer can stop the session from the notification, or restore the control from the notification and stop it from the inspector UI.

The control being hidden must never implicitly stop the active measurement mode.

## 5. State Model

The design separates session state from control presentation state.

### 5.1 Inspector session state

Conceptually:

```text
STOPPED
RUNNING(SIZE)
RUNNING(GAP)
RUNNING(RULER)
RUNNING(BOUNDS)
```

Only one measurement mode may be active at a time.

Selecting a new tool while running transitions directly from one `RUNNING(mode)` state to another.

### 5.2 Floating control state

Conceptually:

```text
HIDDEN
COLLAPSED
EXPANDED
DOCKED_LEFT
DOCKED_RIGHT
```

These states are independent from session state.

Examples of valid combinations include:

```text
STOPPED + COLLAPSED
RUNNING(SIZE) + COLLAPSED
RUNNING(GAP) + EXPANDED
RUNNING(RULER) + DOCKED_RIGHT
RUNNING(BOUNDS) + HIDDEN
```

### 5.3 Visibility transitions

Expected transitions:

```text
HIDDEN --(shake/notification)--> COLLAPSED
COLLAPSED --(tap)--> EXPANDED
EXPANDED --(tap outside)--> COLLAPSED
COLLAPSED --(drag/release near left edge)--> DOCKED_LEFT
COLLAPSED --(drag/release near right edge)--> DOCKED_RIGHT
DOCKED_LEFT/RIGHT --(tap)--> COLLAPSED
EXPANDED --(Hide Inspector)--> HIDDEN
```

If the control is already visible, shake/notification activation should reveal it in a usable collapsed state rather than create another overlay or duplicate control.

## 6. Architecture

The redesign should evolve the current architecture instead of replacing it.

### 6.1 `InspectorController`

`InspectorController` remains the process-wide source of inspector state.

It should own or expose:

- whether a measurement session is stopped or running,
- the active `MeasureMode`,
- floating control presentation state,
- current normalized floating position,
- current dock side/state,
- state-change listeners used by Activity overlay instances.

The existing direct mode switching behavior is retained and formalized.

Session state must not be inferred from whether the control is visible.

### 6.2 `InspectorLifecycle`

The existing lifecycle approach remains:

- register one inspector overlay per resumed Activity,
- attach it to that Activity's `decorView`,
- do not use a system overlay,
- avoid duplicate overlays inside one Activity.

When Activity A is replaced by Activity B, the newly attached overlay reads the same process-wide controller state and persisted floating-control position. This makes the control feel continuous across Activities even though each Activity owns its own overlay view instance.

### 6.3 `InspectorOverlay`

`InspectorOverlay` remains a full-window `FrameLayout` so the measurement canvas uses the same window coordinate system as the existing implementation.

It contains two logically independent layers:

1. **Measurement layer**
   - full-window `MeasureCanvas`,
   - responsible only for measurement input and drawing,
   - remains unpadded by system insets.

2. **Control layer**
   - floating circle,
   - expanded tool menu,
   - drag/dock/hide interactions,
   - constrained by safe system insets.

The old fixed bottom `LinearLayout` toolbar is removed.

The inspector control itself must remain excluded from `ViewCapture` so it is never measured as application content.

### 6.4 Persistence store

Introduce a small inspector-state persistence component backed by Android `SharedPreferences`.

Persist only presentation information that should survive process restarts:

- normalized horizontal position,
- normalized vertical position,
- dock side (`NONE`, `LEFT`, `RIGHT`),
- enough information to restore the last usable placement.

Do not persist an active measurement session. A new process starts with the session stopped and control hidden.

Do not persist `EXPANDED` as a startup state; expanded is transient and must restore as collapsed.

A deliberate full **Hide Inspector** action should keep the startup policy unchanged: after a new app process starts, the inspector remains hidden until shake/notification activation.

### 6.5 Position representation

Persist position as normalized coordinates rather than raw pixels.

Example concept:

```text
xFraction: 0.0 .. 1.0
yFraction: 0.0 .. 1.0
```

This allows placement to survive:

- different screen sizes,
- portrait/landscape changes,
- multi-window resizing,
- changes to available safe bounds.

On restoration, clamp the reconstructed position to the current safe draggable area.

## 7. Edge-to-Edge and Android 16+

The redesigned control must work with edge-to-edge windows without changing measurement coordinates.

### 7.1 Measurement layer

The measurement canvas remains full-window and must not receive system-bar padding.

Adding system-bar padding to the entire overlay would shift the inspector coordinate system and create incorrect measurement rendering.

### 7.2 Floating control layer

Only the floating control and expanded menu use safe window insets.

Their draggable bounds should account for the relevant safe areas, including navigation/system gesture and display-cutout constraints, so interactive controls do not become obscured or difficult to use.

The control must request/apply current insets when dynamically attached to an Activity because its overlay may be inserted after the Activity's initial inset dispatch.

The inspector must not consume host-app insets. Existing host application edge-to-edge behavior must continue unchanged.

### 7.3 Docking with edge-to-edge

A docked button may be intentionally half outside the ordinary visible-control region on the horizontal axis, but its tappable visible half must remain inside a safe interactive region vertically.

Docking must not place the tappable portion underneath mandatory system UI or a display cutout.

## 8. Notification Behavior

The existing persistent notification remains the recovery/control channel when the floating UI is unavailable.

### 8.1 Notification tap

Tapping the notification:

- reveals the floating control if it is fully hidden,
- returns a docked control to a usable collapsed state when appropriate,
- must not create duplicate overlay instances,
- must not stop an active measurement session.

### 8.2 Stop action

When a measurement session is running, the notification provides a **Stop Inspector** action.

Stopping the inspector:

- changes the session to `STOPPED`,
- clears current measurement selections/rendering,
- does not need to hide the floating control.

The expanded/settings inspector UI exposes an equivalent explicit Stop action.

### 8.3 Notification content

When a tool is active, notification text should identify the active measurement mode where practical, for example:

```text
Layout Inspector · Gap active
```

When no tool is active, it can communicate that tapping will show the inspector controls.

## 9. Shake Behavior

Shake remains an activation shortcut.

Expected behavior:

- if floating control is hidden: reveal it collapsed,
- if floating control is docked: bring it back to a usable collapsed state,
- if already visible: do not create duplicates,
- shaking does not stop a running tool.

The redesign should avoid making shake a general start/stop toggle because session state and control visibility are now intentionally independent.

## 10. Stop Semantics

There are two different actions and they must remain distinct:

### Hide Inspector

Affects only floating-control visibility.

```text
RUNNING(GAP) + EXPANDED
    --Hide Inspector-->
RUNNING(GAP) + HIDDEN
```

### Stop Inspector

Affects the measurement session.

```text
RUNNING(GAP) + HIDDEN
    --Stop Inspector from notification-->
STOPPED + HIDDEN
```

or:

```text
RUNNING(SIZE) + EXPANDED
    --Stop Inspector from UI-->
STOPPED + EXPANDED/COLLAPSED
```

The UI may collapse after Stop for simplicity, but Stop must not be implemented as an alias for Hide.

## 11. Touch and Interaction Priority

The floating control must receive drag/tap events only inside its own hit area.

When the inspector session is stopped, the full-window measurement canvas must not intercept application touches.

When a measurement mode is active, the existing measurement input behavior remains authoritative for the measurement canvas.

The floating control/menu sits above the measurement canvas and its own touch targets must win over measurement input so the developer can always move, switch, hide, or stop the inspector.

## 12. Capture Exclusion

The current inspector overlay tag/exclusion behavior must continue to prevent inspector UI from appearing in captured layout nodes.

This includes:

- floating circle,
- expanded menu,
- docked control,
- other inspector-owned control surfaces.

Measurement geometry must continue to operate only on inspected application content.

## 13. Persistence Rules

Persist:

- normalized position,
- dock side/state.

Do not persist:

- active measurement mode/session across process death,
- expanded-menu state,
- transient drag state.

On app process restart:

```text
session = STOPPED
control = HIDDEN
saved placement = retained for next reveal
```

On Activity transition within the same process:

```text
session = preserved
control state = preserved
placement = preserved
```

If an Activity/window size makes the saved placement invalid, clamp it to the nearest valid safe position.

## 14. Error and Edge Cases

The implementation must handle:

- orientation change,
- Activity transition while a tool is active,
- split-screen or window resize,
- saved coordinates outside current safe bounds,
- notification permission denied on Android versions requiring permission,
- repeated notification/shake activation,
- docking while a mode is active,
- hiding while a mode is active,
- restoring while a mode is active,
- display cutouts,
- gesture navigation and three-button navigation.

If notifications are unavailable because permission is denied, the inspector must not crash. Existing graceful notification degradation remains required. Because a fully hidden control is intentionally recoverable only from the notification, **Hide Inspector must be unavailable while notification recovery is unavailable**. The expanded menu should disable or omit the full-hide action and communicate that notification access is required for full hide. Docking remains available as the non-blocking alternative. Shake may still reveal the inspector while the app is running, but it is not a recovery path from the deliberate fully hidden state.

## 15. Testing Strategy

### 15.1 Unit tests

Add pure/unit-testable coverage for:

- session state transitions,
- direct mode switching,
- visibility state transitions,
- Hide vs Stop independence,
- dock-side selection,
- snap-to-edge threshold logic,
- normalized-position conversion,
- clamping restored position into current safe bounds,
- persistence serialization/deserialization.

### 15.2 Lifecycle tests or focused verification

Verify that:

- only one inspector overlay exists per Activity,
- an Activity transition recreates the overlay UI from shared controller state,
- active measurement session survives Activity transition,
- control position/dock state survives Activity transition,
- process restart resets session/control visibility while retaining saved placement.

### 15.3 Manual sample-app verification

Exercise the sample app on edge-to-edge configurations, including Android 16+:

1. Launch app: inspector control is hidden.
2. Shake: collapsed floating control appears.
3. Drag control freely.
4. Drag to left/right edge: control docks half-visible.
5. Tap docked control: returns to collapsed state.
6. Tap collapsed control: tool menu expands.
7. Select Size, then Gap, then Ruler: each switches directly.
8. Hide control while a tool is active: measurement remains active.
9. Restore from notification: control returns without restarting session.
10. Stop from notification: measurement stops.
11. Move to another Activity: session/control state follows correctly.
12. Restart process: session is stopped and control hidden; saved placement is used on next reveal.
13. Verify navigation bar, gesture area, cutout, landscape, and split-screen placement.
14. Take a screenshot with control fully hidden: no inspector control is visible.

### 15.4 Build verification

Required verification commands remain:

```bash
./gradlew :inspector:testDebugUnitTest
./gradlew :sample:assembleDebug
```

The final implementation should also run both together as the release gate.

## 16. Migration from Current Toolbar

The current horizontal bottom toolbar in `InspectorOverlay` is replaced by the floating-control layer.

Existing measurement behavior should be reused rather than rewritten:

- `MeasureCanvas` drawing logic,
- `ViewCapture`,
- `ComposeCapture`,
- geometry helpers,
- lifecycle overlay attachment,
- notification channel infrastructure.

The main behavioral change is control/presentation state management, not measurement math.

## 17. Acceptance Criteria

The redesign is complete when all of the following are true:

- No fixed inspector toolbar overlaps the Android 16+ navigation/gesture area.
- Inspector is hidden on fresh app start.
- Shake and notification can reveal one floating control inside the inspected app.
- Floating control is draggable within safe bounds.
- Releasing near left/right edge produces a half-visible docked state.
- Tapping a docked control restores it.
- Expanded menu exposes all four measurement modes plus Settings and Hide Inspector.
- Hide Inspector fully removes the floating control.
- Notification restores a fully hidden control.
- Full Hide is disabled or unavailable when notification recovery is unavailable, preventing an unrecoverable hidden state.
- An active measurement mode continues while the control is docked or hidden.
- Notification and inspector UI expose an explicit Stop action.
- Selecting another measurement tool switches directly.
- Position/dock placement survives Activity transitions and process restarts.
- Measurement session does not survive process restart.
- Floating/expanded/docked inspector UI is excluded from captured layout nodes.
- Full-window measurement coordinates remain correct under edge-to-edge.
- Inspector does not require system overlay permission.
- Existing host application insets are not consumed or altered.
- Unit tests pass and sample debug APK assembles successfully.

## 18. Recommended Implementation Direction

Evolve the existing architecture:

- expand `InspectorController` into the authoritative session/control state holder,
- keep `InspectorLifecycle` responsible for per-Activity overlay attachment,
- split `InspectorOverlay` into a full-window measurement layer plus inset-aware floating-control layer,
- add a lightweight `SharedPreferences` persistence component for placement,
- extend `NotificationTrigger` with reveal and explicit Stop actions.

This direction preserves the working measurement engine and lifecycle integration while replacing the toolbar interaction model that is incompatible with modern edge-to-edge UI.

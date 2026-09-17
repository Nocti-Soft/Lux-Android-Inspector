# Indigo inspector UI — independent scoped review

Coordinator note: the review below is preserved as returned. It completed before the current-source emulator run finished. The final completion report records the subsequently passing gesture-navigation and three-button QA at the exact same source revision; the review's pending-runtime wording is historical.

# Scoped independent re-review — fix round 1

## Scope

Reviewed only the correction range `6a9b441c040db6da4cdb1a375fbc8881a321a909..138f8805edbe185cee84ab4c6b616c950ae66a61`, the supplied requirements and reports, and adjacent implementation/tests relevant to the three findings.

No files were changed and no tests, builds, Git commands, device actions, or helper agents were run.

## Verdicts

| Finding | Verdict |
|---|---|
| 1. Centered target icon with working menu-opening action | **ADDRESSED** |
| 2. Compact anchored menu with constrained fallback behavior | **ADDRESSED** |
| 3. Four-edge white halo and honest partial-offscreen clipping | **ADDRESSED** |

No new defects attributable to the fix diff were found.

## 1. Centered target icon and action — ADDRESSED

The floating control is now an `ImageButton`, with:

- centered image scaling;
- symmetric 16dp padding;
- the existing white target vector;
- the explicit indigo state background;
- the stable `Layout inspector controls` accessibility action;
- the existing click listener and drag listener;
- the unchanged 56dp control size.

Relevant implementation:

- `inspector/src/main/java/com/noctisoft/layoutmeasurement/FloatingInspectorControl.kt:37`
- `inspector/src/main/java/com/noctisoft/layoutmeasurement/FloatingInspectorControl.kt:58-70`
- `inspector/src/main/java/com/noctisoft/layoutmeasurement/FloatingInspectorControl.kt:90-91`
- `inspector/src/main/java/com/noctisoft/layoutmeasurement/FloatingInspectorControl.kt:172-223`
- `inspector/src/main/java/com/noctisoft/layoutmeasurement/FloatingInspectorControl.kt:253-258`

This corrects the original start-compound-drawable layout problem rather than merely proving that an icon resource exists.

The new native raster regression renders the actual control at 440dpi and verifies:

- non-empty white target pixels;
- horizontal bounding-box center;
- horizontal pixel centroid;
- vertical bounding-box center;
- vertical pixel centroid.

That coverage is at `inspector/src/test/java/com/noctisoft/layoutmeasurement/FloatingInspectorControlRenderTest.kt:16-49`.

The action remains behaviorally covered: a dispatched touch sequence invokes expansion exactly once, while a docked control invokes undocking instead, at `inspector/src/test/java/com/noctisoft/layoutmeasurement/FloatingInspectorControlTest.kt:88-107`. Stable accessibility text, `ImageButton` type, active-state description, and unchanged indigo background are checked at `inspector/src/test/java/com/noctisoft/layoutmeasurement/FloatingInspectorControlTest.kt:167-182`.

The vector itself remains a symmetric 24dp white target at `inspector/src/main/res/drawable/ic_inspector_target.xml:1-2`.

## 2. Compact menu and stable circle anchoring — ADDRESSED

The source now limits the popup to a nominal 216dp width, clamped by the safe-area width:

- `inspector/src/main/java/com/noctisoft/layoutmeasurement/FloatingInspectorControl.kt:94-100`
- `inspector/src/main/java/com/noctisoft/layoutmeasurement/FloatingInspectorControl.kt:311-317`

Placement now:

1. Measures the compact menu.
2. Retains the circle’s current coordinates.
3. Calculates room independently on the left and right.
4. Uses the preferred side when it fits.
5. Tries the other side when the preferred side does not fit.
6. Falls back above or below only when neither side fits.
7. Constrains fallback height to the available vertical region, preserving the `ScrollView`.
8. Does not rewrite the circle’s `x` or `y` while positioning the menu.

This behavior is implemented at `inspector/src/main/java/com/noctisoft/layoutmeasurement/FloatingInspectorControl.kt:274-317`.

`SafeMenu` now caps each incoming measure specification against its safe maximum instead of replacing the incoming constraint. It preserves `EXACTLY` and `AT_MOST` modes and converts only `UNSPECIFIED` to bounded `AT_MOST`:

- `inspector/src/main/java/com/noctisoft/layoutmeasurement/FloatingInspectorControl.kt:435-453`

The realistic regression uses a 1080×2340 viewport at 440dpi and reconstructs the reported circle placement. It verifies that:

- circle `x` is unchanged after expansion;
- circle `y` is unchanged after expansion;
- menu width remains between 180dp and 220dp;
- the menu is placed to the left with the required gap;
- the menu and circle remain inside and separate.

See `inspector/src/test/java/com/noctisoft/layoutmeasurement/FloatingInspectorControlTest.kt:64-86`.

Existing focused coverage continues to exercise:

- expansion followed by resize: `FloatingInspectorControlTest.kt:46-62`;
- click/undock behavior: `FloatingInspectorControlTest.kt:88-107`;
- active-pointer cancellation: `FloatingInspectorControlTest.kt:109-130`;
- expanded and docked dragging: `FloatingInspectorControlTest.kt:132-165`;
- outside-touch dismissal: `FloatingInspectorControlTest.kt:253-265`;
- docking: `FloatingInspectorControlTest.kt:267-286`;
- API 24 placement commits: `FloatingInspectorControlTest.kt:288-298`;
- cancelled-drag restoration: `FloatingInspectorControlTest.kt:300-321`;
- constrained scrolling and final-action reachability: `FloatingInspectorControlTest.kt:323-341`;
- narrow-safe-area resizing: `FloatingInspectorControlTest.kt:343-357`.

The fix is localized and does not introduce a parallel placement mechanism or alter persistence, docking geometry, or drag callback contracts.

## 3. Viewport-edge halo and honest clipping — ADDRESSED

The native viewport-edge test now verifies the visible inward white halo on all four sides in addition to the magenta core:

- magenta edge samples: `inspector/src/test/java/com/noctisoft/layoutmeasurement/MeasureCanvasRenderTest.kt:50-58`;
- inward white samples: `inspector/src/test/java/com/noctisoft/layoutmeasurement/MeasureCanvasRenderTest.kt:59-61`.

The added partially offscreen case passes the original negative left coordinate directly to the renderer and verifies:

- the viewport’s left edge remains untouched;
- the genuinely visible right magenta core renders;
- the corresponding outward white halo renders.

See `inspector/src/test/java/com/noctisoft/layoutmeasurement/MeasureCanvasRenderTest.kt:64-73`.

This complements the existing invalid and fully offscreen coverage at `MeasureCanvasRenderTest.kt:75-85`.

No production renderer change was made in this fix range. The renderer continues to create `RectF` geometry directly from the original `Bounds`, without clamping an offscreen side onto the viewport, at `inspector/src/main/java/com/noctisoft/layoutmeasurement/SelectionOutlineRenderer.kt:19-27`. Measurement bounds and measurement semantics therefore remain unchanged.

## New-breakage assessment

No concrete regression introduced by `138f880` was identified.

The widget replacement preserves click, drag, focus, accessibility state, sizing, and background behavior. The placement change is bounded to menu measurement and positioning. The renderer production path was not changed; only the previously missing edge-halo and partial-clipping assertions were added.

## Supplied verification evidence

`verification-latest.json` is applicable because both its source and finishing head are `138f8805edbe185cee84ab4c6b616c950ae66a61`. It records:

- full tests, debug assemblies, sample release assembly, and lint: exit 0;
- inspector tests: 77, with 0 failures/errors/skips;
- sample tests: 3, with 0 failures/errors/skips;
- total: 80 tests;
- debug APK containing inspector classes;
- release APK excluding inspector classes;
- clean tree and clean `diff --check`;
- overall state: `PASS`.

These results were reviewed as supplied evidence, not rerun during this re-review.

## Pending runtime evidence

`ui-qa-latest.json` is from source head `6a9b441c040db6da4cdb1a375fbc8881a321a909`, not `138f8805edbe185cee84ab4c6b616c950ae66a61`. Its wide-menu observation and final script failure therefore are not latest-code evidence.

Corrected emulator QA at `138f880` remains pending for:

- visual confirmation of the centered target on the emulator;
- confirmation that the 216dp popup remains beside the dragged circle;
- corrected interaction flow using observed geometry;
- native channel-settings screen evidence;
- viewport-edge halo appearance on the emulator.

This is an evidence limitation, not a source defect. The relevant implementation, native raster test, high-density attached-view test, full unit gate, assemblies, and lint all pass at the current source head.

## Final decision

**Ready to integrate: YES**

**Severity counts:** Critical **0** · Important **0** · Minor **0**

**Evidence limitations:** No current-head emulator/UI-QA record is available; `ui-qa-latest.json` targets the superseded `6a9b441` head. Current-head source, native raster, high-density view tests, full tests/builds, release exclusion, and lint evidence are passing.

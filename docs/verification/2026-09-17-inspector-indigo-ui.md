# Indigo inspector UI verification — 2026-09-17

## Scope

This recovery completes the approved indigo floating-inspector follow-up without changing measurement geometry or capture semantics.

- The floating control remains indigo while idle and active, keeps the stable `Layout inspector controls` action, exposes activity through `stateDescription`, and uses a centered white target vector.
- The quick menu uses explicit indigo state drawables, white 14sp bold labels, semantic vector icons, 48dp rows, spaced rounded actions, and a red-accented Stop action. Host background tint and state-list animation are cleared.
- Start resumes the retained mode; Stop ends inspection, clears selection through the existing overlay synchronization, collapses the menu, and retains the circle.
- Settings uses a light surface with indigo controls for Back, guarded Hide, and explicit Notification Controls. Notification settings dispatches to the inspector channel on API 26+, includes the API 24 app-settings fallback extras, and logs unavailable activities without crashing.
- Menu placement uses a compact nominal 216dp popup, preserves the circle position when a side fits, falls back above/below with a bounded scrolling viewport, and respects both incoming measurement constraints and safe-area limits.
- Selected outlines use a density-aware 2dp magenta core over a wider white halo on the same rectangular path. Multiple selected outlines render all halos before all cores, and labels render before selected borders so they cannot erase them. Invalid and genuinely offscreen geometry is not replaced with invented edges.

## Root causes and RED evidence

- The inherited active circle changed to red and advertised Stop even though tapping it only opens controls; state belonged to the bottom quick action.
- Platform `Button` theme tint/animation remained able to weaken explicit inspector colors, and main actions lacked the required vectors and spacing.
- Wider rows could overlap the circle because the menu was only clamped to the safe rectangle, not laid out with circle separation.
- A disabled Hide button still invoked its listener through programmatic `performClick`; the click path lacked the required second recovery guard.
- The initial white outline stroke was narrower than the magenta stroke and drawn on a separately inset path, so the core covered the intended contrast treatment. Closely spaced Gap selections could also have a later halo cover an earlier core, while labels were drawn after borders.

Relevant recovery logs (untracked scratch evidence):

- `recover-1-focused-red.log`: 27 focused tests, 3 expected assertion failures.
- `recover-1-expanded-red-2.log`: valid expanded RED coverage after correcting an initial test-compilation error.
- `recover-1-api24-settings-red.log`: API 24 legacy settings extras failed before implementation.
- `recover-1-focused-green-final.log`: four changed suites green; XML totals were 42 tests, 0 failures, 0 errors, 0 skips.

The baseline device evidence did **not** reproduce a completely missing right side for an interior Size rectangle. It did reproduce a thin approximately two-physical-pixel outline. This work therefore does not claim an original missing-draw-call defect; it improves contrast, density behavior, edge rendering, and draw ordering.

## Fresh full gate

Command:

```text
./gradlew :inspector:testDebugUnitTest :sample:testDebugUnitTest :inspector:assembleDebug :sample:assembleDebug --rerun-tasks
```

Result: exit 0, `BUILD SUCCESSFUL`, 87/87 actionable tasks executed. Log: `.superpowers/sdd/2026-09-16-inspector-indigo-ui/recover-full-gate.log`.

| Module | Tests | Failures | Errors | Skips |
|---|---:|---:|---:|---:|
| `:inspector` | 74 | 0 | 0 | 0 |
| `:sample` | 3 | 0 | 0 | 0 |
| **Total** | **77** | **0** | **0** | **0** |

Both debug assemblies completed. The sample test compiler emitted only its existing deprecated system-UI flag warnings; native libraries were packaged unstripped as reported by the Android build.

## Fix round 1 — centered target and compact anchoring

Independent review found that the target was installed as a `TextView` start compound drawable. Native rendering confirmed its white-pixel horizontal center at 32.5px inside a 154px control whose center was 76.5px. The floating control is now an `ImageButton` with centered image scaling and symmetric padding; its indigo background, stable action, state description, 56dp size, and existing click/drag handling remain unchanged. A native 440dpi raster test checks both the white target pixel bounds and centroid on both axes.

Coordinator device evidence also exposed a separate load-bearing layout defect: on a 1080×2340 / 440dpi viewport, MATCH_PARENT rows caused the menu to consume nearly the full width, `SafeMenu` replaced tighter incoming height constraints, and `positionMenu` unconditionally moved the circle to a screen edge. The corrected menu:

- has a nominal 216dp width clamped to the safe area;
- respects the tighter of incoming measure specs and safe maxima;
- uses the circle's existing position when the popup fits left or right;
- falls back above or below with height constrained to the actual available region and keeps scrolling;
- does not move the circle merely to make the menu fit.

The realistic 440dpi regression reproduces the coordinator geometry with the circle at approximately `[744,1403][898,1557]`, verifies that expansion leaves both circle coordinates unchanged, bounds the menu to 180–220dp, and places it left of the circle. Existing narrow-window, scrolling, safe-area, drag, cancellation, docking, and pointer-loss tests remain green.

Viewport-edge native coverage now checks inward white halo pixels on all four edges as well as all four magenta core sides. A partially offscreen rectangle verifies that clipping does not synthesize an outline on the viewport edge while its genuinely visible right core and halo still render. The renderer needed no production change for this added coverage.

The first device QA “no pink” result is not treated as renderer evidence: its hardcoded outside tap landed on Stop in the defective full-width menu, and the following tap navigated to Compose Activity. No QA scripts were changed in this implementation round.

### Fix-round TDD and gate evidence

- `fix-1-red.log`: **25 tests, 3 expected failures** — native target center was 32.5px versus 76.5px, high-density expansion moved circle y from 1403 to 2186, and the control was not an `ImageButton`. The new edge-halo/offscreen renderer assertions were already green.
- `fix-1-green-2.log`: four affected suites, **36 tests, 0 failures, 0 errors, 0 skips**.
- `fix-1-full-gate.log`: first full attempt exposed five stale `InspectorOverlayTest` helper failures because it only recognized a `TextView`; the helper was corrected to identify the public control action independent of widget class.
- `fix-1-full-gate-2.log`: exact requested command exited 0 with `BUILD SUCCESSFUL`, 87/87 actionable tasks executed, both debug assemblies complete.

| Module | Tests | Failures | Errors | Skips |
|---|---:|---:|---:|---:|
| `:inspector` | 77 | 0 | 0 | 0 |
| `:sample` | 3 | 0 | 0 | 0 |
| **Total** | **80** | **0** | **0** | **0** |

## Pending evidence

Fresh coordinator re-review and corrected device interaction/screenshots are pending for the fix-round commit. The coordinator's lint/release verification is also pending. These JVM/native-raster tests and debug assemblies are not an all-device or all-platform claim.

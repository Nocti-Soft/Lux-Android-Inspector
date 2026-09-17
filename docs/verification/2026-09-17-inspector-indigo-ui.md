# Indigo inspector UI verification — 2026-09-17

## Scope

This recovery completes the approved indigo floating-inspector follow-up without changing measurement geometry or capture semantics.

- The floating control remains indigo while idle and active, keeps the stable `Layout inspector controls` action, exposes activity through `stateDescription`, and uses a centered white target vector.
- The quick menu uses explicit indigo state drawables, white 14sp bold labels, semantic vector icons, 48dp rows, spaced rounded actions, and a red-accented Stop action. Host background tint and state-list animation are cleared.
- Start resumes the retained mode; Stop ends inspection, clears selection through the existing overlay synchronization, collapses the menu, and retains the circle.
- Settings uses a light surface with indigo controls for Back, guarded Hide, and explicit Notification Controls. Notification settings dispatches to the inspector channel on API 26+, includes the API 24 app-settings fallback extras, and logs unavailable activities without crashing.
- Menu placement reserves the circle, reanchors when horizontal room exists, stacks when constrained, and retains scrolling inside the safe area.
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

## Pending evidence

Actual emulator screenshots and interaction checks are pending coordinator execution. Independent review and the coordinator's fresh lint/release/verification gate are also pending. These JVM/native-raster tests and debug assemblies are not an all-device or all-platform claim.

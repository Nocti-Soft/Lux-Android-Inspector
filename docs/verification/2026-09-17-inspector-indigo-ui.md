# Indigo inspector UI verification — 2026-09-17

**Final status:** implementation and scoped independent review complete. Fresh full gate: 80 tests passed; actual API 36 gesture and three-button checks passed. Source `138f880`; changes remain on `ui/inspector-indigo-controls`, not `main`.

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

## Historical first-candidate full gate (6a9b441)

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

## Final coordinator verification and independent signoff

**Status: complete and independently approved for local integration. Not merged or pushed.**

Verified source: `138f8805edbe185cee84ab4c6b616c950ae66a61` on `ui/inspector-indigo-controls`. The final report commit is documentation-only; all execution evidence is pinned to this source revision.

The independent scoped re-review completed at `2026-09-17T03:03:25.295482+00:00`. The centered-icon finding, compactness/anchoring finding, and edge-halo test-coverage finding were all **ADDRESSED**. **Ready to integrate: YES; 0 Critical, 0 Important, 0 Minor findings in the fix range.** Its runtime-evidence paragraph was written before the latest QA completed, so the coordinator evidence below supersedes that pending status without altering the independent review text.

### Fresh full build, test, and lint gate

```bash
./gradlew :inspector:testDebugUnitTest :sample:testDebugUnitTest \
  :inspector:assembleDebug :sample:assembleDebug :sample:assembleRelease \
  :inspector:lintDebug :sample:lintDebug --rerun-tasks
```

**BUILD SUCCESSFUL in 23s; 157 actionable tasks, all executed.**

| Module | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| Inspector | 77 | 0 | 0 | 0 |
| Sample | 3 | 0 | 0 | 0 |
| Total | 80 | 0 | 0 | 0 |

Inspector/sample debug assembly, sample release assembly, and lint succeeded. Lint records **0 errors and 33 warnings** (22 inspector, 11 sample), not a warning-free result. Two inspector `InlinedApi` warnings concern guarded notification-settings fallback String constants; their dispatch and unavailable-activity behavior are covered by API 24 tests and were reviewed. The other warning categories are retained in the verification JSON. No SDK/toolchain or runtime-dependency upgrade was performed.

Debug and release dependency graphs and actual DEX inspection confirm inspector classes present in debug and absent from release. `git diff --check` passed; no tracked `.superpowers` paths or new system-overlay/service declarations were found.

Evidence: `.superpowers/sdd/2026-09-16-inspector-indigo-ui/verification-20260917T030013Z/result.json` and adjacent command logs (see also `verification-latest.json` in this follow-up's scratch directory).

### Actual Android 16 gesture-navigation verification

Emulator: `emulator-5582`, Android API 36, 1080x2340, 440dpi, 16 KiB pages; the sample remains **targetSdk 35**.

Corrected latest-APK QA completed at `2026-09-17T03:03:46.910910+00:00` with **PASS** for hidden startup, shake reveal/drag, ordered Start/Stop quick menu with no Hide row, Start, Stop returning to idle, Settings Hide, hidden measurement continuity with shake ignored, notification restoration, native channel-settings launch, and compact anchoring.

- Collapsed and expanded circle bounds both equal `[744, 1404, 898, 1558]`: expansion no longer relocates it.
- Quick-menu row width is `550`px, inside the 216dp nominal popup including its padding.
- For Size and each of the two Gap selections, sampled magenta coverage was **100% along all four straight sides**, including right; the core was approximately **6 physical pixels** at this emulator density. Corner pixels are excluded from the straight-side metric. White-halo and viewport-edge raster assertions are covered separately by native graphics tests.
- The coordinator visually inspected actual screenshots: the circle target is centered; indigo controls have legible white icons/text; the selected rectangle is visibly closed; Settings has the approved light treatment.
- Channel settings was validated by the actual Android `ChannelNotificationSettings` fragment and visible Show notifications control, not by assuming the title must appear in the accessibility tree.

Exact debug APK SHA-256: `34f7bf6314a81f4f01ca572e6e575b11e3dd6905dc878571d320f4679d85b271`.

Evidence directory: `.superpowers/sdd/2026-09-16-inspector-indigo-ui/device/verification-20260917T030055Z`. Key PNGs: `after-quick-idle.png`, `after-quick-active.png`, `after-outline-quick.png`, `after-settings-active.png`, `after-gap-outline.png`. XML/node dumps and the command transcript are retained alongside them; `tested.apk` is a snapshot of the exact installed artifact.

Earlier QA runs are historical and not counted as final failures or passes: a hardcoded dismissal point hit Stop in the then-full-width menu; a zero-distance drag became a tap; a title check incorrectly failed on a successfully opened native settings page. Each was diagnosed against actual state, the script was corrected, and the complete final flow was rerun. The actual full-width and off-center-icon production defects were fixed rather than hidden by the script changes.

### Three-button navigation regression

A second targeted run completed at `2026-09-17T03:05:38.143069+00:00` with **3/3 checks passed**: bottom quick-menu safety, Settings safety, and Back returning to the quick menu. The navigation bar begins at y=`2208`px; the circle ends at y=`2177`px and the lowest tested quick action at y=`2178`px. Both remain above the navigation area. Expanding retained the circle position.

Evidence: `.superpowers/sdd/2026-09-16-inspector-indigo-ui/device/three-button-20260917T030446Z/result.json`, `window-insets.txt`, screenshots, and transcript.

### Scope limits and retained behavior

This is targeted emulator validation, not physical-device testing, targetSdk 36 validation, or exhaustive Android/OEM coverage. Viewport-edge core/halo and partial-offscreen cases are native Robolectric bitmap tests; the emulator pixel checks exercised fully visible sample controls. No claim is made that an absent right-side draw call was reproduced in the original APK.

Start resumes the retained mode (Size by default); selecting a tool still starts/switches it directly. Deliberate Hide is Settings-only and notification-recoverable, and does not stop measurements. Settings replaces the quick panel within safe bounds rather than forcing the mockup's side-by-side submenu onto a narrow phone.

### Implementation rulings

1. Start uses the retained controller mode rather than introducing a separate idle-mode state. Cost if wrong: a user expecting a new default selection must choose that tool explicitly.
2. Settings adapts in-place to available safe width. Cost if wrong: the submenu placement differs from the side-by-side mockup, while all requested actions remain available.
3. A compact ordinary portrait popup must preserve the dragged circle position when there is space; constrained layouts scroll rather than teleport the circle. Cost if wrong: fallback placement can differ in unusually small windows; focused tests cover the retained safe-area and input contracts.

# Floating inspector candidate completion verification

**Status:** `DONE_WITH_CONCERNS` — candidate completion evidence only; an independent whole-branch review and coordinator rerun remain required before approval.

**Original fresh-gate source HEAD:** `ba511534fe44c9d021f3be7517a3eb3ce58a77f4` (`docs: close Task8 verification gaps`). Post-build artifact evidence below was collected at documentation-only HEAD `ff3e21976472f365d15b3afb08197335d288a950`.

## Tasks 6–9

| Task | Result | Evidence |
| --- | --- | --- |
| 6 — lifecycle, startup, and persistence | PASS | Commit `3810781`; focused lifecycle/overlay tests and the inspector unit suite passed. Startup restores placement but resets a fresh process to stopped/hidden; detached Stop/restart clears stale canvas selection. |
| 7 — sample edge-to-edge | PASS | Commit `c22ed52`; all three sample Activities explicitly lay out behind system bars, proven by three SDK 28 Robolectric cases. |
| 8 — Android 16 validation | PASS within stated bounds | Production revision `c22ed52`; 18/18 Android 16/API 36 **emulator** matrix rows passed, plus a separate real channel-block guard PASS. |
| 9 — final hygiene | PASS with inherited diagnostics | Fresh test/build, lint, and sample release gates below passed at `ba51153`; no production code was changed. |

## Final state and behavior

- Fresh process state is `session=STOPPED` and `control=HIDDEN`. Saved normalized X/Y placement and dock side are loaded, but neither an active measurement session nor an expanded menu is restored.
- The session and control visibility are independent. Selecting `SIZE`, `GAP`, `RULER`, or `BOUNDS` starts/switches the sole active session directly. **Stop Inspector** ends the session and clears measurement state while keeping the control available.
- **Hide Inspector** removes the control while retaining an active session and its rendered output. It is available only when notification recovery is available. A deliberately hidden control rejects shake recovery; the notification Show action restores it. Startup-hidden controls may be revealed by shake or Show, and docked controls remain half-visible and tappable.
- Placement is persisted on committed moves/docks and survives Activity recreation and process restart. The restored process remains hidden/stopped until reveal; the saved dock/position then reappears. Notification **Show** restores/expands the control state without stopping an active session; notification **Stop** stops the session.

## Fresh local verification — 2026-09-14T17:01Z–17:03Z

| Command | Exit | Observed result |
| --- | ---: | --- |
| `./gradlew :inspector:testDebugUnitTest :sample:testDebugUnitTest :inspector:assembleDebug :sample:assembleDebug --rerun-tasks` | 0 | `BUILD SUCCESSFUL in 14s`; 87 actionable tasks, all executed. Full stdout/stderr: `.superpowers/sdd/2026-09-14-inspector-floating-control/task-9-full-gate.log`. |
| `./gradlew :inspector:lintDebug :sample:lintDebug` | 0 | `BUILD SUCCESSFUL in 788ms`; 66 actionable tasks, 2 executed. Full stdout/stderr: `.superpowers/sdd/2026-09-14-inspector-floating-control/task-9-lint.log`. |
| `./gradlew :sample:assembleRelease` | 0 | `BUILD SUCCESSFUL in 658ms`; 47 actionable tasks, 1 executed. Full stdout/stderr: `.superpowers/sdd/2026-09-14-inspector-floating-control/task-9-release.log`. |

Generated XML was inspected rather than assuming counts:

| Module | Test suites | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: | ---: |
| `:inspector` | 10 | 60 | 0 | 0 | 0 |
| `:sample` | 1 | 3 | 0 | 0 | 0 |

The sample debug APK from this final local gate is `sample/build/outputs/apk/debug/sample-debug.apk`, SHA-256 `9c297175aca42245eafef92fd03a1530f89054b1f05372871bff1e43ebf9120e`.

## Lint and environment diagnostics

Both final lint tasks passed. The final XML matches the preflight warning totals and contains no lint errors:

- `:inspector`: 20 inherited warnings — obsolete AGP/dependency notices (18 occurrences), `LaunchActivityFromNotification` for the notification content intent (1), and `ViewConstructor` for the programmatically created overlay (1).
- `:sample`: 11 inherited warnings — `OldTargetApi` (1), `MissingApplicationIcon` (1), `ButtonStyle` (2), and `HardcodedText` (7).

No dependencies, SDK values, package names, or lint suppressions were changed. The full forced test compile still reports the intentional SDK 28 test-only deprecations in `SampleEdgeToEdgeTest` for legacy layout flags/system UI visibility. The permitted narrow `@Suppress("DEPRECATION")` was not needed to make the gate pass, so no test source was weakened. Robolectric also reports that SDK 36 requires Java 21 while this environment uses Java 17; the XML reports zero skipped tests. This is an environment-discovery warning, not Android 16 execution and not evidence of an Android 16 pass.

## Android 16 emulator evidence

Task 8 tested `c22ed52829b85117f62ce2082b80861dfe18dd45` on owned `emulator-5580`: Android 16/API 36, 16 KiB pages, sample targetSdk 35. The tested installed APK and local APK each had SHA-256 `c8c9612bc27096dbf3de63ecee58f4b1de82aaf28072c1706ae8d908cf7e7a96`.

| Matrix area | Result | Concrete evidence summary |
| --- | --- | --- |
| Install, identity, fresh start, shake/Show recovery | PASS | Exact `:sample:installDebug` installed on one device; fresh/repeated shake and notification Show each left exactly one control. |
| Drag, docking, insets, rotation, navigation modes | PASS | Left/right half-docks were 77 px visible and restored by tap; portrait/landscape, gestural/three-button, cutout, compact bounds, and actual split screen kept controls/menu inside safe bounds. |
| Measurement modes and menu actions | PASS | Size, Gap, Ruler, and Bounds each rendered output and switched directly; Settings Back and Stop were verified. |
| Hide/recovery/notification Stop | PASS | A live 32.0 dp/88 px Gap remained after Hide, ignored a bounded shake, restored from the real notification, then stopped from its real Stop action. |
| Activity and process continuity | PASS | Main → Compose and Main → Mixed retained an active measurement, one docked control, and output; force-stop/relaunch restored placement but started hidden/stopped. |
| Notification recovery guards | PASS | App-wide denial disabled Hide; separately, with app permission granted, disabling only channel `layout_inspector` disabled Hide and a disabled-row tap retained the controls. |

The original sensor-interference right-undock claim was withdrawn and is not used as current evidence. The API 36 `run-as ... am start` path was rejected by the platform package/UID restriction; actual focused sample navigation completed both forward Activity scenarios without a security bypass.

**Limits:** this is emulator-only validation, not physical-device validation; it validates targetSdk 35 at runtime API 36, not a targetSdk 36 build. Robolectric did not run SDK 36 on JDK 17. Task 8’s separate channel-block result is supplemental evidence, not an extra matrix row.

## Artifact-level release exclusion evidence

At documentation-only HEAD `ff3e219`, the coordinator ran `./gradlew :sample:dependencies --configuration debugRuntimeClasspath` and the matching `releaseRuntimeClasspath` command; both exited 0. The debug graph contains project `:inspector`, while the release graph does not.

Actual APK DEX payload inspection corroborates the graph: `InspectorController`, `InspectorOverlay`, `MeasureCanvas`, `InspectorInitializer`, and `InspectorActionReceiver` descriptors are present in `sample/build/outputs/apk/debug/sample-debug.apk` (`9c297175aca42245eafef92fd03a1530f89054b1f05372871bff1e43ebf9120e`) and absent in `sample/build/outputs/apk/release/sample-release-unsigned.apk` (`a50f39ef49557b7be8d3ae773d47d3012deebd2609579b308b4c2a8699e87f15`).

The Task 8 tested/installed debug container remains `c8c9612bc27096dbf3de63ecee58f4b1de82aaf28072c1706ae8d908cf7e7a96`; the rebuilt debug container is `9c297175aca42245eafef92fd03a1530f89054b1f05372871bff1e43ebf9120e`. Every extracted ZIP entry name and byte content compared identical, with no added, removed, or differing entries. The whole-file hashes remain distinct container identities; no cause for the byte-level container difference is asserted. Machine-readable evidence: `.superpowers/sdd/2026-09-14-inspector-floating-control/final-release-exclusion-proof.json`.

## Scope and hygiene

- `git diff --check` returned exit 0 with no output before documentation changes.
- The final required capture tag scan found identical values in `InspectorOverlay.TAG` and `ViewCapture.OVERLAY_TAG`: `com.noctisoft.layoutmeasurement.OVERLAY`.
- The obsolete `ACTION_TOGGLE`, `ToggleReceiver`, `InspectorController.toggle()`, `buildToolbar`, and `Gravity.BOTTOM` scan had no matches.
- Exact overlay/service scan had no matches for overlay permission/type, foreground-service calls, `<service>`, foreground-service type, or foreground-service permission. The manifest was inspected directly: it has only `POST_NOTIFICATIONS`, the startup provider, and the non-exported Show/Stop receiver. The plan’s broad `Service\(` pattern was intentionally not treated as evidence because `getSystemService()` is ordinary Android plumbing and would be a false positive.
- The cumulative `main...HEAD` code diff is limited to inspector controller/session/control/placement/overlay/notification/lifecycle changes, their tests, and sample edge-to-edge/test wiring. It has no measurement-math semantic, Compose capture, package, SDK-version, or runtime-dependency drift. `sample/build.gradle.kts` retains `debugImplementation(project(":inspector"))`; the successful sample release assembly is additional evidence that the inspector remains debug-only runtime integration.
- The final tree after the Task 9 hygiene fix has no tracked `.superpowers/` paths (`git ls-files .superpowers` is empty). The earlier `ff3e219` documentation commit did temporarily track the non-sensitive audit Markdown in scratch; it was moved without history rewrite or evidence deletion to `docs/verification/2026-09-14-floating-inspector-task9-evidence.md`. Ignored logs, JSON, and local SDD reports remain non-commit candidates. The original checkout was not inspected or claimed clean.

## Relevant commits

- `3810781 feat: restore inspector controls across activity recreation`
- `c22ed52 test: exercise inspector in edge to edge sample screens`
- `4bc7ea2 docs: complete Android16 inspector verification`
- `ba51153 docs: close Task8 verification gaps`

## Appendix: SDD rulings from `progress.md`

1. **Task 1:** minimal migration of the three compile-blocking callers to the new controller API was allowed so focused tests could compile; Tasks 3/5/6 retained full redesign ownership.
2. **Task 1:** use one-shot Git author identity on commit rather than modifying repository/global Git configuration.
3. **Task 3:** notification posting failures must log a warning with the original exception while retaining graceful degradation.
4. **Task 4:** accessibility activation must share the click handler; drag cancellation restores committed placement; expanded/docked drags make coherent collapse/undock transitions.
5. **Task 4:** expanded menu must be bounded/scrollable in `SafeArea`; docked drawing/hit-testing must be clipped to keep the visible half out of protected insets.
6. **Task 4:** add focused real View/Robolectric tests with existing test-only dependencies; no runtime dependency or toolchain upgrade.
7. **Task 5:** add focused overlay/canvas integration tests and repair only reproduced adjacent integration defects.
8. **Task 5:** use root-insets snapshot after dynamic attach while keeping an overlay-local non-consuming listener and never changing host insets/padding/window flags.
9. **Task 5:** persist committed placement including undock; avoid layout overwrites; clear cached nodes/selections on Stop.
10. **Task 6:** clear obsolete local canvas state on detach so detached Stop/restart cannot revive a stale selection while preserving shared state.
11. **Task 6:** add focused lifecycle/initializer, notification, and geometry tests using existing dependencies.
12. **Task 7:** add minimal sample test-only JUnit/Robolectric configuration to prove explicit SDK 28 edge-to-edge flags.
13. **Task 8:** treat the original right-undock report as unconfirmed sensor/test interference until revalidated under stable sensor, actual dock state, and correct visible-half bounds; do not change production code from it.
14. **Task 9:** replace the plan’s broad `Service\(` scan with exact system-overlay/foreground-service declarations/calls, plus manifest/architecture review.
15. **Task 9:** narrowly scoped test-only deprecation suppression is permitted for Task 7 SDK 28 assertions; do not upgrade SDK/toolchain merely to remove the SDK 36/JDK 17 discovery warning.
16. **Task 9:** correct the accidentally tracked non-sensitive SDD report by moving its permanent audit copy under `docs/verification` and retaining an ignored local SDD copy, without rewriting commits or deleting evidence. The earlier commit retains its historical path, but the final tree does not track scratch.

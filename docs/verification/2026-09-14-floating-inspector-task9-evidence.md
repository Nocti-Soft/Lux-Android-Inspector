# Task 9 report — final verification and hygiene

**Historical scope:** This audit records the Task 9 candidate committed as `ff3e219`. Its permanent copy was subsequently relocated from `.superpowers/sdd/2026-09-14-inspector-floating-control/task-9-report.md` to this `docs/verification/` path by `847a4d1`; the local SDD copy remains ignored. The result and gate statements below describe that earlier candidate, not the latest whole-branch verdict. For current status, including the completed coordinator verification/smoke and the implemented final-fix persistence correction pending independent scoped re-review, see `docs/verification/2026-09-14-floating-inspector-completion.md`.

**Result at the original candidate revision:** `DONE_WITH_CONCERNS` — fresh gates pass and no new production defect was found by the Task 9 worker. At that point, independent whole-branch review and coordinator rerun were pending.

## Fresh commands and exit status

| Command | Exit | Result |
| --- | ---: | --- |
| `./gradlew :inspector:testDebugUnitTest :sample:testDebugUnitTest :inspector:assembleDebug :sample:assembleDebug --rerun-tasks` | 0 | `BUILD SUCCESSFUL in 14s`; 87 actionable tasks executed. |
| `./gradlew :inspector:lintDebug :sample:lintDebug` | 0 | `BUILD SUCCESSFUL in 788ms`; 66 actionable tasks, 2 executed. |
| `./gradlew :sample:assembleRelease` | 0 | `BUILD SUCCESSFUL in 658ms`; 47 actionable tasks, 1 executed. |

Full stdout/stderr is saved in ignored scratch as `task-9-full-gate.log`, `task-9-lint.log`, and `task-9-release.log` under `.superpowers/sdd/2026-09-14-inspector-floating-control/`.

## Parsed unit-test XML

| Module | Suites | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: | ---: |
| `:inspector` | 10 | 60 | 0 | 0 | 0 |
| `:sample` | 1 | 3 | 0 | 0 | 0 |

The inspector/sample Robolectric XML includes the existing SDK 36/JDK 17 discovery message. It did not cause a skipped test and is not an Android 16 runtime pass.

## Lint comparison

Final lint XML contains no errors and matches preflight totals: inspector 20 warnings and sample 11 warnings. Inspector warnings are inherited obsolete AGP/dependency notices (18), notification content intent (1), and programmatic custom-view constructor (1). Sample warnings are inherited target SDK (1), application icon (1), button style (2), and hardcoded text (7). No lint rule was disabled and no SDK/toolchain/runtime dependency was changed.

The forced sample test compilation reports the intentional Task 7 SDK 28 legacy-flag deprecations. The allowed narrow suppression was unnecessary, so it was not added.

## Required static/scope checks

- Capture tag scan: `InspectorOverlay.TAG` and `ViewCapture.OVERLAY_TAG` both equal `com.noctisoft.layoutmeasurement.OVERLAY`.
- Legacy toggle/fixed toolbar scan: no matches.
- Exact system-overlay/foreground-service scan: no matches. Direct manifest inspection found no service declaration, overlay permission/type, foreground-service permission/type, or foreground-service call; only startup provider and the non-exported Show/Stop receiver.
- The broad plan pattern `Service\(` was not relied on because it falsely matches required `getSystemService()` calls. This follows the Task 9 ruling; manifest inspection supplies the additional architecture check.
- `git diff main...HEAD -- inspector sample` was restricted to the intended control/session/placement/overlay/notification/lifecycle work, tests, and sample edge-to-edge setup. No package, SDK, dependency, Compose-capture, or unrelated measurement-math drift was found.
- `sample/build.gradle.kts` still uses `debugImplementation(project(":inspector"))`; `:sample:assembleRelease` passed. Debug APK: `sample/build/outputs/apk/debug/sample-debug.apk`, SHA-256 `9c297175aca42245eafef92fd03a1530f89054b1f05372871bff1e43ebf9120e`.
- Before documentation changes, `git diff --check` was clean and `git status --short` was empty. Generated `build/`, APK, IDE, and Gradle cache output remains ignored and unstaged.

## Task 6–8 reconciliation and limitations

Task 6 commit `3810781` establishes startup hidden/stopped state while restoring placement, one overlay through Activity recreation, and detached Stop/restart selection clearing. Task 7 commit `c22ed52` verifies explicit edge-to-edge setup for Main, Compose, and Mixed activities with three SDK 28 tests.

The reconciled Task 8 report at production revision `c22ed52` records 18/18 PASS Android 16/API 36 emulator matrix groups: start/reveal, drag/dock/safe insets, four mode switching, menu Stop/Back, Gap-active Hide/recovery/notification Stop, notification guards, Activity continuity, persistence, cutout/compact/split-screen, and crash check. Separate supplemental evidence disables only channel `layout_inspector` with app permission granted; Hide is disabled and an attempted disabled-row click retains controls. The target is SDK 35 at runtime API 36 on an emulator with 16 KiB pages, not a targetSdk 36 or physical-device result. The withdrawn early sensor-interference claim is not used.

See `docs/verification/2026-09-14-floating-inspector-completion.md` for the complete state model, Show/Stop/Hide distinctions, device matrix summary, all SDD rulings, paths, and full limits.

## Original commit scope (`ff3e219`)

The following are the historical paths in `ff3e219`, not current commit candidates. Commit `847a4d1` subsequently moved the second path to `docs/verification/2026-09-14-floating-inspector-task9-evidence.md`; the final tree does not track `.superpowers/` scratch.

- `docs/verification/2026-09-14-floating-inspector-completion.md`
- `.superpowers/sdd/2026-09-14-inspector-floating-control/task-9-report.md`

The ignored `.superpowers/sdd/2026-09-14-inspector-floating-control/task-9-final-evidence.json` was deliberately excluded from the commit and remains local evidence.

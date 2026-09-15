# Final Fix Wave Re-review

**Reviewed range:** `847a4d107bf551871ccea852aa335dd01a09bee3..6d95283f8908994abd776dd179eed32589fb822d`
**Review mode:** Read-only. No edits, Git writes, subagents, test/build/device reruns, or device execution.
**Finding count in this fix diff:** **0 Critical, 0 Important, 0 Minor**

## Original Findings

| Original finding | Verdict | Basis |
|---|---|---|
| **Important:** notification Show undocking was not persisted with no attached overlay | **ADDRESSED** | `InspectorActionReceiver` snapshots placement, invokes notification Show, and persists only a changed placement at the Android boundary: `inspector/src/main/java/com/noctisoft/layoutmeasurement/NotificationTrigger.kt:92-98`. The new application-only tests seed real preferences and prove left/SDK 35 and right/SDK 24 docked placements become persisted `DockSide.NONE`, retaining the original normalized coordinates and active Gap mode: `inspector/src/test/java/com/noctisoft/layoutmeasurement/NotificationTriggerTest.kt:68-76`, `:90-110`. |
| **Minor:** relocated Task 9 report retained stale final-tree wording | **ADDRESSED** | The report explicitly identifies its historical `ff3e219` scope, identifies the previous scratch path as historical, names the relocation commit, and directs readers to current-status documentation: `docs/verification/2026-09-14-floating-inspector-task9-evidence.md:2-4`, `:49-56`. |

## Spec-compliance verdict: **PASS**

The receiver fix fulfills the missing notification-undock persistence contract:

- A notification Show on a docked controller changes `placement.dockSide` to `NONE` through the unchanged controller behavior: `InspectorController.kt:34-59`, `:63-71`.
- The receiver persists that resulting placement when, and only when, it changed: `NotificationTrigger.kt:93-97`.
- This handles the original no-Activity/no-overlay case because persistence no longer relies on `InspectorOverlay`'s attached listener.
- Left and right placement cases are covered at SDK 35 and API 24 respectively, with both normalized coordinates retained exactly and an active Gap session/mode retained: `NotificationTriggerTest.kt:68-76`, `:95-110`.
- The controller remains Android-free; the sole newly added Android persistence call is at the Android notification receiver boundary. Existing attached-overlay persistence remains unchanged at `InspectorOverlay.kt:25-38`.
- No write occurs for an unchanged Show placement: a hidden startup reveal, collapsed Show, expanded Show collapse, or other Show invocation whose `FloatingPlacement` is unchanged does not satisfy the receiver’s conditional. The existing overlay observer also writes only on placement inequality.
- In the attached-overlay changed-placement path, the existing synchronous overlay observer may perform an additional idempotent preference save before the receiver reaches its conditional save. This is the explicitly accepted, harmless duplicate-write cost; it does not alter behavior or create a write for unchanged placement.
- Show still invokes `revealControls(RevealSource.NOTIFICATION)`, Stop still invokes only `stopInspection()`, and unmatched actions still do not mutate controller state: `NotificationTrigger.kt:91-101`. The post-action notification refresh remains unchanged.
- There is no controller Android dependency, unsafe lifecycle access, or persistence architecture expansion in the fix diff.

The final documentation accurately distinguishes historical evidence from the final fix and does not convert green JVM results into approval. Its currently recorded “pending smoke” language was accurate when committed; separate coordinator smoke evidence now exists at the fix head.

## Code-quality verdict: **PASS**

The implementation is the smallest correct boundary fix: one before/after placement comparison and one real-store write. It preserves the existing controller/listener separation and avoids a new abstraction or lifecycle coupling.

The new tests use the real `InspectorPlacementStore` and an Application-only receiver invocation—there is no Activity or `InspectorOverlay` construction in their setup—so they exercise the precise former gap. Their `@After` resets active state, visibility, placement, and persisted placement: `NotificationTriggerTest.kt:20-27`. The inactive selected mode remains whatever the test last selected, consistent with the existing controller test cleanup pattern; no test in this class depends on a default inactive mode, and the full suite passed. This is not a new observable defect.

## Evidence reviewed

### TDD evidence
- **Baseline focused class:** exit 0.
- **RED focused class:** exit 1; **6 tests, 2 failures**. The two new tests failed precisely at the persisted-placement assertion because the store retained `LEFT` and `RIGHT`, respectively: `final-fix-1-red.log:42-62`.
- **GREEN focused class:** exit 0; **6 tests, 0 failures, 0 errors, 0 skips**: `final-fix-1-green.log:39-42`.

### Coordinator fresh verification at `6d95283`
The independent coordinator full gate used `--rerun-tasks` and included:

- `:inspector:testDebugUnitTest`
- `:sample:testDebugUnitTest`
- `:inspector:assembleDebug`
- `:sample:assembleDebug`
- `:sample:assembleRelease`
- `:inspector:lintDebug`
- `:sample:lintDebug`

It exited **0** after **157 actionable tasks**, with:

| Module | Tests | Failures | Errors | Skips |
|---|---:|---:|---:|---:|
| `:inspector` | 62 | 0 | 0 | 0 |
| `:sample` | 3 | 0 | 0 | 0 |

The verification JSON is explicitly pinned to `6d95283f8908994abd776dd179eed32589fb822d`; it also records passing debug/release dependency and DEX exclusion checks, clean worktree/whitespace checks, and no tracked `.superpowers` paths.

### Final rebuilt-APK smoke
`final-apk-smoke.json` and its dedicated result/transcript are present and identify the exact reviewed head:

- **HEAD:** `6d95283f8908994abd776dd179eed32589fb822d`
- **APK SHA-256:** `ea812facbc75c740d73b530496f91b9e5d6e147b5158b13918520acef40821be`
- **Runtime:** API 36 emulator, 16 KiB pages
- **Result:** PASS, all **7/7** scripted checks:
  1. latest APK install
  2. fresh hidden startup
  3. shake reveals one control
  4. Gap mode and Hide
  5. notification Gap and Stop content
  6. notification restore retains Gap
  7. notification Stop ends the session

The raw transcript confirms installation and runtime SDK/page-size collection. This is final-code runtime evidence for those seven smoke checks.

The smoke **does not** exercise the no-overlay notification-persistence scenario; that exact case is covered by the focused Robolectric regressions only. It should not be represented as runtime proof of no-overlay persistence.

## Residual evidence limits

These are not new findings in this fix diff:

1. The final runtime evidence is **emulator-only**, not physical-device testing.
2. It runs **Android 16/API 36 with targetSdk 35**, not a targetSdk 36 build.
3. The new no-overlay persistence coverage is Robolectric at SDK 35 and API 24; it is not an API 36 runtime no-overlay test.
4. The prior Android 16 matrix remains historical evidence for earlier production code; it reports **18/18 emulator matrix rows** plus one supplemental channel-block check, not an all-platform claim.
5. No focused test demonstrates capture/canvas coordinate equivalence under a deliberately nonzero window origin.

## Final verdict

**Ready to integrate: YES.**

The original Important persistence defect and Minor documentation wording finding are both addressed. The fix introduces no new Critical, Important, or Minor breakage in its scoped diff, and the final-code full coordinator gate plus exact-head API 36/16 KiB rebuilt-APK smoke both passed. The completion document may remain candidate/pending until the coordinator records this actual final signoff evidence; no execution gate remains pending in the supplied evidence.

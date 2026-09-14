# Android 16 floating inspector verification

**Result:** DONE
**Implementation revision:** `c22ed52829b85117f62ce2082b80861dfe18dd45` (`ab3d38500653f7a548863cbe129c34fc381fd34a` is documentation-only)
**APK SHA-256:** `c8c9612bc27096dbf3de63ecee58f4b1de82aaf28072c1706ae8d908cf7e7a96` (local and installed `base.apk` matched)
**Device:** owned `emulator-5580`, Android 16/API 36, 16 KiB pages, build `google/sdk_gphone16k_x86_64/emu64xa16k:16/BE4B.251210.005/14574095:userdebug/dev-keys`
**Application:** `com.noctisoft.layoutmeasurement`, version `1.0`/1, targetSdk 35

## Evidence handling and corrected setup

Raw screenshots, UIAutomator XML, parsed node bounds, and the scoped ADB transcript are under `.superpowers/sdd/2026-09-14-inspector-floating-control/device-validation/evidence/`. The bounded helper is `device-validation/task8_qa.py`; every ADB subprocess used `-s emulator-5580`.

The initial slice's reported right-undock failure is withdrawn. Acceleration had been left continuously at `40:40:40`, repeatedly invoking shake callbacks, and bounds `[926,1082][1080,1236]` describe a full 154 px circle rather than a 77 px half-dock. All continuation shakes used a 250 ms `40:40:40` pulse followed immediately by `0:9.81:0`, with more than one second between pulses. Drags began inside the control and away from Android's gesture edge. Corrected right-dock evidence is `[1003,1082][1080,1236]`, followed by a tap restoring a full circle.

## Final matrix (18 rows: 18 PASS, 0 PARTIAL, 0 NOT_RUN, 0 FAIL)

| Check | Result | Measured device evidence |
|---|---|---|
| Build, install, launch, and identity | PASS | `./gradlew :sample:assembleDebug` exited 0 (`BUILD SUCCESSFUL`); local APK and installed `base.apk` both hashed to the value above. Package dump recorded targetSdk 35 on Android 16/API 36. |
| Fresh hidden and stopped start | PASS | Existing `01-fresh-hidden.png/xml`; after a later real force-stop/relaunch, `final-38-restart-hidden-stopped.png/xml` has no floating node or measurement. |
| Real shake reveal / exactly one circle | PASS | Existing corrected `continuation-stable-reveal.png/xml/nodes.json`; one bounded sensor pulse produced one `Layout inspector controls` node. |
| Repeated shake reveal / no duplicate | PASS | `final-01-repeat-shake-1.*` and `final-02-repeat-shake-2.*`: each hierarchy contains exactly one control at `[36,1082][190,1236]`. |
| Actual notification reveal / repeat / no duplicate | PASS | `final-03-stopped-notification.*` shows `Tap to show inspector controls`; two taps on the actual notification body, each followed by shade collapse, yielded exactly one control in `final-04-notification-show.*` and `final-05-notification-repeat.*`. |
| Drag and gesture-navigation safe bounds | PASS | Existing `continuation-left-dock.*` remains above gesture navigation. Corrected interior drags and `final-39-restart-saved-right-dock.*` demonstrate a 77 px half-dock rather than a full circle. |
| Left dock and tap-undock | PASS | Existing `continuation-left-dock.*` is `[0,1082][77,1236]`; actual tap produced full `[36,1082][190,1236]` in `continuation-left-undock.*`. |
| Right dock and tap-undock | PASS | Existing `repro-controlled-2-rightdock.*` is `[1003,1082][1080,1236]`; actual tap produced full `[890,1082][1044,1236]` in `repro-controlled-3-undock.*`. The earlier failure was setup interference, not reproduced. |
| Portrait/landscape and gesture/three-button safe areas | PASS | Landscape right half-dock `[2263,452][2340,606]` in `final-40-landscape-right-dock.*`; tap restored full `[2186,452][2340,606]` in `final-41-landscape-undocked.*`. `final-43-landscape-menu-safe.*` keeps all menu rows inside `2340x1080`. Under three-button navigation, `final-44-landscape-threebutton.*`, `final-47-portrait-threebutton.*`, and bottom drag `final-48-threebutton-bottom-clamp.*` show the control ending at nav boundary `y=2208`, not underneath system UI. |
| Expand and outside collapse | PASS | `final-06-menu.*` exposes all six actions; actual outside tap leaves only one collapsed control in `final-07-outside-collapse.*`, without changing session state. |
| Size, Gap, Ruler, and Bounds direct switching | PASS | Active markers are `• Size`, `• Gap`, `• Ruler`, and `• Bounds` in `final-10-size-marker.*`, `final-12-gap-marker.*`, `final-14-ruler-marker.*`, and `final-16-bounds-marker.*`. Meaningful device-rendered output appears in `final-09-size-measurement.png`, `final-11-gap-measurement.png` (32.0 dp/88 px), `final-13-ruler-measurement.png` (171.6 dp/472 px), and `final-15-bounds-measurement.png`. No intermediate Stop was used. |
| Settings Back and Stop Inspector | PASS | `final-17-settings-active.*` shows `Stop Inspector` and `Back`; Back returns to the active Bounds main menu in `final-18-settings-back.*`; reopening Settings and tapping Stop removes measurement while retaining the circle in `final-19-settings-stopped.*`. |
| Hide active measurement / clean screenshot | PASS | A 212.0 dp/583 px ruler remains visible in `final-20-hidden-ruler.png`, while XML contains no floating-control/menu node. This is the requested screenshot use case. |
| Deliberate-hidden shake, notification restore, and notification Stop | PASS | A bounded shake leaves controls absent and ruler present in `final-21-hidden-shake-ignored.*`. `final-22-ruler-notification.*` shows `Ruler active` plus actual `Stop Inspector`. Tapping the notification body restores exactly one control and preserves the ruler in `final-23-notification-restored.png`; tapping the notification Stop action removes the ruler in `final-24-notification-stopped.*`. |
| Notification-denied recovery guard | PASS | Revoking `POST_NOTIFICATIONS` caused a normal permission-change process restart, not a crash. A bounded shake still revealed one control (`final-26-denied-reveal.*`). `final-27-denied-hide-guard.*` exposes `Hide (notification required)` with `enabled=false`; right docking still produced `[1003,1082][1080,1236]` in `final-28-denied-right-dock.*`. Deliberate unrecoverable HIDDEN could not be selected. |
| Main, Compose, and Mixed Activity continuity | PASS | Navigation used the actual sample `COMPOSE SCREEN` and `MIXED SCREEN` buttons while stopped. Compose kept the normalized right-side placement (`final-29-compose-stopped.*`), rendered Size (`final-31-compose-size-active.*`), and Android Back returned to Main with one control at identical `[878,1082][1032,1236]`; Size remained active in `final-33-main-size-continuity.png`. Mixed rendered Gap in `final-35-mixed-gap-active.png`; Android Back returned to Main with Gap still active in `final-36-main-gap-from-mixed.png`. No non-exported Activity launch or test bridge was used. |
| Process-restart persistence | PASS | A real drag saved a right half-dock `[1003,1082][1080,1236]` in `final-37-persistence-right-dock.*`; `am force-stop` and normal exported-Activity launch preserved app data. `final-38-restart-hidden-stopped.*` is hidden/stopped. First bounded-shake reveal restored the same right half-dock in `final-39-restart-saved-right-dock.*`, with no expanded menu. Preferences were read only as corroboration, never written. |
| Cutout, compact bounds, actual multi-window, and crash check | PASS | With the real `display.cutout.emulation.hole` overlay, a top drag clamps the full circle to `[501,169][655,323]` (`final-50-cutout-top-clamp.*`) and all menu rows remain visible (`final-51-cutout-menu-safe.*`). A separate `wm size 720x1280` compact-bounds check kept the menu inside `[0,0][720,1280]` (`final-52-compact-bounds.*`, `final-53-compact-menu.*`); this was not counted as multi-window. Actual split screen was entered from Recents → app menu → Split screen, selecting the second sample task: `final-58-actual-split-screen.png` shows two live panes and `am stack list` records task bounds `[0,0][1080,1156]` and `[0,1184][1080,2340]`; the lower menu stays inside its pane in `final-59-split-menu-safe.*`. The crash log buffer was empty and `dumpsys activity exit-info` contained only test force-stop/permission-change exits. |

## Reproduction guide

All commands use `/home/pinij_par/Development/Android/Sdk/platform-tools/adb -s emulator-5580` through the helper.

1. Stabilize the sensor with `python3 .superpowers/sdd/2026-09-14-inspector-floating-control/device-validation/task8_qa.py baseline`. For each shake, run `... task8_qa.py pulse`; the helper holds `40:40:40` for 250 ms and immediately restores `0:9.81:0`.
2. Capture each state with `... task8_qa.py capture NAME`; this writes screenshot, XML, and parsed accessible nodes.
3. Open notifications with `... task8_qa.py shell cmd statusbar expand-notifications`, tap bounds read from the XML, then collapse with `... task8_qa.py shell cmd statusbar collapse`.
4. Switch navigation with `cmd overlay enable-exclusive --category com.android.internal.systemui.navbar.threebutton` or `.gestural`; lock rotation with `accelerometer_rotation=0` and set `user_rotation=1`/`0`.
5. Enable the cutout with `cmd overlay enable com.android.internal.display.cutout.emulation.hole`; use `wm size 720x1280` only for the separately labeled compact-bounds check.
6. For actual split screen, open Recents, tap the sample app header, choose **Split screen**, and select the other sample task. A direct `--windowingMode 5` request fell back to fullscreen and was not treated as multi-window evidence.

## Restoration and limitations

The disposable emulator was left running for Task 9. Final checks confirmed acceleration `0:9.81:0`, auto-rotation `1`, user rotation `0`, physical size `1080x2340`, gestural navigation enabled, three-button and cutout overlays disabled, notification permission restored to its recorded baseline `granted=false`, no sample split tasks visible, and the sample force-stopped. No production source, manifest export, app preference, security setting, physical device, or other AVD was modified.

No product defect or remaining Task 8 environmental limitation was observed.

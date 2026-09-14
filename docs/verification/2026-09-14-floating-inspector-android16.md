# Android 16 floating inspector verification

**Status:** DONE_WITH_CONCERNS  
**HEAD:** `c22ed52829b85117f62ce2082b80861dfe18dd45`  
**APK SHA-256:** `c8c9612bc27096dbf3de63ecee58f4b1de82aaf28072c1706ae8d908cf7e7a96`  
**Device:** Android 16/API 36, 16 KiB pages, targetSdk 35, `emulator-5580`.

## Corrected evidence

All raw screenshots, XML, parsed bounds, and command JSONL are in `.superpowers/sdd/2026-09-14-inspector-floating-control/device-validation/evidence/`. The bounded helper is `device-validation/task8_qa.py`; every subprocess scopes `adb` to `emulator-5580`.

The initial slice's right-dock failure is **withdrawn**. The sensor was accidentally left at continuous `40:40:40`, which repeatedly invoked shake callbacks, and the alleged dock bounds `[926,1082][1080,1236]` were a full 154 px circle. A stable `0:9.81:0` baseline with a 250 ms high pulse prevents this interference. The initial deep-edge drag was also susceptible to Android back-gesture arbitration.

## Final matrix (17 rows: 5 PASS, 3 PARTIAL, 9 NOT_RUN, 0 FAIL)

| Check | Result | Device evidence |
|---|---|---|
| Build/install/launch | PASS | Gradle build exit 0; scoped install Success; environment recorded in `00-environment.txt`. |
| Fresh hidden start | PASS | `01-fresh-hidden.png/xml`, `02-denied-main.png/xml`. |
| Real shake reveal / exactly one circle | PASS | `continuation-stable-reveal.png/xml/nodes.json`: exactly one `[890,1082][1044,1236]` control after a bounded pulse. |
| Repeat reveal/no duplicate | PARTIAL | Stable single reveal verified; repeat sequence not completed after corrected reset. |
| Notification Show/repeat/no duplicate | NOT_RUN | Notification presence captured but actual Show tap was not completed. |
| Drag safe bounds / gesture navigation | PASS | `continuation-left-dock.png/xml/nodes.json`: valid half-visible control remains above navigation. |
| Left docking and tap-undock | PASS | Actual interior drag `(967,1159)->(120,1159)` creates `[0,1082][77,1236]`; actual tap `(35,1159)` restores collapsed `[36,1082][190,1236]`, no menu. `continuation-left-dock*`, `continuation-left-undock*`. |
| Right docking and tap-undock | PASS | Coordinator-controlled raw evidence: `repro-controlled-2-rightdock` `[1003,1082][1080,1236]`, persisted `0.9611231|0.5|RIGHT`; one tap `repro-controlled-3-undock` `[890,1082][1044,1236]`, persisted `NONE`. |
| Landscape / 3-button | NOT_RUN | No valid corrected run completed. |
| Expand and outside collapse | PARTIAL | `19-expanded-menu.png` confirms expansion and all actions; outside dismissal not completed. |
| Size/Gap/Ruler/Bounds direct switching; Settings Stop | NOT_RUN | No verified active mode. |
| Hide active measurement / clean screenshot | NOT_RUN | No verified active mode. |
| Hidden shake ignored / notification mode content / Show / Stop | NOT_RUN | No verified deliberate-hidden state. |
| Notification permission denied channel guard | PARTIAL | Denial prompt and stable app captured (`01`–`04`); disabled Hide label/dock under denied permission not completed. |
| Main/Compose/Mixed continuity | NOT_RUN | Not exercised under verified active mode. |
| Process force-stop persistence | NOT_RUN | Not re-run from corrected saved dock state. |
| Cutout and actual multi-window | NOT_RUN | Overlay/window-mode capability listed but not exercised. |
| App-specific crash check | PASS | Captured logcat had no `FATAL EXCEPTION` associated with the package. |

## Limits and restoration

This report records only actual device evidence; no source/JVM inference or production changes were used. Sensor baseline was restored to `0:9.81:0`. The original emulator baseline is auto-rotation `1`, user rotation `0`, notifications denied; verify/restore these again before handing off because further testing was not completed in this continuation.

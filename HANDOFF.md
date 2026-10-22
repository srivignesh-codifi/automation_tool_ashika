# HANDOFF: on-device UAT automation tool, extended to a second app (Tradion)

You are taking over an engineering task. Everything below is the state of the work. Read it fully before changing anything. Be honest about what is verified and what is not.

## 1. What this is
An Android "automation tool" app (Flutter UI + Kotlin AccessibilityService engine) that tests other Flutter apps ON THE SAME PHONE, with no Appium, no ADB during normal runs, and no PC. The tester types credentials (user id, password or MPIN, OTP) into the tool and taps Start. The tool clears the target app's data, launches it, drives it through Android accessibility APIs, and produces a pass/fail report (JSON, HTML, text, screenshots of failures).

Target apps mark their widgets with `Semantics(identifier: '...')`. On Android these show up as `resource-id`. Shared loading, empty and error widgets are tagged once (`ui_loading`, `ui_empty`, `ui_error`), so "does every section finish loading" can be checked on any screen: wait until no `ui_loading` node remains, scroll a few screenfuls, fail if `ui_error` is present.

Safety rules baked in:
- Never places or changes orders, never moves funds, never reads SMS (OTP is typed by the tester).
- Package allowlist.
- Credentials are never logged or put in reports.
- Press Back only to close an overlay whose own marker was just confirmed present.

## 2. Repos and paths (macOS)
- Tool: `/Users/srivigneshbalaji/Projects/ClientView/test/mobile_automation_tool`
  - Kotlin: `android/app/src/main/kotlin/com/mobileautomation/tool/`
  - `AutomationEngine.kt`: primitives (waitForAny, tap, tapAndConfirm, longTap, enterText, customAction, revealInList, scrollPage, tapUpTo, labelsOf, ownLabelsOf, screenshot) and run lifecycle.
  - `AccessibilityNodeFinder.kt`: locators; resolves self, then descendants, then ancestors for the actionable node.
  - `AutomationAccessibilityService.kt`: tree reads, gestures, screenshots.
  - `AutomationConfig.kt`: allowlist, ids (`Ids` for Ashika, `TradionIds` for Tradion), timeouts, texts.
  - `TargetApp.kt`: enum of supported apps; this IS the allowlist. `ALLOWED_PACKAGES` derives from it.
  - `FlowBase.kt`: shared step bookkeeping, aborts, `checkPageLoads`, `closeWithBack`.
  - `LoginAutomationFlow.kt`: Ashika's flow (60 steps). Kept with its own private helpers; deliberately NOT refactored.
  - `TradionAutomationFlow.kt`: Tradion's flow (67 steps), extends FlowBase.
  - Flutter UI under `lib/`: `screens/login_automation_screen.dart` (app picker + credentials form), `services/automation_service.dart`, `services/secure_storage_service.dart`, `screens/running_test_screen.dart`.
  - `README.md` documents everything (sections 1a, 16, 17, 18 are the relevant ones).
  - Build: `flutter build apk --release`, output `build/app/outputs/flutter-apk/app-release.apk`.
  - Checks: `flutter analyze` (clean), `flutter test` (11 pass), `cd android && ./gradlew :app:compileDebugKotlin --offline -q`.
- Target 1, Ashika: `/Users/srivigneshbalaji/Projects/ashika_broking_mobile`, package `com.codifi.dhanush`. Its annotations live on git branch `improvement/automation_aug06`, commit c514077e.
- Target 2, Tradion: `/Users/srivigneshbalaji/Projects/tradion_mobile`, package `com.codifi.rmoney`, Flutter package name `tradion`.
  - Annotations are UNCOMMITTED in the working tree.
  - Identifier contract: `docs/AUTOMATION_SEMANTICS.md` (authoritative on the app side).
  - Helpers: `lib/shared_widgets/automation_markers.dart` (`AutomationTag`, `UiStateMarker.loading/empty/error`, `automationSlug`) and `lib/shared_widgets/marked_shimmer.dart`.
  - `marked_shimmer.dart` is a drop-in for `Shimmer.fromColors` that reports `ui_loading`. 23 files had their shimmer import swapped to it.
  - Tradion analyzer baseline is 664 issues. It must stay 664.

## 3. The Tradion flow (user's requirements, in order)
Login: clear app data, launch, User ID, Login, Password, Proceed, OTP (the field auto-submits on the 6th digit; tap "Confirm OTP" only as a fallback after ~7.5 s), optional Risk Disclosure ("I understand"/"Next", tap until it closes; otherwise skipped), Home. There is no intro carousel and no MPIN. The biometric-enrol screen has no Skip, so the run stops there with an explanation.

Watchlist (user said: NO load tests; they only want actions):
- Watchlist tabs are only counted, not tapped or load-checked (the last tab is "Recently Viewed" and is not editable).
- A Predefined list must refuse Search/add. This was confirmed passing on device.
- Basket mode is toggled and restored.
- Filter screen is opened and closed.
- Scrip details sheet is opened and closed.
- From the details sheet: Chart is opened, waited on (`chart_ready`), screenshotted, then Back. More Details tabs are visited (equity has Market Depth, Technicals, Fundamentals, Info; other segments have no Fundamentals), each within 7 s.
- Edit watchlist (long-press a row = ACTION_LONG_CLICK): rename (adds "Q" to the name), move the first scrip down via the "Move down" custom accessibility action, delete the new first scrip, Save, check the count dropped by 1, reopen to verify name and order persisted, rename back and save.
- Search: for each segment (Stocks TCS, F&O NIFTY, Commodity CRUDEOIL) add the first new result, confirm it stays added for 2 s (catches server rollback), delete it, confirm it stays deleted. The All segment (INFY) adds and KEEPS one (compensates the edit's delete). Then open a result's details sheet. Delete is only done if the results held none of the user's own scrips before the add, otherwise skipped with a note. Finally the count is checked against the net change.

Orders: Pending, Order Book, Trade Book, Basket, GTT, SIP tabs, each must finish loading.

Dashboard:
- Trading tab: Option Chain. For each segment NFO, MCX, CDS, BFO: tap search icon, pick the segment chip, pick the first underlying, and the chain table must load within 7 s. Also Research calls (every tab and sub tab loads) and the Short/Long Buildup chips.
- Investments tab: scanner chips, then Scanners "View All" (every tab loads).

Portfolio: Holdings loads; Net P&L / Day P&L toggle; Stock/MTF switch (one CupertinoSwitch, flipped and restored); sort sheet opened and closed; Positions loads; its Net/Day toggle; sort sheet.

Profile: Account and Trade Settings tabs load. Then Logout. If a step fails, record it and move on: all post-login failures are soft (failAndContinue, with dependent steps skipped). Only login steps abort.

Inner tab labels carry counts ("Pending (3)") and are localised, so tabs are keyed by index or role, never by label.

## 4. Current status (be careful with claims)
Verified by static checks: Kotlin compiles, `flutter analyze` is clean, 11 Flutter tests pass, Tradion analyze is unchanged at 664, and release APKs build.

Observed on an Android 16 (API 36) emulator in earlier runs (older builds):
- Data clear (about 3.5 s), launch, User ID, Password, OTP auto-submit, disclosure, Home all work.
- Watchlist tabs and Predefined tabs loaded.
- The Predefined-refuses-add check passed.
- Basket and Filter steps passed.
- Scrip details passed.
- Search add and delete (Stocks, F&O) passed; a Commodity delete was skipped by design.
- Search result details passed.
- Orders tabs started passing.

NOT yet seen running: the newest steps (Edit watchlist rename/reorder/save/verify/restore, Chart screenshot, More Details tabs, Option Chain per segment, Research calls, Scanners View All, Portfolio extras, Logout). They compile but are unverified on a device. The user runs the APKs themselves on their emulator and sends back screenshots of the run report; debug from those.

## 5. Known issues and open questions
1. Count label "nothing readable" after mutations (edit or search). Likely cause: Tradion drifts to the Orders tab on its own. It handles an "orderBook" push notification with `changeTabOrder`, and also drifted during automation. The flow now re-opens Watchlist via `backOnWatchlist()` before reading, and the failure message prints the label state plus visible identifiers. The root cause is not confirmed.
2. The edit screen once "hung": Save is disabled when nothing changed, so waiting for the screen to close timed out and every later step ran on the Edit screen. Fix: after a failed Save, press Back and tap `reorder_discard_button`.
3. The Android 16 notification-permission prompt (appears after the data clear) was not answered once by ACTION_CLICK. The engine now retries with a gesture tap, waits for the prompt (a person may allow it) instead of treating it as "app lost", and reports "stuck" with the `adb shell pm grant <pkg> android.permission.POST_NOTIFICATIONS` fix. Whether Android blocks automated input there is unconfirmed. Do NOT set `android:isAccessibilityTool="true"` to work around it without the owner's informed decision.
4. The "Move down" accessibility action on the reorder rows is assumed (Flutter ReorderableListView publishes it). If absent, the step fails and lists the actions seen.
5. Sort-option tests in the Filter screen were intentionally not added, because they change a saved setting. Ask the owner first.
6. The user's wording on Predefined was ambiguous; the implemented and passing behaviour is "Predefined cannot add scrips".

## 6. Important facts and traps
- Tradion `ApiLinks.isUAT = false` means the working tree builds the LIVE environment. Same package for UAT and live, so the allowlist can't tell them apart. Do not flip it unasked; warn about it.
- A Tradion run mutates data: it deletes 1 scrip and renames and restores a watchlist; the All-segment search adds 1 back. Use a test account.
- On Tradion's home screen, system Back EXITS the app (WillPopScope returns true). Never press Back unless an overlay marker is confirmed. `closeWithBack` enforces this.
- The Tradion biometric-enrol screen can't be skipped, so use a device or emulator with no biometrics enrolled.
- Force-stopping or reinstalling the tool turns its accessibility service off (and interrupts a run). Re-enable it via Settings, or on an emulator with `adb shell settings put secure enabled_accessibility_services com.mobileautomation.tool/com.mobileautomation.tool.AutomationAccessibilityService` and `accessibility_enabled 1`.
- `CredentialField` uses `autofillHints: const <String>[]`. `null` is NOT an opt-out; Android autofill filled a saved login into the tool's form once.
- Tradion diff size: `dart format` re-indented wrapped subtrees (about 6000 lines of whitespace churn). `git diff -w` shows the real change (about 1000 lines). Prefer these patterns for new tags to avoid churn: `final marked = X(...); return AutomationTag('id', child: marked);`, or rename `build` to `_buildMarked` with a one-line wrapper. Do not run `dart format` on whole files.
- The tool never taps Buy/Sell/Alert, a row's trade button, or the dashboard "Scalper" tile (it opens an external page).

## 7. Incident to be aware of (do not repeat)
While smoke-testing, scripted `adb` taps and reinstalls ran on the owner's emulator while they were using it. Android autofill put their saved live Tradion login into the tool's form, and one run logged into their real account (read-only, failed before any edit). Rule: do not run scripted `adb input`, reinstalls, or tool runs on a device the owner may be using without asking. Prefer a separate AVD. Use obviously fake credentials, and never let a test reach a live backend with real credentials.

## 8. How to run, and how to extend
- Install APKs: tool release APK plus the annotated Tradion build (`flutter build apk --debug --no-pub` in tradion_mobile; the owner may use `flutter run`). On the phone: enable the tool's accessibility service (Android 13+ may need "Allow restricted settings"), open Run Automation, pick Tradion, enter User ID, Password and OTP, press Start. Don't touch the screen during a run.
- Reports are in `/sdcard/Android/data/com.mobileautomation.tool/files/reports/` (`.txt` / `.json` / `.html`, plus `screenshots/`). Each step's note says how a tap was made. Failure reasons list the identifiers visible at that moment, which usually pinpoints the problem.
- Adding an app: add to `TargetApp.kt` (allowlist), its package to `accessibility_service_config.xml` `packageNames` and the manifest `<queries>`, an ids object in `AutomationConfig.kt`, a flow extending `FlowBase`, and a `when` branch in `AutomationEngine.start`. Annotate the target app and document the ids.

## 9. What to do next (suggested)
1. Ask the owner for the report from the newest run (67 steps) and fix by root cause, in this order: first failing step, then dependent skips.
2. Confirm or refute known issues 1, 3 and 4 from real report text, not from guesses.
3. Once the flow is stable: commit both repos in sensible commits (Tradion annotations separate from the tool), and consider the `applicationIdSuffix` or flavour fix so the allowlist can enforce UAT-only.
4. Keep the owner's style: concise replies, root-cause fixes, flows that continue past failures, and honest reporting of what was and wasn't run.

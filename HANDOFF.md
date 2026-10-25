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
  - `TradionAutomationFlow.kt`: Tradion's flow (126 steps), extends FlowBase.
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

Dashboard (see section 4f for the 15 scenario steps added 2026-10-23):
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

## 4a. Added 2026-10-22 (not yet run on a device)
- Screen recording of a whole run (README 13a): `ScreenRecorder.kt`, `requestScreenRecording` on the channel, `recordScreen` on start, `videoPath` / `recordingStartedAtMs` / `recordingNote` on the result, report video player and per-step seek links. Compiles, analyzes, unit-tested for the report side; the MediaProjection capture itself has NOT been seen running.
- Target apps skip their notification permission request when the tool launches them: the tool adds the `com.mobileautomation.tool.AUTOMATION_RUN` extra (`TargetAppLauncher`), each app's `MainActivity` exposes it (extra or launching package) on channel `com.codifi.automation/run`, and `AutomationRun.isActive()` (`shared_widgets/automation_markers.dart`) gates the requests; the old `--dart-define=AUTOMATION_TEST=true` flag still works as an override. Root cause of the repeated prompt: the tool's own Clear-storage reset revokes runtime permissions each run. Known issue 3 below applies only to app builds without this change. Ashika's version is a patch (`ashika_automation_run.patch`) for `improvement/automation_aug06`; its Kotlin was not compiled (its AGP is not cached offline).

## 4e. "Clear storage button not found" on real phones (2026-10-23)
- Defect: `clearTargetAppData` found the control case-insensitively (and by content description) but then decided Clear-vs-Storage with a case-sensitive `clear.text !in CLEAR_STORAGE`. A button spelled "Clear Data" or exposed only as a description was tapped as if it were the Storage row, which opened the confirm dialog, and the next wait for a Clear button failed with exactly "the Clear storage button was not found in Settings". The phone's real wording was not seen (no dump), so this is the code-level cause, not a confirmed diagnosis of that phone.
- Fix: `SettingsMatch.kt` (meaning-based, unit-tested in `android/app/src/test`, 6 JVM tests, run with `./gradlew :app:testDebugUnitTest`), what-matched-decides, Clear-first ordering, the failure now prints the Settings package and its labels, and a run-form switch "Clear the app's data first" (off = step skipped with a note). `AutomationConfig.SettingsTexts` was removed.
- First field report after that fix: on a real phone the Storage page (`com.android.settings`) showed only `Storage | Tradion | 1.0.1 | Space used | App size | 96.90 MB | User data | 4.91 MB | Cache | 0 byte | Total | 102 MB`, i.e. no Clear control in the active window's text. So the tool now (a) searches EVERY Settings window (`windowRootsOf`), not just the active one, (b) after 6 s with no Clear button taps the page's overflow button (`SettingsMatch.isMoreOptions`, content description "More options" etc.) and looks again, and (c) on failure dumps every Settings window's labels with `*` for tappable, `desc:` for description-only buttons and `?*` for unlabelled tappables. If it still fails, that dump is what to read. NOT verified on a device.

- Second field report (reference screenshots of the phone): App info shows "Storage & cache" and Archive / Uninstall / Force stop buttons; the Storage page shows "Clear storage" / "Clear cache" round buttons with labels. Yet the tool saw none of the buttons' labels. Leading hypothesis (NOT confirmed): Android 14+ `accessibilityDataSensitive` hides action buttons from a service that is not `isAccessibilityTool`; the same mechanism would explain the notification dialog's Allow button not being answered on Android 16. DECISION FOR THE OWNER: declaring `android:isAccessibilityTool="true"` in `accessibility_service_config.xml` would likely fix both, but it labels an automation tool as an accessibility aid (Play Store policy forbids that for store apps; fine only for an internal sideloaded tool), so it has NOT been set.
- Third field report: with step 3 skipped, step 5 failed after 45 s listing only `research_call_screen`, `research_tab_0..4`, `research_subtab_0/1` (no bottom bar): the launch had merely brought the still-running app forward on a deep screen left by an earlier run, and the "already signed in" check knew only `home_screen` / `watchlist_screen`. Fixed: step 4 now launches with `FLAG_ACTIVITY_CLEAR_TASK` (`TargetAppLauncher.launch(.., freshTask = true)`, both apps) so the app restarts from its launcher screen, and step 5 also treats any bottom-bar tab id as "signed in". NOT verified on a device.
- Meanwhile Tradion resets from inside when Settings fails (`resetFromInside`: Switch Account on a remembered user, `logOutFromInside` when signed in). Not run on a device. Ashika has no equivalent.

## 4f. Dashboard scenarios (2026-10-23, compiled, NOT run on a device)
The owner supplied 15 Dashboard test scenarios; each is one report step, added after the existing Dashboard steps (which were kept). Where the scenario wording does not match Tradion, the mapping used is:
- "Screener" = the Scanners screen (Investments › Scanners › View All). Its "dropdown" is the Equity / Futures / Options / Commodity bar; its sort sheet's reset control is labelled **Close** (it calls `clearFilter` and pops), not Reset.
- "Summary and Option Chain tabs" does not exist: the scrip sheet has Chart, Op.Chain and More Details boxes, so the step checks those (owner agreed).
- "View All strategies" = Futures › View All (tabs, filter icon with Alphabetically / Last Traded Price / Change and CLEAR). "Option Trading Tools › View All" opens the OptionBees SDK, which the tool cannot read.
- "Research tab" = the Research calls tile; its page is titled Trading Ideas. Its search box is only built for a sub tab with more than one call; if none has, the step passes with a note saying nothing was searched.
- "Index dropdown" = the arrow in the dashboard header; the panel lists only the two pinned indices plus funds.
Steps: `dashboard_index_dropdown_checked`, `dashboard_trading_tab_selected`, `trading_ideas_tabs_clickable`, `trading_ideas_subtabs_search`, `trading_ideas_menu_opened`, `trading_ideas_filter_checked`, `futures_view_all_checked`, `futures_scrip_buy_sell_checked`, `fii_dii_view_all_checked`, `dashboard_customize_checked`, `screener_tabs_checked`, `screener_dropdown_checked`, `screener_search_checked`, `screener_sort_checked`, `screener_scrip_checked`. The module's step list is `AppModules.DASHBOARD_SCENARIO_STEPS`.
Things to watch on the first device run:
- `selected` is published through `AutomationTag(selected:)`; `AutomationEngine.isSelected` reads the tag node or its clickable. If the tab step fails with "does not report itself as selected", check the node dump first.
- Typing into the Scanners / Trading Ideas search leaves the keyboard open (Back is not pressed for it: with no keyboard Back would leave the screen).
- Customize is left with Back; if the app shows "Do you want to save changes?" the step fails and the run is stuck on that dialog (the tool never taps Save/Discard).
- The Futures and FII / DII steps fail when their card is missing: either no data loaded or the section is switched off under Customize.
- With network, `flutter pub get` then `flutter analyze --no-pub` in tradion_mobile reports 664 issues, the same baseline as before these tags (no errors); offline it cannot resolve `vector_math 2.2.0`. A Tradion build and a device run are still outstanding.

## 4g. Watchlist revamp (2026-10-25, compiled, NOT run on a device)
The earlier ~40 Watchlist steps (long chained mutating steps, six-exchange add/remove, 10-20 s waits) were replaced by `WatchlistSuite.kt`: 27 independent test cases `WL-01..WL-41` from the owner's "Complete Smart Automation Testing" spec (the old 4g text is superseded). Run total is now 116 steps.
- **Modules:** `watchlist` (WL-01..16), `watchlist_search` (WL-20..25), `watchlist_edit` (WL-30..32), new `watchlist_settings` (WL-40/41). Each case is a step with `code`, `expected`, `severity` and, when skipped, `skipKind` (blocked / not_applicable / not_tested); statuses in the report are PASS / FAIL / BLOCKED / NOT APPLICABLE / NOT TESTED. An unexecuted case is never PASS.
- **Rules kept in code:** one representative instrument per case, no loops over symbols / exchanges / tabs; normal clicks 3 s (`Timeouts.UI_RESPONSE`, retry 1.5 s), server data 10 s (`DATA_LOAD`), chart WebView 15 s, Edit Save 20 s; slow-but-passing loads are recorded in the step's API column. After every case `recover()` closes only overlays whose own marker it just saw and returns to the Watchlist landing, so one failure never cascades; a login screen ends the run (`sessionLost`).
- **Safety:** BUY / SELL only open the order pad (one tap each) and leave with Back; the Alert page is never submitted; Edit deletes only an instrument the run added itself (`ensureTestScrip`, TCS then RELIANCE, always cleaned up); renamed watchlist and Manage Watchlist preferences are restored; a full 50/50 watchlist makes WL-24/31/32 BLOCKED rather than touching user data; WL-25 is NOT TESTED unless the list is already full.
- **Known gaps / owner decisions:** Predefined 'Search within watchlist' only shows "You can not add scrips to Pre-defined watch list." in this build, so WL-16 is NOT APPLICABLE with that text; chart candles and timeframes live in the WebView (only `chart_ready` + a screenshot are checked); the alert-condition dropdown and the EMA/SMA toggle and the exchange filter chips are untagged, so they are tapped by visible text (WL-08 is NOT TESTED if the text is not found); More Details is run on an equity only (never indices).
- **Tradion tags added:** `watchlist_index_dropdown` (header arrow) and `watchlist_ticker` (pinned-index row), uncommitted in `tradion_mobile`.
- **Report:** text report gains WATCHLIST TEST CASES (ID / Scenario / Expected / Actual / Status / Screenshot), DEFECTS with severity, PERFORMANCE and a QA ASSESSMENT; screenshots are named `WL-xx_<label>` and are included in the zip.
- Not verified on a device: every selector in the suite, the 3 s budgets on a real phone, and the text-based taps.

### 4g-2. Remediation after the 2026-10-25 report (compiled, NOT run on a device)
Root causes found in the 08:52 report and what changed (all in `WatchlistSuite.kt` unless noted):
- **Blank `texts: ()` (WL-04/05/07/11/12/13/15):** `AccessibilityNodeFinder.visibleTexts` read only `node.text`; Flutter publishes labels as the content description (and merges a card's lines with newlines). It now reads both and splits lines. Content is read through `readContent(container, ...)`, which waits for readable content under the screen's own identifier. If nothing is readable the case is **BLOCKED / accessibility limitation** (screenshot + hierarchy + recommendation), never FAIL; readable-but-label-missing is FAIL class **unconfirmed** ("needs review"), not a product defect.
- **Failure classes:** each failed/blocked step carries `cause` (`app_defect`, `unconfirmed`, `automation`, `accessibility`, `api_data`, `test_data`), `evidence` (identifiers + readable text at the moment of failure), `recommendation` and `cleanup`; the text report groups findings by class and has RESULTS BY AREA (Login vs Watchlist), CLEANUP and a QA ASSESSMENT that counts only confirmed application defects.
- **WL-20:** "No result found" is what the screen shows until the request returns; the wait is now for rows that match the query. On a real miss the Stocks category is asked once: results there and none under All = application defect; none anywhere = API/data.
- **WL-30:** the tab label is `Tab n of m` + name (prefix stripped). One Save per phase, early exit on a validation message ("already exists"), request-in-flight and screenshot recorded; the restore Save is never repeated (a second attempt only when Edit could not be opened or typed into). Cleanup is reported separately.
- **WL-40/41:** radios are read after the 500 ms Depth preview animation; restore is verified by the list layout and by reopening the screen; WL-41 uses a chip for an exchange the watchlist holds (chips are generated from the list's exchanges, so there is no INDEX chip without an index) and sorts only on a watchlist with two or more instruments (otherwise NOT TESTED).
- **WL-08:** the old text locator could never match: the button shows the *selected* condition, not the hint. Tradion now tags `CustomDropdownButton(automationId: 'alert_condition')` and its options `alert_condition_item_<i>` (uncommitted in `tradion_mobile`; needs a rebuild, else WL-08 is BLOCKED with that recommendation).
- **WL-06:** after `chart_ready` the screenshot is inspected (`ScreenshotInspector`: colours and non-background area of the chart band). A pixel statistic, not recognition of candles; the screenshot is kept.
- **WL-14:** `needRep` no longer reads rows while More Details is open (that is what made it "empty").
- **WL-90 (new):** a last case compares the working watchlist's name, count and tile layout with how the run found them.
- **Zip:** now includes the `WL-xx_*` checkpoint screenshots as well as `failed_*`.
- **Not changed:** the run still starts by clearing the app's data and signing in (Login is in scope, and a known state matters more than the saved minute); no on-device run was possible here.

### 4g-3. Second remediation, from the 11 failure screenshots (compiled, NOT run on a device)
The screenshots show every screen rendering correctly, so WL-04/05/07/11/12/13/15 were automation false failures (text read from `node.text` only; fixed in 4g-2). Further root causes, read from the screenshots and Tradion's code:
- **WL-20 was the tool, not the app:** the step failed in 0.9 s, but Tradion debounces search by 1 s and shows "No result found" until the request returns (`search_provider.dart`: one-second text debounce, `_searchScripList` cleared on every request). The old wait took that empty state as the answer. The wait is now for rows matching the query; on a real miss the Stocks category is asked once to decide app defect vs API/data. For developers to confirm (not proven defects): the All tab sends `["All","NFO","NSE","CDS","MCX","NCX","BSE","BCD"]` as exchanges (a literal "All" entry, and no BFO) while Stocks sends `["NSE","BSE"]`.
- **WL-30:** "Do you want to save changes?" is raised by Back (or the app bar arrow) when something is unsaved; the old failure screenshot was taken after the tool's own Back. `saveEdit` now taps Save once and, if the dialog appears, its own Save (`reorder_confirm_save_button`), never Discard; the wait ends on the screen closing or a validation message; no 20-second blind wait beyond the real server call; the restore Save is never repeated. Why the old restore Save did not complete is still unknown (Tradion's Save only acts while `isMwNameChange / isListOrdered / deleteScripList` is true, and validates the name against all watchlists including the current one).
- **WL-40/41:** Manage Watchlist radios change provider state at once; Apply (`saveFilter`) persists to preferences. The restore is verified by the list layout and by reopening the screen. Filter chips are generated from the exchanges of the instruments in the list (`market_provider.dart` ~1343: INDEX only when an index is in it), so a list holding only INFY has just an NSE chip: WL-41 uses `watchlist_filter_chip_<exchange>` and checks its `selected` state. The INDEX chip needs an index instrument in the working watchlist.
- **WL-13:** the page is scrolled to its top before reading; stat labels are matched by stem; share-holding and Financials are looked for below the fold.
- **Evidence:** failure screenshots are taken at the moment of failure and named `<case>_<failure|unreadable|save_stuck>_<n>_<screen>` (screen read from the live identifier), so WL-40 and WL-41 can no longer share a picture; the report prefers them over the generic post-clean-up `failed_*` shot.
- **Classes in the report** use the fixed vocabulary APPLICATION / AUTOMATION / API / TEST DATA / ACCESSIBILITY / UNKNOWN.

### 4g-4. "Direct login only" (compiled, tests pass, NOT run on a device)
A switch under Login in the module sheet (`login_direct_only`, off by default; enabled only while Login is ticked). On: the five detours (`guest_user_checked`, `unblock_account_checked`, `join_now_checked`, `forgot_password_checked`, `switch_account_checked` = `TradionAutomationFlow.LOGIN_DETOUR_STEPS`) are skipped with the note "direct login only"; the Confirm OTP button and Enable 2FA checks stay because they sit on the sign-in path and leave nothing to come back from. Unticking Login still skips all seven. Plumbing: `loginDirectOnly` Dart → `MainActivity` → `StartRequest` → `ModuleSelection.directLogin`. Ashika has no Login module, so no switch there.

### 4g-5. Third remediation, from the 10:21 report (compiled, NOT run on a device)
- **Wrong instrument:** the representative was the first NSE row, a bond (`750TCSL28-N0`) left in the account by an earlier run; it has no chart, Technicals, Fundamentals or Op.Chain data, so WL-06/07/12/13 judged the wrong thing. `needRep` now takes a plain NSE equity (letters-only name, well-known first: INFY, TCS, RELIANCE...), and notes it when none exists.
- **Grid vs list names:** a Grid card shows `INFY`, search and Edit show `INFY-EQ`. Rows are now matched by base name + exchange (`sameScrip`), which removes the false failures of WL-24/31/32 and the 10-second waits behind the "slowest steps".
- **Test instrument:** WL-31/32 added a bond because searching `TCS` under Stocks lists bonds first. It now takes an `-EQ` NSE row from RELIANCE / HDFCBANK / SBIN and records it *before* adding, so the final clean-up always knows about it. **The earlier failed runs left `755TCSL28-N0` and `792TCSL29-N0` (and the older `750TCSL28-N0`) in the account's watchlist: remove them by hand.**
- **Duplicate openings:** `tapAndConfirm` re-taps when the next screen has not appeared within the retry time; on a page that was only slow, that opened it twice. Navigation taps now tap once and wait the full 3 s (`tapOpen`, `openSheet`). The sheet cases (WL-05, 06, 07, 08, 09, 10) no longer return to the Watchlist between them: an open sheet or Alert page is reused (`sheetFor`, `openAlert`), WL-09 does not open the sheet to learn it has no Op.Chain button (WL-05 recorded that), and WL-08 uses the Alert page WL-07 left open.
- **More Details:** each tab's own body (`more_info_body_<tab>`) is read, so the header and tab names no longer count as content, and every tab's load time is recorded (the Info tab now reports its load time and how many lines it showed).
- **WL-07/08:** the value field is checked before typing (the keyboard covers it); WL-08 waits for the dropdown and scrolls to it instead of testing the instant the page opens.
- **WL-40:** the original layout was read *after* Manage Watchlist had covered the list (so "list showing" was wrong and a correct restore was reported as a failure). It is now read from the Watchlist first. The radio read no longer waits per radio, which was most of its 22.8 s.
- **WL-06:** a blank chart is now "needs review" (no history for the instrument, or not painted), not an application defect.

### 4g-6. Real instruments only, and one of each type per segment (compiled, NOT run on a device)
- Test instruments are `-EQ` equities only: the Edit cases add `RELIANCE-EQ` (then `TCS-EQ`, `HDFCBANK-EQ`) and match the exact name, so a bond such as `755TCSL28-N0` can no longer be picked. The representative for the sheet cases is a plain NSE equity from the watchlist.
- New case **WL-26** (`wl_search_segment_types`, module "Search and add"): adds one instrument of each type per segment from search: Stocks `-EQ` (NSE), F&O futures, call and put (NFO), Commodity future (MCX). The type is read from the row (`scripType`: `-EQ`; a `FUT` token; `CE` / `PE` token, or a strike ending in CE / PE), the count must rise by the number added and the exchanges must appear in the list, and every instrument is removed again by its search-row key even when the case fails (cleanup is reported). If the watchlist has less room than the case needs it is BLOCKED; a type with no `+` result is noted, not failed.

### 4g-7. "Password entered" failing in direct login (compiled, NOT run on a device)
Symptom (10:41 run): "tapped 'password_input' but it never took focus (it still offers no ACTION_SET_TEXT)" with the password page visible. Cause: `enterText` tapped the field once, right after the page appeared; the Navigator swallows touches while the page slides in, so the tap was lost. With the detours (Forgot Password, Switch Account) the page had settled by then; "Direct login only" removed that accidental delay. Fix in `AutomationEngine.enterText` (all fields): a tap that did not focus the field is repeated, at most three taps, each waiting on the field advertising `ACTION_SET_TEXT`; nothing is typed before that.

### 4g-8. Fourth remediation, from the 10:45 report (compiled, NOT run on a device)
24 of 29 Watchlist cases passed. The four failures:
- **WL-11 (false):** BUY / SELL are in the bar below the tab body, outside `more_info_body_market_depth`; they are now looked for by identifier / on the screen.
- **WL-40 (a real inconsistency, class APPLICATION):** with a Grid layout the case selects List, Apply turns the list into a list, but reopening Manage Watchlist shows Grid selected (Depth and Open Price persist). The earlier run showed the same the other way round (List to Grid). The case now says exactly that, with the screen's actual choice, and no longer waits 3 s per radio.
- **WL-30 / WL-90:** the restore Save did nothing (its label stayed, no dialog, no message, so no request started); the screenshot also showed the name field reading `testQRfh`, i.e. the keyboard left characters behind. Now: `typeName` checks the field reads exactly what was typed (one corrective retype only on a mismatch); `saveEdit` recognises a Save that started no request after 3 s instead of waiting 20 s (Save shows a spinner instead of its label while a request runs); a Save that did nothing is retried once, but only after `reloadWatchlist()` (Orders tab and back) so the second attempt starts from reloaded state; a Save that was answered is never repeated. If it still does nothing it is reported as APPLICATION with the facts (Tradion's name field compares with `oldMwName` in `reorder_screen.dart`), and the cleanup line tells the operator the exact name to restore. Likely but unproven cause: Tradion's `oldMwName` still holds the previous name, so typing it back counts as "no change".
- **WL-26:** F&O call and put were not found because the result list builds only the first rows; the search now scrolls the list (three screens at most) and also tries `NIFTY`; removal scrolls the same way.
- **Slowest steps:** WL-30 24 s (20 s of it waiting for a Save that never started) and WL-40 20.4 s (3 s per radio for a state that was simply false) are cut as above.

### 4g-9. Remembered module choice, direct login on by default
The module ticks and "Direct login only" are saved per app (`SecureStorageService.saveModulesOff` / `saveDirectLogin`, keys `tool_modules_off_<app>` and `tool_login_direct_<app>`) and restored when the tool opens or the app is switched. The *unticked* modules are stored, so a module added later starts ticked. First time: every module ticked and Direct login only ON; it stays whatever the operator last chose.

### 4g-10. Fifth remediation, from the 11:04 report and its recording (compiled, NOT run on a device)
25 of 29 passed. Root causes of the remaining failures and the slow steps, read from the recording:
- **WL-26 (21.9 s, left `TCS 270CT 1080 PE` in the list):** removal read the PE row while the list was still reloading (a spinner instead of its tick), took "no tick" as "already removed" and skipped it; the case then waited the full 10 s for a count that could not change. Now each row is read until it shows a tick or a + (`removeOne`), instruments of one search are removed in one search session, a second pass runs only when the count shows something is left, and the count wait is the UI time.
- **WL-40 (19.1 s):** Manage Watchlist was opened four times and the recording shows 3-4 s idle stretches on the same screen: a radio tap made while the screen was still settling was lost and `choose` waited the whole 3 s before failing and retrying. `choose` now looks after 1 s and taps once more; the restore no longer reopens the screen to verify (the reopened screen shows a stale Tile choice, the application defect this case reports, and a second Apply from there could undo the restore); the list layout is the proof of the restore. Two openings instead of four.
- **WL-30 (10.4 s):** the first restore Save did nothing (no request started), then worked after the watchlist was reloaded: that is the evidence for the stale `oldMwName` state, so the case now reports it as an APPLICATION finding (name restored, cleanup OK) instead of a plain pass.
- **WL-06 (5.7 s):** the pixel check waited 0.8 s before each look although the chart had reported ready; 0.3 s now.
- WL-30 (rename to `Wl<ddMMyHHmmss>`) and the More Details / Info load times (0.5 s each) are as intended.

## 4h. Investment tab scenarios (2026-10-23, compiled, NOT run on a device)
26 steps added to the Dashboard module (126 steps in total), one per feature with the tabs named in the note (owner's choice; not one per row of their sheet): `investment_tab_selected`, `alerts_page_opened`, `alert_created`, `alerts_search_checked`, `alert_deleted`, `ipo_page_checked`, `ipo_details_checked`, `reports_page_opened`, `etf_page_opened`, `etf_tabs_options`, `etf_tabs_buy_sell`, `etf_search_settings`, `pledge_page_opened`, `back_office_opened`, `other_request_opened`, `top_sectors_view_all_checked`, `scanners_{equity,futures,options,commodity}_tab_{options,buy_sell}`, `scanners_equity_search_settings`, `investment_customize_checked`. Run order is the order in `AppModules.DASHBOARD_SCENARIO_STEPS`; the Scanners ones run inside the same open of Scanners as the `screener_*` steps.
Owner's decisions: (1) the tool DOES create a real alert: Alerts page → + New Alert → scrip field → the app's search screen (`tcs`, first result) → name `AUTOCHECK<5 digits>` and value `9999999` (condition left at its default, Greater than) → CREATE → found in the list, found by the Alerts search → opened and DELETEd. If a later step fails, remove any `AUTOCHECK…` alert by hand. (2) IPO details: only the category toggle chips are tapped; `ipo_apply_button` is only checked, never tapped; applied IPOs do not open details, so up to three rows are tried. (3) Reports, Pledge, Back Office and Other request all open an in-app web page (`web_view_screen`, 30 s allowed for the vendor sign-in); the step checks it opens and that Back returns to the dashboard.
Mapping notes: the Alerts page's `+ New Alert` is the suffix of the search field; Quick Tools tiles are keyed by position (`invest_tile_0` Alerts, 1 IPO, 2 Mutual Funds SDK (not driven), 3 Reports, 4 ETF, 5 Pledge, 6 Back Office, 7 Other); "Top Sectors tabs" are the tabs of the page behind View All; "Summary" does not exist (see 4f), the Scanners per-tab check is Chart, Alert, Op.Chain, More Details; ETF has Chart, Alert, Basket, More Details (a box the sheet doesn't offer is noted, not failed).
Risks to watch: (a) RUNTIME: every tab of four Scanners segments is opened for options and again for Buy / Sell, each needing a fresh sheet per option, so the Dashboard module now takes tens of minutes. (b) The alert scrip pick assumes tapping a search result in alert mode returns to the create page. (c) Options and Commodity tabs may legitimately list nothing; that fails with "lists no instrument". (d) The Basket box opens the select-basket screen (`basket_select_screen`), left with Back; nothing is added. (e) `forEachTab` waits for load with `checkPageLoads(.., 1)`, so a slow segment shows as a load failure, not a missing tab.
Found while doing this: the Scanners sheet has its own copy of `SectorFilterSort`; before this the earlier `screener_sort_checked` step could not have found its rows. All eight copies in Tradion are now tagged where a scenario uses them (futures, scanners, ETF).

## 4d. Build script (2026-10-23)
- `build.sh` bumps `pubspec.yaml`, generates `lib/build_info.dart`, builds with `--build-name/--build-number`, renames the APK, writes a release note, and can commit. Verified: syntax, rollback on a failed build, and a full run on a throwaway copy (the APK reported versionCode 2 / versionName 1.0.1). The commit/push prompts and `split`/`appbundle` paths were not exercised.

## 4c. Modules (2026-10-23)
- The run form has a module checklist (`ModulePicker`, `AppModules.kt`); the selection reaches the flows as `ModuleSelection`, and an unselected module's steps are skipped ("module not selected: X"). Sign-in and sign-out are never modules. Tradion's blocks are gated with `if (moduleOn(..))` / `run {}` in `run()`, `runWatchlist()` and `runTabs()`; Ashika's watchlist block and its tab sections likewise. Compiled and unit-tested on the Dart side; NOT run on a device.
- Edit watchlist without Search and add never deletes (nothing would put the scrip back).

## 4b. Edit watchlist, step 38 flake (2026-10-23)
- "name restored and saved" failed only when the first watchlist held ONE scrip: the edit session deleted it, leaving an empty watchlist, and the rename-back Save never closed the screen (20 s). Why the app does not close Save on an empty watchlist is NOT established (Tradion's `savebutton` returns without popping on a failed name validation or an exception, and pops otherwise); reproduce by hand: empty a watchlist, rename it, Save. The tool now skips the delete when there is one scrip, retries the restore Save once, and puts the screen's texts (snackbars) in the failure message.
- A failed restore leaves the watchlist named with the temporary `…Q` suffix; rename it back by hand.

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
- The Tradion biometric-enrol screen can't be skipped. The tool taps Enable now and waits up to 90 s (`Timeouts.BIOMETRIC_CONFIRM`) for a person to touch the sensor / use the PIN; it never answers the prompt. Run on 2026-10-23 aborted at step 22 within 0 ms of step 21 tapping Enable now, i.e. while the prompt was waiting for a person: that abort is what was removed. Not yet verified on a device. A hands-off alternative (owner's decision, not done): skip the enrolment screen in test builds only, behind the compile-time `kAutomationBuild` flag (not the spoofable launch extra), since it is a mandatory security step.
- Force-stopping or reinstalling the tool turns its accessibility service off (and interrupts a run). Re-enable it via Settings, or on an emulator with `adb shell settings put secure enabled_accessibility_services com.mobileautomation.tool/com.mobileautomation.tool.AutomationAccessibilityService` and `accessibility_enabled 1`.
- `CredentialField` uses `autofillHints: const <String>[]`. `null` is NOT an opt-out; Android autofill filled a saved login into the tool's form once.
- Tradion diff size: `dart format` re-indented wrapped subtrees (about 6000 lines of whitespace churn). `git diff -w` shows the real change (about 1000 lines). Prefer these patterns for new tags to avoid churn: `final marked = X(...); return AutomationTag('id', child: marked);`, or rename `build` to `_buildMarked` with a one-line wrapper. Do not run `dart format` on whole files.
- The tool never taps Alert, Modify/Cancel/Exit, the Place/Submit button on the order pad, or the dashboard "Scalper" tile (it opens an external page). Since 2026-10-23 (owner's decision) it DOES tap the plain Buy and Sell buttons on a scrip sheet in the Dashboard module, only to open the order pad, and leaves it with Back. Those two buttons carry `trade_buy_button` / `trade_sell_button` only in the plain mode of `BuySellButton` (not Modify, Cancel, Exit, Add, Repeat). Tradion's `ApiLinks.isUAT` is false, so this reaches the LIVE backend: opening a pad places nothing, but use a test account.

## 7. Incident to be aware of (do not repeat)
While smoke-testing, scripted `adb` taps and reinstalls ran on the owner's emulator while they were using it. Android autofill put their saved live Tradion login into the tool's form, and one run logged into their real account (read-only, failed before any edit). Rule: do not run scripted `adb input`, reinstalls, or tool runs on a device the owner may be using without asking. Prefer a separate AVD. Use obviously fake credentials, and never let a test reach a live backend with real credentials.

## 8. How to run, and how to extend
- Install APKs: tool release APK plus the annotated Tradion build (`flutter build apk --debug --no-pub` in tradion_mobile; the owner may use `flutter run`). On the phone: enable the tool's accessibility service (Android 13+ may need "Allow restricted settings"), open Run Automation, pick Tradion, enter User ID, Password and OTP, press Start. Don't touch the screen during a run.
- Reports are in `/sdcard/Android/data/com.mobileautomation.tool/files/reports/` (`.txt` / `.json` / `.html`, plus `screenshots/`). Each step's note says how a tap was made. Failure reasons list the identifiers visible at that moment, which usually pinpoints the problem.
- Adding an app: add to `TargetApp.kt` (allowlist), its package to `accessibility_service_config.xml` `packageNames` and the manifest `<queries>`, an ids object in `AutomationConfig.kt`, a flow extending `FlowBase`, and a `when` branch in `AutomationEngine.start`. Annotate the target app and document the ids.

## 9. What to do next (suggested)
1. Ask the owner for the report from the newest run (126 steps) and fix by root cause, in this order: first failing step, then dependent skips.
2. Confirm or refute known issues 1, 3 and 4 from real report text, not from guesses.
3. Once the flow is stable: commit both repos in sensible commits (Tradion annotations separate from the tool), and consider the `applicationIdSuffix` or flavour fix so the allowlist can enforce UAT-only.
4. Keep the owner's style: concise replies, root-cause fixes, flows that continue past failures, and honest reporting of what was and wasn't run.

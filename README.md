# Mobile Automation Tool

On-device test automation for Android Flutter applications. Two apps are
supported, picked on the tool's **Run Automation** screen:

| App | Package | Login | Regression pass |
| --- | --- | --- | --- |
| **Ashika** | `com.codifi.dhanush` | Client ID → OTP → MPIN → Skip Biometric | 60 steps: watchlist and search, load check of every tab of Home, Research, Portfolio and Orders, logout |
| **Tradion** | `com.codifi.rmoney` | User ID → Password → OTP → (Risk Disclosure → "I understand") | 116 steps: login side paths (Guest User, Unblock Account, Join Now, Forgot Password, Switch Account, Login with OTP, Confirm OTP button, Enable 2FA), watchlist (27 independent test cases WL-01..WL-41: landing, switching, Predefined, indices / funds panel, instrument sheet with Chart / Alert / Op.Chain / BUY-SELL order pad, More Details, search by category, green-tick add / remove, Edit rename / reorder / delete of a test instrument, Manage Watchlist view / sort / filter / Clear), Dashboard (index dropdown, Trading tab selected, Option Chain per segment, Research calls, Trading Ideas tabs / search / menu / filters, Futures View All with sort and Buy / Sell, FII / DII, Customize, Scanners tabs / segments / search / sort / scrip sheet with Buy / Sell, then the Investment tab: Alerts (a test alert is created, searched and deleted), IPO, Reports, ETF (every tab), Pledge, Back Office, Other request, Top Sectors, every Scanners tab of Equity / Futures / Options / Commodity, Customize), load check of every tab of Orders, Portfolio (Holdings, Positions) and Profile, logout — see section 1a |

Sections 2–18 describe the shared tool and, unless a section says otherwise,
Ashika's flow; Tradion's is in section 1a. Everything runs on a single Android
device. There is no Appium, no Python, no
Selenium, no ADB during normal execution, no PC or Mac during normal execution,
and no external automation server. A host machine is needed only to build and
install the two APKs.

---

## 1. Project purpose

Two APKs are installed side by side on one phone (or three, with both targets):

| APK | Role |
| --- | --- |
| The UAT build of a target app (`com.codifi.dhanush` or `com.codifi.rmoney`) | the application under test |
| Automation Tool (`com.mobileautomation.tool`) | the test driver and reporter |

The operator picks the app, types its UAT credentials (Ashika: Client ID, OTP,
MPIN; Tradion: User ID, Password, OTP) into the Automation Tool and taps
**Start … Automation**. The tool launches the target app, drives the
login through Android's accessibility APIs, verifies that Home or the Watchlist
is reached, and produces a pass/fail checklist as JSON, HTML and plain text.

Ashika's pass is the happy path only: login, then a watchlist and search pass, then a
visit to every bottom tab (Home, Research, Portfolio, Orders) checking that each
of their tabs finishes loading within 8 seconds (5 for the Orders tabs and
Positions), then logout from Profile. There is deliberately no invalid Client
ID, invalid OTP or invalid MPIN coverage, nothing that places or changes an
order or moves funds, and no second test suite.

Load checks read markers the target app puts on its shared loading, empty and
retry widgets (`ui_loading`, `ui_empty`, `ui_error`). A tab passes when no
`ui_loading` remains on any screenful of it within its budget. A tab that is
still loading, or shows the Reload screen, fails its own step and the run moves
on, so the report shows every tab and the run still logs out.

## 1a. Tradion regression pass

`TradionAutomationFlow` drives the Tradion app (`com.codifi.rmoney`). It shares
the engine, the reports and the load-check idea with Ashika's flow; the screens
and controls are Tradion's own. The identifiers it reads are listed in
`tradion_mobile/docs/AUTOMATION_SEMANTICS.md` and mirrored in
`AutomationConfig.TradionIds`.

```
Clear app data → launch
User ID page   Guest User · Unblock Account · Join Now (each opened and left; see below)
User ID → Login → Password page   Forgot Password · Switch Account (back to login and again to the password page)
Password → Proceed → (authenticator accounts: Login with OTP) → OTP: Confirm OTP button checked,
OTP typed (auto-submits) → Enable 2FA (biometric devices only) → Risk Disclosure (if shown) → Home

Watchlist   27 independent cases (WL-01..WL-41), one representative instrument each, 3 s per
            normal click, 10 s for server data, 15 s for the chart WebView · landing · switch ·
            Predefined · indices panel · instrument sheet · Chart · Alert page (never submitted) ·
            Option Chain · BUY / SELL open the order pad (left with Back) · More Details ·
            search All / F&O / Commodity / Stocks · green-tick remove and re-add · Edit rename
            (restored) / reorder / delete a test instrument the run added · Manage Watchlist
            view, sort, filter, Clear (restored). Results: PASS / FAIL / BLOCKED / NOT APPLICABLE /
            NOT TESTED, with a defect list and QA assessment in the report (HANDOFF 4g)
Logout      Profile → Logout → Yes → back on the login screens
```

**The Edit cases never delete the account's own instruments.** They add a test instrument themselves (TCS, else RELIANCE), reorder and delete only that one, and remove it again even when an earlier case failed; a full watchlist makes them BLOCKED.

**Login side paths** (steps `guest_user_checked` … `enable_2fa_checked`). A link's form is opened,
a fixed fake value (`9999999999`, `ABCDE1234F`) is typed into each field that accepts typing, and
Back returns to the page it came from. No button on those forms is tapped, so nothing is sent.
Facts that shape them:

* The User ID field on Unblock Account and Forgot Password is read-only and prefilled, so only
  its PAN field is typed into. The Guest form has one field (mobile number).
* Join Now opens the registration page in the **browser**, which the tool cannot read. The check
  is that Tradion leaves the foreground and can be brought back to the login page; the page's
  content is not inspected.
* Switch Account clears the app's saved user (local only) and returns to the User ID page; the
  tool signs the same User ID in again to reach the password page.
* "Login with OTP" lives on the authenticator (TOTP) screen, shown only to accounts that use one.
  Other accounts go straight to the OTP screen and the step is skipped with that note.
* The Confirm OTP button is checked (on screen, enabled, tappable) but never tapped, because the
  OTP auto-submits and a second submit could spend the one-time code twice.
* "Enable 2FA" is the biometric screen ("Enable 2FA security" / "Enable now"). It appears only on
  a device with biometrics; its button is tapped, which starts Android's own prompt that the tool
  does not drive. A person answers it (see the Tradion-specific limits below).
* A failed check is recorded and the run continues, unless the flow can no longer get back to the
  page it needs (then the run stops with the reason). Build Tradion from the current sources: older
  builds lack these identifiers, and the failure message says so.


How it differs from Ashika, and why:

* **No intro carousel.** After the splash screen's network and version checks a
  fresh install routes straight to the User ID screen, so there is no "Login" step
  before it (45 s is allowed for the splash).
* **The OTP field is a plain text field** that submits itself on the sixth digit,
  so the tool types the code and waits for the next screen; it taps *Confirm OTP*
  only if nothing happens within 7.5 s, and that button ignores taps while a
  submit is in flight.
* **The risk disclosure is optional.** Home is built first and the disclosure is
  pushed a frame later, so after reaching Home the tool waits up to 6 s for
  `risk_disclosure_dialog`; if it appears the tool taps *I understand* (or *Next*)
  until it closes, otherwise the step is reported as skipped.
* **Back exits the app from the home screen.** Tradion's `WillPopScope` returns
  `true` after showing its exit dialog, so the tool only presses Back to close an
  overlay it has just confirmed is open (details sheet, sort sheets, Filter and
  edit screens). The sort sheets and details sheet carry their own markers for
  that reason, and the exit dialog's *Yes* is tagged `exit_confirm_button`, never
  `logout_confirm_button`.
* **Inner tab labels carry live counts** ("Pending (3)", "holdings (12)") and are
  localised, so tabs are found by position-based identifiers, never by label.
* **The watchlist is touched only by test instruments and settings.** The Watchlist
  cases add one instrument (INFY via the green tick, TCS / RELIANCE for the Edit
  cases) and remove it again, rename a watchlist and restore the name, and change
  Manage Watchlist preferences and restore them. A clean run ends with the account
  as it started; a failure in the middle is cleaned up by the suite's own final
  step. Use a dedicated test account anyway.

Soft failures: any step after login that fails is recorded and the run moves on
(steps that depend on it are marked skipped), so the report shows every tab and
the run still logs out. Only the login steps, which nothing can proceed without,
abort the run.

Tradion-specific limits:

* **A device with biometrics enrolled** gets Tradion's mandatory *Enable 2FA security*
  screen after the OTP. It has no Skip, and the tool never drives fingerprint, face or
  PIN authentication. So the tool taps **Enable now** (once, from the Login module's
  check or from the disclosure step if that check is off) and then **waits up to 90 s
  for you** to touch the sensor or use the phone's PIN, and the run carries on to Home
  the moment the app does. If the prompt is dismissed, Enable now is tapped again (twice
  at most). Nobody answering in time stops the run with that explanation. This means
  a run on such a phone is not hands-off at that one moment; a device or emulator with no
  biometrics enrolled is. Tradion asks again after every data reset, and after a reset
  from inside the app, so expect it on each run.
* **TOTP accounts** (an authenticator code instead of an SMS OTP) get a different
  screen, which this tool does not drive.
* **A user who still has to set a password**, or a blocked account, is routed to
  other login screens; the run fails at *Password screen displayed* and says so.
* **A disclosure of the "blocked" type** opens an external link instead of closing;
  the run fails at *Risk disclosure accepted* and says so.
* **Holdings › Stock / MTF** is one toggle: the tool flips it once, checks the
  other view loads, and flips it back. Net P&L / Day P&L are restored the same way.
* **UAT versus live:** Tradion selects its environment with
  `ApiLinks.isUAT` (`false` = live) in `lib/api/core/api_links.dart`, under the
  same package name — see section 16.

---

## 2. Architecture

```
┌──────────────────────── Automation Tool APK ────────────────────────┐
│                                                                      │
│  Flutter UI (lib/)                                                   │
│    dashboard · credentials form · live progress · report · history    │
│         │                                                            │
│         │  MethodChannel  com.mobileautomation.tool/commands         │
│         │  EventChannel   com.mobileautomation.tool/events           │
│         ▼                                                            │
│  MainActivity.kt                                                     │
│         │                                                            │
│         ▼                                                            │
│  AutomationEngine (worker thread)                                    │
│    · polls the tree every 350 ms against a per-step deadline          │
│    · enterText / tap / keypad taps / screenshot                       │
│    · LoginAutomationFlow (Ashika) / TradionAutomationFlow hold the    │
│      checklists; FlowBase is their shared bookkeeping                 │
│         │                                                            │
│         ├── AccessibilityNodeFinder  (locator strategies)             │
│         ├── TargetAppLauncher        (PackageManager)                 │
│         ▼                                                            │
│  AutomationAccessibilityService                                       │
│    scoped via android:packageNames to the target + Settings/perms     │
└──────────────────────────────┬───────────────────────────────────────┘
                               │ reads nodes, dispatches taps
                               ▼
                    ┌──────────────────────────┐
                    │  Target UAT app          │
                    │  Semantics(identifier:)  │
                    └──────────────────────────┘
```

Division of labour, as specified:

* **Flutter** — dashboard, credentials form, test progress, checklist report,
  test history, report export.
* **Kotlin** — the accessibility service, reading nodes, finding elements,
  clicking, entering text, launching the target app, detecting screen
  transitions, returning results to Flutter.

The engine runs on its own thread. Accessibility tree reads block, and the
platform thread has to stay free so `stopAutomation` can arrive mid-run.

### Source layout

```
mobile_automation_tool/
├── lib/
│   ├── main.dart
│   ├── app.dart
│   ├── models/
│   │   ├── automation_step.dart        # StepStatus + one checklist row
│   │   ├── automation_result.dart      # whole-run result, report source
│   │   └── test_run.dart               # history entry + report file paths
│   ├── screens/
│   │   ├── dashboard_screen.dart       # readiness, entry point, history
│   │   ├── login_automation_screen.dart# credentials form + pre-flight
│   │   ├── running_test_screen.dart    # live checklist
│   │   └── report_screen.dart          # report, export paths, share
│   ├── services/
│   │   ├── automation_service.dart     # typed platform-channel wrapper
│   │   ├── report_service.dart         # JSON / HTML / text + history
│   │   └── secure_storage_service.dart # Keystore-backed credentials
│   ├── widgets/
│   │   ├── test_step_tile.dart
│   │   ├── status_indicator.dart
│   │   └── credential_field.dart
│   └── constants/
│       └── automation_ids.dart
├── android/app/src/main/kotlin/com/mobileautomation/tool/
│   ├── MainActivity.kt                    # MethodChannel + EventChannel
│   ├── AutomationAccessibilityService.kt  # tree, gestures, screenshots
│   ├── AccessibilityNodeFinder.kt         # locator strategies
│   ├── AutomationEngine.kt                # primitives + run lifecycle
│   ├── FlowBase.kt                        # step bookkeeping, aborts, load check
│   ├── LoginAutomationFlow.kt             # Ashika's flow (keeps its own helpers)
│   ├── TradionAutomationFlow.kt           # Tradion's flow (extends FlowBase)
│   ├── TargetApp.kt                       # the apps this tool may drive
│   ├── AutomationStepResult.kt            # step + run result models
│   ├── TargetAppLauncher.kt               # PackageManager
│   └── AutomationConfig.kt                # allowlist, identifiers, timeouts
├── android/app/src/main/res/xml/
│   └── accessibility_service_config.xml
├── reports/                               # placeholder; reports live on-device
└── README.md
```

`AutomationConfig.kt` is one file beyond the suggested structure. Everything
security relevant — the allowlist, the identifiers, the timeouts — is collected
there so it can be reviewed in one place. The allowlist itself is
`TargetApp.kt`: `ALLOWED_PACKAGES` is derived from it, so adding an app there
without also adding its flow does not compile.

---

## 3. Target Flutter project changes

Target project: `/Users/srivigneshbalaji/Projects/ashika_broking_mobile`

**Discovered facts**

| | |
| --- | --- |
| Android package (applicationId / namespace) | `com.codifi.dhanush` |
| Main activity | `com.codifi.dhanush.MainActivity` (extends `FlutterFragmentActivity`) |
| minSdk / targetSdk | 23 / 36 |
| Navigation | named routes via `MaterialApp.onGenerateRoute` → `AppRouter.router` |
| Client ID screen | `MobileScreen`, route `userLoginScreen` |
| OTP screen | `UserLoginOtpDialog` — a **modal bottom sheet**, not a route |
| MPIN screen | `MpinValidScreen`, route `mPinValid` |
| Biometric screen | `BioMetricEnableDialog(isSkip: true)` — also a **bottom sheet** |
| Home | `TabBarController`, route `tabscreen` |
| Watchlist | `WatchlistScreen` (tab index 1 inside `TabBarController`) |
| Pin input widget | `CustomPinCode` → `pin_code_fields` `PinCodeTextField` |
| OTP | 6 digits, auto-submits on completion, **plus** a "Verify" button |
| MPIN | 4 digits, auto-submits on completion, **no** submit button |
| Custom MPIN keypad | none — the OS keyboard drives the pin field |

Because the OTP and biometric steps are bottom sheets rather than routes, screen
detection is by identifier presence, not by window transitions — they never open
a new Android window.

**Files modified** (7, accessibility annotations only):

1. `lib/screens/login/gust_user_login/mobile_screen.dart`
2. `lib/screens/login/login_otp/user_otp_screen.dart`
3. `lib/screens/login/mpin/mpin_screen.dart`
4. `lib/screens/login/biometric_enable_screen.dart`
5. `lib/screens/bottom_tab/tab_screen.dart`
6. `lib/screens/watchlist/watchlist_screen.dart`
7. `lib/screens/login/disclosure/disclosure_screen.dart`

Plus one new document: `docs/AUTOMATION_SEMANTICS.md`.

No authentication logic, API call, UI design, validation rule or navigation path
was changed. No screen was duplicated. Every annotation wraps the existing
widget and reuses the existing controllers and callbacks. `flutter analyze` on
the target reports the **same 828 issues, in the same files, under the same
rules**, before and after.

---

## 4. Semantics identifiers added

| Identifier | Where |
| --- | --- |
| `client_id_screen` | `MobileScreen` root |
| `client_id_input` | `CustomTextFormField` for `userIdController` |
| `client_id_continue_button` | "Continue" `CustomOutlineButton` |
| `otp_screen` | `UserLoginOtpDialog` root |
| `otp_input` | 6-digit `CustomPinCode` |
| `otp_submit_button` | "Verify" `CustomOutlineButton` |
| `mpin_screen` | `MpinValidScreen` root |
| `mpin_input` | 4-digit `CustomPinCode` |
| `biometric_screen` | `BioMetricEnableDialog` root |
| `biometric_skip_button` | "Skip" `CustomHyperTextButton` |
| `home_screen` | `TabBarController` root |
| `watchlist_screen` | `WatchlistScreen` root |
| `risk_disclosure_dialog` | `RiskDisclosureDialogBox` root — see below |

On Android, `Semantics(identifier:)` is published **unprefixed** through
`AccessibilityNodeInfo.setViewIdResourceName`, so it reads as `resource-id`.

### Three things worth knowing

**`mpin_submit_button` does not exist in this app.** The MPIN screen submits from
`onCompleted` when the 4th digit lands. The tool taps a submit button when one is
present and otherwise waits for the auto-submit. The OTP screen auto-submits
*and* has a Verify button, so the tool probes for the next screen before tapping
Verify — otherwise it would submit a one-time code twice.

**The two pin fields also expose `onSetText`.** `pin_code_fields` keeps its real
`TextFormField` inside `AbsorbPointer(absorbing: true)`.
`RenderAbsorbPointer.describeSemanticsConfiguration` sets
`config.isBlockingUserActions = true`, and a blocking configuration strips every
semantic action except accessibility focus — so that node advertises **no
`ACTION_SET_TEXT`** and an accessibility service cannot type into it at all. The
wrapper therefore supplies the action itself and writes through the same
`TextEditingController` the keyboard writes to. `PinCodeTextField` listens to
that controller, so `onChanged` / `onCompleted` fire exactly as for a real user,
including the auto-submit. No validation, API call or navigation is bypassed.

**`risk_disclosure_dialog` is an extra beyond the requested list, added for
correctness.** `LoginProvider.screenNavigation()` can raise
`RiskDisclosureDialogBox` over the tab screen right after login, and a modal
route's barrier wraps the routes beneath it in `BlockSemantics` — so while that
dialog is open, `home_screen` is *not reachable* in the accessibility tree.
Without this identifier a fully successful login would be reported as "Home not
displayed". The tool treats seeing this dialog as proof that login succeeded and
says so in the report.

Full detail: `ashika_broking_mobile/docs/AUTOMATION_SEMANTICS.md`.

---

## 5. Automation Tool setup

Requirements: Flutter 3.32+ (built with 3.32.7 / Dart 3.8.1), JDK 17, Android
SDK with platform 35 and NDK 27.0.12077973.

```bash
cd /Users/srivigneshbalaji/Projects/ClientView/test/mobile_automation_tool
flutter pub get
flutter analyze     # expect: No issues found!
flutter test        # expect: All tests passed!
```

Package name `com.mobileautomation.tool`, minSdk 29 (Android 10), targetSdk 35.

---

## 6. Building the Automation Tool APK

Use `build.sh` (modeled on `tradion_mobile/build.sh`, for this Android-only app):

```bash
cd /Users/srivigneshbalaji/Projects/ClientView/test/mobile_automation_tool
./build.sh                       # release APK; bumps the version, renames the file
./build.sh apk split             # one APK per ABI (arm64-v8a, armeabi-v7a, x86_64)
./build.sh appbundle             # Android App Bundle
./build.sh apk debug             # debug APK
./build.sh apk --no-bump         # build the current version; no bump, note or commit
./build.sh apk gitc "what changed"   # also commit with a message (asks first)
```

What it does, in order: `flutter pub get`, then `flutter analyze` and `flutter test`
(a failure stops here, before any file is touched; `--skip-checks` to bypass); bumps the
patch and build number in `pubspec.yaml` (`1.0.0+1` → `1.0.1+2`) and writes
`lib/build_info.dart` (the dashboard shows "Version … · built …", which tells you which
build is installed); `flutter clean` + `pub get` (`--no-clean` to skip); builds with
`--build-name` and `--build-number`; renames the result to
`AutomationTool_v1.0.1+2_ddMMyyyy_HHmm.apk`, prints its size and SHA-256 and reveals it in
Finder (`--no-rename`, `--no-reveal`); writes `release_notes/android_1.0.1_2_<date>.md`
from what changed in `lib/`, `android/app/src/`, `test/`, `scripts/` including files not
yet added to git (`--no-note`). If the build fails, `pubspec.yaml` and `build_info.dart`
are put back. Anything after `--` goes to `flutter build`.

Differences from Tradion's script, on purpose: the version lives in `pubspec.yaml` (no
`version_config.json`); the commit stages only `pubspec.yaml`, `build_info.dart` and the
release note unless `--commit-all` is given (Tradion's `git add .` would sweep in every
other change); the push is a second question; there is no iOS or LIVE/UAT label.

The same build without the script:

```bash
flutter build apk --release
```

Output:

```
build/app/outputs/flutter-apk/app-release.apk
```

A smaller per-architecture build is also available:

```bash
flutter build apk --release --split-per-abi
# build/app/outputs/flutter-apk/app-arm64-v8a-release.apk
```

The release build is signed with the **debug** keystore on purpose, so an
internal tool builds without provisioning a private key. Give it a real signing
config before distributing it any further than the test bench.

---

## 7. Installing both APKs

Both APKs go on the **same physical device**. This is the only step that needs a
computer; after it, nothing outside the phone is involved.

```bash
# 1. The target UAT application — see the warning in section 16 first
adb install -r /path/to/ashika-uat-release.apk

# 2. The Automation Tool
adb install -r build/app/outputs/flutter-apk/app-release.apk

# 3. Pre-grant the notification permission so the OS never raises the
#    "Allow Dhanush to send you notifications?" dialog over the intro
#    screen — see section 17. Repeat this after every reinstall.
adb shell pm grant com.codifi.dhanush android.permission.POST_NOTIFICATIONS
```

Sideloading from the device (file manager, internal distribution link) works
equally well. Emulators are not supported for a real pass: the login flow needs
live UAT connectivity and real OTP delivery.

---

## 7a. First-launch setup (and install-time grants)

The first time the tool opens, a **Set up the tool** screen collects everything it
needs, so nothing asks for permission in the middle of a run:

| Item | Needed | How |
|---|---|---|
| Accessibility service | Required | Android lets only you switch it on. The screen opens App info (Android 13+: menu > *Allow restricted settings*) and Accessibility settings, and notices when you come back. *Finish setup* unlocks once it is on. |
| Notifications (the tool's own) | Recommended | A normal dialog; lets Android show the "recording" notice. |
| Screen recording | Recommended | *Test screen recording* records 2.5 s and deletes it, proving the device can record. Android 14+ cannot remember the answer, so each run asks again at **Start** (before the test, never during it) and the tool taps *Share screen* for you. |
| Apps to test | Info | Shows whether Ashika and Tradion are installed. |

The screen reappears whenever the accessibility service is off (a reinstall turns it
off), and is reachable from the dashboard (*Permissions setup*) and from the run form.

**Nothing can run at install time** on Android; the first launch is the earliest.
For true install-time grants, run `scripts/provision_device.sh [serial]` from a
computer after `adb install`: it enables the accessibility service, allows restricted
settings, grants the tool's notification permission, and grants the notification
permission of Ashika and Tradion (so they never show it mid-run, with or without the
`AUTOMATION_TEST` build flag). It cannot grant screen recording. The script has not
been run on a device; do not point it at a phone someone is using.

**Play Protect.** The tool is an accessibility service that records the screen and taps
other apps' dialogs, and the release APK is signed with the debug key, so Play Protect
may warn about or block it. It cannot be switched off from inside the app, and the app
makes no attempt to hide from it. On a test phone, turn it off by hand: Play Store >
profile picture > Play Protect > settings (gear) > turn off *Scan apps with Play Protect*
(or choose *Install anyway* / *More details* on the warning). Adding `--relax-play-protect`
to the script also turns off the package verification that adb installs go through; its
effect on Android 16 has not been checked. A proper release signing key would reduce the
"unknown developer" warning; that needs a keystore you own.

---

## 8. Enabling the Accessibility Service

1. Open the Automation Tool. The dashboard shows **Accessibility service — Not
   enabled**.
2. Tap **Open Accessibility Settings**.
3. Go to **Installed apps** (some OEMs label it *Downloaded apps* or
   *Installed services*).
4. Tap **Automation Tool** and turn it on.
5. Accept the system permission dialog.
6. Return to the tool. The status flips to **Enabled and running** — the
   dashboard re-checks automatically on resume.

The tool reports two separate facts, because both matter: whether the service is
enabled in Settings, and whether the system has actually bound it. A stale
Settings entry naming a stopped service would otherwise let a run start and then
fail on its first tree read.

---

## 9. Android 13+ restricted settings

On Android 13 and above a sideloaded app's accessibility toggle is greyed out
until restricted settings are allowed. The tool detects this and shows the
instructions; **Open App Info (restricted settings)** takes you straight there.

1. Open **App info** for Automation Tool.
2. Tap the **⋮ menu, top right**.
3. Choose **Allow restricted settings**.
4. Go back to **Settings → Accessibility**.
5. Enable **Automation Tool**.

This is a deliberate Android protection. The tool only navigates you to the
screen — it makes no attempt to work around the restriction.

---

## 10. Entering UAT credentials

On **Run Automation**, pick the **Target application** first; the form follows it:

| Field | Ashika | Tradion |
| --- | --- | --- |
| Target application package | shown, read-only — the allowlist is enforced natively | shown, read-only |
| User id | **Client ID** — UAT test client code | **User ID** — UAT test user |
| OTP | typed by hand, 6 digits | typed by hand, 6 digits |
| Secret | **MPIN** — digits, **always masked** | **Password** — **always masked** |

There is no reveal toggle on a secret. **Remember …** stores the user id and the
secret *for the picked app* in Android's Keystore-backed
`EncryptedSharedPreferences` (separate keys per app; Ashika's keep their original
names, so credentials saved before Tradion was added are still found). The
**OTP is stored too, under the same switch** — meant for test accounts whose OTP is
fixed. With **Remember** on it is kept and refilled next time; with it off, the OTP
is cleared from the form the moment a run finishes. A genuinely one-time OTP is
useless the second time, so leave **Remember** off for such an account.
**Clear saved …** wipes the user id, secret and OTP kept for that app.

Autofill, autocorrect and suggestions are switched off on every field so the
values are not offered to the platform's autofill or dictionary services.

---

## 11. Running the Login Automation

1. Make sure the target app is **not already logged in**. If it has a stored
   session it opens straight onto the MPIN screen and the run fails with a
   message telling you exactly that. Force stop it first.
2. Have the OTP ready — request it, read it, and type it into the tool. It has a
   short validity, so do this immediately before starting.
3. Tap **Start Login Automation**.
4. The live view shows each checklist item as it runs. **Do not touch the screen
   while a run is in progress** — a stray tap changes the state the driver is
   reasoning about.
5. **Stop Automation** cancels at the next poll; the run is reported as STOPPED.

What happens under the covers:

```
Launch target app                      (allowlist checked first)
Tap Login on the intro screen          20 s, skipped if already past it
  (a system dialog, e.g. the notification permission prompt, may sit on
  top of this screen — see §17)
Wait for client_id_screen              15 s
Find client_id_input → enter Client ID
Tap client_id_continue_button
Wait for otp_screen                    30 s
Find otp_input → enter OTP
Probe for mpin_screen (auto-submit)     4 s → else tap otp_submit_button
Wait for mpin_screen                   30 s
mpin_input present?  → enter MPIN
  else mpin_key_<digit> present? → tap the digits
Tap mpin_submit_button if one exists, else wait for auto-submit
Wait for biometric_screen | home | watchlist | disclosure   30 s
biometric_screen shown? → tap biometric_skip_button
  else mark "Biometric skipped" as SKIPPED
Wait for home_screen | watchlist_screen | risk_disclosure_dialog   30 s
```

### Choosing modules

Under the target application package, the run form has a **Modules to test** field
showing the choice ("All modules (8)", or "3 of 8: Login, Orders, Profile"). Tap it and
the checklist opens in a bottom sheet, all ticked. Untick what a run should leave out;
its steps are marked *skipped* (with "module not selected") and the report names the
modules that ran and the ones left out. **All** and **None** are on the sheet's header,
and changes apply as you tick them (**Done** just closes the sheet). Signing in and signing out always run, because every module
needs the app signed in and a run ends logged out.

| App | Modules |
|---|---|
| Tradion | Login (the login page checks: Guest User, Unblock, Join Now, Forgot Password, Switch Account, Confirm OTP button, Enable 2FA), Watchlist, Edit watchlist, Search and add, Orders, Dashboard, Portfolio, Profile |
| Ashika | Watchlist and Search, Home, Research, Portfolio, Orders |

Things that follow from what each module changes:

* **Edit watchlist** and **Search and add** change the account's data. Search's INFY add
  is what puts back the scrip the edit deletes, so with Edit on and Search off the edit
  renames and reorders but **does not delete**.
* With Edit off and Search on, the count check starts from the watchlist's current count.
* The Watchlist tab is opened and its tabs counted whenever any of Watchlist, Edit or
  Search is selected, since they all need it; with none selected it is not opened.
* Ashika's profile step opens Orders first when Orders is off, because Logout is reached
  from Profile.

The module lists live in `AppModules.kt`, which is also what the run form shows.

### Locator strategy

Priority, highest first:

1. Accessibility view id (`viewIdResourceName`)
2. Content description
3. Flutter `Semantics(identifier:)`
4. Visible text
5. Node class name / hierarchy
6. Screen coordinates — last resort

On a Flutter surface (1) and (3) are the *same field*, because Flutter publishes
`identifier` through `setViewIdResourceName`. The finder accepts both the bare
name and the `package:id/name` form, so the same locators keep working if a
screen is ever rewritten as a native view.

An identifier match is treated as an **anchor**, not as the target: a
`Semantics` wrapper is its own node, and the node carrying the tap or the text
action is usually a descendant (an `InkWell`, an `EditableText`) and can be an
ancestor if Flutter merged the annotation upwards. The finder resolves self →
descendants → a few ancestors.

**About coordinates.** There are no hard-coded screen positions anywhere in this
tool. The only gesture fallback taps the centre of a node the tool has already
resolved from the accessibility tree, and it is used only when that node
advertises no `ACTION_CLICK`. It still carries real limitations, which is why it
is last: it can miss if the view scrolls between the bounds read and the
gesture, and it hits whatever is topmost if the node is covered. Every use is
recorded in the report as "tapped at the resolved node's own bounds centre".

### Waits and timeouts

Polling every **350 ms** against a per-step deadline. No long blocking sleeps.

| Step | Budget |
| --- | --- |
| Client ID screen | 15 s |
| OTP screen | 30 s |
| MPIN screen | 30 s |
| Biometric screen | 10 s |
| Home screen | 30 s |
| App launch | 15 s |
| Auto-submit probe | 4 s |

Tune them in `AutomationConfig.Timeouts`.

A run also aborts if the target app stops being the readable foreground window
for 8 s. Because the service is scoped to one package, another app taking over
appears as a sustained unreadable window rather than as a different package name
— so the tool can detect that it lost the app, but cannot name what replaced it.
That is a deliberate trade for the narrow scope.

---

## 12. Viewing reports

Every finished run is saved automatically and appears under **Test history** on
the dashboard. **View Last Report** opens the newest.

The report screen shows the verdict badge, the completed count, run metadata
(target package, main activity, target version, device, Android version, start,
end, duration, run id), the full checklist with per-step status, start, end,
duration and reason, the failure block, screenshot paths, and the plain-text
checklist with a Copy button. **Download Reports** is available as soon as a run
finishes, on the report screen, and beside every run in **Test history**. It
copies that run's HTML, JSON and plain-text files to:

```
Downloads/Automation Tool Reports/
```

The download uses Android's scoped MediaStore and therefore needs no storage
permission. Unlike the app-specific originals below, downloaded copies remain
available after the Automation Tool is uninstalled.

### The text report

The `.txt` report (also in the zip) is laid out for a monospace font; in the app it
scrolls sideways. Its sections:

| Section | What it holds |
|---|---|
| Result | verdict, completed count, and how many steps passed, were skipped, failed or never ran |
| RUN | package, app version, main activity, device, start / end, total duration and the time spent inside steps, run ID, recording file |
| FAILURE | the first failed step, when it happened, its position in the video, the full reason, its screenshot |
| API / DATA CHECKS | data loads, server changes and sign-ins counted by outcome, the verdict, the slowest loads |
| SLOWEST STEPS | the five longest steps |
| CHECKLIST | one table row per step: `#`, result, step, start, end, time, video position, type (read / write / auth) and the API / data check |
| STEP DETAILS | every step's full note or failure reason, with its failure screenshot |
| EVIDENCE | the screenshot files |

**The API / data check is inferred from the screen; the tool does not see network
traffic.** A **read** (a screen that loads data) is OK when the app's loading spinners
clear in time with no "Something went wrong" screen, and is marked EMPTY (loaded, no
data), SLOW (over 5 s), TIMEOUT (still loading when the budget ended), ERROR (the error
screen showed) or NOT CHECKED (the step failed before the data could be checked). A
**write** (add, delete, save) is CONFIRMED when the flow re-read the server's state
afterwards and it matched, otherwise NOT CONFIRMED. A **sign-in / out** is OK when the next
screen followed. Which steps are which is decided in `StepApi.kt`. The same fields are in
the JSON (`apiSummary` and per-step `apiKind`, `apiStatus`, `apiDetail`, `apiMs`) and as a
column in the HTML report.

Each checklist item carries: step name, status
(Pending / Running / Passed / Failed / Skipped), start time, end time, duration,
and failure reason.

### Report storage location

```
/Android/data/com.mobileautomation.tool/files/reports/
├── 20260806_143012_a1b2c3d4.json
├── 20260806_143012_a1b2c3d4.html
├── 20260806_143012_a1b2c3d4.txt
├── history.json
└── screenshots/
    └── a1b2c3d4_completed.png
```

This is application-specific external storage: no storage permission is needed,
and everything is removed when the tool is uninstalled. The exact paths are shown
on the report screen and are selectable.

### Success

```
Phase 1 - Login Automation

✓ Target application installed
✓ Accessibility permission enabled
✓ Target application launched
✓ Intro screen Login tapped
✓ Client ID screen displayed
✓ Client ID input found
✓ Client ID entered
✓ Continue button tapped
✓ OTP screen displayed
✓ OTP input found
✓ OTP entered
✓ OTP submitted
✓ MPIN screen displayed
✓ MPIN entered
✓ MPIN submitted
✓ Biometric screen checked
✓ Biometric skipped
✓ Home or Watchlist displayed

Result: PASSED
Completed: 18/18
```

### Failure

```
Phase 1 - Login Automation

✓ Target application installed
✓ Accessibility permission enabled
✓ Target application launched
✓ Intro screen Login tapped
✓ Client ID screen displayed
✓ Client ID input found
✓ Client ID entered
✓ Continue button tapped
✕ OTP screen displayed
· OTP input found  (not reached)
...

Failure:
The OTP screen was not detected within 30 seconds. Identifiers visible when the
wait expired: client_id_screen, client_id_input, client_id_continue_button. The
Client ID may have been rejected, or the OTP request may have failed.

Result: FAILED
Completed: 7/17
```

Timeout messages list the identifiers that *were* visible, which usually
identifies the problem immediately. Identifiers are static strings from the
target app's source, so this is safe to include; node **text** is never
collected, because text on a login screen can contain the value just typed.

---

## 13. Downloading and sharing reports

**Download Reports** saves the HTML, JSON and text versions to
`Downloads/Automation Tool Reports/` and confirms how many files were written.

**Share Report** on the report screen opens the Android share sheet with the
HTML, JSON and text files attached and a subject line of
`<test name> — <verdict> (<completed>/<total>)`.

Sending a report to an external service publishes it. The files carry no
credential, but they do carry the target package, the device identity and the
UAT app version — send them somewhere appropriate for internal test evidence.

---

## 13a. Screen recording of the run

**Record the screen** (on the run form, on by default) saves one MP4 of the whole
run, from before the data reset to the final state, and the report ties each
step to its place in it.

* Android asks "Share your screen?" every time you press Start; the tool pins it
  to the **entire screen** (Android 14+ otherwise offers a single app) and
  taps **Share screen** itself (`RecordingPromptAccepter.kt`). That needs the
  service to read System UI (`com.android.systemui` in the service's
  `packageNames`), but only for ~20 s after you press Start and only the one
  dialog that names this tool. If Android ignores the automated tap, press it
  yourself; the run waits. Leave the phone alone afterwards. Declining offers to run without a video; the
  report then says why there is none.
* The HTML report embeds the player and gives every step a `▶ m:ss` link that
  seeks to it. The video is named like the report (`<base>.mp4`); **keep the two
  in the same folder** — opened from a phone's file manager the page cannot reach
  a sibling file, so play the `.mp4` from Files/Gallery, or copy both to a
  computer. **Download Reports** and **Share Report** include the video.
* It is H.264 at 480 px on the short side, at most 8 fps, about 100 kbit/s
  (variable) with a key frame every 20 s: roughly 0.75 MB per minute, with the
  prices still readable (an 8-minute run went from 107 MB to about 6 MB). It is
  encoded with MediaCodec + MediaMuxer, because MediaRecorder forces a key
  frame every second. The run does not start recording with under 500 MB free.
* It is a picture of the screen: whatever Tradion shows is in it, and a window
  marked `FLAG_SECURE` is black. Use a test account, and treat the file like the
  report (section 13).
* A recording problem never changes a run's verdict; it is reported as a note.
  Android shows its own "recording" indicator for the duration.
* **Summary zip.** When a run finishes, the tool also writes
  `<app>_<ddMMyyyy>_<HHmm>.zip` (for example `ashika_23102026_0810.zip`) with the text report, the video and the failed-step
  screenshots (`screenshots/`, as 720-px-wide JPEGs under the report's names
  with `.jpg`; the full-size PNGs stay in the app's folder) and copies it to
  `Downloads/Automation Tool Reports/` on the phone, with no tap needed. The run
  screen and report screen say where it went, or why not. A copy stays in the
  app's `reports/` folder. Code: `SummaryZip.kt`.
* Code: `ScreenRecorder.kt` (MediaProjection in a `mediaProjection` foreground
  service), the `requestScreenRecording` method in `MainActivity.kt`, and
  `beginRecording` / `endRecording` in `AutomationEngine.kt`.

---

## 14. Screenshot limitations

Screenshots are best effort and are attempted on a failed step and on
completion, via `AccessibilityService.takeScreenshot`.

They are unavailable when:

* the device is Android 10 (API 29) — the API needs API 30+;
* the target app marks a window `FLAG_SECURE`, which financial apps commonly do
  (the current target build does not, but that can change);
* the system rate-limits or refuses the request.

In every case the checklist and the logs are still generated, the screenshot is
recorded as unavailable **with the reason**, and the run's verdict is unchanged.
A missing screenshot never fails an automation run.

---

## 15. Flutter accessibility limitations

* **The tree only exists on demand.** Flutter builds its semantics tree when an
  accessibility service is active. Enable the service *before* launching the
  target app.
* **Only annotated widgets are addressable.** A widget with no
  `Semantics(identifier:)` has to be found by visible text, which breaks under
  localisation and copy changes. Extending coverage means adding identifiers to
  the target app.
* **`AbsorbPointer` and `IgnorePointer` strip semantic actions.** Anything under
  one sets `isBlockingUserActions`, which removes every action except
  accessibility focus. This is exactly what `pin_code_fields` does, and it is why
  the two pin wrappers supply `onSetText` themselves. Expect the same problem
  from any widget that hides a real input behind a pointer barrier.
* **Container-only nodes are "not important".** Flutter marks a semantics node
  with no label of its own as not-important-for-accessibility, and the platform
  filters those out of the tree unless the service sets
  `flagIncludeNotImportantViews`. The `*_screen` markers are exactly that shape,
  so the service sets that flag.
* **`flagReportViewIds` is mandatory.** Without it `viewIdResourceName` is never
  populated and every identifier lookup returns null.
* **`findAccessibilityNodeInfosByViewId` does not work on Flutter surfaces.**
  Flutter's `AccessibilityBridge` does not implement it for its virtual node
  tree, so the finder tries it as a fast path and falls back to walking the tree.
* **Modal barriers hide what is beneath them.** A modal route wraps lower routes
  in `BlockSemantics`; while a dialog is open, screens below it are invisible to
  the tool. This is the whole reason `risk_disclosure_dialog` exists.
* **Obscured pin nodes still hold their value.** The blocked node behind a pin
  field carries the typed digits in its `text`. The finder never reads node text
  for reporting — only `textLength`, and only to compare a length.

---

## 16. Security restrictions

This is an internal UAT tool for a financial trading application.

* **Allowlist.** `AutomationConfig.ALLOWED_PACKAGES` contains exactly the
  packages of `TargetApp` — Ashika (`com.codifi.dhanush`) and Tradion
  (`com.codifi.rmoney`). Any other value is refused before the app is inspected
  or launched. The accessibility service is *additionally* scoped via
  `android:packageNames` to those packages plus the system screens used to reset
  them before each run:
  Settings (`com.android.settings`, and `com.miui.securitycenter` on Xiaomi) to
  clear the app's data, and the permission controller to answer the
  notification prompt that clearing brings back. It cannot read any other
  application, and every login/watchlist lookup is confined to the target
  package in code (`AutomationEngine.targetRoot`).
* **Pre-run reset.** Each run first opens the target's App info and taps
  Storage > Clear storage > Delete, so every run starts from a fresh install
  state. The Settings controls are recognised by meaning (`SettingsMatch.kt`): a
  Clear button is any short label with "clear" and "data" or "storage" but not
  "cache" ("Clear data", "Clear Data", "CLEAR ALL DATA", "Clear storage"), a Storage
  entry any short label with "storage" but not "clear"; on App info the Clear button
  is used if it is there (MIUI), otherwise the Storage entry is opened first. If a
  phone still defeats it, the failure lists the Settings package and every Settings
  window's labels. **Tradion then carries on instead of stopping**: step 3 is marked
  skipped with the reason, and if the app opens on a remembered user the tool presses
  Switch Account, and if it opens signed in it logs out from inside (Profile > Account >
  Logout), so the run still starts at the User ID screen. (Ashika has no such fallback
  yet; there, turn off **Clear the app's data first** and clear the app by hand.) Why
  Settings can fail on newer phones: since Android 14 an app can hide views from a
  service not declared an accessibility tool (`android:isAccessibilityTool`), and
  Android 15+ Settings keeps its Uninstall / Force stop / Clear storage buttons from
  such services; this tool does not declare itself one (see HANDOFF 4e). After launch, the "Allow … to send you notifications?" prompt is answered with
  Allow; other permission prompts are left alone.
* **No order, trade or funds capability.** On Portfolio and Orders the tool
  only selects tabs and scrolls; it never taps a row or an action button. There
  is no code path that places an order, buys, sells, or moves funds. Logout
  is the run's last step.
* **No SMS, no notifications.** `READ_SMS` is not requested and notification
  events are not observed. The OTP must be typed in by hand. That is why the
  service config declares no notification event types.
* **No biometric automation.** Fingerprint and face authentication are never
  driven. The tool only detects the biometric screen and taps Skip / Not Now /
  Later.
* **No credential exposure.** Credentials travel one way, into
  `startLoginAutomation`. They are not logged, not read back, not echoed into a
  step note, and not present in any report — `AutomationResult` has no field that
  could hold one. A test asserts that neither renderer can emit a credential.
  Failure diagnostics use static identifier strings only, never node text.
* **Minimal permissions.** No runtime permissions at all. Verified against the
  built APK with `aapt2 dump permissions`, the only `uses-permission` in the
  shipped manifest is `com.mobileautomation.tool.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`,
  a signature-level permission `share_plus` declares for its own broadcast
  receiver. There is no `INTERNET`, no storage, no SMS, and **no**
  `QUERY_ALL_PACKAGES` — package visibility comes from an explicit
  `<queries><package>` entry for the one allowlisted package, so the tool cannot
  enumerate what else is installed.
* **Verified service scope.** In the shipped APK the compiled service config
  reads `packageNames="com.codifi.dhanush,com.android.settings,com.miui.securitycenter,com.google.android.permissioncontroller,com.android.permissioncontroller"`, `accessibilityEventTypes=0x820`
  (window state changed | window content changed) and `accessibilityFlags=0x53`
  (default | includeNotImportantViews | reportViewIds | retrieveInteractiveWindows, the last so the notification permission prompt can be found over the target app) — no key-event filtering,
  no touch exploration, no notification events.
* **No security bypasses.** Android 13+ restricted settings are explained to the
  operator, never worked around.

### ⚠ UAT vs live: the one thing the allowlist cannot enforce

The target project builds its **UAT and its CUG/live variants from the same
`applicationId`** (`com.codifi.dhanush`). They differ only in the API endpoints
compiled into the build, selected by a source constant:

```dart
// ashika_broking_mobile/lib/api/core/api_links.dart
static bool isLive = true;   // true → CUG/live · false → UAT
```

A package-name allowlist therefore **cannot** distinguish them, and no code in
this tool can. Ensuring the installed build is the UAT one is the operator's
responsibility:

* Set `ApiLinks.isLive = false` before building the app you install for
  automation.
* Use dedicated UAT test credentials only. Never a real client's.
* At the time of writing, that constant is `true` in the target project's
  working tree, so a plain `flutter build apk --release` there produces the
  **live/CUG** build, not a UAT one.

**Tradion has the same property**, with a different switch:

```dart
// tradion_mobile/lib/api/core/api_links.dart
static bool isUAT = false;   // true → UAT · false → live
```

Package `com.codifi.rmoney` is used for both, and at the time of writing the flag
is `false` in the working tree, so a plain `flutter build apk` there produces the
**live** build. Set `ApiLinks.isUAT = true` before building the app you automate,
and use a dedicated test user: a clean Tradion run **deletes one scrip** from
an editable watchlist and adds one back (it never places or changes an order).

Giving the variants distinct `applicationId`s (a flavour, or an
`applicationIdSuffix`) would let the allowlist enforce this properly. That is a
change to the target apps' build configuration and is out of scope here, but it
is the right fix.

---

## 17. Known limitations

* **Ashika's flow has not been executed on a device by its author.** It is
  verified by static analysis, unit tests and a clean release build; the
  end-to-end login needs the UAT build installed and a live OTP. Section 18
  lists what to watch on the first run.
* **Tradion's flow was observed on an Android 16 (API 36) emulator** through the
  checks the first version of it reached: data clear (≈3.5 s), launch, User ID →
  Password → OTP (auto-submitted) → risk disclosure ("confirmed across 1 page")
  → Home, the watchlist (9 scrips), all 6 watchlist tabs and all 5 Predefined
  tabs loading. Every step after the Predefined check — basket, filter, details,
  edit/trim, search/add, the Orders / Dashboard / Portfolio / Profile load
  checks and logout — is compiled and analysed but **not yet seen running**.
  The first full run should be read step by step (the report's per-step notes
  say how each tap was made).
* **The target app must not be already logged in.** With a stored session it
  opens on the MPIN screen; the run fails and says so. Force stop it first.
* **An already-enabled biometric login can block the MPIN screen.** If the test
  account has biometric login active, `MpinValidScreen` auto-triggers the OS
  biometric prompt on entry. That prompt belongs to another package, so the
  window becomes unreadable and the MPIN step times out. Disable biometric login
  for the test account. The failure message says this.
* **The notification permission dialog can block the intro screen's Login
  button.** On a fresh install the OS asks "Allow Dhanush to send you
  notifications?" over the intro carousel. That dialog belongs to another
  package too, so `intro_login_tapped` cannot see or tap it — it can only
  retry the Login tap underneath, which lands only if the button still exposes
  `ACTION_CLICK` with the dialog on top. Pre-grant the permission before the
  run so the dialog never appears at all:
  `adb shell pm grant com.codifi.dhanush android.permission.POST_NOTIFICATIONS`
  (run again after every reinstall — a fresh install resets it).
* **One run at a time**, and it cannot survive the tool's process being killed.
  Android may stop a background app; keep the tool in recents and the screen on.
* **The intruding app cannot be named.** The narrow `packageNames` scope means a
  foreign foreground app appears as an unreadable window, not as a package name.
* **No keyboard-key fallback.** Typing via the on-screen keyboard is impossible
  by construction here: the IME is a different package, and the service cannot
  see it. `ACTION_SET_TEXT` and the in-app `mpin_key_<digit>` path are the two
  text-entry routes. This is a deliberate consequence of keeping the service
  narrow.
* **Screenshots may be unavailable** — see section 14.
* **Identifiers are duplicated** between `AutomationConfig.kt` and
  `automation_ids.dart` (Ashika's) and, for Tradion, between
  `AutomationConfig.TradionIds` and `tradion_mobile/docs/AUTOMATION_SEMANTICS.md`.
  They are kept in step by hand.
* **The notification prompt after a data reset (avoided in the current app
  builds).** Why it kept coming back: every run starts with *Clear storage*, which
  revokes the app's runtime permissions, so the app asks again at every launch (a
  one-time `pm grant` is undone by that reset), and the tool's tap on the dialog is
  unreliable on recent Android. So the apps no longer ask when the tool launches
  them: the tool puts the extra `com.mobileautomation.tool.AUTOMATION_RUN` on every
  launch, each app's `MainActivity` reports it (extra, or the launching package) to
  Dart through the `com.codifi.automation/run` channel, and `AutomationRun.isActive()`
  skips the request. No build flag is needed; `--dart-define=AUTOMATION_TEST=true`
  still works as an override. Ashika's version of this change is a patch for branch
  `improvement/automation_aug06`. An older app build, or one without the change,
  still asks, and then: clearing the target's data
  resets its notification permission, so the app asks again on launch and the tool
  answers *Allow*: ACTION_CLICK first, then a tap gesture at the button, and while
  the prompt is up the run waits for it rather than treating it as the app being
  lost (so a person may tap *Allow* by hand). If Android ignores the automated
  input — it can on recent versions — the failure says so and gives the
  `adb shell pm grant <package> android.permission.POST_NOTIFICATIONS` fix.
  Android also stops asking after a user dismisses the prompt twice, so on a
  device that has already refused it the prompt simply does not appear.
* **Do not run the tool on a device someone else is using.** It types into and
  taps the screen of a shared device, clears the target app's data, and a
  reinstall of either APK stops a run in progress ("the accessibility service was
  turned off"). Its form fields opt out of Android autofill, so a saved login
  from a password manager cannot land in them.
* **The release APK is debug-signed.** Fine for a test bench, not for
  distribution.
* **Android 10+ only** (minSdk 29). Tested against compileSdk/targetSdk 35.

---

## 18. Adding future test flows

The engine is flow-agnostic; `LoginAutomationFlow` (Ashika) and
`TradionAutomationFlow` are consumers of it. To add a third app, follow how Tradion
was added: an entry in `TargetApp.kt` (which is the allowlist), its package in
`accessibility_service_config.xml`'s `packageNames` and the manifest's `<queries>`,
an `object …Ids` in `AutomationConfig.kt`, and a flow that extends `FlowBase`.

1. **Annotate the target app.** Add `Semantics(identifier: ...)` to the widgets
   the new flow touches, and record them in
   `ashika_broking_mobile/docs/AUTOMATION_SEMANTICS.md`. If a widget sits under
   an `AbsorbPointer` or `IgnorePointer`, supply the action on the wrapper (see
   section 4).
2. **Add the identifiers** to `AutomationConfig.Ids` and, if the UI needs them,
   to `automation_ids.dart`.
3. **Write the flow.** Extend `FlowBase` (see `TradionAutomationFlow`): declare the
   checklist as a `List<AutomationStepResult>`, then use the engine primitives —
   `waitForAny`, `findNow`, `isPresent`, `enterText`, `tap`, `longTap`,
   `tapAndConfirm`, `tapUpTo`, `revealInList`, `scrollPage`, `verifyLength`,
   `screenshot` — and `begin` / `pass` / `skip` / `abort` / `failAndContinue` for
   bookkeeping. `checkPageLoads` is the "every section finished loading" check.
   Add a timeout block to `AutomationConfig.Timeouts`.
4. **Route it.** Add the app to `TargetApp` and let `AutomationEngine.start` pick
   its flow (a `when` over `TargetApp`, so a missing flow does not compile). The
   existing event vocabulary and report pipeline need no changes — reports are
   rendered from whatever checklist the flow declares, and the running screen
   shows the checklist the flow announces when it starts.
5. **Show it in the UI.** The app picker lists whatever `getAutomationConfig`
   reports; give the new app's credentials a label in `TargetAppInfo` if they
   differ from the existing two.

Two rules for anything new: **read-only or login-only**. Do not add a flow that
can place an order, trade, or move funds, and do not widen the allowlist or the
service's `packageNames` to reach a production package.

### First-run checklist for the real device test

When the end-to-end run is executed, verify in order:

1. The service reports **Enabled and running** before the target app is
   launched.
2. Step 4 passes — this proves identifiers survive the release build and that
   `flagReportViewIds` + `flagIncludeNotImportantViews` are doing their job.
3. Step 6 passes — this proves `ACTION_SET_TEXT` reaches the plain
   `TextFormField`.
4. Step 10 passes — this is the real test of the `onSetText` wrapper on
   `pin_code_fields`.
5. Step 11 reports "submitted automatically", not a Verify tap — confirming the
   OTP is not submitted twice.
6. Step 17 names `home_screen`, `watchlist_screen`, or the disclosure dialog.

If step 4 fails with "No accessibility identifiers were visible at all", the
installed target build predates the Semantics changes — rebuild and reinstall it.


--------

Both projects build; tool analyze clean, 9/9 tests pass. Here's the delivery.

⚠ Two things to read first

1. The APK I built for the target app is the LIVE/CUG build, not UAT. The target project selects environment from a source constant, not a build flag:
// ashika_broking_mobile/lib/api/core/api_links.dart:8
static bool isLive = true;   // true → CUG/live · false → UAT
It's true in your working tree, so flutter build apk --release produced a CUG artifact (its own build script names such files ashika_CUG_...). I did not flip it — that's your release configuration. Set isLive = false and rebuild before installing the app you automate against.

2. A package-name allowlist cannot enforce UAT-only here. UAT and live share applicationId com.codifi.dhanush, so the allowlist (and the service's packageNames scope) can't tell them apart. Giving them distinct application IDs via a flavour or applicationIdSuffix would make the allowlist actually enforce this. Out of scope, but it's the real fix.

3. I have not run this on a device. Everything below is verified by static analysis, unit tests, APK inspection, and clean release builds. The end-to-end login needs the UAT build installed and a live OTP. README §18 has a first-run checklist ordered by what each step proves.

Target app: package, activity, files

com.codifi.dhanush / com.codifi.dhanush.MainActivity (a FlutterFragmentActivity). Navigation is named routes via onGenerateRoute.

7 files modified (annotations only) + docs/AUTOMATION_SEMANTICS.md. 13 identifiers: client_id_screen/_input/_continue_button, otp_screen/_input/_submit_button, mpin_screen/_input, biometric_screen/_skip_button, home_screen, watchlist_screen, risk_disclosure_dialog.

flutter analyze on the target reports the same 828 issues, same files, same rules before and after — I diffed against a stashed baseline; every difference was a line-number shift.

Four findings changed the design:
- The OTP and biometric screens are modal bottom sheets, not routes — so screen detection is by identifier presence; they never open an Android window.
- pin_code_fields hides its real TextFormField inside AbsorbPointer, which sets isBlockingUserActions and strips every semantic action. ACTION_SET_TEXT on the OTP/MPIN fields is therefore impossible. The two pin wrappers supply onSetText themselves, writing through the same controller the keyboard writes to, so onChanged/onCompleted and the auto-submit fire exactly as for a real user. No validation or API call is bypassed.
- There is no mpin_submit_button — MPIN auto-submits on the 4th digit. The OTP auto-submits and has a Verify button, so the flow probes for the next screen before tapping Verify; otherwise it would submit a one-time code twice.
- risk_disclosure_dialog is one identifier beyond your list, added for correctness. That dialog can cover Home right after login, and a modal barrier wraps lower routes in BlockSemantics — so home_screen becomes unreachable and a successful login would have reported "Home not displayed".

Automation tool

/Users/srivigneshbalaji/Projects/ClientView/test/mobile_automation_tool — 27 files, package com.mobileautomation.tool, minSdk 29. Channels …/commands and …/events with all six documented commands and event names; skipped steps ride step_passed with status: "skipped" to keep the vocabulary at six.

APK: mobile_automation_tool/build/app/outputs/flutter-apk/app-release.apk (55.3 MB, debug-signed). Verified by dex inspection that the latest Kotlin is in it.

Verified in the shipped APK rather than asserted:
- Only uses-permission is share_plus's own signature-level one. No INTERNET, storage, SMS, or QUERY_ALL_PACKAGES.
- Service config: packageNames="com.codifi.dhanush", events 0x820, flags 0x13 (default | includeNotImportantViews | reportViewIds).

flagReportViewIds is mandatory — without it viewIdResourceName is never populated and every lookup returns null. flagIncludeNotImportantViews is also required: I decompiled Flutter's AccessibilityBridge.isImportant() and confirmed it returns false for a node with no label and no actions, which is exactly the shape of a *_screen marker. As defence in depth each screen is detected by its marker or by a control unique to it, so detection doesn't hinge on that one flag.

Locators run identifier → content-description → text → class, and an identifier match is an anchor: the node carrying the tap/text action is resolved from it (self → descendants → ancestors). There are no hard-coded coordinates; the only gesture fallback taps the centre of an already-resolved node, and every use is recorded in the report.

Credentials go one way into startLoginAutomation and are never logged, read back, or put in a report — AutomationResult has no field that could hold one, and a test asserts neither renderer can emit one. Timeout diagnostics list visible identifiers only, never node text, because the blocked node behind a pin field holds the typed digits.

Reports (JSON + HTML + text + history.json) land in /Android/data/com.mobileautomation.tool/files/reports/.

Remaining manual configuration

1. Set ApiLinks.isLive = false and rebuild the target APK.
2. Install both APKs; enable the service (Android 13+: "Allow restricted settings" first — the tool links you there).
3. Force-stop the target app before a run — with a stored session it opens on the MPIN screen and the run fails saying so.
4. Disable biometric login for the test account, or the OS biometric prompt covers the MPIN screen and makes the window unreadable.
5. Replace the debug signing config before distributing the tool further than the test bench.

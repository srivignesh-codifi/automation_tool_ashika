# Mobile Automation Tool

On-device UAT test automation for an Android Flutter application. Phase 1
automates exactly one scenario — a **successful login**:

```
Client ID → OTP → MPIN → Skip Biometric → Home / Watchlist
```

Everything runs on a single Android device. There is no Appium, no Python, no
Selenium, no ADB during normal execution, no PC or Mac during normal execution,
and no external automation server. A host machine is needed only to build and
install the two APKs.

---

## 1. Project purpose

Two APKs are installed side by side on one phone:

| APK | Role |
| --- | --- |
| The UAT build of the target app (`com.codifi.dhanush`) | the application under test |
| Automation Tool (`com.mobileautomation.tool`) | the test driver and reporter |

The operator types the UAT Client ID, OTP and MPIN into the Automation Tool and
taps **Start Login Automation**. The tool launches the target app, drives the
login through Android's accessibility APIs, verifies that Home or the Watchlist
is reached, and produces a pass/fail checklist as JSON, HTML and plain text.

Phase 1 is the happy path only. There is deliberately no invalid Client ID,
invalid OTP, invalid MPIN, logout, watchlist, portfolio, order or funds coverage,
and no second test suite.

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
│    · LoginAutomationFlow holds the 18-step checklist                  │
│         │                                                            │
│         ├── AccessibilityNodeFinder  (locator strategies)             │
│         ├── TargetAppLauncher        (PackageManager)                 │
│         ▼                                                            │
│  AutomationAccessibilityService                                       │
│    scoped to ONE package via android:packageNames                     │
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
│   ├── LoginAutomationFlow.kt             # the 18-step flow
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
there so it can be reviewed in one place.

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

```bash
cd /Users/srivigneshbalaji/Projects/ClientView/test/mobile_automation_tool
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

On **Login Automation**:

| Field | Notes |
| --- | --- |
| Target application package | prefilled from the allowlist; a non-allowlisted value is refused |
| Client ID | UAT test client code |
| OTP | typed by hand, 6 digits |
| MPIN | **always masked**, no reveal toggle |

**Remember Client ID and MPIN** stores those two in Android's Keystore-backed
`EncryptedSharedPreferences`. The **OTP is never stored** — it is a one-time
value, and it is cleared from the form the moment a run finishes.
**Clear saved Client ID and MPIN** wipes what was kept.

Autofill, autocorrect and suggestions are switched off on all four fields so the
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
checklist with a Copy button.

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

## 13. Sharing reports

**Share Report** on the report screen opens the Android share sheet with the
HTML, JSON and text files attached and a subject line of
`<test name> — <verdict> (<completed>/<total>)`.

Sending a report to an external service publishes it. The files carry no
credential, but they do carry the target package, the device identity and the
UAT app version — send them somewhere appropriate for internal test evidence.

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

* **Allowlist.** `AutomationConfig.ALLOWED_PACKAGES` contains exactly one
  package. Any other value is refused before the app is inspected or launched.
  The accessibility service is *additionally* scoped to that same package via
  `android:packageNames`, so it is technically incapable of reading any other
  application on the device.
* **No order, trade or funds capability.** The tool can find and act on login
  identifiers only. There is no code path that places an order, buys, sells, or
  moves funds.
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
  reads `packageNames="com.codifi.dhanush"`, `accessibilityEventTypes=0x820`
  (window state changed | window content changed) and `accessibilityFlags=0x13`
  (default | includeNotImportantViews | reportViewIds) — no key-event filtering,
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

Giving the two variants distinct `applicationId`s (a flavour, or an
`applicationIdSuffix`) would let the allowlist enforce this properly. That is a
change to the target app's build configuration and is out of scope here, but it
is the right fix.

---

## 17. Known limitations

* **Not yet executed on a real device.** Everything here is verified by static
  analysis, unit tests and a clean release build. The end-to-end login has
  **not** been run on physical hardware — it needs the UAT build installed and a
  live OTP. Section 18 lists what to watch on the first run.
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
  `automation_ids.dart` and are kept in step by hand.
* **The release APK is debug-signed.** Fine for a test bench, not for
  distribution.
* **Android 10+ only** (minSdk 29). Tested against compileSdk/targetSdk 35.

---

## 18. Adding future test flows

The engine is flow-agnostic; `LoginAutomationFlow` is just one consumer of it.

1. **Annotate the target app.** Add `Semantics(identifier: ...)` to the widgets
   the new flow touches, and record them in
   `ashika_broking_mobile/docs/AUTOMATION_SEMANTICS.md`. If a widget sits under
   an `AbsorbPointer` or `IgnorePointer`, supply the action on the wrapper (see
   section 4).
2. **Add the identifiers** to `AutomationConfig.Ids` and, if the UI needs them,
   to `automation_ids.dart`.
3. **Write the flow.** Copy the shape of `LoginAutomationFlow`: declare the
   checklist as a `List<AutomationStepResult>`, then use the engine primitives —
   `waitForAny`, `findNow`, `isPresent`, `enterText`, `tap`, `tapKeypadDigits`,
   `verifyLength`, `screenshot` — and `begin` / `pass` / `abort` / `skip` for
   bookkeeping. Add a timeout block to `AutomationConfig.Timeouts`.
4. **Route it.** Add a command alongside `startLoginAutomation` in
   `MainActivity.handle`, and let `AutomationEngine.start` pick the flow. The
   existing event vocabulary and report pipeline need no changes — reports are
   rendered from whatever checklist the flow declares.
5. **Add the checklist to `AutomationChecklist`** so the UI can show the steps
   before the first event arrives.

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
import 'package:flutter/services.dart';

import '../models/automation_result.dart';
import '../models/automation_step.dart';

/// Whether the accessibility service is usable right now.
class AccessibilityStatus {
  const AccessibilityStatus({
    required this.enabledInSettings,
    required this.connected,
  });

  const AccessibilityStatus.unknown()
    : enabledInSettings = false,
      connected = false;

  final bool enabledInSettings;
  final bool connected;

  /// Enabled by the user *and* bound by the system. Both halves matter: a stale
  /// Settings entry can name a service the platform has since stopped, and a run
  /// started in that state fails on its first tree read.
  bool get isReady => enabledInSettings && connected;

  String get summary {
    if (isReady) return 'Enabled and running';
    if (enabledInSettings) return 'Enabled in Settings but not running';
    return 'Not enabled';
  }
}

/// Whether the allowlisted target app is present on the device.
class TargetAppStatus {
  const TargetAppStatus({
    required this.packageName,
    required this.installed,
    required this.allowlisted,
    this.mainActivity,
    this.versionName,
  });

  const TargetAppStatus.unknown(this.packageName)
    : installed = false,
      allowlisted = false,
      mainActivity = null,
      versionName = null;

  final String packageName;
  final bool installed;
  final bool allowlisted;
  final String? mainActivity;
  final String? versionName;

  String get summary {
    if (!allowlisted) return 'Not in the automation allowlist';
    if (!installed) return 'Target application is not installed';
    return versionName == null ? 'Installed' : 'Installed (v$versionName)';
  }
}

/// The credential a target app asks for after the one-time code.
enum TargetSecret {
  mpin('MPIN'),
  password('Password');

  const TargetSecret(this.label);

  final String label;

  static TargetSecret parse(Object? raw) =>
      raw == 'password' ? TargetSecret.password : TargetSecret.mpin;
}

/// A part of an app's run the operator can switch off.
class AutomationModule {
  const AutomationModule({
    required this.id,
    required this.label,
    this.description = '',
  });

  final String id;
  final String label;
  final String description;
}

/// One application the native driver has a test flow for. The native
/// `TargetApp` enum is authoritative; this mirrors what it reports.
class TargetAppInfo {
  const TargetAppInfo({
    required this.id,
    required this.displayName,
    required this.packageName,
    required this.testName,
    required this.secret,
    this.modules = const <AutomationModule>[],
  });

  final String id;
  final String displayName;
  final String packageName;
  final String testName;
  final TargetSecret secret;

  /// What a run can be limited to. The native driver is authoritative.
  final List<AutomationModule> modules;

  /// What the first credential is called in this app's own login.
  String get userIdLabel => id == 'tradion' ? 'User ID' : 'Client ID';

  /// The one-line summary of what a run does, shown under the Start button.
  String get runSummary => id == 'tradion'
      ? 'Checks the login page links (Guest User, Unblock Account, Join Now, '
            'Forgot Password, Switch Account, Login with OTP, Confirm OTP, '
            'Enable 2FA), logs in (User ID, Password, OTP), then exercises the watchlist: '
            'scrip details, edit and delete, and Search in every segment '
            '(add, then delete), and checks that a Predefined list takes no '
            'scrips. It then checks the load state of every Orders, '
            'Dashboard, Portfolio and Profile tab and logs out. A failed '
            'step is recorded and the run moves on. It never places or '
            'changes an order. The edit step deletes 1 scrip from the first '
            'watchlist and the All-segment search adds 1 back.'
      : 'Logs in (Client ID, OTP, MPIN), then checks the watchlist and '
            'search, the load state of every tab, and logs out. It never '
            'places or changes an order.';
}

/// Static configuration reported by the native driver.
class AutomationConfigInfo {
  const AutomationConfigInfo({
    required this.allowedPackages,
    required this.defaultTargetPackage,
    required this.testName,
    required this.requiresRestrictedSettingsGuidance,
    required this.targets,
  });

  const AutomationConfigInfo.fallback()
    : allowedPackages = const <String>[
        'com.codifi.dhanush',
        'com.codifi.rmoney',
      ],
      defaultTargetPackage = 'com.codifi.dhanush',
      testName = 'Phase 1 - Login Automation',
      requiresRestrictedSettingsGuidance = true,
      targets = const <TargetAppInfo>[
        TargetAppInfo(
          id: 'ashika',
          displayName: 'Ashika',
          packageName: 'com.codifi.dhanush',
          testName: 'Phase 1 - Login Automation',
          secret: TargetSecret.mpin,
          modules: <AutomationModule>[
            AutomationModule(id: 'watchlist', label: 'Watchlist and Search'),
            AutomationModule(id: 'home', label: 'Home'),
            AutomationModule(id: 'research', label: 'Research'),
            AutomationModule(id: 'portfolio', label: 'Portfolio'),
            AutomationModule(id: 'orders', label: 'Orders'),
          ],
        ),
        TargetAppInfo(
          id: 'tradion',
          displayName: 'Tradion',
          packageName: 'com.codifi.rmoney',
          testName: 'Tradion - Regression Automation',
          secret: TargetSecret.password,
          modules: <AutomationModule>[
            AutomationModule(id: 'login', label: 'Login'),
            AutomationModule(id: 'watchlist', label: 'Watchlist'),
            AutomationModule(id: 'watchlist_edit', label: 'Edit watchlist'),
            AutomationModule(id: 'watchlist_search', label: 'Search and add'),
            AutomationModule(id: 'orders', label: 'Orders'),
            AutomationModule(id: 'dashboard', label: 'Dashboard'),
            AutomationModule(id: 'portfolio', label: 'Portfolio'),
            AutomationModule(id: 'profile', label: 'Profile'),
          ],
        ),
      ];

  final List<String> allowedPackages;
  final String defaultTargetPackage;
  final String testName;

  /// Every app the driver can test, in the order the picker shows them.
  final List<TargetAppInfo> targets;

  /// True on Android 13+, where a sideloaded app's accessibility toggle is
  /// greyed out until "Allow restricted settings" is granted from App info.
  final bool requiresRestrictedSettingsGuidance;
}

/// The answer to the system "start recording" dialog.
enum ScreenRecordingConsent { granted, declined, unsupported }

/// An event streamed from the native driver during a run.
class AutomationEvent {
  const AutomationEvent({
    required this.name,
    required this.runId,
    this.stepId,
    this.stepIndex,
    this.stepName,
    this.status,
    this.durationMs,
    this.note,
    this.reason,
    this.totalSteps,
    this.declaredSteps,
    this.result,
  });

  /// One of: automation_started, step_started, step_passed, step_failed,
  /// automation_completed, automation_stopped.
  final String name;
  final String runId;
  final String? stepId;
  final int? stepIndex;
  final String? stepName;

  /// Present on step_passed / step_failed. A skipped step arrives as
  /// `step_passed` with a status of `skipped`, so the event vocabulary stays at
  /// the six documented names while the UI can still tell them apart.
  final StepStatus? status;
  final int? durationMs;
  final String? note;
  final String? reason;

  final int? totalSteps;
  final List<AutomationStep>? declaredSteps;
  final AutomationResult? result;

  factory AutomationEvent.fromMap(Map<Object?, Object?> map) {
    final name = (map['event'] as String?) ?? 'unknown';

    List<AutomationStep>? declared;
    final rawDeclared = map['steps'];
    if (rawDeclared is List) {
      declared = rawDeclared
          .whereType<Map<Object?, Object?>>()
          .map(AutomationStep.fromMap)
          .toList(growable: false);
    }

    AutomationResult? result;
    final rawResult = map['result'];
    if (rawResult is Map<Object?, Object?>) {
      result = AutomationResult.fromMap(rawResult);
    }

    return AutomationEvent(
      name: name,
      runId: (map['runId'] as String?) ?? 'unknown',
      stepId: map['stepId'] as String?,
      stepIndex: (map['stepIndex'] as num?)?.toInt(),
      stepName: map['name'] as String?,
      status: map.containsKey('status')
          ? StepStatus.parse(map['status'])
          : null,
      durationMs: (map['durationMs'] as num?)?.toInt(),
      note: map['note'] as String?,
      reason: map['reason'] as String?,
      totalSteps: (map['totalSteps'] as num?)?.toInt(),
      declaredSteps: declared,
      result: result,
    );
  }
}

/// Thin, typed wrapper over the two platform channels.
///
/// Credentials travel one way only — into [startLoginAutomation] — and are never
/// read back, logged, or returned by any method here.
class AutomationService {
  AutomationService({MethodChannel? commands, EventChannel? events})
    : _commands =
          commands ?? const MethodChannel('com.mobileautomation.tool/commands'),
      _events =
          events ?? const EventChannel('com.mobileautomation.tool/events');

  final MethodChannel _commands;
  final EventChannel _events;

  Stream<AutomationEvent>? _eventStream;

  /// Broadcast so the running-test screen and the dashboard can both listen.
  Stream<AutomationEvent> get events {
    return _eventStream ??= _events
        .receiveBroadcastStream()
        .where((dynamic e) => e is Map)
        .map((dynamic e) => AutomationEvent.fromMap(e as Map<Object?, Object?>))
        .asBroadcastStream();
  }

  Future<AccessibilityStatus> accessibilityStatus() async {
    final raw = await _invokeMap('isAccessibilityEnabled');
    if (raw == null) return const AccessibilityStatus.unknown();
    return AccessibilityStatus(
      enabledInSettings: raw['enabledInSettings'] == true,
      connected: raw['connected'] == true,
    );
  }

  Future<bool> openAccessibilitySettings() async {
    final result = await _invoke<bool>('openAccessibilitySettings');
    return result ?? false;
  }

  /// Opens this app's App info page, where Android 13+ hides the
  /// "Allow restricted settings" switch.
  Future<bool> openAppInfoSettings() async {
    final result = await _invoke<bool>('openAppInfoSettings');
    return result ?? false;
  }

  Future<TargetAppStatus> targetAppStatus(String packageName) async {
    final raw = await _invokeMap('isTargetAppInstalled', <String, Object?>{
      'packageName': packageName,
    });
    if (raw == null) return TargetAppStatus.unknown(packageName);
    return TargetAppStatus(
      packageName: (raw['packageName'] as String?) ?? packageName,
      installed: raw['installed'] == true,
      allowlisted: raw['allowlisted'] == true,
      mainActivity: raw['mainActivity'] as String?,
      versionName: raw['versionName'] as String?,
    );
  }

  Future<AutomationConfigInfo> config() async {
    final raw = await _invokeMap('getAutomationConfig');
    if (raw == null) return const AutomationConfigInfo.fallback();
    final packages = (raw['allowedPackages'] as List<Object?>? ?? const [])
        .whereType<String>()
        .toList(growable: false);
    final targets = (raw['targets'] as List<Object?>? ?? const [])
        .whereType<Map<Object?, Object?>>()
        .map(
          (t) => TargetAppInfo(
            id: (t['id'] as String?) ?? '',
            displayName: (t['displayName'] as String?) ?? '',
            packageName: (t['packageName'] as String?) ?? '',
            testName: (t['testName'] as String?) ?? 'Login Automation',
            secret: TargetSecret.parse(t['secret']),
            modules: (t['modules'] as List<Object?>? ?? const <Object?>[])
                .whereType<Map<Object?, Object?>>()
                .map(
                  (m) => AutomationModule(
                    id: (m['id'] as String?) ?? '',
                    label: (m['label'] as String?) ?? '',
                    description: (m['description'] as String?) ?? '',
                  ),
                )
                .where((m) => m.id.isNotEmpty && m.label.isNotEmpty)
                .toList(growable: false),
          ),
        )
        .where((t) => t.id.isNotEmpty && t.packageName.isNotEmpty)
        .toList(growable: false);
    return AutomationConfigInfo(
      targets: targets.isEmpty
          ? const AutomationConfigInfo.fallback().targets
          : targets,
      allowedPackages: packages,
      defaultTargetPackage:
          (raw['defaultTargetPackage'] as String?) ?? 'com.codifi.dhanush',
      testName: (raw['testName'] as String?) ?? 'Login Automation',
      requiresRestrictedSettingsGuidance:
          raw['requiresRestrictedSettingsGuidance'] == true,
    );
  }

  /// Starts a run. Returns null on success, or the reason it could not start.
  ///
  /// [mpin] is what Ashika asks for after the OTP and [password] what Tradion
  /// asks for before it; the driver reads whichever the target app needs.
  Future<String?> startLoginAutomation({
    required String targetPackage,
    required String clientId,
    required String otp,
    String mpin = '',
    String password = '',
    bool recordScreen = false,
    List<String>? modules,
  }) async {
    final raw = await _invokeMap('startLoginAutomation', <String, Object?>{
      'targetPackage': targetPackage,
      'clientId': clientId,
      'otp': otp,
      'mpin': mpin,
      'password': password,
      'recordScreen': recordScreen,
      'modules': modules,
    });
    if (raw == null) return 'The native automation driver did not respond.';
    if (raw['started'] == true) return null;
    return (raw['error'] as String?) ??
        'The automation run could not be started.';
  }

  /// Shows the system "start recording" dialog. True when the operator
  /// accepted. The grant is single use: start the run straight afterwards with
  /// `recordScreen: true`.
  Future<ScreenRecordingConsent> requestScreenRecording() async {
    try {
      final granted = await _commands.invokeMethod<bool>(
        'requestScreenRecording',
      );
      return granted == true
          ? ScreenRecordingConsent.granted
          : ScreenRecordingConsent.declined;
    } on MissingPluginException {
      // The Dart side is newer than the installed native code: typically a
      // hot reload/restart, which never updates Kotlin or the manifest.
      return ScreenRecordingConsent.unsupported;
    } on PlatformException {
      return ScreenRecordingConsent.declined;
    }
  }

  /// Whether the tool may post notifications (always true before Android 13).
  /// An install without the native part reports true, so setup never blocks on it.
  Future<bool> notificationsGranted() async =>
      (await _invoke<bool>('notificationPermission')) ?? true;

  /// Shows the notification permission dialog (Android 13+). False when denied,
  /// including when Android no longer shows the dialog at all.
  Future<bool> requestNotifications() async =>
      (await _invoke<bool>('requestNotificationPermission')) ?? false;

  /// Records two and a half seconds of screen and discards it. Call after
  /// [requestScreenRecording] returned granted: the grant is spent here.
  Future<({bool ok, String detail})> testScreenRecording() async {
    final raw = await _invokeMap('testScreenRecording');
    if (raw == null) {
      return (
        ok: false,
        detail:
            'This install has no screen-recording support. Install the tool '
            'again with a full build (a hot reload cannot add it).',
      );
    }
    return (
      ok: raw['ok'] == true,
      detail: (raw['detail'] as String?) ?? 'no detail',
    );
  }

  Future<bool> stopAutomation() async {
    final result = await _invoke<bool>('stopAutomation');
    return result ?? false;
  }

  Future<bool> isRunning() async {
    final result = await _invoke<bool>('isAutomationRunning');
    return result ?? false;
  }

  /// The most recent run held by the native driver, or null if it has none.
  /// Survives navigating away from the running screen, but not a process death —
  /// [ReportService] is what persists a run.
  Future<AutomationResult?> lastReport() async {
    final raw = await _invokeMap('getLastReport');
    if (raw == null) return null;
    return AutomationResult.fromMap(raw);
  }

  Future<T?> _invoke<T>(String method, [Map<String, Object?>? args]) async {
    try {
      return await _commands.invokeMethod<T>(method, args);
    } on PlatformException {
      return null;
    } on MissingPluginException {
      return null;
    }
  }

  Future<Map<Object?, Object?>?> _invokeMap(
    String method, [
    Map<String, Object?>? args,
  ]) => _invoke<Map<Object?, Object?>>(method, args);
}

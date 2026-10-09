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

/// Static configuration reported by the native driver.
class AutomationConfigInfo {
  const AutomationConfigInfo({
    required this.allowedPackages,
    required this.defaultTargetPackage,
    required this.testName,
    required this.requiresRestrictedSettingsGuidance,
  });

  const AutomationConfigInfo.fallback()
    : allowedPackages = const <String>['com.codifi.dhanush'],
      defaultTargetPackage = 'com.codifi.dhanush',
      testName = 'Phase 1 - Login Automation',
      requiresRestrictedSettingsGuidance = true;

  final List<String> allowedPackages;
  final String defaultTargetPackage;
  final String testName;

  /// True on Android 13+, where a sideloaded app's accessibility toggle is
  /// greyed out until "Allow restricted settings" is granted from App info.
  final bool requiresRestrictedSettingsGuidance;
}

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
  AutomationService({
    MethodChannel? commands,
    EventChannel? events,
  }) : _commands =
           commands ??
           const MethodChannel('com.mobileautomation.tool/commands'),
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
    return AutomationConfigInfo(
      allowedPackages: packages,
      defaultTargetPackage:
          (raw['defaultTargetPackage'] as String?) ?? 'com.codifi.dhanush',
      testName: (raw['testName'] as String?) ?? 'Login Automation',
      requiresRestrictedSettingsGuidance:
          raw['requiresRestrictedSettingsGuidance'] == true,
    );
  }

  /// Starts a run. Returns null on success, or the reason it could not start.
  Future<String?> startLoginAutomation({
    required String targetPackage,
    required String clientId,
    required String otp,
    required String mpin,
  }) async {
    final raw = await _invokeMap('startLoginAutomation', <String, Object?>{
      'targetPackage': targetPackage,
      'clientId': clientId,
      'otp': otp,
      'mpin': mpin,
    });
    if (raw == null) return 'The native automation driver did not respond.';
    if (raw['started'] == true) return null;
    return (raw['error'] as String?) ??
        'The automation run could not be started.';
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

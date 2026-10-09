import 'automation_step.dart';

/// The full outcome of one automation run, as produced by the native driver.
///
/// This object is what the reports are rendered from. It deliberately has no
/// field for a Client ID, an OTP or an MPIN — those values never leave the
/// platform call that started the run.
class AutomationResult {
  const AutomationResult({
    required this.runId,
    required this.testName,
    required this.targetPackage,
    required this.steps,
    this.targetMainActivity,
    this.targetVersionName,
    this.deviceModel = 'unknown',
    this.deviceManufacturer = 'unknown',
    this.androidVersion = 'unknown',
    this.sdkInt = 0,
    this.startedAtMs,
    this.finishedAtMs,
    this.durationMs,
    this.passed = false,
    this.stopped = false,
    this.failureMessage,
    this.screenshots = const <String, String>{},
    this.screenshotUnavailableReason,
  });

  final String runId;
  final String testName;
  final String targetPackage;
  final String? targetMainActivity;
  final String? targetVersionName;
  final String deviceModel;
  final String deviceManufacturer;
  final String androidVersion;
  final int sdkInt;
  final int? startedAtMs;
  final int? finishedAtMs;
  final int? durationMs;
  final bool passed;
  final bool stopped;
  final String? failureMessage;
  final List<AutomationStep> steps;

  /// label -> absolute path of a captured screenshot.
  final Map<String, String> screenshots;

  /// Why a screenshot could not be captured, when that happened. A missing
  /// screenshot never changes the verdict of a run.
  final String? screenshotUnavailableReason;

  DateTime? get startedAt => startedAtMs == null
      ? null
      : DateTime.fromMillisecondsSinceEpoch(startedAtMs!);

  DateTime? get finishedAt => finishedAtMs == null
      ? null
      : DateTime.fromMillisecondsSinceEpoch(finishedAtMs!);

  Duration? get duration =>
      durationMs == null ? null : Duration(milliseconds: durationMs!);

  int get totalSteps => steps.length;

  int get completedSteps => steps
      .where(
        (s) => s.status == StepStatus.passed || s.status == StepStatus.skipped,
      )
      .length;

  int get failedSteps =>
      steps.where((s) => s.status == StepStatus.failed).length;

  AutomationStep? get firstFailure =>
      steps.cast<AutomationStep?>().firstWhere(
        (s) => s!.status == StepStatus.failed,
        orElse: () => null,
      );

  String get verdict {
    if (passed) return 'PASSED';
    if (stopped) return 'STOPPED';
    return 'FAILED';
  }

  factory AutomationResult.fromMap(Map<Object?, Object?> map) {
    final rawSteps = (map['steps'] as List<Object?>? ?? const <Object?>[])
        .whereType<Map<Object?, Object?>>()
        .map(AutomationStep.fromMap)
        .toList(growable: false);

    final rawShots = map['screenshots'];
    final shots = <String, String>{};
    if (rawShots is Map) {
      for (final entry in rawShots.entries) {
        final key = entry.key;
        final value = entry.value;
        if (key is String && value is String) shots[key] = value;
      }
    }

    return AutomationResult(
      runId: (map['runId'] as String?) ?? 'unknown',
      testName: (map['testName'] as String?) ?? 'Login Automation',
      targetPackage: (map['targetPackage'] as String?) ?? 'unknown',
      targetMainActivity: map['targetMainActivity'] as String?,
      targetVersionName: map['targetVersionName'] as String?,
      deviceModel: (map['deviceModel'] as String?) ?? 'unknown',
      deviceManufacturer: (map['deviceManufacturer'] as String?) ?? 'unknown',
      androidVersion: (map['androidVersion'] as String?) ?? 'unknown',
      sdkInt: (map['sdkInt'] as num?)?.toInt() ?? 0,
      startedAtMs: (map['startedAtMs'] as num?)?.toInt(),
      finishedAtMs: (map['finishedAtMs'] as num?)?.toInt(),
      durationMs: (map['durationMs'] as num?)?.toInt(),
      passed: map['passed'] == true,
      stopped: map['stopped'] == true,
      failureMessage: map['failureMessage'] as String?,
      steps: rawSteps,
      screenshots: shots,
      screenshotUnavailableReason: map['screenshotUnavailableReason'] as String?,
    );
  }

  Map<String, Object?> toJson() => <String, Object?>{
    'runId': runId,
    'testName': testName,
    'targetPackage': targetPackage,
    'targetMainActivity': targetMainActivity,
    'targetVersionName': targetVersionName,
    'deviceManufacturer': deviceManufacturer,
    'deviceModel': deviceModel,
    'androidVersion': androidVersion,
    'sdkInt': sdkInt,
    'startedAtMs': startedAtMs,
    'finishedAtMs': finishedAtMs,
    'durationMs': durationMs,
    'passed': passed,
    'stopped': stopped,
    'verdict': verdict,
    'failureMessage': failureMessage,
    'totalSteps': totalSteps,
    'completedSteps': completedSteps,
    'steps': steps.map((s) => s.toJson()).toList(growable: false),
    'screenshots': screenshots,
    'screenshotUnavailableReason': screenshotUnavailableReason,
  };
}

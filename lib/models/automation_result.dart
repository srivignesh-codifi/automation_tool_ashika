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
    this.videoPath,
    this.recordingStartedAtMs,
    this.recordingNote,
    this.modulesRun = const <String>[],
    this.modulesSkipped = const <String>[],
    this.sessionBreaks = const <SessionBreak>[],
    this.feed,
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

  /// Absolute path of the screen recording of the whole run, when one exists.
  final String? videoPath;

  /// When the recording began; a step's start minus this is its position in
  /// the video.
  final int? recordingStartedAtMs;

  /// Why there is no video, or what the video does not cover.
  final String? recordingNote;

  /// The modules the operator left selected, and the ones switched off.
  final List<String> modulesRun;
  final List<String> modulesSkipped;

  /// Each time the target app's session expired mid-run (HTTP 401), where,
  /// which API, and whether the run signed in again and resumed.
  final List<SessionBreak> sessionBreaks;

  /// The market-data feed over the run: `mode` is `live` (measured) or `bod`
  /// (not measured). Null for a run that did not report it.
  final Map<String, Object?>? feed;

  bool get hasVideo => videoPath != null && videoPath!.isNotEmpty;

  /// Seconds into the video at which [step] began, or null when it is not
  /// covered (no video, or the step never started).
  double? videoOffsetOf(AutomationStep step) {
    final origin = recordingStartedAtMs;
    final at = step.startedAtMs;
    if (!hasVideo || origin == null || at == null) return null;
    final seconds = (at - origin) / 1000.0;
    return seconds < 0 ? 0 : seconds;
  }

  /// The same result pointing at [path], used once the report moves the video
  /// next to its report files.
  AutomationResult withVideoPath(String? path) => AutomationResult(
    runId: runId,
    testName: testName,
    targetPackage: targetPackage,
    steps: steps,
    targetMainActivity: targetMainActivity,
    targetVersionName: targetVersionName,
    deviceModel: deviceModel,
    deviceManufacturer: deviceManufacturer,
    androidVersion: androidVersion,
    sdkInt: sdkInt,
    startedAtMs: startedAtMs,
    finishedAtMs: finishedAtMs,
    durationMs: durationMs,
    passed: passed,
    stopped: stopped,
    failureMessage: failureMessage,
    screenshots: screenshots,
    screenshotUnavailableReason: screenshotUnavailableReason,
    videoPath: path,
    recordingStartedAtMs: recordingStartedAtMs,
    recordingNote: recordingNote,
    modulesRun: modulesRun,
    modulesSkipped: modulesSkipped,
    sessionBreaks: sessionBreaks,
    feed: feed,
  );

  DateTime? get startedAt => startedAtMs == null
      ? null
      : DateTime.fromMillisecondsSinceEpoch(startedAtMs!);

  DateTime? get finishedAt => finishedAtMs == null
      ? null
      : DateTime.fromMillisecondsSinceEpoch(finishedAtMs!);

  Duration? get duration =>
      durationMs == null ? null : Duration(milliseconds: durationMs!);

  int get totalSteps => steps.length;

  /// Steps that never ran, or were still running when the run ended.
  int get notRunSteps => steps
      .where(
        (s) => s.status == StepStatus.pending || s.status == StepStatus.running,
      )
      .length;

  int get skippedSteps =>
      steps.where((s) => s.status == StepStatus.skipped).length;

  int get passedSteps =>
      steps.where((s) => s.status == StepStatus.passed).length;

  /// How the run's server-facing steps came out; see [ApiSummary].
  ApiSummary get apiSummary => ApiSummary.of(steps);

  int get completedSteps => steps
      .where(
        (s) => s.status == StepStatus.passed || s.status == StepStatus.skipped,
      )
      .length;

  int get failedSteps =>
      steps.where((s) => s.status == StepStatus.failed).length;

  AutomationStep? get firstFailure => steps.cast<AutomationStep?>().firstWhere(
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
      screenshotUnavailableReason:
          map['screenshotUnavailableReason'] as String?,
      videoPath: map['videoPath'] as String?,
      recordingStartedAtMs: (map['recordingStartedAtMs'] as num?)?.toInt(),
      recordingNote: map['recordingNote'] as String?,
      modulesRun: _strings(map['modulesRun']),
      modulesSkipped: _strings(map['modulesSkipped']),
      sessionBreaks: map['sessionBreaks'] is List
          ? (map['sessionBreaks'] as List)
                .whereType<Map<Object?, Object?>>()
                .map(SessionBreak.fromMap)
                .toList(growable: false)
          : const <SessionBreak>[],
      feed: map['feed'] is Map
          ? Map<String, Object?>.from(
              (map['feed'] as Map).map((k, v) => MapEntry(k.toString(), v)),
            )
          : null,
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
    'apiSummary': apiSummary.toJson(),
    'steps': steps.map((s) => s.toJson()).toList(growable: false),
    'screenshots': screenshots,
    'screenshotUnavailableReason': screenshotUnavailableReason,
    'videoPath': videoPath,
    'videoFile': hasVideo ? videoPath!.split('/').last : null,
    'recordingStartedAtMs': recordingStartedAtMs,
    'recordingNote': recordingNote,
    'modulesRun': modulesRun,
    'modulesSkipped': modulesSkipped,
    'sessionBreaks': sessionBreaks.map((b) => b.toJson()).toList(),
    'feed': feed,
  };

  static List<String> _strings(Object? raw) => raw is List
      ? raw.whereType<String>().toList(growable: false)
      : const <String>[];
}

/// Roll-up of the per-step "API / data checks". Inferred from the screen, not
/// from network traffic: see [AutomationStep.apiKind].
class ApiSummary {
  const ApiSummary({
    this.reads = 0,
    this.readsOk = 0,
    this.readsEmpty = 0,
    this.readsSlow = 0,
    this.readsTimeout = 0,
    this.readsError = 0,
    this.readsNotChecked = 0,
    this.changes = 0,
    this.changesConfirmed = 0,
    this.changesNotConfirmed = 0,
    this.signIns = 0,
    this.signInsOk = 0,
    this.signInsFailed = 0,
  });

  factory ApiSummary.of(List<AutomationStep> steps) {
    var reads = 0, readsOk = 0, readsEmpty = 0, readsSlow = 0;
    var readsTimeout = 0, readsError = 0, readsNotChecked = 0;
    var changes = 0, confirmed = 0, notConfirmed = 0;
    var signIns = 0, signInsOk = 0, signInsFailed = 0;
    for (final step in steps) {
      final status = step.apiStatus;
      if (!step.hasApiCheck || status == 'skipped') continue;
      switch (step.apiKind) {
        case 'read':
          reads++;
          switch (status) {
            case 'ok':
              readsOk++;
            case 'empty':
              readsEmpty++;
            case 'slow':
              readsSlow++;
            case 'timeout':
              readsTimeout++;
            case 'error':
              readsError++;
            default:
              readsNotChecked++;
          }
        case 'write':
          changes++;
          if (status == 'confirmed') {
            confirmed++;
          } else {
            notConfirmed++;
          }
        case 'auth':
          signIns++;
          if (status == 'ok') {
            signInsOk++;
          } else {
            signInsFailed++;
          }
      }
    }
    return ApiSummary(
      reads: reads,
      readsOk: readsOk,
      readsEmpty: readsEmpty,
      readsSlow: readsSlow,
      readsTimeout: readsTimeout,
      readsError: readsError,
      readsNotChecked: readsNotChecked,
      changes: changes,
      changesConfirmed: confirmed,
      changesNotConfirmed: notConfirmed,
      signIns: signIns,
      signInsOk: signInsOk,
      signInsFailed: signInsFailed,
    );
  }

  final int reads;
  final int readsOk;
  final int readsEmpty;
  final int readsSlow;
  final int readsTimeout;
  final int readsError;
  final int readsNotChecked;
  final int changes;
  final int changesConfirmed;
  final int changesNotConfirmed;
  final int signIns;
  final int signInsOk;
  final int signInsFailed;

  bool get isEmpty => reads + changes + signIns == 0;

  /// Anything that points at a server or data problem.
  int get problems =>
      readsTimeout +
      readsError +
      readsNotChecked +
      changesNotConfirmed +
      signInsFailed;

  String get readsLine =>
      '$reads checked: $readsOk OK, $readsEmpty empty, $readsSlow slow, '
      '$readsTimeout timed out, $readsError error'
      '${readsNotChecked > 0 ? ', $readsNotChecked not checked' : ''}';

  String get changesLine =>
      '$changes checked: $changesConfirmed confirmed, $changesNotConfirmed not confirmed';

  String get signInsLine =>
      '$signIns checked: $signInsOk OK, $signInsFailed failed';

  Map<String, Object?> toJson() => <String, Object?>{
    'reads': <String, int>{
      'total': reads,
      'ok': readsOk,
      'empty': readsEmpty,
      'slow': readsSlow,
      'timeout': readsTimeout,
      'error': readsError,
      'notChecked': readsNotChecked,
    },
    'changes': <String, int>{
      'total': changes,
      'confirmed': changesConfirmed,
      'notConfirmed': changesNotConfirmed,
    },
    'signIns': <String, int>{
      'total': signIns,
      'ok': signInsOk,
      'failed': signInsFailed,
    },
    'problems': problems,
  };
}

/// One session expiry during a run: the step it interrupted, the API the app
/// reported as refused (HTTP 401), and what the run did next.
class SessionBreak {
  const SessionBreak({
    required this.number,
    required this.stepId,
    required this.stepName,
    this.code,
    required this.context,
    required this.screen,
    required this.api,
    this.atMs,
    required this.outcome,
  });

  final int number;
  final String stepId;
  final String stepName;
  final String? code;
  final String context;
  final String screen;
  final String api;
  final int? atMs;
  final String outcome;

  factory SessionBreak.fromMap(Map<Object?, Object?> map) => SessionBreak(
    number: (map['number'] as num?)?.toInt() ?? 0,
    stepId: (map['stepId'] as String?) ?? 'unknown',
    stepName: (map['stepName'] as String?) ?? 'unknown',
    code: map['code'] as String?,
    context: (map['context'] as String?) ?? '',
    screen: (map['screen'] as String?) ?? '',
    api: (map['api'] as String?) ?? '',
    atMs: (map['atMs'] as num?)?.toInt(),
    outcome: (map['outcome'] as String?) ?? '',
  );

  Map<String, Object?> toJson() => <String, Object?>{
    'number': number,
    'stepId': stepId,
    'stepName': stepName,
    'code': code,
    'context': context,
    'screen': screen,
    'api': api,
    'atMs': atMs,
    'outcome': outcome,
  };
}

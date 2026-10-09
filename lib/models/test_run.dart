import 'automation_result.dart';

/// A saved run: the summary shown in the history list plus the paths of the
/// three report files written for it.
class TestRun {
  const TestRun({
    required this.runId,
    required this.testName,
    required this.targetPackage,
    required this.startedAtMs,
    required this.passed,
    required this.stopped,
    required this.completedSteps,
    required this.totalSteps,
    required this.jsonPath,
    required this.htmlPath,
    required this.textPath,
    this.durationMs,
  });

  final String runId;
  final String testName;
  final String targetPackage;
  final int startedAtMs;
  final int? durationMs;
  final bool passed;
  final bool stopped;
  final int completedSteps;
  final int totalSteps;
  final String jsonPath;
  final String htmlPath;
  final String textPath;

  DateTime get startedAt => DateTime.fromMillisecondsSinceEpoch(startedAtMs);

  Duration? get duration =>
      durationMs == null ? null : Duration(milliseconds: durationMs!);

  String get verdict {
    if (passed) return 'PASSED';
    if (stopped) return 'STOPPED';
    return 'FAILED';
  }

  factory TestRun.fromResult(
    AutomationResult result, {
    required String jsonPath,
    required String htmlPath,
    required String textPath,
  }) {
    return TestRun(
      runId: result.runId,
      testName: result.testName,
      targetPackage: result.targetPackage,
      startedAtMs: result.startedAtMs ?? DateTime.now().millisecondsSinceEpoch,
      durationMs: result.durationMs,
      passed: result.passed,
      stopped: result.stopped,
      completedSteps: result.completedSteps,
      totalSteps: result.totalSteps,
      jsonPath: jsonPath,
      htmlPath: htmlPath,
      textPath: textPath,
    );
  }

  factory TestRun.fromJson(Map<String, Object?> map) {
    return TestRun(
      runId: (map['runId'] as String?) ?? 'unknown',
      testName: (map['testName'] as String?) ?? 'Login Automation',
      targetPackage: (map['targetPackage'] as String?) ?? 'unknown',
      startedAtMs: (map['startedAtMs'] as num?)?.toInt() ?? 0,
      durationMs: (map['durationMs'] as num?)?.toInt(),
      passed: map['passed'] == true,
      stopped: map['stopped'] == true,
      completedSteps: (map['completedSteps'] as num?)?.toInt() ?? 0,
      totalSteps: (map['totalSteps'] as num?)?.toInt() ?? 0,
      jsonPath: (map['jsonPath'] as String?) ?? '',
      htmlPath: (map['htmlPath'] as String?) ?? '',
      textPath: (map['textPath'] as String?) ?? '',
    );
  }

  Map<String, Object?> toJson() => <String, Object?>{
    'runId': runId,
    'testName': testName,
    'targetPackage': targetPackage,
    'startedAtMs': startedAtMs,
    'durationMs': durationMs,
    'passed': passed,
    'stopped': stopped,
    'completedSteps': completedSteps,
    'totalSteps': totalSteps,
    'jsonPath': jsonPath,
    'htmlPath': htmlPath,
    'textPath': textPath,
  };
}

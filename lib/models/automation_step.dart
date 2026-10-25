/// Status of one checklist item.
enum StepStatus {
  pending,
  running,
  passed,
  failed,
  skipped;

  static StepStatus parse(Object? wire) {
    switch (wire) {
      case 'running':
        return StepStatus.running;
      case 'passed':
        return StepStatus.passed;
      case 'failed':
        return StepStatus.failed;
      case 'skipped':
        return StepStatus.skipped;
      default:
        return StepStatus.pending;
    }
  }

  String get wire => name;

  String get label => switch (this) {
    StepStatus.pending => 'Pending',
    StepStatus.running => 'Running',
    StepStatus.passed => 'Passed',
    StepStatus.failed => 'Failed',
    StepStatus.skipped => 'Skipped',
  };

  /// Glyph used in the plain-text checklist.
  String get glyph => switch (this) {
    StepStatus.passed => '✓', // ✓
    StepStatus.failed => '✕', // ✕
    StepStatus.skipped => '–', // –
    StepStatus.running => '·', // ·
    StepStatus.pending => '·',
  };

  bool get isTerminal =>
      this == StepStatus.passed ||
      this == StepStatus.failed ||
      this == StepStatus.skipped;
}

/// One line of the automation checklist.
///
/// Never holds a credential: the native driver builds [note] and [failureReason]
/// from fixed strings and locator names only.
class AutomationStep {
  const AutomationStep({
    required this.id,
    required this.name,
    this.status = StepStatus.pending,
    this.startedAtMs,
    this.finishedAtMs,
    this.durationMs,
    this.failureReason,
    this.note,
    this.apiKind,
    this.apiStatus,
    this.apiDetail,
    this.apiMs,
    this.apiChecks = 0,
    this.code,
    this.expected,
    this.severity,
    this.skipKind,
    this.module,
    this.cause,
    this.evidence,
    this.recommendation,
    this.cleanup,
  });

  final String id;
  final String name;
  final StepStatus status;
  final int? startedAtMs;
  final int? finishedAtMs;
  final int? durationMs;
  final String? failureReason;
  final String? note;

  /// The step's "API / data check", inferred from the screen (the tool does not
  /// see network traffic): `read`, `write` or `auth`, how it came out, a short
  /// explanation, the longest load time recorded, and how many loads were checked.
  final String? apiKind;
  final String? apiStatus;
  final String? apiDetail;
  final int? apiMs;
  final int apiChecks;

  /// Test-case metadata for steps that are formal cases (the Watchlist suite): its id such as `WL-06`, the
  /// expected result, the severity a defect would carry, and why a skipped case was skipped
  /// (`blocked`, `not_applicable` or `not_tested`).
  final String? code;
  final String? expected;
  final String? severity;
  final String? skipKind;

  /// The area (module id) the step belongs to; null for the sign-in / sign-out steps every run has.
  final String? module;

  /// What a failed or blocked case turned out to be: `app_defect`, `unconfirmed`, `automation`, `accessibility`,
  /// `api_data` or `test_data`; the UI hierarchy at that moment; what the app would need so the case can be verified;
  /// and whether the case put back what it changed.
  final String? cause;
  final String? evidence;
  final String? recommendation;
  final String? cleanup;

  /// The cause in the report's fixed vocabulary.
  String get causeTag => switch (cause) {
    'app_defect' => 'APPLICATION',
    'automation' => 'AUTOMATION',
    'accessibility' => 'ACCESSIBILITY',
    'api_data' => 'API',
    'test_data' => 'TEST DATA',
    'unconfirmed' => 'UNKNOWN',
    _ => '',
  };

  String get causeLabel => switch (cause) {
    'app_defect' => 'Application defect',
    'unconfirmed' => 'Needs review (expected content not found in readable text)',
    'automation' => 'Automation / locator',
    'accessibility' => 'Accessibility limitation',
    'api_data' => 'API / data dependency',
    'test_data' => 'Test data / state',
    _ => '',
  };

  bool get isCase => code != null;

  /// PASS / FAIL / BLOCKED / NOT APPLICABLE / NOT TESTED, the vocabulary of a test report.
  String get outcomeLabel => switch (status) {
    StepStatus.passed => 'PASS',
    StepStatus.failed => 'FAIL',
    StepStatus.skipped => switch (skipKind) {
      'blocked' => 'BLOCKED',
      'not_applicable' => 'NOT APPLICABLE',
      'not_tested' => 'NOT TESTED',
      _ => 'SKIPPED',
    },
    StepStatus.running => 'RUNNING',
    StepStatus.pending => 'NOT TESTED',
  };

  bool get hasApiCheck => apiKind != null && apiStatus != null;

  String get apiKindLabel => switch (apiKind) {
    'read' => 'read',
    'write' => 'write',
    'auth' => 'auth',
    _ => '',
  };

  String get apiStatusLabel => switch (apiStatus) {
    'ok' => 'OK',
    'empty' => 'EMPTY',
    'slow' => 'SLOW',
    'timeout' => 'TIMEOUT',
    'error' => 'ERROR',
    'confirmed' => 'CONFIRMED',
    'not_confirmed' => 'NOT CONFIRMED',
    'not_checked' => 'NOT CHECKED',
    'skipped' => 'SKIPPED',
    _ => '',
  };

  DateTime? get startedAt => startedAtMs == null
      ? null
      : DateTime.fromMillisecondsSinceEpoch(startedAtMs!);

  DateTime? get finishedAt => finishedAtMs == null
      ? null
      : DateTime.fromMillisecondsSinceEpoch(finishedAtMs!);

  Duration? get duration =>
      durationMs == null ? null : Duration(milliseconds: durationMs!);

  AutomationStep copyWith({
    StepStatus? status,
    int? startedAtMs,
    int? finishedAtMs,
    int? durationMs,
    String? failureReason,
    String? note,
  }) {
    return AutomationStep(
      apiKind: apiKind,
      apiStatus: apiStatus,
      apiDetail: apiDetail,
      apiMs: apiMs,
      apiChecks: apiChecks,
      code: code,
      expected: expected,
      severity: severity,
      skipKind: skipKind,
      module: module,
      cause: cause,
      evidence: evidence,
      recommendation: recommendation,
      cleanup: cleanup,
      id: id,
      name: name,
      status: status ?? this.status,
      startedAtMs: startedAtMs ?? this.startedAtMs,
      finishedAtMs: finishedAtMs ?? this.finishedAtMs,
      durationMs: durationMs ?? this.durationMs,
      failureReason: failureReason ?? this.failureReason,
      note: note ?? this.note,
    );
  }

  factory AutomationStep.fromMap(Map<Object?, Object?> map) {
    return AutomationStep(
      id: (map['id'] as String?) ?? 'unknown',
      name: (map['name'] as String?) ?? 'Unnamed step',
      status: StepStatus.parse(map['status']),
      startedAtMs: (map['startedAtMs'] as num?)?.toInt(),
      finishedAtMs: (map['finishedAtMs'] as num?)?.toInt(),
      durationMs: (map['durationMs'] as num?)?.toInt(),
      failureReason: map['failureReason'] as String?,
      note: map['note'] as String?,
      apiKind: map['apiKind'] as String?,
      apiStatus: map['apiStatus'] as String?,
      apiDetail: map['apiDetail'] as String?,
      apiMs: (map['apiMs'] as num?)?.toInt(),
      apiChecks: (map['apiChecks'] as num?)?.toInt() ?? 0,
      code: map['code'] as String?,
      expected: map['expected'] as String?,
      severity: map['severity'] as String?,
      skipKind: map['skipKind'] as String?,
      module: map['module'] as String?,
      cause: map['cause'] as String?,
      evidence: map['evidence'] as String?,
      recommendation: map['recommendation'] as String?,
      cleanup: map['cleanup'] as String?,
    );
  }

  Map<String, Object?> toJson() => <String, Object?>{
    'id': id,
    'name': name,
    'status': status.wire,
    'startedAtMs': startedAtMs,
    'finishedAtMs': finishedAtMs,
    'durationMs': durationMs,
    'failureReason': failureReason,
    'note': note,
    'apiKind': apiKind,
    'apiStatus': apiStatus,
    'apiDetail': apiDetail,
    'apiMs': apiMs,
    'apiChecks': apiChecks,
    'code': code,
    'expected': expected,
    'severity': severity,
    'skipKind': skipKind,
    'module': module,
    'cause': cause,
    'evidence': evidence,
    'recommendation': recommendation,
    'cleanup': cleanup,
  };
}

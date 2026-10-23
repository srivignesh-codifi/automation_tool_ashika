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
  };
}

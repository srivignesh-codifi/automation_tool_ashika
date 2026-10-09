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
  });

  final String id;
  final String name;
  final StepStatus status;
  final int? startedAtMs;
  final int? finishedAtMs;
  final int? durationMs;
  final String? failureReason;
  final String? note;

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
  };
}

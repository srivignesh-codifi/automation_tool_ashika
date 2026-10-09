import 'package:flutter/material.dart';

import '../models/automation_step.dart';
import 'status_indicator.dart';

/// One checklist row: status glyph, name, timings, and the detail or the reason.
class TestStepTile extends StatelessWidget {
  const TestStepTile({
    required this.step,
    required this.index,
    super.key,
    this.showTimings = true,
  });

  final AutomationStep step;
  final int index;
  final bool showTimings;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final style = StatusStyle.of(step.status, scheme);
    final detail = step.failureReason ?? step.note;

    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(12),
        color: step.status == StepStatus.failed
            ? scheme.errorContainer.withValues(alpha: 0.28)
            : scheme.surfaceContainerHighest.withValues(alpha: 0.35),
        border: step.status == StepStatus.running
            ? Border.all(color: scheme.primary, width: 1.4)
            : null,
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 24,
            height: 24,
            child: step.status == StepStatus.running
                ? const Padding(
                    padding: EdgeInsets.all(3),
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : Icon(style.icon, size: 21, color: style.color),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Expanded(
                      child: Text(
                        '${index + 1}. ${step.name}',
                        style: theme.textTheme.bodyMedium?.copyWith(
                          fontWeight: FontWeight.w600,
                          color: step.status == StepStatus.pending
                              ? scheme.onSurfaceVariant
                              : null,
                        ),
                      ),
                    ),
                    if (showTimings && step.duration != null)
                      Text(
                        _duration(step.duration!),
                        style: theme.textTheme.labelSmall?.copyWith(
                          color: scheme.onSurfaceVariant,
                          fontFeatures: const [FontFeature.tabularFigures()],
                        ),
                      ),
                  ],
                ),
                if (detail != null && detail.isNotEmpty) ...[
                  const SizedBox(height: 3),
                  Text(
                    detail,
                    style: theme.textTheme.bodySmall?.copyWith(
                      color: step.status == StepStatus.failed
                          ? scheme.error
                          : scheme.onSurfaceVariant,
                    ),
                  ),
                ],
                if (showTimings && step.startedAt != null) ...[
                  const SizedBox(height: 3),
                  Text(
                    _timings(step),
                    style: theme.textTheme.labelSmall?.copyWith(
                      color: scheme.outline,
                    ),
                  ),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }

  static String _two(int v) => v.toString().padLeft(2, '0');

  static String _clock(DateTime t) =>
      '${_two(t.hour)}:${_two(t.minute)}:${_two(t.second)}';

  static String _timings(AutomationStep step) {
    final start = step.startedAt;
    if (start == null) return '';
    final end = step.finishedAt;
    if (end == null) return 'started ${_clock(start)}';
    return '${_clock(start)} → ${_clock(end)}';
  }

  static String _duration(Duration d) {
    if (d.inSeconds < 1) return '${d.inMilliseconds}ms';
    if (d.inMinutes < 1) return '${(d.inMilliseconds / 1000).toStringAsFixed(1)}s';
    return '${d.inMinutes}m ${d.inSeconds % 60}s';
  }
}

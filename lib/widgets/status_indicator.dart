import 'package:flutter/material.dart';

import '../models/automation_step.dart';

/// Colour and icon vocabulary shared by every status surface in the tool.
class StatusStyle {
  const StatusStyle(this.color, this.icon);

  final Color color;
  final IconData icon;

  static StatusStyle of(StepStatus status, ColorScheme scheme) {
    return switch (status) {
      StepStatus.passed => const StatusStyle(
        Color(0xFF1B873F),
        Icons.check_circle,
      ),
      StepStatus.failed => StatusStyle(scheme.error, Icons.cancel),
      StepStatus.skipped => const StatusStyle(
        Color(0xFF9A7B10),
        Icons.remove_circle_outline,
      ),
      StepStatus.running => StatusStyle(scheme.primary, Icons.autorenew),
      StepStatus.pending => StatusStyle(
        scheme.outline,
        Icons.radio_button_unchecked,
      ),
    };
  }
}

/// A labelled state row: an icon, a title, and a one-line explanation.
class StatusIndicator extends StatelessWidget {
  const StatusIndicator({
    required this.title,
    required this.detail,
    required this.ok,
    super.key,
    this.busy = false,
    this.trailing,
  });

  final String title;
  final String detail;
  final bool ok;
  final bool busy;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final color = ok ? const Color(0xFF1B873F) : scheme.error;

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      decoration: BoxDecoration(
        color: scheme.surfaceContainerHighest.withValues(alpha: 0.45),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        children: [
          SizedBox(
            width: 22,
            height: 22,
            child: busy
                ? const Padding(
                    padding: EdgeInsets.all(2),
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : Icon(
                    ok ? Icons.check_circle : Icons.error_outline,
                    size: 22,
                    color: color,
                  ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                    fontWeight: FontWeight.w600,
                  ),
                ),
                Text(
                  detail,
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(
                    color: scheme.onSurfaceVariant,
                  ),
                ),
              ],
            ),
          ),
          if (trailing != null) ...[const SizedBox(width: 8), trailing!],
        ],
      ),
    );
  }
}

/// The PASSED / FAILED / STOPPED badge.
class VerdictBadge extends StatelessWidget {
  const VerdictBadge({required this.verdict, super.key});

  final String verdict;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final color = switch (verdict) {
      'PASSED' => const Color(0xFF1B873F),
      'STOPPED' => const Color(0xFF9A7B10),
      _ => scheme.error,
    };
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
      decoration: BoxDecoration(
        color: color,
        borderRadius: BorderRadius.circular(999),
      ),
      child: Text(
        verdict,
        style: const TextStyle(
          color: Colors.white,
          fontWeight: FontWeight.w700,
          letterSpacing: 0.6,
        ),
      ),
    );
  }
}

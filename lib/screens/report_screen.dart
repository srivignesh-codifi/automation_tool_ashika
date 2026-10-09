import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../models/automation_result.dart';
import '../models/test_run.dart';
import '../services/report_service.dart';
import '../widgets/status_indicator.dart';
import '../widgets/test_step_tile.dart';

/// A saved run's checklist report, with export and share.
class ReportScreen extends StatefulWidget {
  const ReportScreen({
    required this.reports,
    required this.run,
    super.key,
    this.checklistText,
  });

  final ReportService reports;
  final TestRun run;

  /// Pre-read plain-text checklist, when the caller already had it.
  final String? checklistText;

  @override
  State<ReportScreen> createState() => _ReportScreenState();
}

class _ReportScreenState extends State<ReportScreen> {
  AutomationResult? _result;
  String? _text;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final json = await widget.reports.readReportFile(widget.run.jsonPath);
    final text =
        widget.checklistText ??
        await widget.reports.readReportFile(widget.run.textPath);

    AutomationResult? result;
    if (json != null) {
      try {
        final decoded = jsonDecode(json);
        if (decoded is Map<String, Object?>) {
          result = AutomationResult.fromMap(decoded);
        }
      } catch (_) {
        result = null;
      }
    }

    if (!mounted) return;
    setState(() {
      _result = result;
      _text = text;
      _loading = false;
    });
  }

  Future<void> _share() async {
    await widget.reports.share(widget.run);
  }

  Future<void> _copyText() async {
    final text = _text;
    if (text == null) return;
    await Clipboard.setData(ClipboardData(text: text));
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('Checklist copied.')),
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final result = _result;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Report'),
        actions: [
          IconButton(
            onPressed: _share,
            icon: const Icon(Icons.share_outlined),
            tooltip: 'Share Report',
          ),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: const EdgeInsets.all(16),
              children: [
                Row(
                  children: [
                    Expanded(
                      child: Text(
                        result?.testName ?? widget.run.testName,
                        style: theme.textTheme.titleMedium,
                      ),
                    ),
                    VerdictBadge(
                      verdict: result?.verdict ?? widget.run.verdict,
                    ),
                  ],
                ),
                const SizedBox(height: 4),
                Text(
                  'Completed '
                  '${result?.completedSteps ?? widget.run.completedSteps}'
                  '/${result?.totalSteps ?? widget.run.totalSteps} steps',
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
                const SizedBox(height: 18),

                if (result != null) ...[
                  _MetaTable(result: result),
                  const SizedBox(height: 20),
                  Text('Checklist', style: theme.textTheme.titleSmall),
                  const SizedBox(height: 10),
                  for (var i = 0; i < result.steps.length; i++)
                    TestStepTile(step: result.steps[i], index: i),
                  if (result.failureMessage != null) ...[
                    const SizedBox(height: 8),
                    Card(
                      color: theme.colorScheme.errorContainer.withValues(
                        alpha: 0.5,
                      ),
                      child: Padding(
                        padding: const EdgeInsets.all(12),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              result.passed ? 'Note' : 'Failure',
                              style: theme.textTheme.titleSmall,
                            ),
                            const SizedBox(height: 4),
                            Text(result.failureMessage!),
                          ],
                        ),
                      ),
                    ),
                  ],
                  const SizedBox(height: 20),
                  _Screenshots(result: result),
                ] else
                  Text(
                    'The JSON report for this run could not be read. The plain '
                    'text checklist is below.',
                    style: theme.textTheme.bodySmall?.copyWith(
                      color: theme.colorScheme.error,
                    ),
                  ),

                const SizedBox(height: 20),
                Row(
                  children: [
                    Expanded(
                      child: Text(
                        'Plain text checklist',
                        style: theme.textTheme.titleSmall,
                      ),
                    ),
                    TextButton.icon(
                      onPressed: _text == null ? null : _copyText,
                      icon: const Icon(Icons.copy_all_outlined, size: 18),
                      label: const Text('Copy'),
                    ),
                  ],
                ),
                const SizedBox(height: 6),
                Container(
                  width: double.infinity,
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: theme.colorScheme.surfaceContainerHighest.withValues(
                      alpha: 0.4,
                    ),
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: SingleChildScrollView(
                    scrollDirection: Axis.horizontal,
                    child: Text(
                      _text ?? 'Not available.',
                      style: const TextStyle(
                        fontFamily: 'monospace',
                        fontSize: 12.5,
                        height: 1.45,
                      ),
                    ),
                  ),
                ),

                const SizedBox(height: 20),
                Text('Report files', style: theme.textTheme.titleSmall),
                const SizedBox(height: 6),
                _PathRow(label: 'JSON', path: widget.run.jsonPath),
                _PathRow(label: 'HTML', path: widget.run.htmlPath),
                _PathRow(label: 'Text', path: widget.run.textPath),

                const SizedBox(height: 20),
                FilledButton.icon(
                  onPressed: _share,
                  icon: const Icon(Icons.share_outlined),
                  label: const Text('Share Report'),
                ),
                const SizedBox(height: 12),
                Text(
                  'Reports contain no Client ID, OTP or MPIN.',
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
                const SizedBox(height: 24),
              ],
            ),
    );
  }
}

class _MetaTable extends StatelessWidget {
  const _MetaTable({required this.result});

  final AutomationResult result;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final rows = <(String, String)>[
      ('Target package', result.targetPackage),
      ('Main activity', result.targetMainActivity ?? '—'),
      ('Target version', result.targetVersionName ?? '—'),
      ('Device', '${result.deviceManufacturer} ${result.deviceModel}'),
      ('Android', '${result.androidVersion} (API ${result.sdkInt})'),
      ('Started', _stamp(result.startedAt)),
      ('Ended', _stamp(result.finishedAt)),
      ('Duration', _duration(result.duration)),
      ('Run ID', result.runId),
    ];

    return Column(
      children: [
        for (final (label, value) in rows)
          Padding(
            padding: const EdgeInsets.only(bottom: 6),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                SizedBox(
                  width: 116,
                  child: Text(
                    label,
                    style: theme.textTheme.bodySmall?.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
                    ),
                  ),
                ),
                Expanded(
                  child: Text(value, style: theme.textTheme.bodySmall),
                ),
              ],
            ),
          ),
      ],
    );
  }

  static String _two(int v) => v.toString().padLeft(2, '0');

  static String _stamp(DateTime? t) => t == null
      ? '—'
      : '${t.year}-${_two(t.month)}-${_two(t.day)} '
            '${_two(t.hour)}:${_two(t.minute)}:${_two(t.second)}';

  static String _duration(Duration? d) {
    if (d == null) return '—';
    if (d.inSeconds < 1) return '${d.inMilliseconds} ms';
    if (d.inMinutes < 1) return '${(d.inMilliseconds / 1000).toStringAsFixed(1)} s';
    return '${d.inMinutes}m ${d.inSeconds % 60}s';
  }
}

class _Screenshots extends StatelessWidget {
  const _Screenshots({required this.result});

  final AutomationResult result;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    if (result.screenshots.isEmpty) {
      final reason = result.screenshotUnavailableReason;
      if (reason == null) return const SizedBox.shrink();
      return Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('Screenshots', style: theme.textTheme.titleSmall),
          const SizedBox(height: 4),
          Text(
            'Unavailable — $reason',
            style: theme.textTheme.bodySmall?.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
        ],
      );
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('Screenshots', style: theme.textTheme.titleSmall),
        const SizedBox(height: 6),
        for (final entry in result.screenshots.entries)
          _PathRow(label: entry.key, path: entry.value),
      ],
    );
  }
}

class _PathRow extends StatelessWidget {
  const _PathRow({required this.label, required this.path});

  final String label;
  final String path;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 62,
            child: Text(
              label,
              style: theme.textTheme.bodySmall?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
          ),
          Expanded(
            child: SelectableText(
              path.isEmpty ? '—' : path,
              style: const TextStyle(fontFamily: 'monospace', fontSize: 11.5),
            ),
          ),
        ],
      ),
    );
  }
}

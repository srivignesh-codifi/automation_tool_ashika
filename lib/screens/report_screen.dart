import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:webview_flutter/webview_flutter.dart';

import '../models/automation_result.dart';
import '../models/report_modules.dart';
import '../models/test_run.dart';
import '../services/report_service.dart';
import '../services/report_upload_service.dart';
import '../widgets/send_to_admin_button.dart';
import '../widgets/status_indicator.dart';
import '../widgets/test_step_tile.dart';

/// A saved run's checklist report, with export and share.
class ReportScreen extends StatefulWidget {
  const ReportScreen({
    required this.reports,
    required this.run,
    super.key,
    this.checklistText,
    this.uploader,
  });

  final ReportService reports;
  final TestRun run;

  /// Sends the report to the admin server; a default one when not given.
  final ReportUploadService? uploader;

  /// Pre-read plain-text checklist, when the caller already had it.
  final String? checklistText;

  @override
  State<ReportScreen> createState() => _ReportScreenState();
}

class _ReportScreenState extends State<ReportScreen> {
  AutomationResult? _result;
  String? _text;
  bool _loading = true;
  bool _downloading = false;

  /// The checklist filter: what was typed, and the module picked (null: every module).
  String _query = '';
  String? _module;
  final TextEditingController _search = TextEditingController();

  /// Show the saved HTML report in a web view instead of the native checklist.
  bool _preview = false;
  WebViewController? _web;

  @override
  void dispose() {
    _search.dispose();
    super.dispose();
  }

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

  /// Puts the whole text report on the clipboard (the same text as the .txt file).
  Future<void> _copy() async {
    final text =
        _text ?? await widget.reports.readReportFile(widget.run.textPath);
    if (!mounted) return;
    final messenger = ScaffoldMessenger.of(context);
    if (text == null || text.isEmpty) {
      messenger.showSnackBar(
        const SnackBar(content: Text('The report text could not be read.')),
      );
      return;
    }
    await Clipboard.setData(ClipboardData(text: text));
    messenger.showSnackBar(
      const SnackBar(content: Text('Full report copied to the clipboard.')),
    );
  }

  Future<void> _share() async {
    await widget.reports.share(widget.run);
  }

  Future<void> _download() async {
    if (_downloading) return;
    setState(() => _downloading = true);
    try {
      final downloaded = await widget.reports.download(widget.run);
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(downloaded.message)));
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('The report files could not be downloaded.'),
        ),
      );
    } finally {
      if (mounted) setState(() => _downloading = false);
    }
  }

  Future<void> _copyText() async {
    final text = _text;
    if (text == null) return;
    await Clipboard.setData(ClipboardData(text: text));
    if (!mounted) return;
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(const SnackBar(content: Text('Checklist copied.')));
  }

  /// The summary by module, the search and module filter, and the checklist rows that pass them.
  List<Widget> _checklist(AutomationResult result, ThemeData theme) {
    final stats = summarizeModules(result.steps);
    final shown = <int>[
      for (var i = 0; i < result.steps.length; i++)
        if ((_module == null || moduleLabelOf(result.steps[i]) == _module) &&
            stepMatchesQuery(result.steps[i], _query))
          i,
    ];
    void pick(String? module) =>
        setState(() => _module = _module == module ? null : module);
    return [
      if (stats.length > 1) ...[
        Text('Summary by module', style: theme.textTheme.titleSmall),
        const SizedBox(height: 8),
        for (final m in stats)
          _ModuleSummaryRow(
            key: ValueKey<String>('summary_${m.label}'),
            stats: m,
            selected: _module == m.label,
            onTap: () => pick(m.label),
          ),
        const SizedBox(height: 14),
      ],
      Text('Checklist', style: theme.textTheme.titleSmall),
      const SizedBox(height: 8),
      TextField(
        key: const ValueKey<String>('checklist_search'),
        controller: _search,
        decoration: InputDecoration(
          isDense: true,
          hintText: 'Search the checklist (name, id, detail)',
          prefixIcon: const Icon(Icons.search),
          suffixIcon: _query.isEmpty
              ? null
              : IconButton(
                  icon: const Icon(Icons.clear),
                  tooltip: 'Clear',
                  onPressed: () {
                    _search.clear();
                    setState(() => _query = '');
                  },
                ),
          border: const OutlineInputBorder(),
        ),
        onChanged: (v) => setState(() => _query = v),
      ),
      if (stats.length > 1) ...[
        const SizedBox(height: 8),
        Wrap(
          spacing: 6,
          runSpacing: 0,
          children: [
            ChoiceChip(
              key: const ValueKey<String>('module_chip_all'),
              label: Text('All (${result.steps.length})'),
              selected: _module == null,
              onSelected: (_) => setState(() => _module = null),
            ),
            for (final m in stats)
              ChoiceChip(
                key: ValueKey<String>('module_chip_${m.label}'),
                label: Text('${m.label} (${m.total})'),
                selected: _module == m.label,
                onSelected: (_) => pick(m.label),
              ),
          ],
        ),
      ],
      const SizedBox(height: 6),
      Text(
        'Showing ${shown.length} of ${result.steps.length} steps',
        style: theme.textTheme.bodySmall?.copyWith(
          color: theme.colorScheme.onSurfaceVariant,
        ),
      ),
      const SizedBox(height: 8),
      if (shown.isEmpty)
        const Padding(
          padding: EdgeInsets.symmetric(vertical: 16),
          child: Center(child: Text('No step matches.')),
        ),
      // The number is the step's place in the whole run, whatever the filter hides.
      for (final i in shown) TestStepTile(step: result.steps[i], index: i),
    ];
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
            onPressed: _copy,
            icon: const Icon(Icons.copy_all_outlined),
            tooltip: 'Copy full report',
          ),
          IconButton(
            onPressed: _downloading ? null : _download,
            icon: _downloading
                ? const SizedBox.square(
                    dimension: 20,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.download_outlined),
            tooltip: 'Download Reports',
          ),
          IconButton(
            onPressed: _share,
            icon: const Icon(Icons.share_outlined),
            tooltip: 'Share Report',
          ),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _withViewSwitch(
              ListView(
                // Edge-to-edge (Android 15+): the list ends above the navigation bar while
                // the background still runs under it.
                padding: EdgeInsets.fromLTRB(
                  16,
                  16,
                  16,
                  16 + MediaQuery.paddingOf(context).bottom,
                ),
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
                    ..._checklist(result, theme),
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
                    if (result.recordingNote != null) ...[
                      const SizedBox(height: 12),
                      Text(
                        'Screen recording',
                        style: theme.textTheme.titleSmall,
                      ),
                      const SizedBox(height: 4),
                      Text(
                        result.recordingNote!,
                        style: theme.textTheme.bodySmall?.copyWith(
                          color: theme.colorScheme.onSurfaceVariant,
                        ),
                      ),
                    ],
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
                      color: theme.colorScheme.surfaceContainerHighest
                          .withValues(alpha: 0.4),
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
                  if (widget.run.videoPath.isNotEmpty)
                    _PathRow(label: 'Video', path: widget.run.videoPath),
                  if (widget.run.zipPath.isNotEmpty)
                    _PathRow(label: 'Summary zip', path: widget.run.zipPath),
                  if (widget.run.zipPath.isNotEmpty)
                    Text(
                      'Kept in app storage for sending to admin; not copied to Downloads.',
                      style: theme.textTheme.bodySmall,
                    ),
                  if (widget.run.zipFolder.isNotEmpty)
                    Text(
                      'Copy in ${widget.run.zipFolder}: text report, video and '
                      'failed-step screenshots.',
                      style: theme.textTheme.bodySmall,
                    )
                  else if (widget.run.zipError.isNotEmpty)
                    Text(
                      'Summary zip not saved: ${widget.run.zipError}',
                      style: theme.textTheme.bodySmall,
                    ),

                  const SizedBox(height: 20),
                  FilledButton.icon(
                    onPressed: _downloading ? null : _download,
                    icon: _downloading
                        ? const SizedBox.square(
                            dimension: 18,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : const Icon(Icons.download_outlined),
                    label: Text(
                      _downloading ? 'Downloading…' : 'Download Reports',
                    ),
                  ),
                  const SizedBox(height: 8),
                  SendToAdminButton(run: widget.run, uploader: widget.uploader),
                  const SizedBox(height: 8),
                  OutlinedButton.icon(
                    onPressed: _copy,
                    icon: const Icon(Icons.copy_all_outlined),
                    label: const Text('Copy full report (text)'),
                  ),
                  const SizedBox(height: 8),
                  OutlinedButton.icon(
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
            ),
    );
  }

  /// The checklist and the preview of the HTML report share the screen; a switch at the top picks one.
  Widget _withViewSwitch(Widget checklist) {
    if (widget.run.htmlPath.isEmpty) return checklist;
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
          child: SegmentedButton<bool>(
            key: const ValueKey<String>('report_view_switch'),
            segments: const [
              ButtonSegment<bool>(
                value: false,
                icon: Icon(Icons.checklist),
                label: Text('Checklist'),
              ),
              ButtonSegment<bool>(
                value: true,
                icon: Icon(Icons.web),
                label: Text('HTML preview'),
              ),
            ],
            selected: <bool>{_preview},
            showSelectedIcon: false,
            onSelectionChanged: (selection) => _showPreview(selection.first),
          ),
        ),
        Expanded(child: _preview ? _previewBody() : checklist),
      ],
    );
  }

  void _showPreview(bool on) {
    if (on && _web == null && File(widget.run.htmlPath).existsSync()) {
      // The report's own script filters its checklist, so JavaScript is on; the page loads only its own file.
      _web = WebViewController()
        ..setJavaScriptMode(JavaScriptMode.unrestricted)
        ..loadFile(Uri.file(widget.run.htmlPath).toString());
    }
    setState(() => _preview = on);
  }

  Widget _previewBody() {
    final web = _web;
    if (web == null) {
      return const Center(
        child: Padding(
          padding: EdgeInsets.all(24),
          child: Text('The HTML report file is not available on this phone.'),
        ),
      );
    }
    return WebViewWidget(
      key: const ValueKey<String>('report_html_preview'),
      controller: web,
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
                Expanded(child: Text(value, style: theme.textTheme.bodySmall)),
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
    if (d.inMinutes < 1) {
      return '${(d.inMilliseconds / 1000).toStringAsFixed(1)} s';
    }
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

/// One module of the summary: its name, its counts and its result; a tap shows only that module's steps.
class _ModuleSummaryRow extends StatelessWidget {
  const _ModuleSummaryRow({
    required this.stats,
    required this.selected,
    required this.onTap,
    super.key,
  });

  final ModuleStats stats;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final failed = stats.failed > 0;
    final color = failed
        ? scheme.error
        : stats.outcome == 'PASS'
        ? Colors.green.shade700
        : scheme.onSurfaceVariant;
    return Card(
      margin: const EdgeInsets.only(bottom: 6),
      elevation: 0,
      color: selected
          ? scheme.primaryContainer.withValues(alpha: 0.5)
          : scheme.surfaceContainerHighest.withValues(alpha: 0.35),
      child: InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
          child: Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(stats.label, style: theme.textTheme.titleSmall),
                    Text(
                      '${stats.total} steps · ${stats.passed} passed · '
                      '${stats.failed} failed · ${stats.skipped + stats.pending} skipped / not tested',
                      style: theme.textTheme.bodySmall?.copyWith(
                        color: scheme.onSurfaceVariant,
                      ),
                    ),
                  ],
                ),
              ),
              Text(
                stats.outcome,
                style: theme.textTheme.labelLarge?.copyWith(color: color),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

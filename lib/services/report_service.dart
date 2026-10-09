import 'dart:convert';
import 'dart:io';

import 'package:intl/intl.dart';
import 'package:path_provider/path_provider.dart';
import 'package:share_plus/share_plus.dart';

import '../models/automation_result.dart';
import '../models/automation_step.dart';
import '../models/test_run.dart';

/// Writes and reads the run reports.
///
/// Three artefacts per run — JSON, HTML and a plain-text checklist — plus a
/// `history.json` index. Everything lands in the application-specific storage
/// directory, so no storage permission is required and the files are removed
/// when the tool is uninstalled.
///
/// The reports are rendered from [AutomationResult], which has no field for a
/// Client ID, an OTP or an MPIN, so a credential cannot reach a report by
/// construction.
class ReportService {
  static const String _historyFile = 'history.json';
  static const int _historyLimit = 50;

  static final DateFormat _stamp = DateFormat('yyyy-MM-dd HH:mm:ss');
  static final DateFormat _fileStamp = DateFormat('yyyyMMdd_HHmmss');

  Directory? _cachedDir;

  /// `<app-specific external>/reports`, falling back to app documents when the
  /// device exposes no external app directory.
  Future<Directory> reportsDirectory() async {
    final cached = _cachedDir;
    if (cached != null) return cached;

    Directory? base;
    try {
      base = await getExternalStorageDirectory();
    } catch (_) {
      base = null;
    }
    base ??= await getApplicationDocumentsDirectory();

    final dir = Directory('${base.path}/reports');
    if (!await dir.exists()) {
      await dir.create(recursive: true);
    }
    _cachedDir = dir;
    return dir;
  }

  /// Renders and stores all three report formats, then indexes the run.
  Future<TestRun> save(AutomationResult result) async {
    final dir = await reportsDirectory();
    final started = result.startedAt ?? DateTime.now();
    final base =
        '${_fileStamp.format(started)}_${result.runId.substring(0, result.runId.length.clamp(0, 8))}';

    final jsonFile = File('${dir.path}/$base.json');
    final htmlFile = File('${dir.path}/$base.html');
    final textFile = File('${dir.path}/$base.txt');

    await jsonFile.writeAsString(
      const JsonEncoder.withIndent('  ').convert(result.toJson()),
    );
    await htmlFile.writeAsString(buildHtml(result));
    await textFile.writeAsString(buildChecklistText(result));

    final run = TestRun.fromResult(
      result,
      jsonPath: jsonFile.path,
      htmlPath: htmlFile.path,
      textPath: textFile.path,
    );
    await _addToHistory(run);
    return run;
  }

  Future<List<TestRun>> history() async {
    final dir = await reportsDirectory();
    final file = File('${dir.path}/$_historyFile');
    if (!await file.exists()) return const <TestRun>[];
    try {
      final decoded = jsonDecode(await file.readAsString());
      if (decoded is! List) return const <TestRun>[];
      return decoded
          .whereType<Map<String, Object?>>()
          .map(TestRun.fromJson)
          .toList(growable: false);
    } catch (_) {
      return const <TestRun>[];
    }
  }

  Future<void> clearHistory() async {
    final dir = await reportsDirectory();
    final file = File('${dir.path}/$_historyFile');
    if (await file.exists()) await file.delete();
  }

  Future<String?> readReportFile(String path) async {
    try {
      final file = File(path);
      if (!await file.exists()) return null;
      return await file.readAsString();
    } catch (_) {
      return null;
    }
  }

  /// Hands the run's report files to the Android share sheet.
  Future<void> share(TestRun run) async {
    final files = <XFile>[];
    for (final path in <String>[run.htmlPath, run.jsonPath, run.textPath]) {
      if (path.isEmpty) continue;
      if (await File(path).exists()) files.add(XFile(path));
    }
    final subject =
        '${run.testName} — ${run.verdict} '
        '(${run.completedSteps}/${run.totalSteps})';
    if (files.isEmpty) {
      await Share.share(subject, subject: subject);
      return;
    }
    await Share.shareXFiles(files, subject: subject, text: subject);
  }

  Future<void> _addToHistory(TestRun run) async {
    final dir = await reportsDirectory();
    final file = File('${dir.path}/$_historyFile');
    final existing = await history();
    final updated = <TestRun>[
      run,
      ...existing.where((r) => r.runId != run.runId),
    ];
    final trimmed = updated.take(_historyLimit).toList(growable: false);
    await file.writeAsString(
      const JsonEncoder.withIndent(
        '  ',
      ).convert(trimmed.map((r) => r.toJson()).toList(growable: false)),
    );
  }

  // ── Renderers ───────────────────────────────────────────────────────────

  /// The plain-text checklist.
  String buildChecklistText(AutomationResult result) {
    final out = StringBuffer()
      ..writeln(result.testName)
      ..writeln();

    for (final step in result.steps) {
      out.write('${step.status.glyph} ${step.name}');
      if (step.status == StepStatus.skipped) {
        out.write('  (skipped)');
      } else if (step.status == StepStatus.pending) {
        out.write('  (not reached)');
      }
      out.writeln();
    }

    final failure = result.firstFailure;
    if (failure != null) {
      out
        ..writeln()
        ..writeln('Failure:')
        ..writeln(failure.failureReason ?? result.failureMessage ?? 'Unknown.');
    } else if (result.failureMessage != null) {
      out
        ..writeln()
        ..writeln('Note:')
        ..writeln(result.failureMessage!);
    }

    out
      ..writeln()
      ..writeln('Result: ${result.verdict}')
      ..writeln('Completed: ${result.completedSteps}/${result.totalSteps}')
      ..writeln()
      ..writeln('Target package: ${result.targetPackage}')
      ..writeln(
        'Device: ${result.deviceManufacturer} ${result.deviceModel} '
        '(Android ${result.androidVersion}, API ${result.sdkInt})',
      );
    final started = result.startedAt;
    if (started != null) out.writeln('Started: ${_stamp.format(started)}');
    final finished = result.finishedAt;
    if (finished != null) out.writeln('Ended: ${_stamp.format(finished)}');
    final duration = result.duration;
    if (duration != null) {
      out.writeln('Duration: ${_formatDuration(duration)}');
    }
    if (result.screenshotUnavailableReason != null) {
      out.writeln('Screenshot: unavailable — ${result.screenshotUnavailableReason}');
    }
    out
      ..writeln()
      ..writeln(
        'Credential values are never recorded: this report contains no Client ID, '
        'OTP or MPIN.',
      );
    return out.toString();
  }

  /// A self-contained HTML report.
  String buildHtml(AutomationResult result) {
    final rows = StringBuffer();
    for (final step in result.steps) {
      final detail = step.failureReason ?? step.note ?? '';
      rows.write('''
      <tr class="${step.status.wire}">
        <td class="glyph">${step.status.glyph}</td>
        <td class="name">${_esc(step.name)}</td>
        <td class="status"><span class="pill ${step.status.wire}">${step.status.label}</span></td>
        <td class="time">${step.startedAt == null ? '—' : _esc(_stamp.format(step.startedAt!))}</td>
        <td class="time">${step.finishedAt == null ? '—' : _esc(_stamp.format(step.finishedAt!))}</td>
        <td class="time">${step.duration == null ? '—' : _esc(_formatDuration(step.duration!))}</td>
        <td class="detail">${_esc(detail)}</td>
      </tr>
''');
    }

    final failure = result.firstFailure;
    final failureBlock = failure == null && result.failureMessage == null
        ? ''
        : '''
    <div class="failure">
      <h2>${failure == null ? 'Note' : 'Failure'}</h2>
      <p>${_esc(failure?.failureReason ?? result.failureMessage ?? '')}</p>
    </div>
''';

    final shots = StringBuffer();
    if (result.screenshots.isNotEmpty) {
      shots.write('<h2>Screenshots</h2><ul class="paths">');
      for (final entry in result.screenshots.entries) {
        shots.write(
          '<li><strong>${_esc(entry.key)}</strong><br><code>${_esc(entry.value)}</code></li>',
        );
      }
      shots.write('</ul>');
    } else if (result.screenshotUnavailableReason != null) {
      shots.write(
        '<h2>Screenshots</h2><p class="muted">Unavailable — '
        '${_esc(result.screenshotUnavailableReason!)}</p>',
      );
    }

    return '''<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${_esc(result.testName)} — ${result.verdict}</title>
<style>
  :root { color-scheme: light dark; --fg:#16181d; --bg:#fff; --muted:#5d6470;
          --line:#e3e6ec; --pass:#127a41; --fail:#c0261c; --skip:#8a6d1f; --card:#f7f8fa; }
  @media (prefers-color-scheme: dark) {
    :root { --fg:#e8eaee; --bg:#14161a; --muted:#9aa2af; --line:#2b2f37;
            --pass:#4ec97f; --fail:#ff6b5e; --skip:#e0b64a; --card:#1c1f25; }
  }
  * { box-sizing: border-box; }
  body { margin:0; padding:24px; font:15px/1.55 -apple-system,Roboto,Segoe UI,sans-serif;
         color:var(--fg); background:var(--bg); }
  h1 { font-size:22px; margin:0 0 4px; }
  h2 { font-size:16px; margin:28px 0 10px; }
  .verdict { display:inline-block; margin:10px 0 18px; padding:6px 14px; border-radius:999px;
             font-weight:700; letter-spacing:.4px; }
  .verdict.PASSED { background:var(--pass); color:#fff; }
  .verdict.FAILED { background:var(--fail); color:#fff; }
  .verdict.STOPPED { background:var(--skip); color:#fff; }
  .meta { border-collapse:collapse; width:100%; max-width:760px; }
  .meta td { padding:5px 10px 5px 0; vertical-align:top; border-bottom:1px solid var(--line); }
  .meta td:first-child { color:var(--muted); white-space:nowrap; width:1%; }
  .scroll { overflow-x:auto; }
  table.steps { border-collapse:collapse; width:100%; min-width:760px; margin-top:8px; }
  table.steps th, table.steps td { padding:8px 10px; border-bottom:1px solid var(--line);
                                   text-align:left; vertical-align:top; }
  table.steps th { font-size:12px; text-transform:uppercase; letter-spacing:.6px; color:var(--muted); }
  td.glyph { font-size:17px; width:1%; }
  tr.passed td.glyph { color:var(--pass); }
  tr.failed td.glyph { color:var(--fail); }
  tr.skipped td.glyph { color:var(--skip); }
  td.time { white-space:nowrap; color:var(--muted); font-variant-numeric:tabular-nums; }
  td.detail { color:var(--muted); font-size:13px; }
  .pill { font-size:12px; padding:2px 9px; border-radius:999px; border:1px solid var(--line); }
  .pill.passed { color:var(--pass); } .pill.failed { color:var(--fail); }
  .pill.skipped { color:var(--skip); } .pill.pending { color:var(--muted); }
  .failure { margin-top:22px; padding:14px 16px; border-radius:10px; background:var(--card);
             border-left:4px solid var(--fail); }
  .failure h2 { margin-top:0; }
  .paths { padding-left:18px; } .paths code { font-size:12px; word-break:break-all; }
  .muted { color:var(--muted); }
  footer { margin-top:30px; padding-top:14px; border-top:1px solid var(--line);
           font-size:12.5px; color:var(--muted); }
</style>
</head>
<body>
  <h1>${_esc(result.testName)}</h1>
  <div class="verdict ${result.verdict}">${result.verdict}</div>
  <p class="muted">Completed ${result.completedSteps} of ${result.totalSteps} steps.</p>

  <h2>Run</h2>
  <table class="meta">
    <tr><td>Test name</td><td>${_esc(result.testName)}</td></tr>
    <tr><td>Run ID</td><td><code>${_esc(result.runId)}</code></td></tr>
    <tr><td>Target package</td><td><code>${_esc(result.targetPackage)}</code></td></tr>
    <tr><td>Main activity</td><td><code>${_esc(result.targetMainActivity ?? '—')}</code></td></tr>
    <tr><td>Target version</td><td>${_esc(result.targetVersionName ?? '—')}</td></tr>
    <tr><td>Device</td><td>${_esc('${result.deviceManufacturer} ${result.deviceModel}')}</td></tr>
    <tr><td>Android</td><td>${_esc(result.androidVersion)} (API ${result.sdkInt})</td></tr>
    <tr><td>Started</td><td>${result.startedAt == null ? '—' : _esc(_stamp.format(result.startedAt!))}</td></tr>
    <tr><td>Ended</td><td>${result.finishedAt == null ? '—' : _esc(_stamp.format(result.finishedAt!))}</td></tr>
    <tr><td>Duration</td><td>${result.duration == null ? '—' : _esc(_formatDuration(result.duration!))}</td></tr>
  </table>

  <h2>Checklist</h2>
  <div class="scroll">
  <table class="steps">
    <thead><tr><th></th><th>Step</th><th>Status</th><th>Start</th><th>End</th><th>Duration</th><th>Detail</th></tr></thead>
    <tbody>
$rows    </tbody>
  </table>
  </div>
$failureBlock$shots
  <footer>
    Generated on device by Mobile Automation Tool. Credential values are never
    recorded — this report contains no Client ID, OTP or MPIN.
  </footer>
</body>
</html>
''';
  }

  static String _formatDuration(Duration d) {
    if (d.inSeconds < 1) return '${d.inMilliseconds} ms';
    if (d.inMinutes < 1) {
      return '${(d.inMilliseconds / 1000).toStringAsFixed(1)} s';
    }
    final seconds = d.inSeconds % 60;
    return '${d.inMinutes}m ${seconds}s';
  }

  static String _esc(String input) => input
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;');
}

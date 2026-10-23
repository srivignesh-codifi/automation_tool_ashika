import 'dart:convert';
import 'dart:io';

import 'package:flutter/services.dart';
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
  ReportService({MethodChannel? downloads})
    : _downloads =
          downloads ??
          const MethodChannel('com.mobileautomation.tool/commands');

  static const String _historyFile = 'history.json';
  static const int _historyLimit = 50;

  static final DateFormat _stamp = DateFormat('yyyy-MM-dd HH:mm:ss');
  static final DateFormat _fileStamp = DateFormat('yyyyMMdd_HHmmss');
  static final DateFormat _zipStamp = DateFormat('ddMMyyyy_HHmm');

  /// The short name a package is known by in file names.
  static const Map<String, String> _appNames = <String, String>{
    'com.codifi.dhanush': 'ashika',
    'com.codifi.rmoney': 'tradion',
  };

  /// `<app>_<ddMMyyyy>_<HHmm>.zip` of the run's start, e.g. `ashika_23102026_0810.zip`.
  static String zipNameFor(AutomationResult result) {
    final app =
        _appNames[result.targetPackage] ??
        result.targetPackage.split('.').last.toLowerCase();
    final started = result.startedAt ?? DateTime.now();
    return '${app}_${_zipStamp.format(started)}.zip';
  }

  Directory? _cachedDir;
  final MethodChannel _downloads;

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

    // The report refers to the video by file name, so it is renamed to share
    // the report's base name and stays next to it.
    final saved = await _adoptVideo(result, dir, base);

    await jsonFile.writeAsString(
      const JsonEncoder.withIndent('  ').convert(saved.toJson()),
    );
    await htmlFile.writeAsString(buildHtml(saved));
    await textFile.writeAsString(buildChecklistText(saved));

    final zip = await _buildSummaryZip(saved, dir, base, textFile);

    final run = TestRun.fromResult(
      saved,
      jsonPath: jsonFile.path,
      htmlPath: htmlFile.path,
      textPath: textFile.path,
      videoPath: saved.videoPath ?? '',
      zipPath: zip.path,
      zipFolder: zip.folder,
      zipError: zip.error,
    );
    await _addToHistory(run);
    return run;
  }

  /// Packs the text report, the recording and the failed-step screenshots into
  /// `<app>_<ddMMyyyy>_<HHmm>.zip` (see [zipNameFor]) and has Android copy it to public Downloads. Best
  /// effort: a failure is returned as text and never fails the save.
  Future<({String path, String folder, String error})> _buildSummaryZip(
    AutomationResult result,
    Directory dir,
    String base,
    File textFile,
  ) async {
    final entries = <Map<String, String>>[
      <String, String>{'path': textFile.path, 'name': '$base.txt'},
    ];
    final video = result.videoPath;
    if (video != null && video.isNotEmpty && await File(video).exists()) {
      entries.add(<String, String>{'path': video, 'name': '$base.mp4'});
    }
    for (final shot in result.screenshots.entries) {
      if (!shot.key.startsWith('failed')) continue;
      if (!await File(shot.value).exists()) continue;
      entries.add(<String, String>{
        'path': shot.value,
        'name': 'screenshots/${shot.value.split('/').last}',
      });
    }
    final name = zipNameFor(result);
    try {
      final raw = await _downloads.invokeMethod<Object?>(
        'buildSummaryZip',
        <String, Object?>{
          'zipPath': '${dir.path}/$name',
          'displayName': name,
          'entries': entries,
        },
      );
      if (raw is! Map<Object?, Object?>) {
        return (path: '', folder: '', error: 'Android returned no zip result.');
      }
      return (
        path: (raw['zipPath'] as String?) ?? '',
        folder: (raw['folder'] as String?) ?? '',
        error: (raw['error'] as String?) ?? '',
      );
    } catch (e) {
      return (path: '', folder: '', error: 'The zip could not be made ($e).');
    }
  }

  /// Renames the native recording to `<base>.mp4`. A run is saved more than
  /// once when its report is reopened, and by then the native path is gone but
  /// `<base>.mp4` is already in place, so that is looked for too.
  Future<AutomationResult> _adoptVideo(
    AutomationResult result,
    Directory dir,
    String base,
  ) async {
    final target = File('${dir.path}/$base.mp4');
    final source = result.videoPath;
    try {
      if (source != null && source.isNotEmpty) {
        final file = File(source);
        if (file.path == target.path) return result;
        if (await file.exists()) {
          await file.rename(target.path);
          return result.withVideoPath(target.path);
        }
      }
      if (await target.exists()) return result.withVideoPath(target.path);
    } catch (_) {
      // Keep the video where the driver left it; the report still names it.
      if (source != null && await File(source).exists()) return result;
    }
    return result.withVideoPath(null);
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
    for (final path in <String>[
      run.htmlPath,
      run.jsonPath,
      run.textPath,
      run.videoPath,
      run.zipPath,
    ]) {
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

  /// Copies all available formats for [run] into public Downloads.
  ///
  /// Android's MediaStore performs the write, so Android 10+ needs no storage
  /// permission and the files remain available after the app is uninstalled.
  Future<ReportDownloadResult> download(TestRun run) async {
    final candidates = <(String, String)>[
      (run.htmlPath, 'text/html'),
      (run.jsonPath, 'application/json'),
      (run.textPath, 'text/plain'),
      (run.videoPath, 'video/mp4'),
    ];
    final files = <Map<String, String>>[];
    for (final (path, mimeType) in candidates) {
      if (path.isEmpty || !await File(path).exists()) continue;
      files.add(<String, String>{
        'path': path,
        'name': File(path).uri.pathSegments.last,
        'mimeType': mimeType,
      });
    }
    if (files.isEmpty) {
      throw const FileSystemException('No saved report files were found.');
    }

    final raw = await _downloads.invokeMethod<Object?>(
      'downloadReportFiles',
      <String, Object?>{'files': files},
    );
    if (raw is! Map<Object?, Object?>) {
      throw PlatformException(
        code: 'invalid_download_result',
        message: 'Android returned an invalid report download result.',
      );
    }
    return ReportDownloadResult(
      savedNames: _stringList(raw['savedNames']),
      failedNames: _stringList(raw['failedNames']),
      directory:
          raw['directory'] as String? ?? 'Downloads/Automation Tool Reports',
    );
  }

  static List<String> _stringList(Object? value) => value is List
      ? value.whereType<String>().toList(growable: false)
      : const <String>[];

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

  static final DateFormat _clock24 = DateFormat('HH:mm:ss');

  /// A load slower than this is marked SLOW; mirrors `StepApi.SLOW_MS` natively.
  static const int _slowSeconds = 5;

  /// The plain-text report: run summary, first failure, API / data checks,
  /// slowest steps, the checklist as a table with times, per-step details and
  /// the evidence files. Meant to be read in a monospace font.
  String buildChecklistText(AutomationResult result) {
    final out = StringBuffer()
      ..writeln(result.testName)
      ..writeln('=' * result.testName.length)
      ..writeln();

    // ── Result ────────────────────────────────────────────────────────────
    final failed = result.failedSteps;
    out
      ..writeln('Result: ${result.verdict}')
      ..writeln(
        'Completed: ${result.completedSteps}/${result.totalSteps} steps  '
        '(${result.passedSteps} passed, ${result.skippedSteps} skipped, '
        '$failed failed, ${result.notRunSteps} not run)',
      )
      ..writeln();

    // ── Run ───────────────────────────────────────────────────────────────
    _heading(out, 'RUN');
    _kv(out, 'Target package', result.targetPackage);
    _kv(out, 'App version', result.targetVersionName ?? '-');
    _kv(out, 'Main activity', result.targetMainActivity ?? '-');
    _kv(
      out,
      'Device',
      '${result.deviceManufacturer} ${result.deviceModel} '
          '(Android ${result.androidVersion}, API ${result.sdkInt})',
    );
    final started = result.startedAt;
    final finished = result.finishedAt;
    _kv(out, 'Started', started == null ? '-' : _stamp.format(started));
    _kv(out, 'Ended', finished == null ? '-' : _stamp.format(finished));
    final duration = result.duration;
    final stepTotal = result.steps
        .map((s) => s.durationMs ?? 0)
        .fold<int>(0, (a, b) => a + b);
    _kv(
      out,
      'Duration',
      duration == null
          ? '-'
          : '${_formatDuration(duration)}  '
                '(time inside steps: ${_formatDuration(Duration(milliseconds: stepTotal))})',
    );
    _kv(out, 'Run ID', result.runId);
    if (result.modulesRun.isNotEmpty || result.modulesSkipped.isNotEmpty) {
      _kv(
        out,
        'Modules run',
        result.modulesRun.isEmpty
            ? 'none (sign-in and sign-out only)'
            : result.modulesRun.join(', '),
      );
      if (result.modulesSkipped.isNotEmpty) {
        _kv(out, 'Not selected', result.modulesSkipped.join(', '));
      }
    }
    if (result.hasVideo) {
      _kv(out, 'Screen recording', _fileName(result.videoPath!));
      if (result.recordingNote != null) _kv(out, '', result.recordingNote!);
    } else if (result.recordingNote != null) {
      _kv(out, 'Screen recording', result.recordingNote!);
    }
    out.writeln();

    // ── Failure ───────────────────────────────────────────────────────────
    final failure = result.firstFailure;
    if (failure != null) {
      _heading(out, 'FAILURE');
      final index = result.steps.indexOf(failure) + 1;
      final at = failure.startedAt == null
          ? ''
          : ' at ${_clock24.format(failure.startedAt!)}';
      final offset = result.videoOffsetOf(failure);
      final video = offset == null ? '' : '  (video ${_clock(offset)})';
      out.writeln('  Step $index, "${failure.name}"$at$video');
      for (final line in _wrap(
        failure.failureReason ?? result.failureMessage ?? 'Unknown.',
        96,
      )) {
        out.writeln('    $line');
      }
      final shot = _screenshotFor(result, failure);
      if (shot != null) out.writeln('    Screenshot: $shot');
      if (failed > 1) {
        out.writeln('  ($failed steps failed in all; see the checklist.)');
      }
      out.writeln();
    } else if (result.failureMessage != null) {
      _heading(out, 'NOTE');
      for (final line in _wrap(result.failureMessage!, 96)) {
        out.writeln('  $line');
      }
      out.writeln();
    }

    // ── API / data checks ─────────────────────────────────────────────────
    final api = result.apiSummary;
    if (!api.isEmpty) {
      _heading(out, 'API / DATA CHECKS');
      if (api.reads > 0) _kv(out, 'Data loads', api.readsLine);
      if (api.changes > 0) _kv(out, 'Server changes', api.changesLine);
      if (api.signIns > 0) _kv(out, 'Sign-in / out', api.signInsLine);
      _kv(
        out,
        'Verdict',
        api.problems == 0
            ? 'no API or data problem was seen'
            : '${api.problems} problem(s): see the rows marked ERROR, TIMEOUT, '
                  'NOT CONFIRMED or NOT CHECKED below',
      );
      final slowLoads =
          result.steps
              .where((s) => s.apiKind == 'read' && (s.apiMs ?? 0) > 0)
              .toList()
            ..sort((a, b) => (b.apiMs ?? 0).compareTo(a.apiMs ?? 0));
      for (var i = 0; i < slowLoads.length && i < 3; i++) {
        final step = slowLoads[i];
        _kv(
          out,
          i == 0 ? 'Slowest loads' : '',
          '${_ms(step.apiMs!)}  ${step.name}',
        );
      }
      out
        ..writeln(
          '  How it is checked: from what the screen shows, not from network traffic.',
        )
        ..writeln(
          '  A load passes when the app\'s loading spinners clear in time with no error screen;',
        )
        ..writeln(
          '  a change is CONFIRMED when it was re-read afterwards and matched; sign-in when',
        )
        ..writeln('  the next screen followed. A load over $_slowSeconds s is marked SLOW.')
        ..writeln();
    }

    // ── Slowest steps ─────────────────────────────────────────────────────
    final timed = result.steps.where((s) => (s.durationMs ?? 0) > 0).toList()
      ..sort((a, b) => (b.durationMs ?? 0).compareTo(a.durationMs ?? 0));
    if (timed.isNotEmpty) {
      _heading(out, 'SLOWEST STEPS');
      for (var i = 0; i < timed.length && i < 5; i++) {
        final step = timed[i];
        out.writeln(
          '  ${i + 1}. ${_ms(step.durationMs!).padRight(8)} ${step.name}',
        );
      }
      out.writeln();
    }

    // ── Checklist table ───────────────────────────────────────────────────
    _heading(out, 'CHECKLIST');
    final hasVideo = result.hasVideo;
    final header = <String>[
      '#',
      'Result',
      'Step',
      'Start',
      'End',
      'Time',
      if (hasVideo) 'Video',
      'Type',
      'API / data check',
    ];
    final rows = <List<String>>[];
    for (var i = 0; i < result.steps.length; i++) {
      final step = result.steps[i];
      final offset = result.videoOffsetOf(step);
      rows.add(<String>[
        '${i + 1}',
        _resultCell(step.status),
        _clip(step.name, 50),
        step.startedAt == null ? '-' : _clock24.format(step.startedAt!),
        step.finishedAt == null ? '-' : _clock24.format(step.finishedAt!),
        step.duration == null ? '-' : _formatDuration(step.duration!),
        if (hasVideo) (offset == null ? '-' : '▶ ${_clock(offset)}'),
        step.apiKindLabel.isEmpty || !step.hasApiCheck ? '-' : step.apiKindLabel,
        _clip(_apiCell(step), 64),
      ]);
    }
    _table(out, header, rows, rightAlign: const <int>{0});
    out.writeln();

    // ── Step details ──────────────────────────────────────────────────────
    final detailed = <int>[
      for (var i = 0; i < result.steps.length; i++)
        if (result.steps[i].note != null || result.steps[i].failureReason != null) i,
    ];
    if (detailed.isNotEmpty) {
      _heading(out, 'STEP DETAILS');
      for (final i in detailed) {
        final step = result.steps[i];
        out.writeln('  ${i + 1}. ${step.name}');
        if (step.failureReason != null) {
          for (final line in _wrap('Failure: ${step.failureReason}', 94)) {
            out.writeln('     $line');
          }
        }
        if (step.note != null) {
          for (final line in _wrap('Note: ${step.note}', 94)) {
            out.writeln('     $line');
          }
        }
        final shot = _screenshotFor(result, step);
        if (shot != null) out.writeln('     Screenshot: $shot');
      }
      out.writeln();
    }

    // ── Evidence ──────────────────────────────────────────────────────────
    final evidence = <String>[
      if (result.screenshots.isNotEmpty)
        for (final e in result.screenshots.entries)
          'Screenshot  ${e.key}: ${_fileName(e.value)}',
      if (result.screenshotUnavailableReason != null)
        'Screenshot: unavailable — ${result.screenshotUnavailableReason}',
    ];
    if (evidence.isNotEmpty) {
      _heading(out, 'EVIDENCE');
      for (final line in evidence) {
        out.writeln('  $line');
      }
      out.writeln();
    }

    out.writeln(
      'Credential values are never recorded: this report contains no Client ID, '
      'OTP or MPIN.',
    );
    return out.toString();
  }

  // ── Text report helpers ─────────────────────────────────────────────────

  static void _heading(StringBuffer out, String title) {
    out
      ..writeln(title)
      ..writeln('-' * title.length);
  }

  static void _kv(StringBuffer out, String key, String value) =>
      out.writeln('  ${key.padRight(18)}$value');

  static String _resultCell(StepStatus status) => switch (status) {
    StepStatus.passed => '✓ PASS',
    StepStatus.failed => '✕ FAIL',
    StepStatus.skipped => '– SKIP',
    StepStatus.running => '· RUNNING',
    StepStatus.pending => '· NOT RUN',
  };

  static String _apiCell(AutomationStep step) {
    if (!step.hasApiCheck) return '-';
    final label = step.apiStatusLabel;
    final detail = step.apiDetail;
    return detail == null || detail.isEmpty ? label : '$label  $detail';
  }

  static String? _screenshotFor(AutomationResult result, AutomationStep step) {
    final path = result.screenshots['failed_${step.id}'];
    return path == null ? null : _fileName(path);
  }

  static String _clip(String text, int max) =>
      text.length <= max ? text : '${text.substring(0, max - 1)}…';

  static String _ms(int millis) => millis < 1000
      ? '$millis ms'
      : '${(millis / 1000).toStringAsFixed(1)} s';

  /// Greedy word wrap at [width] columns.
  static List<String> _wrap(String text, int width) {
    final lines = <String>[];
    for (final paragraph in text.split('\n')) {
      var line = '';
      for (final word in paragraph.split(' ')) {
        if (line.isEmpty) {
          line = word;
        } else if (line.length + 1 + word.length <= width) {
          line = '$line $word';
        } else {
          lines.add(line);
          line = word;
        }
      }
      lines.add(line);
    }
    return lines;
  }

  /// A plain-text table with columns sized to their widest cell.
  static void _table(
    StringBuffer out,
    List<String> header,
    List<List<String>> rows, {
    Set<int> rightAlign = const <int>{},
  }) {
    final widths = <int>[
      for (var c = 0; c < header.length; c++)
        [header[c], ...rows.map((r) => r[c])]
            .map((cell) => cell.length)
            .reduce((a, b) => a > b ? a : b),
    ];
    String line(List<String> cells) {
      final parts = <String>[
        for (var c = 0; c < cells.length; c++)
          rightAlign.contains(c)
              ? cells[c].padLeft(widths[c])
              : cells[c].padRight(widths[c]),
      ];
      return parts.join('  ').trimRight();
    }

    out
      ..writeln(line(header))
      ..writeln(line(<String>[for (final w in widths) '-' * w]));
    for (final row in rows) {
      out.writeln(line(row));
    }
  }

  /// A self-contained HTML report.
  String buildHtml(AutomationResult result) {
    final rows = StringBuffer();
    final hasVideo = result.hasVideo;
    for (final step in result.steps) {
      final detail = step.failureReason ?? step.note ?? '';
      final offset = result.videoOffsetOf(step);
      final videoCell = !hasVideo
          ? ''
          : offset == null
          ? '<td class="time">—</td>'
          : '<td class="time"><a href="#rec" onclick="seek(${offset.toStringAsFixed(1)});return false">'
                '▶ ${_clock(offset)}</a></td>';
      rows.write('''
      <tr class="${step.status.wire}">
        <td class="glyph">${step.status.glyph}</td>
        <td class="name">${_esc(step.name)}</td>
        <td class="status"><span class="pill ${step.status.wire}">${step.status.label}</span></td>
        <td class="time">${step.startedAt == null ? '—' : _esc(_stamp.format(step.startedAt!))}</td>
        <td class="time">${step.finishedAt == null ? '—' : _esc(_stamp.format(step.finishedAt!))}</td>
        <td class="time">${step.duration == null ? '—' : _esc(_formatDuration(step.duration!))}</td>
        $videoCell
        <td class="api">${_apiHtml(step)}</td>
        <td class="detail">${_esc(detail)}</td>
      </tr>
''');
    }

    final api = result.apiSummary;
    final apiBlock = api.isEmpty
        ? ''
        : '''
  <h2>API / data checks</h2>
  <table class="meta">
    ${api.reads > 0 ? '<tr><td>Data loads</td><td>${_esc(api.readsLine)}</td></tr>' : ''}
    ${api.changes > 0 ? '<tr><td>Server changes</td><td>${_esc(api.changesLine)}</td></tr>' : ''}
    ${api.signIns > 0 ? '<tr><td>Sign-in / out</td><td>${_esc(api.signInsLine)}</td></tr>' : ''}
    <tr><td>Verdict</td><td>${api.problems == 0 ? 'no API or data problem was seen' : '${api.problems} problem(s): see the rows marked ERROR, TIMEOUT, NOT CONFIRMED or NOT CHECKED'}</td></tr>
  </table>
  <p class="muted">Inferred from what the screen shows, not from network traffic: a load passes when the
  app's spinners clear in time with no error screen (over $_slowSeconds s is SLOW); a change is CONFIRMED when it
  was re-read afterwards and matched; sign-in when the next screen followed.</p>
''';

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

    final recording = hasVideo
        ? '''
  <h2>Screen recording</h2>
  <video id="rec" controls preload="metadata" src="${_esc(_fileName(result.videoPath!))}"></video>
  <p class="muted">${_esc(_fileName(result.videoPath!))} — keep it in the same folder as this
  report. A time in the checklist jumps to that step.${result.recordingNote == null ? '' : ' ${_esc(result.recordingNote!)}.'}</p>
'''
        : result.recordingNote == null
        ? ''
        : '<h2>Screen recording</h2><p class="muted">${_esc(result.recordingNote!)}</p>';

    // Only a report with a video needs script, and only to seek that video.
    const seekScript = '''
  <script>
    function seek(t) {
      var v = document.getElementById('rec');
      if (!v) return;
      v.currentTime = t;
      v.play();
      v.scrollIntoView({behavior: 'smooth', block: 'center'});
    }
  </script>''';
    final seekScriptTag = hasVideo ? seekScript : '';

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
  video { display:block; max-width:min(100%, 360px); max-height:75vh; border-radius:10px;
          background:#000; }
  td.api { font-size:13px; }
  .pill.ok, .pill.confirmed { color:var(--pass); }
  .pill.error, .pill.timeout, .pill.not_confirmed { color:var(--fail); }
  .pill.slow, .pill.empty, .pill.not_checked { color:var(--skip); }
  td.time a { color:inherit; text-decoration:none; border-bottom:1px dotted var(--muted); }
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
    ${result.modulesRun.isEmpty && result.modulesSkipped.isEmpty ? '' : '<tr><td>Modules run</td><td>${_esc(result.modulesRun.isEmpty ? 'none (sign-in and sign-out only)' : result.modulesRun.join(', '))}</td></tr>'}
    ${result.modulesSkipped.isEmpty ? '' : '<tr><td>Not selected</td><td>${_esc(result.modulesSkipped.join(', '))}</td></tr>'}
    <tr><td>Target package</td><td><code>${_esc(result.targetPackage)}</code></td></tr>
    <tr><td>Main activity</td><td><code>${_esc(result.targetMainActivity ?? '—')}</code></td></tr>
    <tr><td>Target version</td><td>${_esc(result.targetVersionName ?? '—')}</td></tr>
    <tr><td>Device</td><td>${_esc('${result.deviceManufacturer} ${result.deviceModel}')}</td></tr>
    <tr><td>Android</td><td>${_esc(result.androidVersion)} (API ${result.sdkInt})</td></tr>
    <tr><td>Started</td><td>${result.startedAt == null ? '—' : _esc(_stamp.format(result.startedAt!))}</td></tr>
    <tr><td>Ended</td><td>${result.finishedAt == null ? '—' : _esc(_stamp.format(result.finishedAt!))}</td></tr>
    <tr><td>Duration</td><td>${result.duration == null ? '—' : _esc(_formatDuration(result.duration!))}</td></tr>
  </table>

$recording
$apiBlock
  <h2>Checklist</h2>
  <div class="scroll">
  <table class="steps">
    <thead><tr><th></th><th>Step</th><th>Status</th><th>Start</th><th>End</th><th>Duration</th>${hasVideo ? '<th>Video</th>' : ''}<th>API / data check</th><th>Detail</th></tr></thead>
    <tbody>
$rows    </tbody>
  </table>
  </div>
$failureBlock$shots$seekScriptTag
  <footer>
    Generated on device by Mobile Automation Tool. Credential values are never
    recorded — this report contains no Client ID, OTP or MPIN.
  </footer>
</body>
</html>
''';
  }

  static String _fileName(String path) => path.split('/').last;

  static String _apiHtml(AutomationStep step) {
    if (!step.hasApiCheck) return '<span class="muted">—</span>';
    final detail = step.apiDetail;
    return '<span class="pill ${_esc(step.apiStatus!)}">${_esc(step.apiStatusLabel)}</span> '
        '<span class="muted">${_esc(step.apiKindLabel)}'
        '${detail == null || detail.isEmpty ? '' : ' · ${_esc(detail)}'}</span>';
  }

  /// A position in the recording as `m:ss`.
  static String _clock(double seconds) {
    final total = seconds.floor();
    return '${total ~/ 60}:${(total % 60).toString().padLeft(2, '0')}';
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

class ReportDownloadResult {
  const ReportDownloadResult({
    required this.savedNames,
    required this.failedNames,
    required this.directory,
  });

  final List<String> savedNames;
  final List<String> failedNames;
  final String directory;

  bool get succeeded => savedNames.isNotEmpty && failedNames.isEmpty;

  String get message {
    if (succeeded) {
      return 'Downloaded ${savedNames.length} report files to $directory.';
    }
    if (savedNames.isNotEmpty) {
      return 'Downloaded ${savedNames.length} report files to $directory; '
          '${failedNames.length} could not be saved.';
    }
    return 'The report files could not be downloaded.';
  }
}

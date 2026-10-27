import '../models/report_modules.dart';
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
  static final RegExp _caseShotKey = RegExp(r'^[A-Z]{2,}-[A-Z]?\d+[a-z]?_');

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

    final zip = await _buildSummaryZip(saved, dir, base, textFile, htmlFile);

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

  /// Packs the text and HTML reports, the recording, the failed-step screenshots and the test cases' checkpoint screenshots into
  /// `<app>_<ddMMyyyy>_<HHmm>.zip` (see [zipNameFor]) and has Android copy it to public Downloads. Best
  /// effort: a failure is returned as text and never fails the save.
  Future<({String path, String folder, String error})> _buildSummaryZip(
    AutomationResult result,
    Directory dir,
    String base,
    File textFile,
    File htmlFile,
  ) async {
    final entries = <Map<String, String>>[
      <String, String>{'path': textFile.path, 'name': '$base.txt'},
      if (await htmlFile.exists())
        <String, String>{'path': htmlFile.path, 'name': '$base.html'},
    ];
    final video = result.videoPath;
    if (video != null && video.isNotEmpty && await File(video).exists()) {
      entries.add(<String, String>{'path': video, 'name': '$base.mp4'});
    }
    for (final shot in result.screenshots.entries) {
      // Failure shots, and the checkpoint shots of test cases (`WL-06_chart_loaded`) that the report's table names.
      if (!shot.key.startsWith('failed') && !_caseShotKey.hasMatch(shot.key)) {
        continue;
      }
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

    // ── Session expiry (HTTP 401) ────────────────────────────────────────
    _writeUnauthorized(out, result);

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

    _areaSection(out, result);
    _caseSections(out, result);

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
        ..writeln(
          '  the next screen followed. A load over $_slowSeconds s is marked SLOW.',
        )
        ..writeln();
    }

    _writeFeed(out, result);

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
        _resultCell(step),
        _clip(step.name, 50),
        step.startedAt == null ? '-' : _clock24.format(step.startedAt!),
        step.finishedAt == null ? '-' : _clock24.format(step.finishedAt!),
        step.duration == null ? '-' : _formatDuration(step.duration!),
        if (hasVideo) (offset == null ? '-' : '▶ ${_clock(offset)}'),
        step.apiKindLabel.isEmpty || !step.hasApiCheck
            ? '-'
            : step.apiKindLabel,
        _clip(_apiCell(step), 64),
      ]);
    }
    _table(out, header, rows, rightAlign: const <int>{0});
    out.writeln();

    // ── Step details ──────────────────────────────────────────────────────
    final detailed = <int>[
      for (var i = 0; i < result.steps.length; i++)
        if (result.steps[i].note != null ||
            result.steps[i].failureReason != null)
          i,
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

  /// The 401s first: every session expiry (where, which API, what the run did)
  /// and every step during which the app reported a refused call.
  static void _writeUnauthorized(StringBuffer out, AutomationResult result) {
    final steps = result.steps.where((s) => s.hadUnauthorized).toList();
    if (result.sessionBreaks.isEmpty && steps.isEmpty) return;
    _heading(out, '!!! SESSION EXPIRED / HTTP 401 !!!');
    for (final b in result.sessionBreaks) {
      final at = b.atMs == null
          ? ''
          : ' at ${_clock24.format(DateTime.fromMillisecondsSinceEpoch(b.atMs!))}';
      out
        ..writeln(
          '  #${b.number}$at  ${b.code == null ? '' : '${b.code} '}${b.stepName}',
        )
        ..writeln('      while   ${b.context} (back on the ${b.screen} screen)')
        ..writeln('      API     ${b.api}')
        ..writeln('      then    ${b.outcome}');
    }
    if (steps.isNotEmpty) {
      out.writeln('  Steps where a call was refused:');
      for (final s in steps) {
        final rerun = s.reruns > 0 ? '  [run again ${s.reruns}x]' : '';
        out.writeln(
          '  >>> 401  ${s.code == null ? '' : '${s.code} '}${s.name}  (${s.status.label})$rerun',
        );
        out.writeln('           ${s.unauthorized}');
      }
    }
    out.writeln();
  }

  /// The market-data feed (websocket) over the run, in its own section: for a
  /// live-trade-time run what it did, for a BOD run that it was not measured.
  static void _writeFeed(StringBuffer out, AutomationResult result) {
    final feed = result.feed;
    if (feed == null) return;
    _heading(out, 'LIVE FEED (WEBSOCKET)');
    if (feed['mode'] != 'live') {
      _kv(out, 'Session', 'BOD');
      out
        ..writeln('  ${feed['note'] ?? 'The live feed was not measured.'}')
        ..writeln();
      return;
    }
    for (final line in _feedLines(feed)) {
      _kv(out, line.$1, line.$2);
    }
    final drops = _feedDrops(feed);
    if (drops.isEmpty) {
      out.writeln(
        '  Disconnected      never: the feed stayed connected for the whole test',
      );
    } else {
      out.writeln('  Where it disconnected:');
      for (final d in drops) {
        out.writeln('    >>> ${d.$1}  ${d.$2}  · during: ${d.$3}');
      }
    }
    final problems = _feedList(
      feed['problems'],
    ).where((p) => !p.startsWith('feed dropped at')).toList();
    if (problems.isNotEmpty) {
      out.writeln('  Problems:');
      for (final p in problems) {
        out.writeln('    !! $p');
      }
    }
    final events = _feedEvents(feed);
    if (events.isNotEmpty) {
      out.writeln(
        '  Events (connects, disconnections, errors), with the step running:',
      );
      for (final e in events) {
        out.writeln(
          '    ${e.$1}  ${e.$3 ? 'ERROR ' : ''}${e.$2}${e.$4 == null ? '' : '  · ${e.$4}'}',
        );
      }
    }
    out
      ..writeln(
        '  From what Tradion reports about its own feed socket (ws_feed_status), '
        'read after every step; not network traffic.',
      )
      ..writeln();
  }

  /// (clock, cause, step) of each time the feed was lost.
  static List<(String, String, String)> _feedDrops(Map<String, Object?> f) {
    final raw = f['disconnections'];
    if (raw is! List) return const [];
    return raw.whereType<Map>().map((d) {
      final at = (d['atMs'] as num?)?.toInt();
      return (
        at == null
            ? '--:--:--'
            : _clock24.format(DateTime.fromMillisecondsSinceEpoch(at)),
        d['cause']?.toString() ?? 'disconnected',
        d['step']?.toString() ?? 'before the first step',
      );
    }).toList();
  }

  static List<String> _feedList(Object? raw) =>
      raw is List ? raw.map((e) => e.toString()).toList() : const <String>[];

  static String _feedMs(Object? raw) => raw is num ? _ms(raw.toInt()) : '—';

  static List<(String, String)> _feedLines(Map<String, Object?> f) {
    String n(String k) => (f[k] as num?)?.toInt().toString() ?? '0';
    final connected = f['connectedAtEnd'];
    return [
      ('Session', 'Live trade time'),
      (
        'Verdict',
        f['verdict'] == 'OK'
            ? 'OK: no feed problem was seen'
            : 'PROBLEMS (below)',
      ),
      (
        'Connected at end',
        connected == true ? 'yes' : (connected == false ? 'NO' : 'unknown'),
      ),
      (
        'Connections',
        '${n('connects')} established of ${n('connectAttempts')} attempt(s); '
            '${n('connectTimeouts')} timed out; ${n('loginRejects')} rejected',
      ),
      ('Disconnections', '${n('disconnects')} unplanned'),
      (
        'Subscriptions',
        '${n('subscribedKeys')} instrument(s) at the end; ${n('unackedKeys')} '
            'never acknowledged; ${n('ackTimeouts')} ack timeout(s); '
            '${n('silentKeys')} silent',
      ),
      (
        'Ticks',
        '${n('ticks')} received; longest gap ${_feedMs(f['maxTickGapMs'])}',
      ),
      (
        'Heartbeats',
        '${n('heartbeatsSent')} sent; ${n('heartbeatsMissing')} unanswered; '
            'slowest round trip ${_feedMs(f['maxRttMs'])}',
      ),
      (
        'Stale / errors',
        '${n('staleDetections')} stale feed(s); ${n('malformedFrames')} '
            'malformed frame(s); ${n('errorEvents')} error event(s)',
      ),
      (
        'Timings',
        'connect → login ack ${_feedMs(f['connectToAckMs'])}; '
            'ack → first tick ${_feedMs(f['ackToFirstTickMs'])}',
      ),
      (
        'Readings',
        '${n('samples')} (one after every step); ${n('unreadSamples')} could not be read',
      ),
    ];
  }

  /// (clock, phase, error, step) of each recorded feed event.
  static List<(String, String, bool, String?)> _feedEvents(
    Map<String, Object?> f,
  ) {
    final raw = f['events'];
    if (raw is! List) return const [];
    return raw.whereType<Map>().map((e) {
      final at = (e['atMs'] as num?)?.toInt();
      return (
        at == null
            ? '--:--:--'
            : _clock24.format(DateTime.fromMillisecondsSinceEpoch(at)),
        e['phase']?.toString() ?? '',
        e['error'] == true,
        e['step']?.toString(),
      );
    }).toList();
  }

  static String _feedHtml(Map<String, Object?>? feed) {
    if (feed == null) return '';
    if (feed['mode'] != 'live') {
      return '<h2>Live feed (websocket)</h2><p class="muted">BOD session: '
          '${_esc(feed['note']?.toString() ?? 'the live feed was not measured.')}</p>';
    }
    final ok = feed['verdict'] == 'OK';
    final rows = _feedLines(feed)
        .map((l) => '<tr><td>${_esc(l.$1)}</td><td>${_esc(l.$2)}</td></tr>')
        .join();
    final problems = _feedList(
      feed['problems'],
    ).where((p) => !p.startsWith('feed dropped at')).toList();
    final events = _feedEvents(feed);
    final drops = _feedDrops(feed);
    final dropBlock = drops.isEmpty
        ? '<p><strong>Disconnected: never</strong>: the feed stayed connected for the whole test.</p>'
        : '<p><strong>Where it disconnected</strong></p><ul>${drops.map((d) => '<li><code>${_esc(d.$1)}</code> <strong>${_esc(d.$2)}</strong> · during ${_esc(d.$3)}</li>').join()}</ul>';
    return '<div class="feed-box${ok ? '' : ' bad'}">'
        '<h2>Live feed (websocket) — ${ok ? 'OK' : 'PROBLEMS'}</h2>'
        '<table class="meta">$rows</table>'
        '$dropBlock'
        '${problems.isEmpty ? '' : '<ul>${problems.map((p) => '<li><strong>${_esc(p)}</strong></li>').join()}</ul>'}'
        '${events.isEmpty ? '' : '<p><strong>Events</strong> (with the step that was running)</p><ul class="paths">${events.map((e) => '<li><code>${_esc(e.$1)}</code> ${e.$3 ? '<strong>ERROR</strong> ' : ''}${_esc(e.$2)}${e.$4 == null ? '' : ' · ${_esc(e.$4!)}'}</li>').join()}</ul>'}'
        '<p class="muted">From what Tradion reports about its own feed socket, read after every step; not network traffic.</p>'
        '</div>';
  }

  static void _heading(StringBuffer out, String title) {
    out
      ..writeln(title)
      ..writeln('-' * title.length);
  }

  static void _kv(StringBuffer out, String key, String value) =>
      out.writeln('  ${key.padRight(18)}$value');

  static String _resultCell(AutomationStep step) => switch (step.status) {
    StepStatus.passed => '✓ PASS',
    StepStatus.failed => '✕ FAIL',
    StepStatus.skipped =>
      step.skipKind == null ? '– SKIP' : '– ${step.outcomeLabel}',
    StepStatus.running => '· RUNNING',
    StepStatus.pending => '· NOT RUN',
  };

  /// The screenshot that shows a test case: the evidence taken at the moment of failure (named by case, kind and the
  /// screen it shows), else a checkpoint of that case, else the generic failure shot (taken after clean-up).
  static String? _caseShot(AutomationResult result, AutomationStep step) {
    final code = step.code;
    if (code != null) {
      for (final prefix in <String>[
        '${code}_failure',
        '${code}_unreadable',
        '${code}_save_stuck',
        '${code}_all_empty',
      ]) {
        final key = result.screenshots.keys
            .where((k) => k.startsWith(prefix))
            .toList();
        if (key.isNotEmpty) return _fileName(result.screenshots[key.first]!);
      }
    }
    final keys = code == null
        ? <String>[]
        : result.screenshots.keys
              .where((k) => k.startsWith('${code}_'))
              .toList();
    if (keys.isNotEmpty) return _fileName(result.screenshots[keys.first]!);
    final failed = result.screenshots['failed_${step.id}'];
    return failed == null ? null : _fileName(failed);
  }

  /// Login and Watchlist (and every other area) as separate results.
  static void _areaSection(StringBuffer out, AutomationResult result) {
    String area(AutomationStep s) => moduleLabelOf(s);

    final groups = <String, List<AutomationStep>>{};
    for (final s in result.steps) {
      groups.putIfAbsent(area(s), () => <AutomationStep>[]).add(s);
    }
    if (groups.length < 2) return;
    _heading(out, 'RESULTS BY AREA');
    _table(
      out,
      <String>[
        'Area',
        'Steps',
        'Pass',
        'Fail',
        'Blocked',
        'Not tested / N/A',
        'Skipped',
        'Time',
      ],
      <List<String>>[
        for (final e in groups.entries)
          () {
            int n(bool Function(AutomationStep) test) =>
                e.value.where(test).length;
            final ms = e.value.fold<int>(0, (a, s) => a + (s.durationMs ?? 0));
            return <String>[
              e.key,
              '${e.value.length}',
              '${n((s) => s.status == StepStatus.passed)}',
              '${n((s) => s.status == StepStatus.failed)}',
              '${n((s) => s.outcomeLabel == 'BLOCKED')}',
              '${n((s) => s.outcomeLabel == 'NOT TESTED' || s.outcomeLabel == 'NOT APPLICABLE')}',
              '${n((s) => s.outcomeLabel == 'SKIPPED')}',
              _formatDuration(Duration(milliseconds: ms)),
            ];
          }(),
      ],
      rightAlign: <int>{1, 2, 3, 4, 5, 6, 7},
    );
    out.writeln();
  }

  static String _classShort(AutomationStep s) =>
      s.causeTag.isEmpty ? '-' : s.causeTag;

  /// One finding: expected, observed, probable cause, UI hierarchy, screenshot, recommendation, cleanup.
  static void _finding(
    StringBuffer out,
    AutomationResult result,
    AutomationStep s,
    String label,
  ) {
    out.writeln('  $label  ${s.code}  [${s.severity ?? 'Medium'}]  ${s.name}');
    void line(String key, String? value) {
      if (value == null || value.isEmpty) return;
      final lines = _wrap(value, 88);
      out.writeln('        ${key.padRight(15)}${lines.first}');
      for (final more in lines.skip(1)) {
        out.writeln('        ${''.padRight(15)}$more');
      }
    }

    line('Expected', s.expected);
    line('Observed', s.status == StepStatus.failed ? s.failureReason : s.note);
    line('Cause', '${s.causeTag}: ${s.causeLabel}');
    line('UI hierarchy', s.evidence);
    final shot = _caseShot(result, s);
    line('Screenshot', shot);
    line('Recommendation', s.recommendation);
    line('Cleanup', s.cleanup);
  }

  /// The formal test-case table, the findings by class, the cleanup status and the QA assessment.
  static void _caseSections(StringBuffer out, AutomationResult result) {
    final cases = result.steps.where((s) => s.isCase).toList();
    if (cases.isEmpty) return;
    int count(String label) =>
        cases.where((s) => s.outcomeLabel == label).length;
    final pass = count('PASS');
    final fail = count('FAIL');
    _heading(out, 'TEST CASES');
    _kv(out, 'Cases', '${cases.length}');
    _kv(
      out,
      'Outcome',
      '$pass PASS, $fail FAIL, ${count('BLOCKED')} BLOCKED, '
          '${count('NOT APPLICABLE')} NOT APPLICABLE, ${count('NOT TESTED')} NOT TESTED',
    );
    out.writeln();
    _table(
      out,
      <String>[
        'ID',
        'Scenario',
        'Expected result',
        'Actual result',
        'Status',
        'Class',
        'Screenshot',
      ],
      <List<String>>[
        for (final s in cases)
          <String>[
            s.code!,
            _clip(s.name, 36),
            _clip(s.expected ?? '-', 60),
            _clip(
              (s.status == StepStatus.failed ? s.failureReason : null) ??
                  s.note ??
                  '-',
              80,
            ),
            s.outcomeLabel,
            _classShort(s),
            _caseShot(result, s) ?? '-',
          ],
      ],
    );
    out.writeln();

    // Findings, by what they turned out to be. A blank accessibility tree is never a product defect.
    final findings = cases
        .where(
          (s) => s.status == StepStatus.failed || s.outcomeLabel == 'BLOCKED',
        )
        .toList();
    List<AutomationStep> of(String cause) =>
        findings.where((s) => (s.cause ?? 'unconfirmed') == cause).toList();
    final groups = <(String, String, String)>[
      ('app_defect', 'CONFIRMED APPLICATION DEFECTS', 'D'),
      (
        'unconfirmed',
        'NEEDS REVIEW: EXPECTED CONTENT NOT FOUND IN READABLE TEXT',
        'R',
      ),
      ('automation', 'AUTOMATION / LOCATOR FAILURES', 'A'),
      (
        'accessibility',
        'ACCESSIBILITY LIMITATIONS (content could not be read, not a defect)',
        'X',
      ),
      ('api_data', 'API / DATA DEPENDENCY FAILURES', 'P'),
      ('test_data', 'TEST-DATA / STATE FAILURES', 'T'),
    ];
    for (final g in groups) {
      final items = of(g.$1);
      _heading(out, g.$2);
      if (items.isEmpty) {
        out.writeln('  none');
      }
      for (var i = 0; i < items.length; i++) {
        _finding(out, result, items[i], '${g.$3}${i + 1}');
      }
      out.writeln();
    }
    final untested = cases
        .where(
          (s) =>
              s.outcomeLabel == 'NOT TESTED' ||
              s.outcomeLabel == 'NOT APPLICABLE',
        )
        .toList();
    _heading(out, 'INTENTIONALLY NOT TESTED / NOT APPLICABLE');
    if (untested.isEmpty) out.writeln('  none');
    for (final s in untested) {
      out.writeln(
        '  ${s.code}  ${s.outcomeLabel.padRight(14)} ${_clip(s.note ?? s.name, 120)}',
      );
    }
    out.writeln();

    final cleaned = cases.where((s) => s.cleanup != null).toList();
    _heading(out, 'CLEANUP OF TEST-CREATED CHANGES');
    if (cleaned.isEmpty) out.writeln('  no case changed anything');
    for (final s in cleaned) {
      out.writeln('  ${s.code}  ${s.cleanup}');
    }
    out.writeln();

    final slow = cases.where((s) => s.apiMs != null && s.apiMs! > 0).toList()
      ..sort((a, b) => b.apiMs!.compareTo(a.apiMs!));
    if (slow.isNotEmpty) {
      _heading(out, 'PERFORMANCE');
      for (final s in slow.take(5)) {
        out.writeln(
          '  ${_ms(s.apiMs!).padRight(8)} ${s.code}  ${s.apiDetail ?? s.name}',
        );
      }
      out.writeln();
    }

    _heading(out, 'QA ASSESSMENT');
    final defects = of('app_defect');
    final critical = defects.where((s) => s.severity == 'Critical').length;
    final unresolved = findings.length - defects.length;
    final cleanupFailed = cleaned
        .where((s) => s.cleanup!.startsWith('FAILED'))
        .length;
    final String verdict;
    if (cases.every((s) => s.status == StepStatus.pending)) {
      verdict = 'Not assessed: no test case ran.';
    } else if (critical > 0) {
      verdict = 'Not ready: $critical critical application defect(s).';
    } else if (defects.isNotEmpty) {
      verdict =
          'Needs fixes: ${defects.length} confirmed application defect(s), none critical.';
    } else if (unresolved > 0 || count('NOT TESTED') > 0) {
      verdict =
          'No application defect confirmed. $unresolved case(s) could not be verified or need review, and '
          '${count('NOT TESTED')} were not tested: they are not passes.';
    } else {
      verdict = 'Ready: every applicable case passed.';
    }
    out
      ..writeln('  $verdict')
      ..writeln(
        cleanupFailed > 0
            ? '  WARNING: $cleanupFailed case(s) could not put back what they changed: see CLEANUP above.'
            : '  Everything the cases changed was put back.',
      )
      ..writeln(
        '  Only cases that ran are counted; a case that could not run or could not be read is never reported as PASS.',
      )
      ..writeln();
  }

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

  static String _ms(int millis) =>
      millis < 1000 ? '$millis ms' : '${(millis / 1000).toStringAsFixed(1)} s';

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
        [
          header[c],
          ...rows.map((r) => r[c]),
        ].map((cell) => cell.length).reduce((a, b) => a > b ? a : b),
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
      final haystack = <String?>[
        step.name,
        step.code,
        detail,
        step.expected,
        step.outcomeLabel,
      ].whereType<String>().join(' ').toLowerCase();
      rows.write('''
      <tr class="${step.status.wire}${step.hadUnauthorized ? ' unauth' : ''}" data-module="${_esc(moduleLabelOf(step))}" data-status="${step.status.wire}" data-text="${_esc(haystack)}">
        <td class="glyph">${step.status.glyph}</td>
        <td class="name">${step.hadUnauthorized ? '<span class="pill unauthorized">401</span> ' : ''}${_esc(step.name)}</td>
        <td class="status"><span class="pill ${step.status.wire}">${step.skipKind != null || step.isCase ? _esc(step.outcomeLabel) : step.status.label}</span></td>
        <td class="time">${step.startedAt == null ? '—' : _esc(_stamp.format(step.startedAt!))}</td>
        <td class="time">${step.finishedAt == null ? '—' : _esc(_stamp.format(step.finishedAt!))}</td>
        <td class="time">${step.duration == null ? '—' : _esc(_formatDuration(step.duration!))}</td>
        $videoCell
        <td class="api">${_apiHtml(step)}</td>
        <td class="detail">${_esc(detail)}</td>
      </tr>
''');
    }

    final stats = summarizeModules(result.steps);
    String rowClass(ModuleStats m) =>
        m.failed > 0 ? 'failed' : (m.outcome == 'PASS' ? 'passed' : 'skipped');
    final moduleRows = stats
        .map(
          (m) =>
              '      <tr class="${rowClass(m)}" data-pick="${_esc(m.label)}" title="Show only ${_esc(m.label)}">\n'
              '        <td class="name">${_esc(m.label)}</td><td>${m.total}</td><td>${m.passed}</td><td>${m.failed}</td><td>${m.skipped + m.pending}</td>\n'
              '        <td class="status"><span class="pill ${rowClass(m)}">${m.outcome}</span></td>\n'
              '      </tr>',
        )
        .join('\n');
    final moduleChips = stats
        .map(
          (m) =>
              '    <button type="button" class="chip" data-m="${_esc(m.label)}">${_esc(m.label)} (${m.total})</button>',
        )
        .join('\n');
    final moduleBlock = stats.isEmpty
        ? ''
        : '''
  <h2>Summary by module</h2>
  <div class="scroll">
  <table class="steps modsum">
    <thead><tr><th>Module</th><th>Steps</th><th>Pass</th><th>Fail</th><th>Skipped / not tested</th><th>Result</th></tr></thead>
    <tbody>
$moduleRows
    </tbody>
  </table>
  </div>
  <p class="muted">Click a module to show only its steps in the checklist below.</p>
''';
    final toolbar = result.steps.isEmpty
        ? ''
        : '''
  <div class="toolbar">
    <input id="q" type="search" placeholder="Search the checklist (name, id, detail)" autocomplete="off">
    <select id="st" aria-label="Status">
      <option value="">All statuses</option>
      <option value="failed">Failed</option>
      <option value="passed">Passed</option>
      <option value="skipped">Skipped / not tested</option>
    </select>
  </div>
  <div class="chips" id="mods">
    <button type="button" class="chip on" data-m="">All (${result.steps.length})</button>
$moduleChips
  </div>
  <p class="muted" id="shown"></p>
''';
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

    final unauthSteps = result.steps.where((s) => s.hadUnauthorized).toList();
    final unauthBlock = result.sessionBreaks.isEmpty && unauthSteps.isEmpty
        ? ''
        : '''
  <div class="unauth-box">
    <h2>Session expired / HTTP 401</h2>
    ${result.sessionBreaks.map((b) => '<p><strong>#${b.number}</strong> ${b.atMs == null ? '' : _esc(_clock24.format(DateTime.fromMillisecondsSinceEpoch(b.atMs!)))} · '
              '<strong>${_esc('${b.code == null ? '' : '${b.code} '}${b.stepName}')}</strong><br>'
              'While ${_esc(b.context)} (back on the ${_esc(b.screen)} screen)<br>'
              'API: <code>${_esc(b.api)}</code><br>Then: ${_esc(b.outcome)}</p>').join('\n    ')}
    ${unauthSteps.isEmpty ? '' : '<p><strong>Steps where a call was refused</strong> (highlighted in the checklist):</p><ul>${unauthSteps.map((s) => '<li>${_esc('${s.code == null ? '' : '${s.code} '}${s.name}')} — <code>${_esc(s.unauthorized!)}</code>${s.reruns > 0 ? ' (run again after signing in)' : ''}</li>').join()}</ul>'}
  </div>
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

    // The checklist filter (search, status, module) is a few lines of inline script: no external request, no dependency.
    const filterScript = r'''
  <script type="text/javascript">
    (function () {
      var q = document.getElementById('q');
      var st = document.getElementById('st');
      var mods = document.getElementById('mods');
      var rows = document.querySelectorAll('table.steps:not(.modsum) tbody tr');
      var shown = document.getElementById('shown');
      var module = '';
      function apply() {
        var words = q.value.toLowerCase().split(/\s+/).filter(Boolean);
        var n = 0;
        rows.forEach(function (r) {
          var ok = (!module || r.dataset.module === module) &&
                   (!st.value || r.dataset.status === st.value) &&
                   words.every(function (w) { return r.dataset.text.indexOf(w) >= 0; });
          r.style.display = ok ? '' : 'none';
          if (ok) n++;
        });
        shown.textContent = 'Showing ' + n + ' of ' + rows.length + ' steps';
        mods.querySelectorAll('.chip').forEach(function (c) {
          c.classList.toggle('on', c.dataset.m === module);
        });
      }
      function pick(m) { module = m; apply(); }
      q.addEventListener('input', apply);
      st.addEventListener('change', apply);
      mods.addEventListener('click', function (e) {
        var c = e.target.closest('.chip');
        if (c) pick(c.dataset.m);
      });
      document.querySelectorAll('tr[data-pick]').forEach(function (r) {
        r.style.cursor = 'pointer';
        r.addEventListener('click', function () {
          pick(r.dataset.pick);
          q.scrollIntoView({behavior: 'smooth', block: 'center'});
        });
      });
      apply();
    })();
  </script>''';

    final filterScriptTag = result.steps.isEmpty ? '' : filterScript;

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
    final seekScriptTag = (hasVideo ? seekScript : '') + filterScriptTag;

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
  .pill.unauthorized { color:#fff; background:#c62828; border-color:#c62828; font-weight:700; }
  tr.unauth td { background:rgba(198,40,40,.12); }
  .feed-box { border:2px solid #2e7d32; border-radius:10px; padding:4px 16px; margin:16px 0; }
  .feed-box.bad { border-color:#c62828; }
  .unauth-box { border:2px solid #c62828; border-radius:10px; padding:4px 16px; margin:16px 0;
                background:rgba(198,40,40,.08); }
  .unauth-box h2 { color:#c62828; }
  td.time a { color:inherit; text-decoration:none; border-bottom:1px dotted var(--muted); }
  .toolbar { display:flex; gap:8px; flex-wrap:wrap; margin:10px 0; }
  .toolbar input { flex:1 1 220px; padding:8px 10px; border-radius:8px; border:1px solid var(--line);
                   background:var(--card); color:inherit; font:inherit; }
  .toolbar select { padding:8px 10px; border-radius:8px; border:1px solid var(--line);
                    background:var(--card); color:inherit; font:inherit; }
  .chips { display:flex; gap:6px; flex-wrap:wrap; margin-bottom:6px; }
  .chip { padding:5px 12px; border-radius:999px; border:1px solid var(--line); background:transparent;
          color:inherit; font:inherit; font-size:13px; cursor:pointer; }
  .chip.on { background:var(--card); border-color:var(--muted); font-weight:600; }
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

$unauthBlock
$recording
$apiBlock
${_feedHtml(result.feed)}
$moduleBlock  <h2>Checklist</h2>
$toolbar  <div class="scroll">
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

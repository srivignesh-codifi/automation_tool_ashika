import '../models/report_modules.dart';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/services.dart';
import 'package:intl/intl.dart';
import 'package:path_provider/path_provider.dart';
import 'package:share_plus/share_plus.dart';

import '../models/automation_result.dart';
import '../models/automation_step.dart';
import '../models/run_details.dart';
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
    'com.codifi.itrade': 'itrade',
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
  /// `<app>_<ddMMyyyy>_<HHmm>.zip` (see [zipNameFor]) in app-private storage.
  /// It is retained there for Send to admin; it is not copied to public
  /// Downloads. Best effort: a failure is returned as text and never fails save.
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
      // `failed_<step>` repeats the case's own failure shot, taken a moment earlier: the report links the case's.
      if (_duplicateFailedShot(result, shot.key)) continue;
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

    // ── Tested by (who, and the phone's IP addresses as the run started) ──
    _writeTestedBy(out, result);

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
    _writeWatchlistAdds(out, result);
    _writeOrderTypes(out, result);

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
          if (!_duplicateFailedShot(result, e.key))
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

  /// The tool user who ran the test, and the phone's IP addresses as the run
  /// started. Left out for a run that recorded neither.
  static void _writeTestedBy(StringBuffer out, AutomationResult result) {
    final by = result.testedBy;
    final net = result.network;
    if (by == null && net == null) return;
    _heading(out, 'TESTED BY');
    if (by == null) {
      _kv(
        out,
        'User',
        'not recorded (no signed-in user was sent with the run)',
      );
    } else {
      _kv(out, 'User ID', by.userId);
      if (by.role != null) _kv(out, 'Role', by.role!);
      _kv(
        out,
        'Signed in with',
        '${by.methodLabel}'
            '${by.signedInAt == null ? '' : ' at ${_stamp.format(by.signedInAt!)}'}',
      );
    }
    if (net != null) {
      _kv(out, 'Device IP', '${net.deviceIp}  (${net.connection})');
      if (net.ipv4.isNotEmpty && net.ipv6.isNotEmpty) {
        _kv(out, 'Device IPv6', net.ipv6.join(', '));
      }
      _kv(
        out,
        'Public IP',
        net.publicIp == null
            ? net.publicIpLabel
            : '${net.publicIp}  (as ${net.publicIpSource ?? 'the internet'} saw it)',
      );
      if (net.capturedAt != null) {
        _kv(
          out,
          'IP captured',
          '${_stamp.format(net.capturedAt!)}, as the run started',
        );
      }
    }
    out.writeln();
  }

  /// Every instrument the run added to a watchlist: from where, into which
  /// list, whether it was added and shown, and whether it was taken out again.
  static void _writeWatchlistAdds(StringBuffer out, AutomationResult result) {
    final adds = result.watchlistAdds;
    if (adds.isEmpty) return;
    _heading(out, 'WATCHLIST — INSTRUMENTS ADDED');
    out
      ..writeln('  ${_addsSummary(adds)}')
      ..writeln();
    _table(
      out,
      const <String>[
        '#',
        'Case',
        'Instrument',
        'Exch',
        'Type',
        'Added from',
        'Watchlist',
        'Added',
        'In watchlist',
        'Removed after',
      ],
      <List<String>>[
        for (var i = 0; i < adds.length; i++)
          <String>[
            '${i + 1}',
            adds[i].code ?? '-',
            _clip(adds[i].scrip, 28),
            adds[i].exchange ?? '-',
            adds[i].type ?? '-',
            _clip(adds[i].from, 30),
            _clip(adds[i].watchlist ?? '-', 18),
            adds[i].addedLabel,
            adds[i].listedLabel,
            adds[i].removedLabel,
          ],
      ],
      rightAlign: const <int>{0},
    );
    for (var i = 0; i < adds.length; i++) {
      final detail = adds[i].detail;
      if (detail == null || detail.isEmpty) continue;
      final lines = _wrap('${i + 1}. ${adds[i].scrip}: $detail', 94);
      for (final line in lines) {
        out.writeln('  $line');
      }
    }
    out.writeln();
  }

  /// "6 tried: 5 added, 1 not added; 5 shown in the watchlist; 4 removed again."
  static String _addsSummary(List<WatchlistAddition> adds) {
    int count(bool Function(WatchlistAddition) test) => adds.where(test).length;
    final added = count((a) => a.added && !a.alreadyThere);
    final there = count((a) => a.alreadyThere);
    final notAdded = count((a) => !a.added && !a.alreadyThere);
    final listed = count((a) => a.listed == true);
    final removed = count((a) => a.removed == true);
    final left = count((a) => a.removed == false);
    return '${adds.length} tried: $added added, $notAdded not added'
        '${there > 0 ? ', $there already in the watchlist' : ''}; '
        '$listed shown in the watchlist; $removed removed again'
        '${left > 0 ? ', $left STILL IN THE WATCHLIST (remove by hand)' : ''}.';
  }

  /// The kinds of order the app offers and what the run did with each.
  static void _writeOrderTypes(StringBuffer out, AutomationResult result) {
    final o = result.orderTypes;
    if (o == null) return;
    _heading(out, 'ORDER TYPES — ${o.app.toUpperCase()}');
    for (final line in _wrap(
      'Offered by ${o.app}: ${o.offered.map((t) => t.shortLabel).join(', ')}.',
      94,
    )) {
      out.writeln('  $line');
    }
    if (o.notOffered.isNotEmpty) {
      out.writeln(
        '  Not in ${o.app}: ${o.notOffered.map((t) => t.shortLabel).join(', ')}.',
      );
    }
    if (!o.moduleRun) {
      out.writeln(
        '  Order placement was not selected for this run, so none was tested.',
      );
    }
    out.writeln();
    final outcomes = <OrderTypeOutcome>[
      for (final t in o.types) o.outcomeOf(t, result.steps),
    ];
    _table(
      out,
      const <String>['Group', 'Order type', 'In app', 'Exchanges', 'This run'],
      <List<String>>[
        for (var i = 0; i < o.types.length; i++)
          <String>[
            o.types[i].group,
            o.types[i].label,
            o.types[i].offered ? 'YES' : 'NO',
            o.types[i].exchanges.join(' '),
            outcomes[i].label,
          ],
      ],
    );
    for (var i = 0; i < o.types.length; i++) {
      if (outcomes[i].detail.isEmpty) continue;
      for (final line in _wrap(
        '${o.types[i].shortLabel}: ${outcomes[i].detail}',
        94,
      )) {
        out.writeln('  $line');
      }
    }
    out
      ..writeln(
        '  SENT · REFUSED is the expected result: an order is only sent while the pad shows',
      )
      ..writeln(
        '  Avail. Margin 0.00 and a shortfall, so the exchange refuses it. ACCEPTED is a defect.',
      )
      ..writeln();
  }

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
        '  From what the app reports about its own feed socket (ws_feed_status), '
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
        '<p class="muted">From what the app reports about its own feed socket, read after every step; not network traffic.</p>'
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

  /// The cases that failed or could not run: what the findings sections list.
  static List<AutomationStep> _findings(List<AutomationStep> cases) => cases
      .where(
        (s) => s.status == StepStatus.failed || s.outcomeLabel == 'BLOCKED',
      )
      .toList();

  /// The QA verdict of a run's [cases], shared by the text and HTML reports. [level] is ready, fixes, not_ready,
  /// review or none (no case ran), for the HTML's colour.
  static ({String verdict, String level, int cleanupFailed}) _assess(
    List<AutomationStep> cases,
  ) {
    final findings = _findings(cases);
    final defects = findings
        .where((s) => (s.cause ?? 'unconfirmed') == 'app_defect')
        .toList();
    final critical = defects.where((s) => s.severity == 'Critical').length;
    final unresolved = findings.length - defects.length;
    final notTested = cases.where((s) => s.outcomeLabel == 'NOT TESTED').length;
    final cleanupFailed = cases
        .where((s) => s.cleanup != null && s.cleanup!.startsWith('FAILED'))
        .length;
    if (cases.every((s) => s.status == StepStatus.pending)) {
      return (
        verdict: 'Not assessed: no test case ran.',
        level: 'none',
        cleanupFailed: cleanupFailed,
      );
    }
    if (critical > 0) {
      return (
        verdict: 'Not ready: $critical critical application defect(s).',
        level: 'not_ready',
        cleanupFailed: cleanupFailed,
      );
    }
    if (defects.isNotEmpty) {
      return (
        verdict:
            'Needs fixes: ${defects.length} confirmed application defect(s), none critical.',
        level: 'fixes',
        cleanupFailed: cleanupFailed,
      );
    }
    if (unresolved > 0 || notTested > 0) {
      return (
        verdict:
            'No application defect confirmed. $unresolved case(s) could not be verified or need review, and '
            '$notTested were not tested: they are not passes.',
        level: 'review',
        cleanupFailed: cleanupFailed,
      );
    }
    return (
      verdict: 'Ready: every applicable case passed.',
      level: 'ready',
      cleanupFailed: cleanupFailed,
    );
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
    final qa = _assess(cases);
    out
      ..writeln('  ${qa.verdict}')
      ..writeln(
        qa.cleanupFailed > 0
            ? '  WARNING: ${qa.cleanupFailed} case(s) could not put back what they changed: see CLEANUP above.'
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

  /// The screenshot the report shows for [step]: its test case's own shot first, else the step's failure shot.
  static String? _screenshotFor(AutomationResult result, AutomationStep step) =>
      _caseShot(result, step);

  /// Whether [key] is a step's `failed_<id>` shot whose test case has a shot of its own (left out of the zip).
  static bool _duplicateFailedShot(AutomationResult result, String key) {
    if (!key.startsWith('failed_')) return false;
    final id = key.substring('failed_'.length);
    final step = result.steps.where((s) => s.id == id).firstOrNull;
    final code = step?.code;
    if (code == null) return false;
    return result.screenshots.keys.any(
      (k) => k != key && k.startsWith('${code}_'),
    );
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

  /// The CSS class that colours an outcome label (PASS, FAIL, BLOCKED, ...).
  static String _outcomeClass(String label) => switch (label) {
    'PASS' || 'PASSED' => 'o-pass',
    'FAIL' || 'FAILED' => 'o-fail',
    'BLOCKED' => 'o-blocked',
    'NOT APPLICABLE' => 'o-na',
    'SKIPPED' || 'SKIP' => 'o-skip',
    _ => 'o-nt',
  };

  /// Where a case's screenshot is next to the report: the zip holds it under screenshots/ as a JPEG.
  static String? _shotHref(AutomationResult result, AutomationStep s) {
    final name = _caseShot(result, s) ?? _screenshotFor(result, s);
    if (name == null) return null;
    final dot = name.lastIndexOf('.');
    return 'screenshots/${dot < 0 ? name : name.substring(0, dot)}.jpg';
  }

  /// A stacked bar of [counts] (outcome label to number), out of [total].
  static String _bar(Map<String, int> counts, int total) {
    if (total == 0) return '<div class="bar"></div>';
    final parts = counts.entries
        .where((e) => e.value > 0)
        .map(
          (e) =>
              '<span class="${_outcomeClass(e.key)}" style="width:${(e.value * 100 / total).toStringAsFixed(2)}%" '
              'title="${_esc(e.key)}: ${e.value}"></span>',
        )
        .join();
    return '<div class="bar">$parts</div>';
  }

  /// How many of [steps] ended each way, in report order.
  static Map<String, int> _outcomeCounts(Iterable<AutomationStep> steps) {
    final counts = <String, int>{
      for (final o in <String>[
        'PASS',
        'FAIL',
        'BLOCKED',
        'NOT APPLICABLE',
        'NOT TESTED',
        'SKIPPED',
      ])
        o: 0,
    };
    for (final s in steps) {
      final label = switch (s.outcomeLabel) {
        'PASSED' => 'PASS',
        'FAILED' => 'FAIL',
        'SKIP' => 'SKIPPED',
        final other => counts.containsKey(other) ? other : 'NOT TESTED',
      };
      counts[label] = counts[label]! + 1;
    }
    return counts;
  }

  /// The findings classes of the HTML report: cause id, title, letter, colour class.
  static const List<(String, String, String, String)> _findingClasses =
      <(String, String, String, String)>[
        ('app_defect', 'Confirmed application defects', 'D', 'c-app'),
        (
          'unconfirmed',
          'Needs review: expected content not found',
          'R',
          'c-review',
        ),
        ('automation', 'Automation / locator failures', 'A', 'c-auto'),
        (
          'accessibility',
          'Accessibility limitations (not a defect)',
          'X',
          'c-a11y',
        ),
        ('api_data', 'API / data dependency failures', 'P', 'c-api'),
        ('test_data', 'Test-data / state failures', 'T', 'c-data'),
      ];

  /// Overview cards, the QA assessment, findings by class, the test-case table and the not-tested, cleanup and
  /// performance lists: the HTML side of [_caseSections].
  static String _caseSectionsHtml(AutomationResult result) {
    final cases = result.steps.where((s) => s.isCase).toList();
    if (cases.isEmpty) return '';
    final counts = _outcomeCounts(cases);
    final total = cases.length;
    final qa = _assess(cases);
    final out = StringBuffer();

    String pct(int n) => (n * 100 / total).round().toString();
    out
      ..writeln('  <section class="overview">')
      ..writeln('  <div class="kpis">')
      ..writeln(
        '    <div class="kpi o-total"><b>$total</b><span>Test cases</span></div>',
      );
    for (final e in counts.entries) {
      if (e.value == 0 && (e.key == 'SKIPPED' || e.key == 'BLOCKED')) continue;
      out.writeln(
        '    <div class="kpi ${_outcomeClass(e.key)}"><b>${e.value}</b><span>${_esc(e.key)}</span><i>${pct(e.value)}%</i></div>',
      );
    }
    out
      ..writeln('  </div>')
      ..writeln('  ${_bar(counts, total)}')
      ..writeln(
        '  <div class="qa qa-${qa.level}"><strong>QA assessment</strong><p>${_esc(qa.verdict)}</p>'
        '<p>${qa.cleanupFailed > 0 ? '⚠ ${qa.cleanupFailed} case(s) could not put back what they changed: see Cleanup.' : 'Everything the cases changed was put back.'}</p>'
        '<p class="muted">Only cases that ran are counted; a case that could not run or could not be read is never reported as PASS.</p></div>',
      )
      ..writeln('  </section>');

    // Findings, by what they turned out to be.
    final findings = _findings(cases);
    out
      ..writeln('  <h2>Findings</h2>')
      ..writeln('  <div class="classes">');
    for (final c in _findingClasses) {
      final n = findings
          .where((s) => (s.cause ?? 'unconfirmed') == c.$1)
          .length;
      out.writeln(
        '    <a class="cls ${c.$4}${n == 0 ? ' none' : ''}" href="#${c.$4}"><b>$n</b> ${_esc(c.$2)}</a>',
      );
    }
    out.writeln('  </div>');
    if (findings.isEmpty) {
      out.writeln('  <p class="muted">No case failed or was blocked.</p>');
    }
    for (final c in _findingClasses) {
      final items = findings
          .where((s) => (s.cause ?? 'unconfirmed') == c.$1)
          .toList();
      if (items.isEmpty) continue;
      out.writeln(
        '  <h3 id="${c.$4}" class="ftitle ${c.$4}">${_esc(c.$2)} <span>${items.length}</span></h3>',
      );
      for (var i = 0; i < items.length; i++) {
        final s = items[i];
        final severity = s.severity ?? 'Medium';
        final observed = s.status == StepStatus.failed
            ? s.failureReason
            : s.note;
        final href = _shotHref(result, s);
        String row(String key, String? value, [String cls = '']) =>
            value == null || value.isEmpty
            ? ''
            : '<dt>$key</dt><dd class="$cls">${_esc(value)}</dd>';
        final thumb = href == null
            ? ''
            : '<a class="thumb" href="${_esc(href)}" target="_blank"><img loading="lazy" src="${_esc(href)}" '
                  'alt="Screenshot of ${_esc(s.code ?? s.name)}" onerror="this.parentNode.classList.add(\'missing\');this.remove()"></a>';
        final hierarchy = s.evidence == null || s.evidence!.isEmpty
            ? ''
            : '<details><summary>UI hierarchy</summary><pre>${_esc(s.evidence!)}</pre></details>';
        out
          ..writeln('  <article class="finding ${c.$4}">')
          ..writeln(
            '    <header><span class="fid">${c.$3}${i + 1}</span><span class="code">${_esc(s.code ?? '')}</span>'
            '<span class="sev sev-${severity.toLowerCase()}">${_esc(severity)}</span><span class="fname">${_esc(s.name)}</span></header>',
          )
          ..writeln(
            '    <div class="fbody"><dl>${row('Expected', s.expected)}${row('Observed', observed, 'obs')}'
            '${row('Cause', '${s.causeTag}: ${s.causeLabel}')}${row('Recommendation', s.recommendation, 'rec')}'
            '${row('Cleanup', s.cleanup)}</dl>$thumb</div>',
          )
          ..writeln('    $hierarchy')
          ..writeln('  </article>');
      }
    }

    // The formal test-case table, filterable by outcome.
    out
      ..writeln('  <h2>Test cases</h2>')
      ..writeln('  <div class="chips" id="cf">')
      ..writeln(
        '    <button type="button" class="chip on" data-o="">All ($total)</button>',
      );
    for (final e in counts.entries.where((e) => e.value > 0)) {
      final o = _outcomeClass(e.key);
      out.writeln(
        '    <button type="button" class="chip $o" data-o="$o">${_esc(e.key)} (${e.value})</button>',
      );
    }
    out
      ..writeln('  </div>')
      ..writeln('  <div class="scroll"><table class="cases">')
      ..writeln(
        '    <thead><tr><th>ID</th><th>Scenario</th><th>Expected result</th><th>Actual result</th>'
        '<th>Status</th><th>Class</th><th>Severity</th><th>Shot</th></tr></thead><tbody>',
      );
    for (final s in cases) {
      final o = _outcomeClass(s.outcomeLabel);
      final actual =
          (s.status == StepStatus.failed ? s.failureReason : null) ??
          s.note ??
          '';
      final href = _shotHref(result, s);
      out.writeln(
        '    <tr class="$o" data-o="$o"><td class="id">${_esc(s.code ?? '')}</td><td>${_esc(s.name)}</td>'
        '<td class="small">${_esc(s.expected ?? '')}</td><td class="small">${_esc(actual)}</td>'
        '<td><span class="pill $o">${_esc(s.outcomeLabel)}</span></td><td class="small">${_esc(_classShort(s))}</td>'
        '<td>${s.severity == null ? '' : '<span class="sev sev-${s.severity!.toLowerCase()}">${_esc(s.severity!)}</span>'}</td>'
        '<td>${href == null ? '' : '<a href="${_esc(href)}" target="_blank">view</a>'}</td></tr>',
      );
    }
    out.writeln('    </tbody></table></div>');

    final untested = cases
        .where(
          (s) =>
              s.outcomeLabel == 'NOT TESTED' ||
              s.outcomeLabel == 'NOT APPLICABLE',
        )
        .toList();
    if (untested.isNotEmpty) {
      out.writeln('  <h2>Not tested / not applicable</h2><ul class="list">');
      for (final s in untested) {
        out.writeln(
          '    <li><span class="pill ${_outcomeClass(s.outcomeLabel)}">${_esc(s.outcomeLabel)}</span> '
          '<b>${_esc(s.code ?? '')}</b> ${_esc(s.note ?? s.name)}</li>',
        );
      }
      out.writeln('  </ul>');
    }

    final cleaned = cases.where((s) => s.cleanup != null).toList();
    if (cleaned.isNotEmpty) {
      out.writeln(
        '  <h2>Cleanup of test-created changes</h2><ul class="list">',
      );
      for (final s in cleaned) {
        final o = s.cleanup!.startsWith('FAILED') ? 'o-fail' : 'o-pass';
        out.writeln(
          '    <li><span class="pill $o">${o == 'o-fail' ? 'NOT RESTORED' : 'RESTORED'}</span> '
          '<b>${_esc(s.code ?? '')}</b> ${_esc(s.cleanup!)}</li>',
        );
      }
      out.writeln('  </ul>');
    }

    final slow = cases.where((s) => s.apiMs != null && s.apiMs! > 0).toList()
      ..sort((a, b) => b.apiMs!.compareTo(a.apiMs!));
    if (slow.isNotEmpty) {
      final top = slow.first.apiMs!;
      out.writeln('  <h2>Performance (slowest checks)</h2><div class="perf">');
      for (final s in slow.take(8)) {
        final width = (s.apiMs! * 100 / top).clamp(4, 100).toStringAsFixed(1);
        final cls = s.apiMs! > _slowSeconds * 1000 ? ' class="slow"' : '';
        out.writeln(
          '    <div class="prow"><span class="pt">${_esc(_ms(s.apiMs!))}</span>'
          '<span class="pb"><i style="width:$width%"$cls></i></span>'
          '<span class="pl"><b>${_esc(s.code ?? '')}</b> ${_esc(s.apiDetail ?? s.name)}</span></div>',
        );
      }
      out.writeln('  </div>');
    }
    return out.toString();
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
    final byModule = <String, List<AutomationStep>>{};
    for (final s in result.steps) {
      byModule.putIfAbsent(moduleLabelOf(s), () => <AutomationStep>[]).add(s);
    }
    String rowClass(ModuleStats m) =>
        m.failed > 0 ? 'failed' : (m.outcome == 'PASS' ? 'passed' : 'skipped');
    final moduleRows = stats
        .map(
          (m) =>
              '      <tr class="${rowClass(m)}" data-pick="${_esc(m.label)}" title="Show only ${_esc(m.label)}">\n'
              '        <td class="name">${_esc(m.label)}</td><td>${m.total}</td><td>${m.passed}</td><td>${m.failed}</td><td>${m.skipped + m.pending}</td>\n'
              '        <td class="barcell">${_bar(_outcomeCounts(byModule[m.label] ?? const <AutomationStep>[]), m.total)}</td>\n'
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
    <thead><tr><th>Module</th><th>Steps</th><th>Pass</th><th>Fail</th><th>Skipped / not tested</th><th>Outcome</th><th>Result</th></tr></thead>
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
      var rows = document.querySelectorAll('table.steps:not(.modsum):not(.extra) tbody tr');
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

    // The test-case table's outcome chips.
    const caseFilterScript = r'''
  <script type="text/javascript">
    (function () {
      var bar = document.getElementById('cf');
      if (!bar) return;
      var rows = document.querySelectorAll('table.cases tbody tr');
      bar.addEventListener('click', function (e) {
        var c = e.target.closest('.chip');
        if (!c) return;
        bar.querySelectorAll('.chip').forEach(function (x) { x.classList.toggle('on', x === c); });
        rows.forEach(function (r) { r.style.display = !c.dataset.o || r.dataset.o === c.dataset.o ? '' : 'none'; });
      });
    })();
  </script>''';
    final filterScriptTag =
        (result.steps.isEmpty ? '' : filterScript) + caseFilterScript;

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
  :root { color-scheme: light dark; --fg:#16181d; --bg:#f6f7fb; --muted:#5d6470;
          --line:#e3e6ec; --card:#fff;
          --pass:#15803d; --fail:#dc2626; --blocked:#d97706; --na:#2563eb; --nt:#6b7280; --skip:#a16207;
          --app:#dc2626; --review:#d97706; --auto:#7c3aed; --a11y:#0284c7; --api:#0d9488; --data:#ea580c;
          --orange:#ee6a4b; --purple:#8e5cf7; --blue:#4a7dff; }
  @media (prefers-color-scheme: dark) {
    :root { --fg:#e8eaee; --bg:#0b0b10; --muted:#9aa2af; --line:#2b2f37; --card:#15151d;
            --pass:#4ade80; --fail:#f87171; --blocked:#fbbf24; --na:#60a5fa; --nt:#9ca3af; --skip:#eab308;
            --app:#f87171; --review:#fbbf24; --auto:#a78bfa; --a11y:#38bdf8; --api:#2dd4bf; --data:#fb923c; }
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
  /* Header band in CodiFi's colours. */
  .hero { margin:-24px -24px 20px; padding:22px 24px 18px; color:#fff;
          background:linear-gradient(120deg,#0b0b10 0%,#2a1640 45%,#1d2a6b 75%,#ee6a4b 130%); }
  .hero h1 { color:#fff; font-size:24px; }
  .hero p { margin:8px 0 0; color:#d6d6e4; font-size:13px; }
  .brand { font-size:12px; letter-spacing:1.2px; text-transform:uppercase; color:#ffb59f; margin-bottom:6px; }
  .brand .logo { display:inline-block; width:18px; height:18px; border-radius:50%; text-align:center; line-height:18px;
                 font-weight:800; color:#fff; background:linear-gradient(135deg,var(--orange),var(--purple),var(--blue)); }
  .hero-row { display:flex; flex-wrap:wrap; gap:8px; align-items:center; margin-top:10px; }
  .hero .verdict { margin:0; }
  .hchip { font-size:12.5px; padding:4px 10px; border-radius:999px; background:rgba(255,255,255,.12);
           border:1px solid rgba(255,255,255,.18); }
  /* Overview: coloured counts, the outcome bar and the QA verdict. */
  .kpis { display:grid; grid-template-columns:repeat(auto-fit,minmax(118px,1fr)); gap:10px; }
  .kpi { background:var(--card); border:1px solid var(--line); border-top:4px solid var(--c,var(--muted));
         border-radius:12px; padding:10px 12px; }
  .kpi b { display:block; font-size:26px; line-height:1.1; color:var(--c,var(--fg)); }
  .kpi span { font-size:11.5px; letter-spacing:.5px; text-transform:uppercase; color:var(--muted); }
  .kpi i { float:right; font-style:normal; font-size:12px; color:var(--c); }
  .o-total { --c:var(--orange); } .o-pass { --c:var(--pass); } .o-fail { --c:var(--fail); }
  .o-blocked { --c:var(--blocked); } .o-na { --c:var(--na); } .o-nt { --c:var(--nt); } .o-skip { --c:var(--skip); }
  .bar { display:flex; height:12px; border-radius:999px; overflow:hidden; background:var(--line); margin:14px 0; }
  .bar span { display:block; height:100%; background:var(--c); }
  td.barcell { min-width:160px; } td.barcell .bar { margin:6px 0; height:10px; }
  .qa { border-radius:12px; padding:12px 16px; border-left:6px solid var(--q); background:var(--card);
        border-top:1px solid var(--line); border-right:1px solid var(--line); border-bottom:1px solid var(--line); }
  .qa strong { color:var(--q); text-transform:uppercase; letter-spacing:.6px; font-size:12.5px; }
  .qa p { margin:4px 0; }
  .qa-ready { --q:var(--pass); } .qa-fixes { --q:var(--blocked); } .qa-not_ready { --q:var(--fail); }
  .qa-review { --q:var(--na); } .qa-none { --q:var(--nt); }
  .pill.o-pass, .pill.o-fail, .pill.o-blocked, .pill.o-na, .pill.o-nt, .pill.o-skip {
    color:var(--c); border-color:var(--c); background:color-mix(in srgb, var(--c) 12%, transparent); font-weight:600; }
  /* Findings: a colour per class, a badge per severity. */
  .c-app { --k:var(--app); } .c-review { --k:var(--review); } .c-auto { --k:var(--auto); }
  .c-a11y { --k:var(--a11y); } .c-api { --k:var(--api); } .c-data { --k:var(--data); }
  .classes { display:flex; flex-wrap:wrap; gap:8px; }
  .cls { text-decoration:none; color:var(--k); border:1px solid var(--k); border-radius:999px; padding:4px 12px;
         font-size:13px; background:color-mix(in srgb, var(--k) 10%, transparent); }
  .cls b { font-size:15px; } .cls.none { opacity:.45; }
  h3.ftitle { color:var(--k); font-size:15px; margin:22px 0 8px; padding-bottom:4px; border-bottom:2px solid var(--k); }
  h3.ftitle span { font-size:12px; color:#fff; background:var(--k); border-radius:999px; padding:1px 8px; margin-left:6px; }
  .finding { background:var(--card); border:1px solid var(--line); border-left:5px solid var(--k);
             border-radius:12px; padding:10px 14px; margin:10px 0; }
  .finding header { display:flex; flex-wrap:wrap; gap:8px; align-items:center; font-weight:600; }
  .fid { color:#fff; background:var(--k); border-radius:6px; padding:1px 7px; font-size:12px; }
  .code { font-family:ui-monospace,Menlo,monospace; color:var(--k); }
  .fbody { display:flex; gap:14px; align-items:flex-start; }
  .fbody dl { flex:1; display:grid; grid-template-columns:max-content 1fr; gap:4px 12px; margin:10px 0 4px; font-size:13.5px; }
  .fbody dt { color:var(--muted); } .fbody dd { margin:0; }
  dd.obs { color:var(--k); } dd.rec { font-style:italic; }
  .thumb img { width:120px; border-radius:8px; border:1px solid var(--line); margin-top:10px; }
  .thumb.missing { display:none; }
  .finding details summary { cursor:pointer; color:var(--muted); font-size:12.5px; }
  .finding pre { white-space:pre-wrap; font-size:11.5px; color:var(--muted); max-height:220px; overflow:auto; }
  .sev { font-size:11px; font-weight:700; letter-spacing:.4px; border-radius:6px; padding:1px 7px; color:#fff; }
  .sev-critical { background:#7f1d1d; } .sev-high { background:#dc2626; } .sev-medium { background:#d97706; }
  .sev-low { background:#64748b; }
  /* The test-case table and the lists. */
  table.cases { border-collapse:collapse; width:100%; min-width:900px; background:var(--card); border-radius:12px; overflow:hidden; }
  table.cases th, table.cases td { padding:8px 10px; border-bottom:1px solid var(--line); text-align:left; vertical-align:top; }
  table.cases th { font-size:12px; text-transform:uppercase; letter-spacing:.6px; color:var(--muted); }
  table.cases tr td:first-child { border-left:4px solid var(--c); }
  table.cases tr.o-fail td { background:color-mix(in srgb, var(--fail) 7%, transparent); }
  table.cases tr.o-blocked td { background:color-mix(in srgb, var(--blocked) 7%, transparent); }
  td.id { font-family:ui-monospace,Menlo,monospace; white-space:nowrap; font-weight:600; color:var(--c); }
  td.small { font-size:12.5px; color:var(--muted); }
  .chip.o-pass, .chip.o-fail, .chip.o-blocked, .chip.o-na, .chip.o-nt, .chip.o-skip { color:var(--c); border-color:var(--c); }
  .chip.on.o-pass, .chip.on.o-fail, .chip.on.o-blocked, .chip.on.o-na, .chip.on.o-nt, .chip.on.o-skip {
    background:var(--c); color:#fff; }
  ul.list { list-style:none; padding:0; } ul.list li { padding:6px 0; border-bottom:1px solid var(--line); font-size:13.5px; }
  .perf .prow { display:grid; grid-template-columns:64px 160px 1fr; gap:10px; align-items:center; font-size:13px; padding:3px 0; }
  .perf .pt { font-variant-numeric:tabular-nums; text-align:right; color:var(--muted); }
  .perf .pb { height:8px; background:var(--line); border-radius:999px; overflow:hidden; }
  .perf .pb i { display:block; height:100%; background:linear-gradient(90deg,var(--blue),var(--purple)); }
  .perf .pb i.slow { background:linear-gradient(90deg,var(--orange),var(--fail)); }
  /* The checklist: each row tinted by its status. */
  table.steps:not(.modsum) tr.failed td { background:color-mix(in srgb, var(--fail) 8%, transparent); }
  table.steps:not(.modsum) tr.failed td.glyph { box-shadow:inset 4px 0 var(--fail); }
  table.steps:not(.modsum) tr.passed td.glyph { box-shadow:inset 4px 0 var(--pass); }
  table.steps:not(.modsum) tr.skipped td.glyph { box-shadow:inset 4px 0 var(--nt); }
  table.steps { background:var(--card); }
  h2 { color:var(--fg); border-left:4px solid var(--orange); padding-left:10px; }
</style>
</head>
<body>
  <header class="hero">
    <div class="brand"><span class="logo">C</span> CodiFi · Automation report</div>
    <h1>${_esc(result.testName)}</h1>
    <div class="hero-row">
      <span class="verdict ${result.verdict}">${result.verdict}</span>
      <span class="hchip">${_esc(result.targetVersionName == null ? 'version —' : 'v${result.targetVersionName}')}</span>
      <span class="hchip">${_esc('${result.deviceManufacturer} ${result.deviceModel}')} · Android ${_esc(result.androidVersion)}</span>
      <span class="hchip">${result.startedAt == null ? '—' : _esc(_stamp.format(result.startedAt!))}</span>
      <span class="hchip">${result.duration == null ? '—' : _esc(_formatDuration(result.duration!))}</span>
      ${result.testedBy == null ? '' : '<span class="hchip">Tested by ${_esc(result.testedBy!.userId)}</span>'}
    </div>
    <p>Completed ${result.completedSteps} of ${result.totalSteps} steps.</p>
  </header>
${_testedByHtml(result)}${_caseSectionsHtml(result)}${_watchlistAddsHtml(result)}${_orderTypesHtml(result)}
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

  /// Who ran the test, and the phone's IP addresses as the run started.
  static String _testedByHtml(AutomationResult result) {
    final by = result.testedBy;
    final net = result.network;
    if (by == null && net == null) return '';
    String row(String key, String value) =>
        '    <tr><td>${_esc(key)}</td><td>${_esc(value)}</td></tr>\n';
    final rows = StringBuffer();
    if (by == null) {
      rows.write(row('User', 'not recorded'));
    } else {
      rows
        ..write(row('User ID', by.userId))
        ..write(by.role == null ? '' : row('Role', by.role!))
        ..write(
          row(
            'Signed in with',
            '${by.methodLabel}${by.signedInAt == null ? '' : ' at ${_stamp.format(by.signedInAt!)}'}',
          ),
        );
    }
    if (net != null) {
      rows.write(row('Device IP', '${net.deviceIp} (${net.connection})'));
      if (net.ipv4.isNotEmpty && net.ipv6.isNotEmpty) {
        rows.write(row('Device IPv6', net.ipv6.join(', ')));
      }
      rows.write(
        row(
          'Public IP',
          net.publicIp == null
              ? net.publicIpLabel
              : '${net.publicIp} (as ${net.publicIpSource ?? 'the internet'} saw it)',
        ),
      );
      if (net.capturedAt != null) {
        rows.write(
          row(
            'IP captured',
            '${_stamp.format(net.capturedAt!)}, as the run started',
          ),
        );
      }
    }
    return '  <h2>Tested by</h2>\n  <table class="meta">\n$rows  </table>\n';
  }

  static String _yesNoPill(String label) {
    final css = switch (label) {
      'YES' ||
      'YES (was there)' ||
      'ALREADY THERE' ||
      "KEPT (user's)" => 'passed',
      'NO' || 'NO, STILL THERE' => 'failed',
      _ => 'skipped',
    };
    return '<span class="pill $css">${_esc(label)}</span>';
  }

  /// Every instrument the run added to a watchlist, and whether it got there.
  static String _watchlistAddsHtml(AutomationResult result) {
    final adds = result.watchlistAdds;
    if (adds.isEmpty) return '';
    final rows = StringBuffer();
    for (var i = 0; i < adds.length; i++) {
      final a = adds[i];
      rows.write(
        '      <tr class="${a.ok ? 'passed' : 'failed'}">'
        '<td>${i + 1}</td><td>${_esc(a.code ?? '—')}</td>'
        '<td class="name">${_esc(a.scrip)}</td><td>${_esc(a.exchange ?? '—')}</td><td>${_esc(a.type ?? '—')}</td>'
        '<td>${_esc(a.from)}</td><td>${_esc(a.watchlist ?? '—')}</td>'
        '<td>${_yesNoPill(a.addedLabel)}</td><td>${_yesNoPill(a.listedLabel)}</td><td>${_yesNoPill(a.removedLabel)}</td>'
        '<td class="detail">${_esc(a.detail ?? '')}</td></tr>\n',
      );
    }
    return '''
  <h2>Watchlist — instruments added</h2>
  <p>${_esc(_addsSummary(adds))}</p>
  <div class="scroll">
  <table class="steps extra">
    <thead><tr><th>#</th><th>Case</th><th>Instrument</th><th>Exch</th><th>Type</th><th>Added from</th><th>Watchlist</th><th>Added</th><th>In watchlist</th><th>Removed after</th><th>Detail</th></tr></thead>
    <tbody>
$rows    </tbody>
  </table>
  </div>
''';
  }

  /// The kinds of order the app offers and what the run did with each.
  static String _orderTypesHtml(AutomationResult result) {
    final o = result.orderTypes;
    if (o == null) return '';
    final rows = StringBuffer();
    for (final t in o.types) {
      final outcome = o.outcomeOf(t, result.steps);
      rows.write(
        '      <tr class="${outcome.css}">'
        '<td>${_esc(t.group)}</td><td class="name">${_esc(t.label)}<br><span class="muted">${_esc(t.meaning)}</span></td>'
        '<td><span class="pill ${t.offered ? 'passed' : 'skipped'}">${t.offered ? 'YES' : 'NO'}</span></td>'
        '<td>${_esc(t.exchanges.join(' '))}</td>'
        '<td class="status"><span class="pill ${outcome.css}">${_esc(outcome.label)}</span></td>'
        '<td class="detail">${_esc(outcome.detail)}</td></tr>\n',
      );
    }
    return '''
  <h2>Order types — ${_esc(o.app)}</h2>
  <p><strong>Offered by ${_esc(o.app)}:</strong> ${_esc(o.offered.map((t) => t.shortLabel).join(', '))}${o.notOffered.isEmpty ? '' : '<br><strong>Not in ${_esc(o.app)}:</strong> ${_esc(o.notOffered.map((t) => t.shortLabel).join(', '))}'}</p>
  ${o.moduleRun ? '' : '<p class="muted">Order placement was not selected for this run, so none was tested.</p>'}
  <div class="scroll">
  <table class="steps extra">
    <thead><tr><th>Group</th><th>Order type</th><th>In app</th><th>Exchanges</th><th>This run</th><th>Detail</th></tr></thead>
    <tbody>
$rows    </tbody>
  </table>
  </div>
  <p class="muted">SENT · REFUSED is the expected result: an order is only sent while the pad shows Avail. Margin 0.00
  and a shortfall, so the exchange refuses it. ACCEPTED is a defect.</p>
''';
  }

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

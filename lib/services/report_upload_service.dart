import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/foundation.dart' show debugPrint, visibleForTesting;

import '../models/test_run.dart';
import 'automation_service.dart';
import 'secure_storage_service.dart';

/// What the admin server answered: the run it filed the report under, and
/// where it can be seen.
class ReportUploadResult {
  const ReportUploadResult({this.runId, this.url});

  final String? runId;
  final String? url;
}

/// Why a report could not be sent, in words the report screen can show.
class ReportUploadException implements Exception {
  const ReportUploadException(this.message);

  final String message;

  @override
  String toString() => message;
}

/// Sends a multipart request: [fields] as form fields and [file] under
/// [fileField]. Returns the HTTP status and the response body.
typedef UploadTransport =
    Future<(int, String)> Function(
      Uri url,
      Map<String, String> fields,
      String fileField,
      File file, {
      String? bearer,
    });

/// Sends a saved run's report zip to the admin server
/// (`POST /api/mobile-reports`, multipart: the zip as `report`, and `meta`
/// JSON with who tested, which broker app and the run id). The zip is the one
/// the run already made (text and HTML reports, recording, screenshots); its
/// text report carries the "Run ID" line the server also reads.
class ReportUploadService {
  ReportUploadService({
    SecureStorageService? storage,
    UploadTransport? transport,
  }) : _storage = storage ?? SecureStorageService(),
       _transport = transport ?? _post;

  final SecureStorageService _storage;
  final UploadTransport _transport;

  /// The admin server's upload address. Plain HTTP on the office network for
  /// now (allowed for this address by the network security config); build with
  /// `--dart-define=REPORT_UPLOAD_URL=https://...` to point elsewhere.
  static const String uploadUrl = String.fromEnvironment(
    'REPORT_UPLOAD_URL',
    defaultValue: 'http://192.168.10.134:4000/api/mobile-reports',
  );

  /// A long run's zip (with its recording) can take a while on a slow network.
  static const Duration _timeout = Duration(minutes: 5);

  Future<ReportUploadResult> upload(TestRun run) async {
    if (run.zipPath.isEmpty) {
      throw ReportUploadException(
        run.zipError.isEmpty
            ? 'This run has no report zip to send.'
            : 'This run has no report zip to send: ${run.zipError}',
      );
    }
    final zip = File(run.zipPath);
    if (!await zip.exists()) {
      throw const ReportUploadException(
        'The report zip is no longer on this phone, so it cannot be sent.',
      );
    }

    final session = await _storage.readSession();
    final meta = <String, String>{
      if (session != null && session.$1.isNotEmpty) 'testedBy': session.$1,
      'broker': _brokerOf(run.targetPackage),
      'runId': run.runId,
    };
    final url = Uri.parse(uploadUrl);
    debugPrint('[Report API] POST $url');
    debugPrint(
      '[Report API] Request fields: ${jsonEncode(<String, String>{'meta': jsonEncode(meta)})}',
    );
    debugPrint(
      '[Report API] Request file: ${zip.uri.pathSegments.last} (${await zip.length()} bytes)',
    );
    if (session != null && session.$2.isNotEmpty) {
      debugPrint('[Report API] Authorization: Bearer [REDACTED]');
    }
    final (int status, String body) response;
    try {
      response = await _transport(
        url,
        <String, String>{'meta': jsonEncode(meta)},
        'report',
        zip,
        bearer: session == null || session.$2.isEmpty ? null : session.$2,
      ).timeout(_timeout);
    } on TimeoutException {
      throw ReportUploadException(
        'The admin server did not answer within ${_timeout.inMinutes} minutes. Try again.',
      );
    } on SocketException {
      throw ReportUploadException(
        'Cannot reach the admin server (${url.host}:${url.port}). '
        'Check that this phone is on the office network.',
      );
    } on HttpException {
      throw const ReportUploadException(
        'The admin server sent a broken reply. Try again.',
      );
    }

    final (status, body) = response;
    debugPrint('[Report API] HTTP $status ${body.trim()}');
    final json = _decode(body);
    if (status != 201 && status != 200) {
      final message = _message(json);
      throw ReportUploadException(
        'Sending failed (server error $status)${message == null ? '' : ': $message'}',
      );
    }
    return ReportUploadResult(
      runId: _string(json, 'runId') ?? run.runId,
      url: _string(json, 'url'),
    );
  }

  /// The app's name ("iTrade") rather than its package, as the server's broker.
  static String _brokerOf(String packageName) {
    for (final t in AutomationConfigInfo.knownTargets) {
      if (t.packageName == packageName) return t.displayName;
    }
    return packageName;
  }

  static Object? _decode(String body) {
    try {
      return jsonDecode(body);
    } catch (_) {
      return null;
    }
  }

  static String? _string(Object? json, String key) {
    if (json is! Map) return null;
    final value =
        json[key] ?? (json['data'] is Map ? (json['data'] as Map)[key] : null);
    return value is String && value.isNotEmpty ? value : null;
  }

  static String? _message(Object? json) {
    if (json is! Map) return null;
    for (final key in const <String>['message', 'error', 'msg', 'detail']) {
      final value = json[key];
      if (value is String && value.trim().isNotEmpty) return value.trim();
    }
    return null;
  }

  /// The real request, for a test against a local server.
  @visibleForTesting
  static Future<(int, String)> postMultipartForTest(
    Uri url,
    Map<String, String> fields,
    String fileField,
    File file,
  ) => _post(url, fields, fileField, file);

  /// A multipart/form-data POST, the file streamed from disk rather than read
  /// into memory (a run's zip can be tens of MB).
  static Future<(int, String)> _post(
    Uri url,
    Map<String, String> fields,
    String fileField,
    File file, {
    String? bearer,
  }) async {
    final boundary = '----codifi-${DateTime.now().microsecondsSinceEpoch}';
    final head = StringBuffer();
    fields.forEach((name, value) {
      head
        ..write('--$boundary\r\n')
        ..write('Content-Disposition: form-data; name="$name"\r\n\r\n')
        ..write('$value\r\n');
    });
    final fileName = file.uri.pathSegments.last;
    head
      ..write('--$boundary\r\n')
      ..write(
        'Content-Disposition: form-data; name="$fileField"; filename="$fileName"\r\n',
      )
      ..write('Content-Type: application/zip\r\n\r\n');
    final headBytes = utf8.encode(head.toString());
    final tailBytes = utf8.encode('\r\n--$boundary--\r\n');

    final client = HttpClient()
      ..connectionTimeout = const Duration(seconds: 20);
    try {
      final request = await client.postUrl(url);
      request.headers.set(
        HttpHeaders.contentTypeHeader,
        'multipart/form-data; boundary=$boundary',
      );
      if (bearer != null) {
        request.headers.set(HttpHeaders.authorizationHeader, 'Bearer $bearer');
      }
      request.contentLength =
          headBytes.length + await file.length() + tailBytes.length;
      request.add(headBytes);
      await request.addStream(file.openRead());
      request.add(tailBytes);
      final response = await request.close();
      return (
        response.statusCode,
        await response.transform(utf8.decoder).join(),
      );
    } finally {
      client.close(force: true);
    }
  }
}

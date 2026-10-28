import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile_automation_tool/models/test_run.dart';
import 'package:mobile_automation_tool/screens/dashboard_screen.dart';
import 'package:mobile_automation_tool/services/automation_service.dart';
import 'package:mobile_automation_tool/services/report_service.dart';
import 'package:mobile_automation_tool/services/report_upload_service.dart';
import 'package:mobile_automation_tool/services/secure_storage_service.dart';
import 'package:mobile_automation_tool/theme/codifi_theme.dart';
import 'package:mobile_automation_tool/widgets/send_to_admin_button.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late Directory dir;
  late File zip;

  setUp(() {
    FlutterSecureStorage.setMockInitialValues(<String, String>{});
    dir = Directory.systemTemp.createTempSync('upload');
    zip = File('${dir.path}/itrade_28102026_1437.zip')
      ..writeAsBytesSync(<int>[1, 2, 3]);
  });
  tearDown(() => dir.deleteSync(recursive: true));

  TestRun run({String? zipPath}) => TestRun.fromJson(<String, Object?>{
    'runId': '549102bb-72db-48b0-871b-e7a3b001def7',
    'testName': 'iTrade - Regression Automation',
    'targetPackage': 'com.codifi.itrade',
    'startedAtMs': 1792838273000,
    'passed': false,
    'stopped': false,
    'completedSteps': 149,
    'totalSteps': 165,
    'jsonPath': '',
    'htmlPath': '',
    'textPath': '',
    'videoPath': '',
    'zipPath': zipPath ?? zip.path,
    'zipFolder': '',
    'zipError': '',
  });

  test(
    'the zip goes to the admin server with who tested, the broker app and the run id',
    () async {
      final storage = SecureStorageService();
      await storage.saveSession(userId: 'qa1', token: 'tok-9');
      Uri? sentTo;
      Map<String, String>? sentFields;
      String? sentField;
      File? sentFile;
      String? sentBearer;
      final uploader = ReportUploadService(
        storage: storage,
        transport: (url, fields, fileField, file, {bearer}) async {
          sentTo = url;
          sentFields = fields;
          sentField = fileField;
          sentFile = file;
          sentBearer = bearer;
          return (
            201,
            '{"runId":"549102bb","url":"http://admin/reports/549102bb"}',
          );
        },
      );

      final sent = await uploader.upload(run());
      expect(
        sentTo.toString(),
        'http://192.168.10.134:4000/api/mobile-reports',
      );
      expect(sentField, 'report');
      expect(sentFile?.path, zip.path);
      expect(sentBearer, 'tok-9');
      expect(jsonDecode(sentFields!['meta']!), <String, Object?>{
        'testedBy': 'qa1',
        'broker': 'iTrade',
        'runId': '549102bb-72db-48b0-871b-e7a3b001def7',
      });
      expect(sent.runId, '549102bb');
      expect(sent.url, 'http://admin/reports/549102bb');
    },
  );

  test(
    'a refusal, an unreachable server and a missing zip are explained',
    () async {
      final refused = ReportUploadService(
        transport: (_, _, _, _, {bearer}) async =>
            (413, '{"message":"File too large"}'),
      );
      await expectLater(
        refused.upload(run()),
        throwsA(
          isA<ReportUploadException>().having(
            (e) => e.message,
            'message',
            'Sending failed (server error 413): File too large',
          ),
        ),
      );
      final offline = ReportUploadService(
        transport: (_, _, _, _, {bearer}) async =>
            throw const SocketException('unreachable'),
      );
      await expectLater(
        offline.upload(run()),
        throwsA(
          isA<ReportUploadException>().having(
            (e) => e.message,
            'message',
            contains('Cannot reach the admin server'),
          ),
        ),
      );
      await expectLater(
        ReportUploadService(
          transport: (_, _, _, _, {bearer}) async => (201, '{}'),
        ).upload(run(zipPath: '${dir.path}/gone.zip')),
        throwsA(isA<ReportUploadException>()),
      );
    },
  );

  test(
    'the real request is multipart with the meta field and the zip streamed whole',
    () async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      addTearDown(() => server.close(force: true));
      String? contentType;
      List<int>? received;
      server.listen((request) async {
        contentType = request.headers.contentType?.mimeType;
        received = await request.fold<List<int>>(
          <int>[],
          (a, b) => a..addAll(b),
        );
        request.response
          ..statusCode = 201
          ..write('{"runId":"r1","url":"u1"}');
        await request.response.close();
      });
      final big = File('${dir.path}/big.zip')
        ..writeAsBytesSync(List<int>.generate(300000, (i) => i % 251));
      // flutter_test answers every HttpClient request with 400; this test needs the real one.
      final multipart = await HttpOverrides.runWithHttpOverrides(
        () => ReportUploadService.postMultipartForTest(
          Uri.parse('http://127.0.0.1:${server.port}/api/mobile-reports'),
          <String, String>{'meta': '{"runId":"r1"}'},
          'report',
          big,
        ),
        _RealHttp(),
      );
      expect(multipart.$1, 201);
      expect(contentType, 'multipart/form-data');
      final body = latin1.decode(received!);
      expect(body, contains('name="meta"'));
      expect(body, contains('name="report"; filename="big.zip"'));
      expect(received!.length, greaterThan(300000));
    },
  );

  testWidgets('the button sends the run and then shows it was sent', (
    tester,
  ) async {
    var calls = 0;
    await tester.pumpWidget(
      MaterialApp(
        theme: CodifiTheme.dark(),
        home: Scaffold(
          body: SendToAdminButton(
            run: run(),
            uploader: ReportUploadService(
              transport: (_, _, _, _, {bearer}) async {
                calls++;
                return (
                  201,
                  '{"runId":"549102bb","url":"http://admin/r/549102bb"}',
                );
              },
            ),
          ),
        ),
      ),
    );
    expect(find.text('Send to admin'), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey<String>('send_to_admin')));
    await tester.runAsync(
      () => Future<void>.delayed(const Duration(milliseconds: 100)),
    );
    await tester.pump();
    expect(calls, 1);
    expect(find.text('Send to admin again'), findsOneWidget);
    expect(find.textContaining('Sent to admin · run 549102bb'), findsOneWidget);
  });

  testWidgets(
    'every run in the home history can be sent to admin, without overflowing a phone row',
    (tester) async {
      tester.view.physicalSize = const Size(1080, 2280);
      tester.view.devicePixelRatio = 3;
      addTearDown(tester.view.reset);
      File(
        '${dir.path}/history.json',
      ).writeAsStringSync(jsonEncode(<Object?>[run().toJson()]));
      const channel = MethodChannel('history_send_test');
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (call) async => null);
      addTearDown(
        () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
            .setMockMethodCallHandler(channel, null),
      );
      await tester.pumpWidget(
        MaterialApp(
          theme: CodifiTheme.dark(),
          home: DashboardScreen(
            automation: AutomationService(commands: channel),
            reports: _DirReports(dir),
            storage: SecureStorageService(),
          ),
        ),
      );
      // The history is a real file read: let it finish before looking for the row.
      for (
        var i = 0;
        i < 20 && find.text('Checking').evaluate().isNotEmpty;
        i++
      ) {
        await tester.runAsync(
          () => Future<void>.delayed(const Duration(milliseconds: 100)),
        );
        await tester.pump(const Duration(milliseconds: 100));
      }
      await tester.scrollUntilVisible(
        find.byKey(
          const ValueKey<String>('send_549102bb-72db-48b0-871b-e7a3b001def7'),
        ),
        300,
        // The page, not the app-tile grid inside it.
        scrollable: find.byType(Scrollable).first,
      );
      expect(
        find.byKey(
          const ValueKey<String>('send_549102bb-72db-48b0-871b-e7a3b001def7'),
        ),
        findsOneWidget,
      );
      expect(tester.takeException(), isNull);
    },
  );
}

class _DirReports extends ReportService {
  _DirReports(this._dir);

  final Directory _dir;

  @override
  Future<Directory> reportsDirectory() async => _dir;
}

class _RealHttp extends HttpOverrides {}

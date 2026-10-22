import 'dart:io';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile_automation_tool/constants/automation_ids.dart';
import 'package:mobile_automation_tool/models/automation_result.dart';
import 'package:mobile_automation_tool/models/automation_step.dart';
import 'package:mobile_automation_tool/models/test_run.dart';
import 'package:mobile_automation_tool/services/automation_service.dart';
import 'package:mobile_automation_tool/services/report_service.dart';

AutomationStep _step(
  String id,
  String name,
  StepStatus status, {
  String? note,
  String? reason,
}) {
  return AutomationStep(
    id: id,
    name: name,
    status: status,
    startedAtMs: 1700000000000,
    finishedAtMs: 1700000001500,
    durationMs: 1500,
    note: note,
    failureReason: reason,
  );
}

AutomationResult _result({
  required List<AutomationStep> steps,
  bool passed = false,
  bool stopped = false,
  String? failureMessage,
}) {
  return AutomationResult(
    runId: 'abcdef12-3456-7890-abcd-ef1234567890',
    testName: AutomationChecklist.testName,
    targetPackage: 'com.codifi.dhanush',
    targetMainActivity: 'com.codifi.dhanush.MainActivity',
    targetVersionName: '1.0.105',
    deviceManufacturer: 'Google',
    deviceModel: 'Pixel 7',
    androidVersion: '14',
    sdkInt: 34,
    startedAtMs: 1700000000000,
    finishedAtMs: 1700000030000,
    durationMs: 30000,
    passed: passed,
    stopped: stopped,
    failureMessage: failureMessage,
    steps: steps,
  );
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  final reports = ReportService();

  test('checklist mirrors the 60 native steps in order', () {
    expect(AutomationChecklist.steps.length, 60);
    expect(AutomationChecklist.steps.first.id, 'target_app_installed');
    expect(AutomationChecklist.steps.last.id, 'logged_out');
  });

  test('the app picker offers Ashika and Tradion with their own credentials', () {
    final targets = const AutomationConfigInfo.fallback().targets;
    expect(targets.map((t) => t.id), <String>['ashika', 'tradion']);

    final ashika = targets.first;
    expect(ashika.packageName, 'com.codifi.dhanush');
    expect(ashika.secret, TargetSecret.mpin);
    expect(ashika.userIdLabel, 'Client ID');

    final tradion = targets.last;
    expect(tradion.packageName, 'com.codifi.rmoney');
    expect(tradion.secret, TargetSecret.password);
    expect(tradion.userIdLabel, 'User ID');
    expect(tradion.runSummary, contains('never places or changes an order'));
  });

  test('the secret kind decodes from the platform shape, defaulting to MPIN', () {
    expect(TargetSecret.parse('password'), TargetSecret.password);
    expect(TargetSecret.parse('mpin'), TargetSecret.mpin);
    expect(TargetSecret.parse(null), TargetSecret.mpin);
  });

  test('a fully passing run renders PASSED with a full count', () {
    final steps = AutomationChecklist.steps
        .map((s) => _step(s.id, s.name, StepStatus.passed))
        .toList();
    final result = _result(steps: steps, passed: true);

    expect(result.verdict, 'PASSED');
    expect(result.completedSteps, 60);

    final text = reports.buildChecklistText(result);
    expect(text, contains('Result: PASSED'));
    expect(text, contains('Completed: 60/60'));
    expect(text, contains('✓ Target application launched'));
    expect(text, isNot(contains('Failure:')));
  });

  test('a skipped step still counts as completed', () {
    final steps = <AutomationStep>[
      _step('target_app_installed', 'Target application installed', StepStatus.passed),
      _step('biometric_skipped', 'Biometric skipped', StepStatus.skipped,
          note: 'no biometric screen appeared'),
    ];
    final result = _result(steps: steps, passed: true);

    expect(result.completedSteps, 2);
    final text = reports.buildChecklistText(result);
    expect(text, contains('– Biometric skipped  (skipped)'));
  });

  test('a failed run reports the failure and stops the count', () {
    final steps = <AutomationStep>[
      _step('target_app_installed', 'Target application installed', StepStatus.passed),
      _step('accessibility_enabled', 'Accessibility permission enabled', StepStatus.passed),
      _step('target_app_launched', 'Target application launched', StepStatus.passed),
      _step('client_id_screen_displayed', 'Client ID screen displayed', StepStatus.passed),
      _step(
        'otp_screen_displayed',
        'OTP screen displayed',
        StepStatus.failed,
        reason: 'The OTP screen was not detected within 30 seconds.',
      ),
      const AutomationStep(id: 'mpin_screen_displayed', name: 'MPIN screen displayed'),
    ];
    final result = _result(
      steps: steps,
      failureMessage: 'The OTP screen was not detected within 30 seconds.',
    );

    expect(result.verdict, 'FAILED');
    expect(result.completedSteps, 4);
    expect(result.firstFailure?.id, 'otp_screen_displayed');

    final text = reports.buildChecklistText(result);
    expect(text, contains('✕ OTP screen displayed'));
    expect(text, contains('Failure:'));
    expect(text, contains('was not detected within 30 seconds'));
    expect(text, contains('Result: FAILED'));
    expect(text, contains('Completed: 4/6'));
    expect(text, contains('· MPIN screen displayed  (not reached)'));
  });

  test('a stopped run reports STOPPED', () {
    final result = _result(
      steps: <AutomationStep>[
        _step('target_app_installed', 'Target application installed', StepStatus.passed),
      ],
      stopped: true,
      failureMessage: 'The run was stopped by the operator.',
    );
    expect(result.verdict, 'STOPPED');
    expect(reports.buildChecklistText(result), contains('Result: STOPPED'));
  });

  test('reports carry no credential values', () {
    // The result model has no field that could hold one, so the guard here is
    // that anything an operator might type cannot appear in either renderer.
    const clientId = 'UATCLIENT99';
    const otp = '654321';
    const mpin = '4321';

    final steps = AutomationChecklist.steps
        .map(
          (s) => _step(
            s.id,
            s.name,
            StepStatus.passed,
            note: 'entered via ACTION_SET_TEXT on ${AutomationIds.otpInput}',
          ),
        )
        .toList();
    final result = _result(steps: steps, passed: true);

    for (final rendered in <String>[
      reports.buildChecklistText(result),
      reports.buildHtml(result),
    ]) {
      expect(rendered, isNot(contains(clientId)));
      expect(rendered, isNot(contains(otp)));
      expect(rendered, isNot(contains(mpin)));
    }
  });

  test('html report escapes untrusted-looking text and is self contained', () {
    final result = _result(
      steps: <AutomationStep>[
        _step(
          'otp_screen_displayed',
          'OTP screen displayed',
          StepStatus.failed,
          reason: 'Identifiers visible: <script>alert("x")</script>',
        ),
      ],
      failureMessage: 'boom & <b>bold</b>',
    );

    final html = reports.buildHtml(result);
    expect(html, isNot(contains('<script>')));
    expect(html, contains('&lt;script&gt;'));
    expect(html, contains('&quot;x&quot;'));
    // No external requests: everything inline.
    expect(html, isNot(contains('http://')));
    expect(html, isNot(contains('https://')));
    expect(html, contains('FAILED'));

    // With no failed step the run-level message is what gets rendered, and it is
    // escaped the same way.
    final noteOnly = _result(
      steps: <AutomationStep>[
        _step('home_displayed', 'Home or Watchlist displayed', StepStatus.passed),
      ],
      passed: true,
      failureMessage: 'boom & <b>bold</b>',
    );
    expect(
      reports.buildHtml(noteOnly),
      contains('boom &amp; &lt;b&gt;bold&lt;/b&gt;'),
    );
    expect(
      reports.buildChecklistText(noteOnly),
      contains('boom & <b>bold</b>'),
    );
  });

  test('result survives a round trip through the platform-channel shape', () {
    final original = _result(
      steps: <AutomationStep>[
        _step('mpin_entered', 'MPIN entered', StepStatus.passed,
            note: 'detected a text input'),
        _step('biometric_skipped', 'Biometric skipped', StepStatus.skipped),
      ],
      passed: true,
    );

    final decoded = AutomationResult.fromMap(original.toJson());
    expect(decoded.runId, original.runId);
    expect(decoded.targetPackage, 'com.codifi.dhanush');
    expect(decoded.steps.length, 2);
    expect(decoded.steps[0].status, StepStatus.passed);
    expect(decoded.steps[0].note, 'detected a text input');
    expect(decoded.steps[1].status, StepStatus.skipped);
    expect(decoded.verdict, 'PASSED');
  });

  test('malformed platform payloads degrade instead of throwing', () {
    final decoded = AutomationResult.fromMap(<Object?, Object?>{
      'runId': null,
      'steps': <Object?>['not a map', 42],
      'screenshots': <Object?, Object?>{'failed': 1, 2: 'x', 'ok': '/tmp/a.png'},
    });
    expect(decoded.runId, 'unknown');
    expect(decoded.steps, isEmpty);
    expect(decoded.screenshots, <String, String>{'ok': '/tmp/a.png'});
    expect(decoded.verdict, 'FAILED');
  });

  test('download sends all available report formats to Android', () async {
    final temp = await Directory.systemTemp.createTemp('automation_reports_');
    final html = File('${temp.path}/run.html');
    final json = File('${temp.path}/run.json');
    final text = File('${temp.path}/run.txt');
    await html.writeAsString('<html></html>');
    await json.writeAsString('{}');
    await text.writeAsString('PASSED');

    const channel = MethodChannel('report_download_test');
    MethodCall? received;
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
          received = call;
          return <String, Object?>{
            'savedNames': <String>['run.html', 'run.json', 'run.txt'],
            'failedNames': <String>[],
            'directory': 'Downloads/Automation Tool Reports',
          };
        });
    addTearDown(() async {
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, null);
      if (await temp.exists()) await temp.delete(recursive: true);
    });

    final service = ReportService(downloads: channel);
    final run = TestRun(
      runId: 'download-test',
      testName: 'Download test',
      targetPackage: 'com.codifi.rmoney',
      startedAtMs: 1700000000000,
      passed: true,
      stopped: false,
      completedSteps: 3,
      totalSteps: 3,
      jsonPath: json.path,
      htmlPath: html.path,
      textPath: text.path,
    );

    final result = await service.download(run);
    expect(result.succeeded, isTrue);
    expect(result.message, contains('Downloaded 3 report files'));
    expect(received?.method, 'downloadReportFiles');

    final arguments = received?.arguments as Map<Object?, Object?>;
    final files = arguments['files'] as List<Object?>;
    expect(files, hasLength(3));
    expect(
      files.whereType<Map<Object?, Object?>>().map((file) => file['mimeType']),
      <String>['text/html', 'application/json', 'text/plain'],
    );
  });
}

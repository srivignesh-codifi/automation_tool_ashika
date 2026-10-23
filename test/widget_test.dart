import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:intl/intl.dart';
import 'package:mobile_automation_tool/constants/automation_ids.dart';
import 'package:mobile_automation_tool/models/automation_result.dart';
import 'package:mobile_automation_tool/models/automation_step.dart';
import 'package:mobile_automation_tool/models/test_run.dart';
import 'package:mobile_automation_tool/screens/setup_screen.dart';
import 'package:mobile_automation_tool/services/secure_storage_service.dart';
import 'package:mobile_automation_tool/widgets/module_picker.dart';
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
    expect(text, contains('Target application launched'));
    expect(text, contains('✓ PASS'));
    expect(text, isNot(contains('FAILURE')));
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
    expect(text, contains('– SKIP'));
    expect(text, contains('Biometric skipped'));
    expect(text, contains('no biometric screen appeared'));
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
    expect(text, contains('✕ FAIL'));
    expect(text, contains('OTP screen displayed'));
    expect(text, contains('FAILURE'));
    expect(text, contains('was not detected within 30 seconds'));
    expect(text, contains('Result: FAILED'));
    expect(text, contains('Completed: 4/6'));
    expect(text, contains('· NOT RUN'));
    expect(text, contains('MPIN screen displayed'));
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

  test('the text report is a table with times and an API / data column', () {
    final base = DateTime(2026, 10, 22, 16, 40, 40).millisecondsSinceEpoch;
    AutomationStep step(
      String id,
      String name,
      StepStatus status,
      int startOffsetMs,
      int durationMs, {
      String? kind,
      String? api,
      String? detail,
      int? apiMs,
      String? reason,
      String? note,
    }) => AutomationStep(
      id: id,
      name: name,
      status: status,
      startedAtMs: base + startOffsetMs,
      finishedAtMs: base + startOffsetMs + durationMs,
      durationMs: durationMs,
      apiKind: kind,
      apiStatus: api,
      apiDetail: detail,
      apiMs: apiMs,
      failureReason: reason,
      note: note,
    );
    final result = AutomationResult(
      runId: 'abcdef12-3456-7890-abcd-ef1234567890',
      testName: 'Tradion - Regression Automation',
      targetPackage: 'com.codifi.rmoney',
      targetVersionName: '1.0.95',
      deviceManufacturer: 'Google',
      deviceModel: 'Pixel',
      androidVersion: '16',
      sdkInt: 36,
      startedAtMs: base,
      finishedAtMs: base + 155000,
      durationMs: 155000,
      steps: <AutomationStep>[
        step('login_tapped', 'Login button tapped', StepStatus.passed, 0, 800,
            kind: 'auth', api: 'ok', detail: 'the next screen followed'),
        step('orders_gtt_loaded', 'Orders › GTT loaded', StepStatus.passed, 1000, 6100,
            kind: 'read', api: 'slow', detail: 'loaded, but took 6.1 s', apiMs: 6100),
        step('search_stocks_added', 'Stocks segment: TCS added', StepStatus.passed, 8000, 2500,
            kind: 'write', api: 'confirmed', detail: 're-read matched',
            note: 'added and still there after 2 s'),
        step('profile_account_loaded', 'Profile › Account loaded', StepStatus.failed, 12000, 10200,
            kind: 'read', api: 'not_checked', detail: 'failed before the data could be checked',
            reason: 'Could not open the Profile tab.'),
        const AutomationStep(id: 'logged_out', name: 'Logged out'),
      ],
    );

    final text = reports.buildChecklistText(result);
    // Header and run facts.
    expect(text, contains('Completed: 3/5 steps  (3 passed, 0 skipped, 1 failed, 1 not run)'));
    expect(text, contains('App version'));
    expect(text, contains('1.0.95'));
    expect(text, contains('2026-10-22 16:40:40'));
    // The table: clock times and a duration per step, then the API column.
    expect(text, contains('Start     End       Time'));
    expect(text, contains('16:40:40  16:40:40  800 ms'));
    expect(text, contains('16:40:41  16:40:47  6.1 s'));
    expect(text, contains('SLOW  loaded, but took 6.1 s'));
    expect(text, contains('CONFIRMED  re-read matched'));
    expect(text, contains('NOT CHECKED'));
    // Summary, slowest steps and details.
    expect(text, contains('API / DATA CHECKS'));
    expect(text, contains('2 checked: 0 OK, 0 empty, 1 slow, 0 timed out, 0 error, 1 not checked'));
    expect(text, contains('1 checked: 1 confirmed, 0 not confirmed'));
    expect(text, contains('1 checked: 1 OK, 0 failed'));
    expect(text, contains('SLOWEST STEPS'));
    expect(text, contains('Profile › Account loaded'));
    expect(text, contains('STEP DETAILS'));
    expect(text, contains('Failure: Could not open the Profile tab.'));
    expect(text, contains('Note: added and still there after 2 s'));
    expect(text, contains('from what the screen shows, not from network traffic'));

    // The same facts reach the JSON and the HTML.
    final json = result.toJson();
    expect((json['apiSummary'] as Map)['problems'], 1);
    final html = reports.buildHtml(result);
    expect(html, contains('API / data check'));
    expect(html, contains('class="pill slow"'));
    expect(html, contains('class="pill confirmed"'));

    final decoded = AutomationResult.fromMap(json);
    expect(decoded.steps[1].apiStatus, 'slow');
    expect(decoded.steps[1].apiMs, 6100);
    expect(decoded.apiSummary.readsSlow, 1);
  });

  test('modules come from the driver, and the fallback lists every one', () async {
    const channel = MethodChannel('modules_config_test');
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
          if (call.method == 'getAutomationConfig') {
            return <String, Object?>{
              'allowedPackages': <String>['com.codifi.rmoney'],
              'targets': <Object?>[
                <String, Object?>{
                  'id': 'tradion',
                  'displayName': 'Tradion',
                  'packageName': 'com.codifi.rmoney',
                  'testName': 'T',
                  'secret': 'password',
                  'modules': <Object?>[
                    <String, Object?>{'id': 'login', 'label': 'Login', 'description': 'd'},
                    <String, Object?>{'id': '', 'label': 'dropped'},
                  ],
                },
              ],
            };
          }
          return null;
        });
    addTearDown(
      () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, null),
    );
    final config = await AutomationService(commands: channel).config();
    expect(config.targets.single.modules.map((m) => m.id), <String>['login']);
    expect(config.targets.single.modules.single.description, 'd');

    final fallback = const AutomationConfigInfo.fallback().targets;
    expect(fallback.first.modules.map((m) => m.id), contains('watchlist'));
    expect(
      fallback.last.modules.map((m) => m.id),
      <String>[
        'login', 'watchlist', 'watchlist_edit', 'watchlist_search',
        'orders', 'dashboard', 'portfolio', 'profile',
      ],
    );
  });

  testWidgets('the module picker starts full, unticks one and clears all', (
    tester,
  ) async {
    final modules = const AutomationConfigInfo.fallback().targets.last.modules;
    var selected = modules.map((m) => m.id).toSet();
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: StatefulBuilder(
            builder: (context, setState) => SingleChildScrollView(
              child: ModulePicker(
                modules: modules,
                selected: selected,
                onChanged: (next) => setState(() => selected = next),
              ),
            ),
          ),
        ),
      ),
    );
    expect(find.text('Modules to test (8 of 8)'), findsOneWidget);

    await tester.tap(find.byKey(const ValueKey<String>('module_orders')));
    await tester.pump();
    expect(selected, isNot(contains('orders')));
    expect(selected.length, 7);
    expect(find.text('Modules to test (7 of 8)'), findsOneWidget);

    await tester.tap(find.text('None'));
    await tester.pump();
    expect(selected, isEmpty);
    expect(find.textContaining('only signing in and signing out run'), findsOneWidget);

    await tester.tap(find.text('All'));
    await tester.pump();
    expect(selected.length, 8);
  });

  testWidgets('the modules field summarises the choice and opens a bottom sheet', (
    tester,
  ) async {
    final modules = const AutomationConfigInfo.fallback().targets.last.modules;
    var selected = modules.map((m) => m.id).toSet();
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: StatefulBuilder(
            builder: (context, setState) => ModuleField(
              modules: modules,
              selected: selected,
              onChanged: (next) => setState(() => selected = next),
            ),
          ),
        ),
      ),
    );
    expect(find.text('All modules (8)'), findsOneWidget);
    expect(find.byType(CheckboxListTile), findsNothing);

    await tester.tap(find.text('All modules (8)'));
    await tester.pumpAndSettle();
    expect(find.byType(BottomSheet), findsOneWidget);
    expect(find.byType(CheckboxListTile), findsNWidgets(8));

    await tester.tap(find.byKey(const ValueKey<String>('module_watchlist_edit')));
    await tester.pump();
    await tester.tap(find.byKey(const ValueKey<String>('module_watchlist_search')));
    await tester.pump();
    expect(selected.length, 6);

    await tester.tap(find.text('Done'));
    await tester.pumpAndSettle();
    expect(find.byType(BottomSheet), findsNothing);
    expect(
      find.text('6 of 8: Login, Watchlist, Orders, Dashboard, Portfolio, Profile'),
      findsOneWidget,
    );
    expect(
      ModuleField.summary(modules, <String>{}),
      'None: only sign-in and sign-out run',
    );
  });

  test('the report says which modules ran and which were left out', () {
    final result = AutomationResult(
      runId: 'abcdef12-3456-7890-abcd-ef1234567890',
      testName: 'Tradion',
      targetPackage: 'com.codifi.rmoney',
      steps: const <AutomationStep>[],
      modulesRun: const <String>['Login', 'Orders'],
      modulesSkipped: const <String>['Edit watchlist', 'Search and add'],
    );
    final text = reports.buildChecklistText(result);
    expect(text, contains('Modules run'));
    expect(text, contains('Login, Orders'));
    expect(text, contains('Not selected'));
    expect(text, contains('Edit watchlist, Search and add'));
    expect(reports.buildHtml(result), contains('Not selected'));
    expect(AutomationResult.fromMap(result.toJson()).modulesSkipped, <String>[
      'Edit watchlist',
      'Search and add',
    ]);
  });

  test('a run without API data still renders a clean table', () {
    final text = reports.buildChecklistText(
      _result(
        steps: <AutomationStep>[
          _step('target_app_installed', 'Target application installed', StepStatus.passed),
        ],
        passed: true,
      ),
    );
    expect(text, contains('API / data check'));
    expect(text, isNot(contains('API / DATA CHECKS')));
  });

  test('a recorded run links each step to its moment in the video', () {
    final base = _result(
      steps: <AutomationStep>[
        AutomationStep(
          id: 'home_displayed',
          name: 'Home displayed',
          status: StepStatus.passed,
          startedAtMs: 1700000000000 + 75000,
          finishedAtMs: 1700000000000 + 76000,
          durationMs: 1000,
        ),
        const AutomationStep(id: 'never_reached', name: 'Not reached'),
      ],
      passed: true,
    );
    final recorded = AutomationResult(
      runId: base.runId,
      testName: base.testName,
      targetPackage: base.targetPackage,
      steps: base.steps,
      startedAtMs: base.startedAtMs,
      videoPath: '/data/reports/recording_abcdef12.mp4',
      recordingStartedAtMs: 1700000000000 + 5000,
    );

    expect(recorded.hasVideo, isTrue);
    expect(recorded.videoOffsetOf(recorded.steps[0]), 70.0);
    expect(recorded.videoOffsetOf(recorded.steps[1]), isNull);

    final html = reports.buildHtml(recorded);
    expect(html, contains('<video id="rec"'));
    expect(html, contains('src="recording_abcdef12.mp4"'));
    expect(html, contains('seek(70.0)'));
    expect(html, contains('▶ 1:10'));
    expect(html, contains('<th>Video</th>'));
    expect(reports.buildChecklistText(recorded),
        contains('recording_abcdef12.mp4'));

    // The path and note survive the JSON the report is reopened from.
    final decoded = AutomationResult.fromMap(recorded.toJson());
    expect(decoded.videoPath, recorded.videoPath);
    expect(decoded.recordingStartedAtMs, recorded.recordingStartedAtMs);
  });

  test('a run that could not be recorded says why and has no player', () {
    final result = AutomationResult(
      runId: 'abcdef12-3456-7890-abcd-ef1234567890',
      testName: 'Tradion',
      targetPackage: 'com.codifi.rmoney',
      steps: const <AutomationStep>[],
      recordingNote: 'not recorded: the system dialog was declined',
    );
    expect(result.hasVideo, isFalse);
    final html = reports.buildHtml(result);
    expect(html, isNot(contains('<video')));
    expect(html, isNot(contains('<script>')));
    expect(html, contains('not recorded: the system dialog was declined'));
    expect(reports.buildChecklistText(result),
        contains('not recorded: the system dialog was declined'));
  });

  test('save moves the recording next to the report and survives a re-save', () async {
    final temp = await Directory.systemTemp.createTemp('automation_save_');
    addTearDown(() async {
      if (await temp.exists()) await temp.delete(recursive: true);
    });
    final video = File('${temp.path}/recording_abcdef12.mp4');
    await video.writeAsBytes(<int>[0, 0, 0, 24]);

    final service = _FixedDirReports(temp);
    final result = AutomationResult(
      runId: 'abcdef12-3456-7890-abcd-ef1234567890',
      testName: 'Tradion',
      targetPackage: 'com.codifi.rmoney',
      steps: const <AutomationStep>[],
      startedAtMs: 1700000000000,
      videoPath: video.path,
      recordingStartedAtMs: 1700000000000,
    );

    final first = await service.save(result);
    expect(first.videoPath, endsWith('_abcdef12.mp4'));
    expect(await File(first.videoPath).exists(), isTrue);
    expect(await video.exists(), isFalse);
    expect(await File(first.htmlPath).readAsString(),
        contains('src="${first.videoPath.split('/').last}"'));

    // Saving the same run again (the native path is gone) keeps the video.
    final again = await service.save(result);
    expect(again.videoPath, first.videoPath);
  });

  test('save zips the text report, the video and only failed screenshots', () async {
    final temp = await Directory.systemTemp.createTemp('automation_zip_');
    const channel = MethodChannel('summary_zip_test');
    MethodCall? received;
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
          received = call;
          return <String, Object?>{
            'zipPath': call.arguments['zipPath'],
            'folder': 'Downloads/Automation Tool Reports',
            'error': null,
          };
        });
    addTearDown(() async {
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, null);
      if (await temp.exists()) await temp.delete(recursive: true);
    });
    final video = File('${temp.path}/recording_abcdef12.mp4')
      ..writeAsBytesSync(<int>[1]);
    final failed = File('${temp.path}/abcdef12_failed_login.png')
      ..writeAsBytesSync(<int>[1]);
    final chart = File('${temp.path}/abcdef12_chart.png')
      ..writeAsBytesSync(<int>[1]);

    final run = await _FixedDirReports(temp, downloads: channel).save(
      AutomationResult(
        runId: 'abcdef12-3456-7890-abcd-ef1234567890',
        testName: 'Tradion',
        targetPackage: 'com.codifi.rmoney',
        steps: const <AutomationStep>[],
        startedAtMs: 1700000000000,
        videoPath: video.path,
        screenshots: <String, String>{
          'failed_login': failed.path,
          'chart': chart.path,
          'failed_gone': '${temp.path}/missing.png',
        },
      ),
    );

    expect(received?.method, 'buildSummaryZip');
    final names = (received!.arguments['entries'] as List)
        .map((e) => (e as Map)['name'])
        .toList();
    expect(names, hasLength(3));
    expect(names, contains(endsWith('.txt')));
    expect(names, contains(endsWith('.mp4')));
    expect(names, contains('screenshots/abcdef12_failed_login.png'));
    expect(run.zipFolder, 'Downloads/Automation Tool Reports');
    final stamp = DateFormat('ddMMyyyy_HHmm').format(
      DateTime.fromMillisecondsSinceEpoch(1700000000000),
    );
    expect(run.zipPath, endsWith('/tradion_$stamp.zip'));
    expect(received!.arguments['displayName'], 'tradion_$stamp.zip');
    expect(
      ReportService.zipNameFor(
        AutomationResult(
          runId: 'x',
          testName: 't',
          targetPackage: 'com.codifi.dhanush',
          steps: const <AutomationStep>[],
          startedAtMs: DateTime(2026, 10, 23, 8, 10).millisecondsSinceEpoch,
        ),
      ),
      'ashika_23102026_0810.zip',
    );
  });

  test('a zip failure is reported on the run and does not fail the save', () async {
    final temp = await Directory.systemTemp.createTemp('automation_zip_fail_');
    addTearDown(() async {
      if (await temp.exists()) await temp.delete(recursive: true);
    });
    // No handler is registered for this channel, as on a device without it.
    final run = await _FixedDirReports(
      temp,
      downloads: const MethodChannel('no_such_channel'),
    ).save(
      AutomationResult(
        runId: 'abcdef12-3456-7890-abcd-ef1234567890',
        testName: 'Tradion',
        targetPackage: 'com.codifi.rmoney',
        steps: const <AutomationStep>[],
        startedAtMs: 1700000000000,
      ),
    );
    expect(run.zipPath, isEmpty);
    expect(run.zipError, startsWith('The zip could not be made'));
    expect(await File(run.textPath).exists(), isTrue);
  });

  testWidgets('setup holds Finish back until the accessibility service is on', (
    tester,
  ) async {
    await _pumpSetup(tester, accessibility: false);
    await tester.scrollUntilVisible(find.text('Finish setup'), 200);
    final button = tester.widget<FilledButton>(
      find.widgetWithText(FilledButton, 'Finish setup'),
    );
    expect(button.onPressed, isNull);
    expect(find.textContaining('Open Accessibility settings'), findsOneWidget);
    expect(find.text('1. Allow restricted settings'), findsOneWidget);
  });

  testWidgets('setup with the service on offers Finish', (tester) async {
    await _pumpSetup(tester, accessibility: true);
    expect(find.text('Enabled and running.'), findsOneWidget);
    await tester.scrollUntilVisible(find.text('Finish setup'), 200);
    final button = tester.widget<FilledButton>(
      find.widgetWithText(FilledButton, 'Finish setup'),
    );
    expect(button.onPressed, isNotNull);
  });

  testWidgets('the recording test reports a refusal and a success', (
    tester,
  ) async {
    await _pumpSetup(tester, accessibility: true);
    await tester.scrollUntilVisible(find.text('Test screen recording'), 200);
    await tester.tap(find.text('Test screen recording'));
    await tester.pumpAndSettle();
    expect(find.textContaining('Screen sharing was not allowed'), findsOneWidget);

    await _pumpSetup(tester, accessibility: true, screenRecording: true);
    await tester.scrollUntilVisible(find.text('Test screen recording'), 200);
    await tester.tap(find.text('Test screen recording'));
    await tester.pumpAndSettle();
    expect(find.textContaining('Works: a test video'), findsOneWidget);
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


/// A setup screen over a mocked platform channel. [accessibility] is whether
/// the service is on; [screenRecording] is the answer to the system dialog.
Future<void> _pumpSetup(
  WidgetTester tester, {
  required bool accessibility,
  bool screenRecording = false,
  bool firstRun = false,
}) async {
  const channel = MethodChannel('setup_screen_test');
  TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
      .setMockMethodCallHandler(channel, (call) async {
        switch (call.method) {
          case 'isAccessibilityEnabled':
            return <String, Object?>{
              'enabledInSettings': accessibility,
              'connected': accessibility,
            };
          case 'notificationPermission':
            return true;
          case 'isTargetAppInstalled':
            return <String, Object?>{
              'packageName': call.arguments['packageName'],
              'installed': true,
              'allowlisted': true,
            };
          case 'requestScreenRecording':
            return screenRecording;
          case 'testScreenRecording':
            return <String, Object?>{'ok': true, 'detail': 'a test video'};
        }
        return null;
      });
  addTearDown(
    () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, null),
  );
  await tester.pumpWidget(
    MaterialApp(
      home: SetupScreen(
        automation: AutomationService(commands: channel),
        storage: SecureStorageService(),
        firstRun: firstRun,
      ),
    ),
  );
  await tester.pumpAndSettle();
}

/// A [ReportService] that writes under a fixed directory instead of the
/// platform's, so `save` can run in a unit test.
class _FixedDirReports extends ReportService {
  _FixedDirReports(this._dir, {super.downloads});

  final Directory _dir;

  @override
  Future<Directory> reportsDirectory() async => _dir;
}

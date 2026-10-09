import 'package:flutter_test/flutter_test.dart';
import 'package:mobile_automation_tool/constants/automation_ids.dart';
import 'package:mobile_automation_tool/models/automation_result.dart';
import 'package:mobile_automation_tool/models/automation_step.dart';
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
  final reports = ReportService();

  test('checklist mirrors the 18 native steps in order', () {
    expect(AutomationChecklist.steps.length, 18);
    expect(AutomationChecklist.steps.first.id, 'target_app_installed');
    expect(AutomationChecklist.steps.last.id, 'home_displayed');
  });

  test('a fully passing run renders PASSED with a full count', () {
    final steps = AutomationChecklist.steps
        .map((s) => _step(s.id, s.name, StepStatus.passed))
        .toList();
    final result = _result(steps: steps, passed: true);

    expect(result.verdict, 'PASSED');
    expect(result.completedSteps, 18);

    final text = reports.buildChecklistText(result);
    expect(text, contains('Result: PASSED'));
    expect(text, contains('Completed: 18/18'));
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
}

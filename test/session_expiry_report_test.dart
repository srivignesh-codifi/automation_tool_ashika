import 'package:flutter_test/flutter_test.dart';
import 'package:mobile_automation_tool/models/automation_result.dart';
import 'package:mobile_automation_tool/models/automation_step.dart';
import 'package:mobile_automation_tool/services/report_service.dart';

void main() {
  final at = DateTime(2026, 10, 27, 11, 5, 12).millisecondsSinceEpoch;
  final result = AutomationResult.fromMap(<Object?, Object?>{
    'runId': 'run-1',
    'testName': 'Tradion - Regression Automation',
    'targetPackage': 'com.codifi.rmoney',
    'passed': true,
    'steps': <Object?>[
      AutomationStep(
        id: 'wl_chart',
        name: 'Chart opens',
        status: StepStatus.passed,
        code: 'WL-06',
        apiKind: 'read',
        apiStatus: 'unauthorized',
        apiDetail:
            'HTTP 401 / session expired: HTTP 401 GET /rest/chart at 11:05:12',
        unauthorized: 'HTTP 401 GET /rest/chart at 11:05:12',
        reruns: 1,
      ).toJson(),
      const AutomationStep(
        id: 'wl_alert_page',
        name: 'Alert page',
        status: StepStatus.passed,
      ).toJson(),
    ],
    'sessionBreaks': <Object?>[
      <Object?, Object?>{
        'number': 1,
        'stepId': 'wl_chart',
        'stepName': 'Chart opens',
        'code': 'WL-06',
        'context': 'checking Chart opens',
        'screen': 'Password',
        'api': 'HTTP 401 GET /rest/chart at 11:05:12',
        'atMs': at,
        'outcome': 'signed in again in 41 s; resumed from this step',
      },
    ],
  });

  test('a session expiry and its 401 API are highlighted in both reports', () {
    expect(result.sessionBreaks.single.api, contains('/rest/chart'));
    expect(result.steps.first.reruns, 1);

    final text = ReportService().buildChecklistText(result);
    expect(text, contains('SESSION EXPIRED / HTTP 401'));
    expect(text, contains('>>> 401  WL-06 Chart opens'));
    expect(text, contains('API     HTTP 401 GET /rest/chart'));
    expect(text, contains('resumed from this step'));

    final html = ReportService().buildHtml(result);
    expect(html, contains('class="unauth-box"'));
    expect(
      html,
      contains('<span class="pill unauthorized">401</span> Chart opens'),
    );
    expect(html, contains('passed unauth'));
  });

  test('a run without a 401 has no session expiry section', () {
    final clean = AutomationResult.fromMap(<Object?, Object?>{
      'runId': 'run-2',
      'testName': 'Tradion',
      'targetPackage': 'com.codifi.rmoney',
      'steps': <Object?>[
        const AutomationStep(
          id: 'a',
          name: 'A',
          status: StepStatus.passed,
        ).toJson(),
      ],
    });
    expect(
      ReportService().buildChecklistText(clean),
      isNot(contains('HTTP 401')),
    );
    expect(
      ReportService().buildHtml(clean),
      isNot(contains('class="unauth-box"')),
    );
  });
}

import 'package:flutter_test/flutter_test.dart';
import 'package:mobile_automation_tool/models/automation_result.dart';
import 'package:mobile_automation_tool/models/automation_step.dart';
import 'package:mobile_automation_tool/services/report_service.dart';

void main() {
  AutomationResult sample() => const AutomationResult(
    runId: 'abcdef12-0000',
    testName: 'iTrade - Regression Automation',
    targetPackage: 'com.codifi.itrade',
    startedAtMs: 1792838273000,
    finishedAtMs: 1792838333000,
    durationMs: 60000,
    steps: <AutomationStep>[
      AutomationStep(
        id: 'home_displayed',
        name: 'Home displayed',
        status: StepStatus.passed,
      ),
      AutomationStep(
        id: 'wl_predefined_lists',
        name: 'Predefined lists load and switch',
        status: StepStatus.failed,
        code: 'WL-03',
        severity: 'High',
        cause: 'app_defect',
        expected: 'Predefined mode shows predefined instruments.',
        failureReason: 'the tab strip above the list is blank',
        recommendation: 'Render the Predefined tabs.',
      ),
      AutomationStep(
        id: 'wl_option_chain',
        name: 'Option chain opens with data',
        status: StepStatus.skipped,
        skipKind: 'blocked',
        code: 'WL-09',
        cause: 'test_data',
        note: 'BLOCKED: the app could not be brought back',
      ),
      AutomationStep(
        id: 'wl_search_results',
        name: 'Search finds INFY (All)',
        status: StepStatus.passed,
        code: 'WL-20',
        cleanup: 'FAILED: INFY-EQ is still in the watchlist',
      ),
    ],
    screenshots: <String, String>{
      'WL-03_failure_1_watchlist': '/r/abcdef12_WL-03_failure_1_watchlist.png',
      'failed_wl_predefined_lists':
          '/r/abcdef12_failed_wl_predefined_lists.png',
    },
  );

  test(
    'the HTML report shows the overview, coloured findings and the case table',
    () {
      final html = ReportService().buildHtml(sample());
      expect(html, contains('class="kpis"'));
      expect(html, contains('<div class="kpi o-fail"><b>1</b>'));
      expect(html, contains('class="qa qa-fixes"'));
      expect(html, contains('<article class="finding c-app">'));
      expect(html, contains('<span class="sev sev-high">High</span>'));
      expect(html, contains('<article class="finding c-data">'));
      expect(
        html,
        contains('screenshots/abcdef12_WL-03_failure_1_watchlist.jpg'),
      );
      expect(html, contains('<table class="cases">'));
      expect(html, contains('NOT RESTORED'));
    },
  );

  test('text and HTML give the same QA verdict', () {
    final text = ReportService().buildChecklistText(sample());
    const verdict =
        'Needs fixes: 1 confirmed application defect(s), none critical.';
    expect(text, contains(verdict));
    expect(ReportService().buildHtml(sample()), contains(verdict));
  });

  test(
    "a step's failed_ shot is not listed again when its test case has its own",
    () {
      final text = ReportService().buildChecklistText(sample());
      expect(text, contains('WL-03_failure_1_watchlist'));
      expect(text, isNot(contains('abcdef12_failed_wl_predefined_lists.png')));
    },
  );
}

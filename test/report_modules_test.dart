import 'package:flutter_test/flutter_test.dart';
import 'package:mobile_automation_tool/models/automation_result.dart';
import 'package:mobile_automation_tool/models/automation_step.dart';
import 'package:mobile_automation_tool/models/report_modules.dart';
import 'package:mobile_automation_tool/services/report_service.dart';

AutomationStep _step(
  String id,
  String name,
  StepStatus status, {
  String? module,
  String? code,
  String? note,
  String? reason,
}) => AutomationStep(
  id: id,
  name: name,
  status: status,
  module: module,
  code: code,
  note: note,
  failureReason: reason,
);

List<AutomationStep> _steps() => <AutomationStep>[
  _step(
    'target_app_installed',
    'Target application installed',
    StepStatus.passed,
  ),
  _step('user_id_entered', 'User ID entered', StepStatus.passed),
  _step(
    'wl_landing_loaded',
    'Watchlist landing screen loads',
    StepStatus.passed,
    module: 'watchlist',
    code: 'WL-01',
  ),
  _step(
    'wl_search_results',
    'Search finds INFY (All)',
    StepStatus.failed,
    module: 'watchlist',
    code: 'WL-20',
    reason: 'no result rows',
  ),
  _step(
    'wl_edit_rename',
    'Edit › rename and restore',
    StepStatus.skipped,
    module: 'watchlist_edit',
    code: 'WL-30',
  ),
  _step(
    'ow_nse_pad',
    'NSE › order pad',
    StepStatus.passed,
    module: 'order_placement',
    code: 'OW-01',
    note: 'funds: Avail. Margin 0.00',
  ),
  _step(
    'orders_pending_loaded',
    'Orders › Pending loaded',
    StepStatus.skipped,
    module: 'orders',
  ),
  _step('logged_out', 'Logged out', StepStatus.passed),
];

void main() {
  test('steps are grouped by module, in the order they first appear', () {
    final stats = summarizeModules(_steps());
    expect(stats.map((m) => m.label), <String>[
      'Login',
      'Watchlist',
      'Order placement',
      'Orders',
    ]);
    final login = stats.first;
    expect(
      login.total,
      3,
      reason: 'sign-in and sign-out steps carry no module and belong to Login',
    );
    expect(login.outcome, 'PASS');
    final watchlist = stats[1];
    expect(
      watchlist.total,
      3,
      reason:
          'a report saved with the old watchlist_edit module still counts as Watchlist',
    );
    expect((watchlist.passed, watchlist.failed, watchlist.skipped), (1, 1, 1));
    expect(watchlist.outcome, 'FAIL');
    expect(stats[3].outcome, 'SKIPPED');
  });

  test(
    'the search finds a step by name, id, detail or module, every word needed',
    () {
      final steps = _steps();
      List<String> hit(String q) =>
          steps.where((s) => stepMatchesQuery(s, q)).map((s) => s.id).toList();
      expect(hit(''), hasLength(steps.length));
      expect(hit('infy'), <String>['wl_search_results']);
      expect(hit('wl-01'), <String>['wl_landing_loaded']);
      expect(hit('avail margin'), <String>['ow_nse_pad']);
      expect(hit('no result'), <String>['wl_search_results']);
      expect(hit('order placement'), <String>['ow_nse_pad']);
      expect(hit('fail'), <String>['wl_search_results']);
      expect(hit('nothing like this'), isEmpty);
    },
  );

  test(
    'the html report has the module summary, the search, the module filter and the rows to filter',
    () {
      final result = AutomationResult(
        runId: 'abcdef12-3456-7890-abcd-ef1234567890',
        testName: 'Tradion',
        targetPackage: 'com.codifi.rmoney',
        steps: _steps(),
      );
      final html = ReportService().buildHtml(result);
      expect(html, contains('Summary by module'));
      expect(html, contains('data-pick="Order placement"'));
      expect(html, contains('id="q"'));
      expect(html, contains('data-m="Watchlist"'));
      expect(html, contains('Watchlist (3)'));
      expect(html, contains('data-module="Watchlist" data-status="failed"'));
      expect(html, contains('data-text="search finds infy (all) wl-20'));
      expect(html, isNot(contains('http://')));
      expect(html, isNot(contains('https://')));
    },
  );
}

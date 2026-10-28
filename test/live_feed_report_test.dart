import 'package:flutter_test/flutter_test.dart';
import 'package:mobile_automation_tool/models/automation_result.dart';
import 'package:mobile_automation_tool/models/automation_step.dart';
import 'package:mobile_automation_tool/models/market_session.dart';
import 'package:mobile_automation_tool/services/report_service.dart';

AutomationResult _run(Map<Object?, Object?> feed) =>
    AutomationResult.fromMap(<Object?, Object?>{
      'runId': 'run-1',
      'testName': 'Tradion - Regression Automation',
      'targetPackage': 'com.codifi.rmoney',
      'passed': true,
      'steps': <Object?>[
        const AutomationStep(
          id: 'home_displayed',
          name: 'Home displayed',
          status: StepStatus.passed,
        ).toJson(),
      ],
      'feed': feed,
    });

void main() {
  test('the market session is BOD unless live was chosen', () {
    expect(MarketSession.fromWire(null), MarketSession.bod);
    expect(MarketSession.fromWire('other'), MarketSession.bod);
    expect(MarketSession.fromWire('live'), MarketSession.live);
  });

  test('a live run reports the feed in its own section', () {
    final at = DateTime(2026, 10, 27, 10, 15, 3).millisecondsSinceEpoch;
    final result = _run(<Object?, Object?>{
      'mode': 'live',
      'verdict': 'PROBLEMS',
      'problems': <Object?>['1 unplanned disconnection(s)'],
      'connectedAtEnd': true,
      'connects': 2,
      'connectAttempts': 2,
      'disconnects': 1,
      'unackedKeys': 3,
      'ticks': 812,
      'maxTickGapMs': 4200,
      'samples': 40,
      'disconnections': <Object?>[
        <Object?, Object?>{
          'atMs': at,
          'cause': 'no data within the stale threshold',
          'step': 'Chart WebView renders',
        },
      ],
      'events': <Object?>[
        <Object?, Object?>{
          'atMs': at,
          'phase': 'connection.stale',
          'error': true,
          'step': 'Chart WebView renders',
        },
      ],
    });
    final text = ReportService().buildChecklistText(result);
    expect(text, contains('LIVE FEED (WEBSOCKET)'));
    expect(text, contains('Live trade time'));
    expect(text, contains('1 unplanned'));
    expect(text, contains('3 never acknowledged'));
    expect(text, contains('!! 1 unplanned disconnection(s)'));
    expect(text, contains('ERROR connection.stale  · Chart WebView renders'));
    expect(text, contains('Where it disconnected:'));
    expect(
      text,
      contains(
        '>>> 10:15:03  no data within the stale threshold  · during: Chart WebView renders',
      ),
    );

    final html = ReportService().buildHtml(result);
    expect(html, contains('class="feed-box bad"'));
    expect(html, contains('Live feed (websocket) — PROBLEMS'));
  });

  test('a clean live run says the feed never disconnected', () {
    final result = _run(<Object?, Object?>{
      'mode': 'live',
      'verdict': 'OK',
      'connectedAtEnd': true,
      'ticks': 500,
      'samples': 30,
    });
    final text = ReportService().buildChecklistText(result);
    expect(text, contains('OK: no feed problem was seen'));
    expect(text, contains('Disconnected      never'));
    expect(ReportService().buildHtml(result), contains('Disconnected: never'));
  });

  test('a BOD run says the feed was not measured', () {
    final result = _run(<Object?, Object?>{
      'mode': 'bod',
      'note':
          'BOD run (before the market opens): the live feed was not measured.',
    });
    final text = ReportService().buildChecklistText(result);
    expect(text, contains('LIVE FEED (WEBSOCKET)'));
    expect(text, contains('the live feed was not measured'));
    expect(ReportService().buildHtml(result), contains('BOD session'));
  });
}

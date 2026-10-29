import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:mobile_automation_tool/models/automation_result.dart';
import 'package:mobile_automation_tool/models/automation_step.dart';
import 'package:mobile_automation_tool/models/run_details.dart';
import 'package:mobile_automation_tool/services/report_service.dart';

/// Who tested, the IP addresses at start, the instruments added to a
/// watchlist and the app's order types: each reaches the text and HTML
/// reports, and survives the JSON report being read back.
void main() {
  // As the native driver sends a run of iTrade (no BO / CO tab, no GTT).
  Map<Object?, Object?> nativeMap() => <Object?, Object?>{
    'runId': 'feedbeef-0000',
    'testName': 'iTrade - Regression Automation',
    'targetPackage': 'com.codifi.itrade',
    'startedAtMs': 1792838273000,
    'finishedAtMs': 1792838333000,
    'durationMs': 60000,
    'steps': <Object?>[
      <Object?, Object?>{
        'id': 'ow_basket_create',
        'name': 'Basket › create a new basket',
        'status': 'passed',
        'code': 'BK-01',
      },
      <Object?, Object?>{
        'id': 'ow_basket_add',
        'name': 'Basket › add the instrument',
        'status': 'failed',
        'code': 'BK-02',
        'failureReason': 'ADD BUY did nothing',
      },
    ],
    'testedBy': <Object?, Object?>{
      'userId': 'admin',
      'role': 'HEAD',
      'signInMethod': 'biometric',
      'signedInAtMs': 1792838200000,
    },
    'network': <Object?, Object?>{
      'capturedAtMs': 1792838272000,
      'transport': 'Wi-Fi',
      'interface': 'wlan0',
      'ipv4': <Object?>['192.168.10.57'],
      'ipv6': <Object?>[],
      'publicIp': '49.207.1.2',
      'publicIpSource': 'api.ipify.org',
    },
    'watchlistAdds': <Object?>[
      <Object?, Object?>{
        'code': 'WL-24',
        'scrip': 'INFY-EQ',
        'exchange': 'NSE',
        'type': 'EQ',
        'from': 'Search › Stocks "INFY"',
        'watchlist': 'Test list',
        'added': true,
        'listed': true,
        'removed': true,
      },
      <Object?, Object?>{
        'code': 'WL-26',
        'scrip': 'CRUDEOIL',
        'exchange': 'MCX',
        'type': 'FUT',
        'from': 'Search › All "CRUDEOIL"',
        'watchlist': 'Test list',
        'added': false,
        'detail': 'Could not tap + for CRUDEOIL',
      },
      <Object?, Object?>{
        'code': 'OW-01',
        'scrip': 'IDEA-EQ',
        'exchange': 'NSE',
        'type': 'EQ',
        'from': 'Search › Stocks "IDEA"',
        'watchlist': 'Orders',
        'added': true,
        'listed': true,
        'removed': false,
      },
    ],
    'orderTypes': <Object?, Object?>{
      'app': 'iTrade',
      'moduleRun': true,
      'types': <Object?>[
        <Object?, Object?>{
          'id': 'market',
          'group': 'Order type',
          'label': 'Market',
          'exchanges': <Object?>['NSE'],
          'offered': true,
        },
        <Object?, Object?>{
          'id': 'limit',
          'group': 'Order type',
          'label': 'Limit',
          'offered': true,
        },
        <Object?, Object?>{
          'id': 'sl',
          'group': 'Order type',
          'label': 'SL (stop-loss limit)',
          'offered': true,
        },
        <Object?, Object?>{
          'id': 'co',
          'group': 'Variety',
          'label': 'CO (cover order)',
          'offered': false,
          'why': 'iTrade has no BO / CO tab on its order pad',
        },
        <Object?, Object?>{
          'id': 'mis',
          'group': 'Product',
          'label': 'MIS (intraday)',
          'offered': true,
        },
        <Object?, Object?>{
          'id': 'cnc',
          'group': 'Product',
          'label': 'CNC (delivery)',
          'offered': true,
        },
        <Object?, Object?>{
          'id': 'gtt',
          'group': 'Orders screen',
          'label': 'GTT (good till triggered)',
          'offered': false,
          'why': 'iTrade has no GTT tab under Orders',
        },
        <Object?, Object?>{
          'id': 'basket',
          'group': 'Orders screen',
          'label': 'Basket',
          'offered': true,
          'stepIds': <Object?>['ow_basket_create', 'ow_basket_add'],
        },
      ],
      'attempts': <Object?>[
        <Object?, Object?>{
          'exchange': 'NSE',
          'side': 'BUY',
          'kind': 'market',
          'product': 'mis',
          'outcome': 'refused',
          'detail': 'NSE BUY Market: sent and REJECTED',
        },
        <Object?, Object?>{
          'exchange': 'NSE',
          'side': 'SELL',
          'kind': 'market',
          'product': 'mis',
          'outcome': 'skipped',
          'detail': 'NSE SELL Market: not sent, Avail. Margin is 12.00',
        },
        <Object?, Object?>{
          'exchange': 'NSE',
          'side': 'BUY',
          'kind': 'limit',
          'product': 'mis',
          'outcome': 'accepted',
          'detail': 'NSE BUY Limit: NOT REFUSED',
        },
        <Object?, Object?>{
          'exchange': 'NSE',
          'side': 'BUY',
          'kind': 'co',
          'product': 'mis',
          'outcome': 'not_applicable',
          'detail': 'NSE BUY CO: not applicable',
        },
      ],
    },
  };

  test('the new parts are read from the driver and survive the JSON report', () {
    final result = AutomationResult.fromMap(nativeMap());
    expect(result.testedBy?.userId, 'admin');
    expect(result.testedBy?.methodLabel, 'Biometric (fingerprint)');
    expect(result.network?.deviceIp, '192.168.10.57');
    expect(result.network?.connection, 'Wi-Fi, wlan0');
    expect(result.watchlistAdds, hasLength(3));
    expect(result.orderTypes?.notOffered.map((t) => t.id), ['co', 'gtt']);

    final again = AutomationResult.fromMap(
      jsonDecode(jsonEncode(result.toJson())) as Map<String, Object?>,
    );
    expect(again.testedBy?.role, 'HEAD');
    expect(again.network?.publicIp, '49.207.1.2');
    expect(again.watchlistAdds.last.removed, isFalse);
    expect(again.orderTypes?.attempts, hasLength(4));
    // The video path is moved after the run; nothing new may be lost on the way.
    expect(again.withVideoPath('/x.mp4').orderTypes?.app, 'iTrade');
  });

  test('each order type gets its outcome from its orders or its cases', () {
    final result = AutomationResult.fromMap(nativeMap());
    final o = result.orderTypes!;
    String label(String id) =>
        o.outcomeOf(o.types.firstWhere((t) => t.id == id), result.steps).label;
    expect(label('market'), 'SENT · REFUSED');
    expect(label('limit'), 'ACCEPTED');
    expect(label('sl'), 'NOT TESTED');
    expect(label('co'), 'NOT IN APP');
    expect(label('gtt'), 'NOT IN APP');
    // MIS is the product of every order type: one accepted order makes it a defect too.
    expect(label('mis'), 'ACCEPTED');
    expect(label('basket'), 'FAILED');
    final market = o.outcomeOf(o.types.first, result.steps).detail;
    expect(market, contains('sent and refused: NSE BUY'));
    expect(market, contains('not sent: NSE SELL (Avail. Margin is 12.00)'));
    // Counted by its product, each order names its type; a CO not in the app says nothing about MIS.
    final mis = o
        .outcomeOf(o.types.firstWhere((t) => t.id == 'mis'), result.steps)
        .detail;
    expect(mis, contains('NOT refused: NSE BUY Limit'));
    expect(mis, contains('sent and refused: NSE BUY Market'));
    expect(mis, isNot(contains('not applicable')));
  });

  test(
    'the text report says who tested, from which IP, what was added and the order types',
    () {
      final text = ReportService().buildChecklistText(
        AutomationResult.fromMap(nativeMap()),
      );
      expect(text, contains('TESTED BY'));
      expect(text, contains('User ID           admin'));
      expect(text, contains('Role              HEAD'));
      expect(text, contains('Biometric (fingerprint) at '));
      expect(text, contains('Device IP         192.168.10.57  (Wi-Fi, wlan0)'));
      expect(
        text,
        contains('Public IP         49.207.1.2  (as api.ipify.org saw it)'),
      );

      expect(text, contains('WATCHLIST — INSTRUMENTS ADDED'));
      expect(
        text,
        contains(
          '3 tried: 2 added, 1 not added; 2 shown in the watchlist; 1 removed again, '
          '1 STILL IN THE WATCHLIST (remove by hand).',
        ),
      );
      expect(text, contains('Search › Stocks "INFY"'));
      expect(text, contains('NO, STILL THERE'));
      expect(text, contains('2. CRUDEOIL: Could not tap + for CRUDEOIL'));

      expect(text, contains('ORDER TYPES — ITRADE'));
      expect(
        text,
        contains('Offered by iTrade: Market, Limit, SL, MIS, CNC, Basket.'),
      );
      expect(text, contains('Not in iTrade: CO, GTT.'));
      expect(text, contains('CO: iTrade has no BO / CO tab on its order pad'));
    },
  );

  test('the HTML report has the same parts, outside the checklist filter', () {
    final html = ReportService().buildHtml(
      AutomationResult.fromMap(nativeMap()),
    );
    expect(html, contains('<span class="hchip">Tested by admin</span>'));
    expect(html, contains('<h2>Tested by</h2>'));
    expect(
      html,
      contains(
        '<td>Public IP</td><td>49.207.1.2 (as api.ipify.org saw it)</td>',
      ),
    );
    expect(html, contains('<h2>Watchlist — instruments added</h2>'));
    expect(html, contains('<h2>Order types — iTrade</h2>'));
    expect(html, contains('<span class="pill failed">ACCEPTED</span>'));
    // The checklist's search and filters must not hide or count these rows.
    expect(
      html,
      contains(
        "querySelectorAll('table.steps:not(.modsum):not(.extra) tbody tr')",
      ),
    );
    expect('<table class="steps extra">'.allMatches(html), hasLength(2));
  });

  test('a run that recorded none of them has none of the sections', () {
    const old = AutomationResult(
      runId: 'old',
      testName: 'Phase 1 - Login Automation',
      targetPackage: 'com.codifi.dhanush',
      steps: <AutomationStep>[],
    );
    final text = ReportService().buildChecklistText(old);
    final html = ReportService().buildHtml(old);
    for (final heading in <String>[
      'TESTED BY',
      'INSTRUMENTS ADDED',
      'ORDER TYPES',
    ]) {
      expect(text, isNot(contains(heading)));
    }
    expect(html, isNot(contains('<h2>Tested by</h2>')));
    expect(html, isNot(contains('Order types')));
  });

  test('a public IP that could not be looked up says why', () {
    final network = RunNetwork.fromMap(<Object?, Object?>{
      'transport': 'Mobile data',
      'ipv4': <Object?>[],
      'ipv6': <Object?>['2401:4900::1'],
      'publicIpError': 'api.ipify.org did not answer within 4 s',
    })!;
    expect(network.deviceIp, '2401:4900::1');
    expect(
      network.publicIpLabel,
      'not available (api.ipify.org did not answer within 4 s)',
    );
  });
}

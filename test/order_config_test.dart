import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile_automation_tool/models/order_config.dart';
import 'package:mobile_automation_tool/services/automation_service.dart';
import 'package:mobile_automation_tool/widgets/module_picker.dart';

void main() {
  test(
    'the default places orders on NSE only, with IDEA, and checks MCX with CRUDEOIL',
    () {
      final c = OrderConfig.defaults();
      expect(c.of('nse').enabled, isTrue);
      expect(c.of('nse').place, isTrue);
      expect(c.of('nse').script, 'IDEA');
      expect(c.of('mcx').enabled, isTrue);
      expect(c.of('mcx').place, isFalse);
      expect(c.of('mcx').script, 'CRUDEOIL');
      for (final k in <String>['bse', 'nfo', 'bfo']) {
        expect(c.of(k).enabled, isFalse, reason: k);
        expect(c.of(k).place, isFalse, reason: k);
      }
      expect(c.summary, 'NSE IDEA (orders) · MCX CRUDEOIL');
    },
  );

  test(
    'orders can only be placed on NSE and BSE, and only on an exchange that is on',
    () {
      var c = OrderConfig.defaults();
      c = c.withSegment('mcx', c.of('mcx').copyWith(place: true));
      expect(c.of('mcx').place, isFalse);
      c = c.withSegment('nse', c.of('nse').copyWith(enabled: false));
      expect(c.of('nse').place, isFalse);
      c = c.withSegment(
        'bse',
        const OrderSegmentConfig(enabled: true, place: true, script: 'IDEA'),
      );
      expect(c.of('bse').place, isTrue);
    },
  );

  test(
    'a saved or sent config survives a round trip and bad input falls back',
    () {
      var c = OrderConfig.defaults();
      c = c.withSegment(
        'bse',
        const OrderSegmentConfig(enabled: true, place: true, script: 'SBIN'),
      );
      final back = OrderConfig.fromJson(c.toJson());
      expect(back.toJson(), c.toJson());

      final odd = OrderConfig.fromJson(<String, Object?>{
        'nse': <String, Object?>{
          'enabled': 'yes',
          'place': 1,
          'script': '  tcs!! ',
        },
        'mcx': <String, Object?>{'enabled': true, 'place': true, 'script': ''},
      });
      expect(odd.of('nse').enabled, isTrue);
      expect(odd.of('nse').script, 'TCS');
      expect(odd.of('mcx').place, isFalse);
      expect(odd.of('mcx').script, 'CRUDEOIL');
      expect(
        OrderConfig.fromJson(null).toJson(),
        OrderConfig.defaults().toJson(),
      );
      expect(
        OrderConfig.fromJson('nonsense').toJson(),
        OrderConfig.defaults().toJson(),
      );
    },
  );

  test('instrument names are upper case, safe and never empty', () {
    expect(OrderConfig.cleanScript('idea', 'X'), 'IDEA');
    expect(OrderConfig.cleanScript('M&M-EQ', 'X'), 'M&M-EQ');
    expect(OrderConfig.cleanScript('a b;c', 'X'), 'ABC');
    expect(OrderConfig.cleanScript('   ', 'FALLBACK'), 'FALLBACK');
    expect(OrderConfig.cleanScript('A' * 40, 'X').length, 24);
  });

  testWidgets(
    'the Order placement module shows its settings, and changes reach the caller',
    (tester) async {
      const modules = <AutomationModule>[
        AutomationModule(id: 'login', label: 'Login'),
        AutomationModule(id: 'order_placement', label: 'Order placement'),
      ];
      var selected = <String>{'order_placement'};
      var config = OrderConfig.defaults();
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: StatefulBuilder(
              builder: (context, setState) => SingleChildScrollView(
                child: ModulePicker(
                  modules: modules,
                  selected: selected,
                  onChanged: (next) => setState(() => selected = next),
                  orderConfig: config,
                  onOrderConfigChanged: (next) => setState(() => config = next),
                ),
              ),
            ),
          ),
        ),
      );
      expect(
        find.byKey(const ValueKey<String>('order_config_panel')),
        findsOneWidget,
      );
      expect(
        find.byKey(const ValueKey<String>('order_place_mcx')),
        findsNothing,
        reason: 'MCX never places orders',
      );
      expect(
        find.byKey(const ValueKey<String>('order_place_nse')),
        findsOneWidget,
      );

      await tester.tap(find.byKey(const ValueKey<String>('order_seg_bse')));
      await tester.pump();
      expect(config.of('bse').enabled, isTrue);
      await tester.tap(find.byKey(const ValueKey<String>('order_place_bse')));
      await tester.pump();
      expect(config.of('bse').place, isTrue);

      await tester.enterText(
        find.byKey(const ValueKey<String>('order_script_nse')),
        'sbin',
      );
      await tester.pump();
      expect(config.of('nse').script, 'SBIN');

      await tester.tap(find.byKey(const ValueKey<String>('order_seg_nse')));
      await tester.pump();
      expect(config.of('nse').enabled, isFalse);
      expect(config.of('nse').place, isFalse);
    },
  );

  testWidgets(
    'a long module description is one line with Read more, which opens the whole text',
    (tester) async {
      final long = 'Order pad and order book on a live account. ' * 6;
      final modules = <AutomationModule>[
        const AutomationModule(
          id: 'login',
          label: 'Login',
          description: 'Short.',
        ),
        AutomationModule(
          id: 'order_placement',
          label: 'Order placement',
          description: long,
        ),
      ];
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: SingleChildScrollView(
              child: ModulePicker(
                modules: modules,
                selected: const <String>{},
                onChanged: (_) {},
              ),
            ),
          ),
        ),
      );
      expect(find.text('Short.'), findsOneWidget);
      expect(
        find.byKey(const ValueKey<String>('module_read_more')),
        findsOneWidget,
        reason: 'only the long one',
      );
      expect(find.text(long), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey<String>('module_read_more')));
      await tester.pumpAndSettle();
      expect(find.byType(AlertDialog), findsOneWidget);
      expect(
        find.text(long),
        findsNWidgets(2),
        reason: 'the dialog shows it in full',
      );
      await tester.tap(find.text('Close'));
      await tester.pumpAndSettle();
      expect(find.byType(AlertDialog), findsNothing);
    },
  );
}

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
      expect(
        c.summary,
        'NSE IDEA (orders) · MCX CRUDEOIL · GTT IDEA · Basket IDEA',
      );
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

  test(
    'the GTT option is on by default, kept through changes, and read back',
    () {
      var c = OrderConfig.defaults();
      expect(c.gtt, isTrue);
      c = c.withGtt(false);
      expect(c.summary, 'NSE IDEA (orders) · MCX CRUDEOIL · Basket IDEA');
      c = c.withSegment('bse', c.of('bse').copyWith(enabled: true));
      expect(
        c.gtt,
        isFalse,
        reason: 'changing an exchange keeps the GTT choice',
      );
      expect(OrderConfig.fromJson(c.toJson()).gtt, isFalse);
      expect(
        OrderConfig.fromJson(<String, Object?>{'gtt': 'maybe'}).gtt,
        isTrue,
      );
      expect(c.gttScript, 'IDEA');
      c = c.withGttScript(' sbin ');
      expect(c.gttScript, 'SBIN');
      expect(c.withGttScript('').gttScript, 'IDEA', reason: 'never empty');
      expect(OrderConfig.fromJson(c.toJson()).gttScript, 'SBIN');
      expect(c.withGtt(true).summary, contains('GTT SBIN'));
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
        find.text('NSE IDEA (orders) · MCX CRUDEOIL · GTT IDEA · Basket IDEA'),
        findsOneWidget,
        reason: 'collapsed: the summary line',
      );
      expect(
        find.byKey(const ValueKey<String>('order_seg_nse')),
        findsNothing,
        reason: 'collapsed until opened',
      );
      await tester.tap(
        find.byKey(const ValueKey<String>('order_config_header')),
      );
      await tester.pumpAndSettle();
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
        find.byKey(const ValueKey<String>('read_more_link')),
        findsOneWidget,
        reason: 'only the long one',
      );
      expect(find.text(long), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey<String>('read_more_link')));
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

  testWidgets('the GTT sub-module has its own switch and instrument', (
    tester,
  ) async {
    const modules = <AutomationModule>[
      AutomationModule(id: 'order_placement', label: 'Order placement'),
    ];
    var config = OrderConfig.defaults();
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: StatefulBuilder(
            builder: (context, setState) => SingleChildScrollView(
              child: ModulePicker(
                modules: modules,
                selected: const <String>{'order_placement'},
                onChanged: (_) {},
                orderConfig: config,
                onOrderConfigChanged: (next) => setState(() => config = next),
              ),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.byKey(const ValueKey<String>('order_config_header')));
    await tester.pumpAndSettle();
    await tester.ensureVisible(
      find.byKey(const ValueKey<String>('order_gtt_script')),
    );
    await tester.enterText(
      find.byKey(const ValueKey<String>('order_gtt_script')),
      'sbin',
    );
    await tester.pump();
    expect(config.gttScript, 'SBIN');
    expect(
      config.of('nse').script,
      'IDEA',
      reason: 'the NSE order instrument is separate',
    );
    await tester.ensureVisible(
      find.byKey(const ValueKey<String>('order_gtt_switch')),
    );
    await tester.tap(find.byKey(const ValueKey<String>('order_gtt_switch')));
    await tester.pump();
    expect(config.gtt, isFalse);
  });

  test(
    'the basket option is on by default, has its own instrument, and is kept',
    () {
      var c = OrderConfig.defaults();
      expect(c.basket, isTrue);
      expect(c.basketScript, 'IDEA');
      c = c.withBasketScript(' relaxo ').withGttScript('sbin');
      expect(c.basketScript, 'RELAXO');
      expect(c.gttScript, 'SBIN', reason: 'the GTT instrument is separate');
      expect(c.withBasketScript('').basketScript, 'IDEA');
      c = c
          .withBasket(false)
          .withSegment('bse', c.of('bse').copyWith(enabled: true));
      expect(
        c.basket,
        isFalse,
        reason: 'changing an exchange keeps the basket choice',
      );
      final back = OrderConfig.fromJson(c.toJson());
      expect(back.basket, isFalse);
      expect(back.basketScript, 'RELAXO');
      expect(back.gttScript, 'SBIN');
      expect(
        OrderConfig.fromJson(<String, Object?>{'basket': 1}).basket,
        isTrue,
      );
    },
  );
}

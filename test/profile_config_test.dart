import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile_automation_tool/models/profile_config.dart';
import 'package:mobile_automation_tool/services/automation_service.dart';
import 'package:mobile_automation_tool/widgets/module_picker.dart';

void main() {
  test(
    'every Profile check is on by default and the settings survive a round trip',
    () {
      final c = ProfileConfig.defaults();
      expect(ProfileConfig.all, hasLength(22));
      expect(c.onCount, 22);
      expect(c.summary, 'All 22 checks');

      final off = c
          .withCheck('profile_toggle_theme', false)
          .withAll(ProfileConfig.profileTab, false);
      expect(off.isOn('profile_toggle_theme'), isFalse);
      expect(off.isOn('profile_details_opened'), isFalse);
      expect(off.isOn('profile_font_size'), isTrue);
      expect(off.summary, '9 of 22 checks: Profile tab 0, Settings tab 9');

      final back = ProfileConfig.fromJson(off.toJson());
      expect(back.off, off.off);
      // Unknown ids and bad input are ignored rather than trusted.
      expect(
        ProfileConfig.fromJson(<String, Object?>{
          'off': <Object?>['nope', 3],
        }).off,
        isEmpty,
      );
      expect(ProfileConfig.fromJson('junk').onCount, 22);
    },
  );

  testWidgets('the Profile settings sit under Profile and report each change', (
    tester,
  ) async {
    var config = ProfileConfig.defaults();
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: SingleChildScrollView(
            child: StatefulBuilder(
              builder: (context, setState) => ModulePicker(
                modules: const <AutomationModule>[
                  AutomationModule(
                    id: 'login',
                    label: 'Login',
                    description: '',
                  ),
                  AutomationModule(
                    id: 'profile',
                    label: 'Profile',
                    description: '',
                  ),
                ],
                selected: const <String>{'login', 'profile'},
                onChanged: (_) {},
                profileConfig: config,
                onProfileConfigChanged: (next) => setState(() => config = next),
              ),
            ),
          ),
        ),
      ),
    );
    expect(find.text('Profile settings'), findsOneWidget);
    expect(find.text('All 22 checks'), findsOneWidget);

    await tester.tap(find.text('Profile settings'));
    await tester.pumpAndSettle();
    await tester.ensureVisible(
      find.byKey(const ValueKey<String>('profile_check_profile_toggle_theme')),
    );
    await tester.tap(
      find.byKey(const ValueKey<String>('profile_check_profile_toggle_theme')),
    );
    await tester.pumpAndSettle();
    expect(config.isOn('profile_toggle_theme'), isFalse);
    expect(
      find.text('21 of 22 checks: Profile tab 12, Settings tab 9'),
      findsOneWidget,
    );

    await tester.ensureVisible(
      find.byKey(const ValueKey<String>('profile_profile_none')),
    );
    await tester.tap(
      find.byKey(const ValueKey<String>('profile_profile_none')),
    );
    await tester.pumpAndSettle();
    expect(ProfileConfig.profileTab.every((c) => !config.isOn(c.id)), isTrue);
  });
}

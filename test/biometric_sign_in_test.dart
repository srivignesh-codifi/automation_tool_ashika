import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile_automation_tool/screens/sign_in_screen.dart';
import 'package:mobile_automation_tool/services/auth_service.dart';
import 'package:mobile_automation_tool/services/automation_service.dart';
import 'package:mobile_automation_tool/services/biometric_service.dart';
import 'package:mobile_automation_tool/services/secure_storage_service.dart';
import 'package:mobile_automation_tool/theme/codifi_theme.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() => FlutterSecureStorage.setMockInitialValues(<String, String>{}));

  // The sign-in API, faked: qa1 / secret gets a token and a role, anything else a 401.
  Future<(int, String)> server(Uri url, String body) async {
    final sent = jsonDecode(body) as Map<String, Object?>;
    if (sent['username'] == 'qa1' && sent['password'] == 'secret') {
      return (200, '{"token":"tok-1","access":{"role":"TESTER"}}');
    }
    return (401, '{"message":"Invalid username or password"}');
  }

  AuthService auth({bool skip = false}) =>
      AuthService(SecureStorageService(), transport: server, skipCheck: skip);

  /// The native biometric calls, faked on their own channel; [calls] records them.
  MethodChannel fakeBiometrics(
    List<MethodCall> calls, {
    required Map<String, Object?> status,
    Map<String, Object?>? unlock,
    Map<String, Object?> enable = const <String, Object?>{'ok': true},
  }) {
    const channel = MethodChannel('biometric_sign_in_test');
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
          calls.add(call);
          return switch (call.method) {
            'biometricStatus' => status,
            'biometricUnlock' => unlock,
            'biometricEnable' => enable,
            'biometricDisable' => true,
            _ => null,
          };
        });
    addTearDown(
      () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, null),
    );
    return channel;
  }

  /// Scrolls [key]'s control into view (the card is taller than a phone screen) and taps it.
  Future<void> tapKey(WidgetTester tester, String key) async {
    final finder = find.byKey(ValueKey<String>(key));
    await tester.ensureVisible(finder);
    await tester.pump();
    await tester.tap(finder);
  }

  Future<void> pumpSignIn(
    WidgetTester tester, {
    required AuthService auth,
    required MethodChannel channel,
    required ValueChanged<AuthUser> onSignedIn,
    bool prompt = false,
  }) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: CodifiTheme.dark(),
        home: SignInScreen(
          auth: auth,
          onSignedIn: onSignedIn,
          biometrics: BiometricService(commands: channel),
          promptBiometric: prompt,
        ),
      ),
    );
    await tester.pump();
    await tester.pump();
  }

  test(
    'a sign-in carries the role and how it was made, never the token, to the report',
    () async {
      final builtIn = AuthService(SecureStorageService(), skipCheck: false);
      final admin = await builtIn.signIn(userId: 'admin', password: 'admin123');
      expect(admin.role, 'HEAD');
      expect(admin.method, SignInMethod.password);
      final report = admin.toReport();
      expect(report['userId'], 'admin');
      expect(report['role'], 'HEAD');
      expect(report['signInMethod'], 'password');
      expect(report['signedInAtMs'], isA<int>());
      expect(report.containsKey('token'), isFalse);

      final byTouch = await auth().signIn(
        userId: 'qa1',
        password: 'secret',
        method: SignInMethod.biometric,
      );
      expect(byTouch.role, 'TESTER');
      expect(byTouch.toReport()['signInMethod'], 'biometric');

      // A refused password is told apart from a server that did not answer.
      await expectLater(
        auth().signIn(userId: 'qa1', password: 'old'),
        throwsA(
          isA<AuthException>().having((e) => e.rejected, 'rejected', isTrue),
        ),
      );
      final offline = AuthService(
        SecureStorageService(),
        transport: (_, _) async => throw const SocketException('unreachable'),
        skipCheck: false,
      );
      await expectLater(
        offline.signIn(userId: 'qa1', password: 'secret'),
        throwsA(
          isA<AuthException>().having((e) => e.rejected, 'rejected', isFalse),
        ),
      );
    },
  );

  test(
    'a phone or install without the native part offers no biometric sign-in',
    () async {
      const channel = MethodChannel('biometric_absent_test');
      final status = await BiometricService(commands: channel).status();
      expect(status.available, isFalse);
      expect(status.enabled, isFalse);
      final outcome = await BiometricService(commands: channel).unlock();
      expect(outcome.ok, isFalse);
      expect(outcome.error, 'unavailable');
    },
  );

  testWidgets(
    'with biometric sign-in on, a fingerprint signs the saved user in',
    (tester) async {
      final calls = <MethodCall>[];
      final channel = fakeBiometrics(
        calls,
        status: <String, Object?>{
          'available': true,
          'enabled': true,
          'userId': 'qa1',
        },
        unlock: <String, Object?>{
          'ok': true,
          'userId': 'qa1',
          'password': 'secret',
        },
      );
      AuthUser? signedIn;
      await pumpSignIn(
        tester,
        auth: auth(),
        channel: channel,
        onSignedIn: (u) => signedIn = u,
      );

      expect(find.text('SIGN IN WITH BIOMETRIC'), findsOneWidget);
      expect(find.text('as qa1'), findsOneWidget);
      expect(
        find.byKey(const ValueKey<String>('sign_in_biometric_switch')),
        findsNothing,
      );
      // Not prompted by itself: that is only at launch.
      expect(calls.map((c) => c.method), isNot(contains('biometricUnlock')));

      await tapKey(tester, 'sign_in_biometric_button');
      await tester.pump(const Duration(milliseconds: 100));
      expect(signedIn?.userId, 'qa1');
      expect(signedIn?.method, SignInMethod.biometric);
      expect(signedIn?.role, 'TESTER');
    },
  );

  testWidgets('at launch the fingerprint prompt opens by itself', (
    tester,
  ) async {
    final calls = <MethodCall>[];
    final channel = fakeBiometrics(
      calls,
      status: <String, Object?>{
        'available': true,
        'enabled': true,
        'userId': 'qa1',
      },
      unlock: <String, Object?>{
        'ok': false,
        'error': 'cancelled',
        'message': 'Biometric sign-in was cancelled.',
      },
    );
    AuthUser? signedIn;
    await pumpSignIn(
      tester,
      auth: auth(),
      channel: channel,
      onSignedIn: (u) => signedIn = u,
      prompt: true,
    );
    await tester.pump(const Duration(milliseconds: 100));

    expect(calls.map((c) => c.method), contains('biometricUnlock'));
    // Cancelling leaves the password form, with no error.
    expect(signedIn, isNull);
    expect(find.text('Biometric sign-in was cancelled.'), findsNothing);
    expect(
      find.byKey(const ValueKey<String>('sign_in_button')),
      findsOneWidget,
    );
  });

  testWidgets('a password sign-in can turn biometric sign-in on', (
    tester,
  ) async {
    final calls = <MethodCall>[];
    final channel = fakeBiometrics(
      calls,
      status: <String, Object?>{'available': true, 'enabled': false},
    );
    AuthUser? signedIn;
    await pumpSignIn(
      tester,
      auth: auth(),
      channel: channel,
      onSignedIn: (u) => signedIn = u,
    );

    expect(find.text('SIGN IN WITH BIOMETRIC'), findsNothing);
    await tapKey(tester, 'sign_in_biometric_switch');
    await tester.pump();
    await tester.enterText(
      find.byKey(const ValueKey<String>('sign_in_user_id')),
      'qa1',
    );
    await tester.enterText(
      find.byKey(const ValueKey<String>('sign_in_password')),
      'secret',
    );
    await tapKey(tester, 'sign_in_button');
    await tester.pump(const Duration(milliseconds: 100));

    final enable = calls.singleWhere((c) => c.method == 'biometricEnable');
    expect(enable.arguments, <String, Object?>{
      'userId': 'qa1',
      'password': 'secret',
    });
    expect(signedIn?.method, SignInMethod.password);
  });

  testWidgets(
    'a saved password the server now refuses turns biometric sign-in off',
    (tester) async {
      final calls = <MethodCall>[];
      final channel = fakeBiometrics(
        calls,
        status: <String, Object?>{
          'available': true,
          'enabled': true,
          'userId': 'qa1',
        },
        unlock: <String, Object?>{
          'ok': true,
          'userId': 'qa1',
          'password': 'changed-since',
        },
      );
      AuthUser? signedIn;
      await pumpSignIn(
        tester,
        auth: auth(),
        channel: channel,
        onSignedIn: (u) => signedIn = u,
      );

      await tapKey(tester, 'sign_in_biometric_button');
      await tester.pump(const Duration(milliseconds: 100));
      expect(signedIn, isNull);
      expect(calls.map((c) => c.method), contains('biometricDisable'));
      expect(
        find.textContaining('Biometric sign-in was turned off'),
        findsOneWidget,
      );
    },
  );

  testWidgets(
    'while sign-in is skipped there is no password to keep, so no biometric',
    (tester) async {
      final calls = <MethodCall>[];
      final channel = fakeBiometrics(
        calls,
        status: <String, Object?>{
          'available': true,
          'enabled': true,
          'userId': 'qa1',
        },
      );
      await pumpSignIn(
        tester,
        auth: auth(skip: true),
        channel: channel,
        onSignedIn: (_) {},
      );
      expect(calls, isEmpty);
      expect(find.text('SIGN IN WITH BIOMETRIC'), findsNothing);
      expect(
        find.byKey(const ValueKey<String>('sign_in_biometric_switch')),
        findsNothing,
      );
    },
  );

  test('a run is started with who tested it', () async {
    const channel = MethodChannel('start_tested_by_test');
    Map<Object?, Object?>? sent;
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
          if (call.method == 'startLoginAutomation') {
            sent = call.arguments as Map<Object?, Object?>;
            return <String, Object?>{'started': true};
          }
          return null;
        });
    addTearDown(
      () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, null),
    );
    final user = AuthUser(
      userId: 'qa1',
      token: 'tok-1',
      role: 'TESTER',
      method: SignInMethod.biometric,
      signedInAt: DateTime(2026, 10, 29, 10),
    );
    final error = await AutomationService(commands: channel)
        .startLoginAutomation(
          targetPackage: 'com.codifi.itrade',
          clientId: 'X',
          otp: '1',
          password: 'p',
          testedBy: user.toReport(),
        );
    expect(error, isNull);
    expect(sent?['testedBy'], <String, Object?>{
      'userId': 'qa1',
      'role': 'TESTER',
      'signInMethod': 'biometric',
      'signedInAtMs': DateTime(2026, 10, 29, 10).millisecondsSinceEpoch,
    });
  });
}

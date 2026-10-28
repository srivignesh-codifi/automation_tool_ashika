import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile_automation_tool/screens/dashboard_screen.dart';
import 'package:mobile_automation_tool/screens/sign_in_screen.dart';
import 'package:mobile_automation_tool/services/auth_service.dart';
import 'package:mobile_automation_tool/services/automation_service.dart';
import 'package:mobile_automation_tool/services/report_service.dart';
import 'package:mobile_automation_tool/services/secure_storage_service.dart';
import 'package:mobile_automation_tool/theme/codifi_theme.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() => FlutterSecureStorage.setMockInitialValues(<String, String>{}));

  // The sign-in API, faked: admin / admin123 gets a token, anything else a 401.
  Future<(int, String)> server(Uri url, String body) async {
    expect(url.path, '/api/admin/login');
    final sent = jsonDecode(body) as Map<String, Object?>;
    if (sent['username'] == 'qa1' && sent['password'] == 'secret') {
      return (200, '{"success":true,"data":{"token":"tok-123"}}');
    }
    return (401, '{"message":"Invalid username or password"}');
  }

  test(
    'the sign-in API: token kept, refusals and an unreachable server explained',
    () async {
      final auth = AuthService(
        SecureStorageService(),
        transport: server,
        skipCheck: false,
      );
      final user = await auth.signIn(userId: 'qa1', password: 'secret');
      expect(user.token, 'tok-123');
      expect((await auth.currentUser())?.token, 'tok-123');

      await expectLater(
        auth.signIn(userId: 'qa1', password: 'nope'),
        throwsA(
          isA<AuthException>().having(
            (e) => e.message,
            'message',
            'Invalid username or password',
          ),
        ),
      );
      final refusedIn200 = AuthService(
        SecureStorageService(),
        transport: (_, _) async =>
            (200, '{"success":false,"message":"Account locked"}'),
        skipCheck: false,
      );
      await expectLater(
        refusedIn200.signIn(userId: 'qa1', password: 'x'),
        throwsA(
          isA<AuthException>().having(
            (e) => e.message,
            'message',
            'Account locked',
          ),
        ),
      );
      final offline = AuthService(
        SecureStorageService(),
        transport: (_, _) async => throw const SocketException('unreachable'),
        skipCheck: false,
      );
      await expectLater(
        offline.signIn(userId: 'qa1', password: 'x'),
        throwsA(
          isA<AuthException>().having(
            (e) => e.message,
            'message',
            contains('Cannot reach the sign-in server'),
          ),
        ),
      );
    },
  );

  test(
    'while sign-in is skipped, SIGN IN opens the tool without asking the server',
    () async {
      final auth = AuthService(
        SecureStorageService(),
        transport: (_, _) async => fail('the server must not be called'),
        skipCheck: true,
      );
      expect(auth.connected, isFalse);
      expect((await auth.signIn(userId: '', password: '')).userId, 'tester');
      expect((await auth.signIn(userId: ' qa2 ', password: '')).userId, 'qa2');
      expect((await auth.currentUser())?.userId, 'qa2');
    },
  );

  test(
    'sign-in refuses empty fields, remembers the user and signs out',
    () async {
      final auth = AuthService(
        SecureStorageService(),
        transport: server,
        skipCheck: false,
      );
      expect(await auth.currentUser(), isNull);
      expect(
        () => auth.signIn(userId: ' ', password: 'x'),
        throwsA(isA<AuthException>()),
      );
      expect(
        () => auth.signIn(userId: 'qa1', password: ''),
        throwsA(isA<AuthException>()),
      );

      final user = await auth.signIn(userId: ' qa1 ', password: 'secret');
      expect(user.userId, 'qa1');
      expect((await auth.currentUser())?.userId, 'qa1');

      await auth.signOut();
      expect(await auth.currentUser(), isNull);
    },
  );

  testWidgets('the sign-in screen signs in and hands the user over', (
    tester,
  ) async {
    AuthUser? signedIn;
    await tester.pumpWidget(
      MaterialApp(
        theme: CodifiTheme.dark(),
        home: SignInScreen(
          auth: AuthService(
            SecureStorageService(),
            transport: server,
            skipCheck: false,
          ),
          onSignedIn: (u) => signedIn = u,
        ),
      ),
    );
    await tester.pump();
    expect(find.text('Sign in'), findsOneWidget);

    await tester.tap(find.byKey(const ValueKey<String>('sign_in_button')));
    await tester.pump();
    expect(find.text('Enter your user ID.'), findsOneWidget);

    await tester.enterText(
      find.byKey(const ValueKey<String>('sign_in_user_id')),
      'qa1',
    );
    await tester.enterText(
      find.byKey(const ValueKey<String>('sign_in_password')),
      'secret',
    );
    await tester.tap(find.byKey(const ValueKey<String>('sign_in_button')));
    await tester.pump(const Duration(milliseconds: 100));
    expect(signedIn?.userId, 'qa1');
  });

  testWidgets(
    'home shows each app by logo and name, never by package, and a tile opens its run',
    (tester) async {
      const channel = MethodChannel('home_tiles_test');
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (call) async {
            if (call.method == 'isTargetAppInstalled') {
              return <String, Object?>{
                'packageName': call.arguments['packageName'],
                'installed':
                    call.arguments['packageName'] == 'com.codifi.itrade',
                'allowlisted': true,
              };
            }
            return null; // no native config: the fallback lists every app
          });
      addTearDown(
        () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
            .setMockMethodCallHandler(channel, null),
      );
      // A phone with a 48 dp gesture / button bar at the bottom.
      tester.view.padding = FakeViewPadding(
        bottom: 48 * tester.view.devicePixelRatio,
      );
      addTearDown(tester.view.resetPadding);
      final dir = Directory.systemTemp.createTempSync('home_tiles');
      addTearDown(() => dir.deleteSync(recursive: true));

      await tester.pumpWidget(
        MaterialApp(
          theme: CodifiTheme.dark(),
          home: DashboardScreen(
            automation: AutomationService(commands: channel),
            reports: _DirReports(dir),
            storage: SecureStorageService(),
            user: const AuthUser(userId: 'qa1'),
            onSignOut: () {},
          ),
        ),
      );
      await tester.runAsync(
        () => Future<void>.delayed(const Duration(milliseconds: 200)),
      );
      await tester.pump(const Duration(milliseconds: 500));

      // Edge-to-edge: the list's last item stays above Android's navigation bar.
      final list = tester.widget<ListView>(find.byType(ListView).first);
      expect(
        (list.padding! as EdgeInsets).bottom,
        16 + tester.view.padding.bottom / tester.view.devicePixelRatio,
      );

      for (final id in <String>['ashika', 'tradion', 'itrade']) {
        expect(find.byKey(ValueKey<String>('app_tile_$id')), findsOneWidget);
      }
      expect(find.text('iTrade'), findsOneWidget);
      expect(find.text('Installed'), findsOneWidget);
      expect(find.textContaining('com.codifi'), findsNothing);

      await tester.tap(find.byKey(const ValueKey<String>('app_tile_itrade')));
      await tester.pump(const Duration(milliseconds: 500));
      await tester.pump(const Duration(milliseconds: 500));
      // The run is for iTrade alone: no app picker, no package anywhere.
      expect(find.byKey(const ValueKey<String>('app_picker')), findsNothing);
      expect(find.textContaining('com.codifi'), findsNothing);
      expect(find.text('iTrade'), findsWidgets);
    },
  );
}

class _DirReports extends ReportService {
  _DirReports(this._dir);

  final Directory _dir;

  @override
  Future<Directory> reportsDirectory() async => _dir;
}

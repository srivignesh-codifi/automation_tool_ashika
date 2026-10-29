import 'package:flutter/material.dart';

import 'screens/dashboard_screen.dart';
import 'screens/setup_screen.dart';
import 'screens/sign_in_screen.dart';
import 'services/auth_service.dart';
import 'services/automation_service.dart';
import 'services/biometric_service.dart';
import 'services/report_service.dart';
import 'services/secure_storage_service.dart';
import 'theme/codifi_theme.dart';

/// Root of the automation tool.
///
/// The three services are created once here and handed down explicitly — the
/// tool has one screen stack and no need for a state-management package.
class MobileAutomationToolApp extends StatefulWidget {
  const MobileAutomationToolApp({super.key});

  @override
  State<MobileAutomationToolApp> createState() =>
      _MobileAutomationToolAppState();
}

class _MobileAutomationToolAppState extends State<MobileAutomationToolApp> {
  final AutomationService _automation = AutomationService();
  final ReportService _reports = ReportService();
  final SecureStorageService _storage = SecureStorageService();
  late final AuthService _auth = AuthService(_storage);
  final BiometricService _biometrics = BiometricService();

  @override
  Widget build(BuildContext context) {
    // CodiFi's look is dark only, whatever the phone's setting.
    return MaterialApp(
      title: 'CodiFi Automation',
      debugShowCheckedModeBanner: false,
      theme: CodifiTheme.dark(),
      themeMode: ThemeMode.dark,
      home: _StartupGate(
        automation: _automation,
        reports: _reports,
        storage: _storage,
        auth: _auth,
        biometrics: _biometrics,
      ),
    );
  }
}

/// Asks the user to sign in, then shows the permission setup before the
/// dashboard at first launch, and again whenever the accessibility service has
/// gone off (a reinstall switches it off).
class _StartupGate extends StatefulWidget {
  const _StartupGate({
    required this.automation,
    required this.reports,
    required this.storage,
    required this.auth,
    required this.biometrics,
  });

  final AutomationService automation;
  final ReportService reports;
  final SecureStorageService storage;
  final AuthService auth;
  final BiometricService biometrics;

  @override
  State<_StartupGate> createState() => _StartupGateState();
}

class _StartupGateState extends State<_StartupGate> {
  bool _checked = false;
  AuthUser? _user;
  bool _sessionRead = false;

  /// The fingerprint prompt opens by itself at launch only: after signing out
  /// the tester may want another account.
  bool _promptBiometric = true;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _start());
  }

  Future<void> _start() async {
    if (!mounted) return;
    setState(() {
      // Always request a fresh sign-in at launch so the report upload token is
      // renewed from the built-in login reply instead of reusing an old token.
      _user = null;
      _sessionRead = true;
    });
  }

  Future<void> _signedIn(AuthUser user) async {
    setState(() => _user = user);
    await _check();
  }

  Future<void> _signOut() async {
    await widget.auth.signOut();
    if (mounted) {
      setState(() {
        _user = null;
        _checked = false;
        _promptBiometric = false;
      });
    }
  }

  Future<void> _check() async {
    final done = await widget.storage.readSetupDone();
    final accessibility = await widget.automation.accessibilityStatus();
    if (!mounted) return;
    if (!done || !accessibility.isReady) {
      await Navigator.of(context).push<bool>(
        MaterialPageRoute<bool>(
          builder: (_) => SetupScreen(
            automation: widget.automation,
            storage: widget.storage,
            firstRun: true,
            onLogout: () {
              Navigator.of(context).pop();
              _signOut();
            },
          ),
        ),
      );
    }
    if (mounted) setState(() => _checked = true);
  }

  @override
  Widget build(BuildContext context) {
    if (_sessionRead && _user == null) {
      return SignInScreen(
        auth: widget.auth,
        onSignedIn: _signedIn,
        biometrics: widget.biometrics,
        promptBiometric: _promptBiometric,
      );
    }
    if (!_checked) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }
    return DashboardScreen(
      automation: widget.automation,
      reports: widget.reports,
      storage: widget.storage,
      user: _user,
      onSignOut: _signOut,
    );
  }
}

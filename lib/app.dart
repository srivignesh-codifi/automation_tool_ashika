import 'package:flutter/material.dart';

import 'screens/dashboard_screen.dart';
import 'screens/setup_screen.dart';
import 'services/automation_service.dart';
import 'services/report_service.dart';
import 'services/secure_storage_service.dart';

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

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Mobile Automation Tool',
      debugShowCheckedModeBanner: false,
      theme: _theme(Brightness.light),
      darkTheme: _theme(Brightness.dark),
      home: _StartupGate(
        automation: _automation,
        reports: _reports,
        storage: _storage,
      ),
    );
  }

  ThemeData _theme(Brightness brightness) {
    return ThemeData(
      useMaterial3: true,
      brightness: brightness,
      colorScheme: ColorScheme.fromSeed(
        seedColor: const Color(0xFF2E5AAC),
        brightness: brightness,
      ),
      inputDecorationTheme: const InputDecorationTheme(
        border: OutlineInputBorder(),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(48)),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(minimumSize: const Size.fromHeight(46)),
      ),
    );
  }
}

/// Shows the permission setup before the dashboard at first launch, and again
/// whenever the accessibility service has gone off (a reinstall switches it off).
class _StartupGate extends StatefulWidget {
  const _StartupGate({
    required this.automation,
    required this.reports,
    required this.storage,
  });

  final AutomationService automation;
  final ReportService reports;
  final SecureStorageService storage;

  @override
  State<_StartupGate> createState() => _StartupGateState();
}

class _StartupGateState extends State<_StartupGate> {
  bool _checked = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _check());
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
          ),
        ),
      );
    }
    if (mounted) setState(() => _checked = true);
  }

  @override
  Widget build(BuildContext context) {
    if (!_checked) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }
    return DashboardScreen(
      automation: widget.automation,
      reports: widget.reports,
      storage: widget.storage,
    );
  }
}

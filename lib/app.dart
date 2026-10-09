import 'package:flutter/material.dart';

import 'screens/dashboard_screen.dart';
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
      home: DashboardScreen(
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
        style: FilledButton.styleFrom(
          minimumSize: const Size.fromHeight(48),
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          minimumSize: const Size.fromHeight(46),
        ),
      ),
    );
  }
}

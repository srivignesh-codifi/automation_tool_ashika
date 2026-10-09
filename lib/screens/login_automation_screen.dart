import 'package:flutter/material.dart';

import '../models/test_run.dart';
import '../services/automation_service.dart';
import '../services/report_service.dart';
import '../services/secure_storage_service.dart';
import '../widgets/credential_field.dart';
import '../widgets/status_indicator.dart';
import 'report_screen.dart';
import 'running_test_screen.dart';

/// Credentials form and pre-flight checks for the Phase 1 login automation.
class LoginAutomationScreen extends StatefulWidget {
  const LoginAutomationScreen({
    required this.automation,
    required this.reports,
    required this.storage,
    super.key,
  });

  final AutomationService automation;
  final ReportService reports;
  final SecureStorageService storage;

  @override
  State<LoginAutomationScreen> createState() => _LoginAutomationScreenState();
}

class _LoginAutomationScreenState extends State<LoginAutomationScreen>
    with WidgetsBindingObserver {
  final _formKey = GlobalKey<FormState>();
  final _packageController = TextEditingController();
  final _clientIdController = TextEditingController();
  final _otpController = TextEditingController();
  final _mpinController = TextEditingController();

  AccessibilityStatus _accessibility = const AccessibilityStatus.unknown();
  AutomationConfigInfo _config = const AutomationConfigInfo.fallback();
  TargetAppStatus? _target;
  bool _remember = false;
  bool _busy = true;
  bool _running = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _load();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _packageController.dispose();
    _clientIdController.dispose();
    // The OTP is one-time: drop it as the screen goes away.
    _otpController.clear();
    _otpController.dispose();
    _mpinController.dispose();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) _refreshStatuses();
  }

  Future<void> _load() async {
    final config = await widget.automation.config();
    final savedPackage = await widget.storage.readTargetPackage();
    final remember = await widget.storage.readRemember();
    final clientId = remember ? await widget.storage.readClientId() : null;
    final mpin = remember ? await widget.storage.readMpin() : null;

    if (!mounted) return;
    setState(() {
      _config = config;
      _packageController.text = savedPackage ?? config.defaultTargetPackage;
      _remember = remember;
      if (clientId != null) _clientIdController.text = clientId;
      if (mpin != null) _mpinController.text = mpin;
      _busy = false;
    });
    await _refreshStatuses();
  }

  Future<void> _refreshStatuses() async {
    final accessibility = await widget.automation.accessibilityStatus();
    final running = await widget.automation.isRunning();
    if (!mounted) return;
    setState(() {
      _accessibility = accessibility;
      _running = running;
    });
  }

  Future<void> _checkTargetApp() async {
    setState(() => _busy = true);
    final status = await widget.automation.targetAppStatus(
      _packageController.text.trim(),
    );
    if (!mounted) return;
    setState(() {
      _target = status;
      _busy = false;
    });
    if (!status.allowlisted) {
      _snack(
        'That package is not in the automation allowlist, so the tool will '
        'refuse to run against it.',
      );
    } else if (!status.installed) {
      _snack('Target application is not installed');
    }
  }

  Future<void> _openAccessibilitySettings() async {
    final opened = await widget.automation.openAccessibilitySettings();
    if (!mounted) return;
    if (!opened) {
      _snack('Could not open Accessibility settings on this device.');
      return;
    }
    if (_config.requiresRestrictedSettingsGuidance &&
        !_accessibility.enabledInSettings) {
      _snack(
        'If the toggle is greyed out, use "Allow restricted settings" from '
        'App info first.',
      );
    }
  }

  Future<void> _start() async {
    if (!(_formKey.currentState?.validate() ?? false)) return;

    final packageName = _packageController.text.trim();
    final clientId = _clientIdController.text.trim();
    final otp = _otpController.text.trim();
    final mpin = _mpinController.text;

    await _refreshStatuses();
    if (!mounted) return;

    if (!_accessibility.isReady) {
      _snack(
        'Enable the Automation Tool accessibility service before starting.',
      );
      return;
    }

    final target = await widget.automation.targetAppStatus(packageName);
    if (!mounted) return;
    setState(() => _target = target);
    if (!target.allowlisted) {
      _snack('Refusing to run: $packageName is not in the allowlist.');
      return;
    }
    if (!target.installed) {
      _snack('Target application is not installed');
      return;
    }

    await widget.storage.saveTargetPackage(packageName);
    await widget.storage.setRemember(_remember);
    if (_remember) {
      // Deliberately not the OTP — it is a one-time value.
      await widget.storage.saveCredentials(clientId: clientId, mpin: mpin);
    } else {
      await widget.storage.clearCredentials();
    }
    if (!mounted) return;

    // The running screen subscribes to the event stream first, then invokes this
    // starter, so no early step event can be missed.
    final run = await Navigator.of(context).push<TestRun>(
      MaterialPageRoute<TestRun>(
        builder: (_) => RunningTestScreen(
          automation: widget.automation,
          reports: widget.reports,
          targetPackage: packageName,
          starter: () => widget.automation.startLoginAutomation(
            targetPackage: packageName,
            clientId: clientId,
            otp: otp,
            mpin: mpin,
          ),
        ),
      ),
    );

    if (!mounted) return;
    // The OTP has been consumed by this run and can never be reused.
    _otpController.clear();
    await _refreshStatuses();
    if (!mounted) return;
    if (run != null) await _openReport(run);
  }

  Future<void> _stop() async {
    final stopped = await widget.automation.stopAutomation();
    if (!mounted) return;
    _snack(stopped ? 'Stop requested.' : 'No automation run is active.');
    await _refreshStatuses();
  }

  Future<void> _openLastReport() async {
    final history = await widget.reports.history();
    if (history.isNotEmpty) {
      if (!mounted) return;
      await _openReport(history.first);
      return;
    }
    final result = await widget.automation.lastReport();
    if (!mounted) return;
    if (result == null) {
      _snack('No report yet. Run the login automation first.');
      return;
    }
    final run = await widget.reports.save(result);
    if (!mounted) return;
    await _openReport(run);
  }

  Future<void> _openReport(TestRun run) async {
    final text = await widget.reports.readReportFile(run.textPath);
    if (!mounted) return;
    await Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => ReportScreen(
          reports: widget.reports,
          run: run,
          checklistText: text,
        ),
      ),
    );
  }

  Future<void> _clearSaved() async {
    await widget.storage.clearCredentials();
    if (!mounted) return;
    setState(() {
      _clientIdController.clear();
      _mpinController.clear();
      _remember = false;
    });
    _snack('Saved Client ID and MPIN cleared.');
  }

  void _snack(String message) {
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(SnackBar(content: Text(message)));
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final target = _target;

    return Scaffold(
      appBar: AppBar(title: const Text('Login Automation')),
      body: Form(
        key: _formKey,
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            StatusIndicator(
              title: 'Accessibility service',
              detail: _accessibility.summary,
              ok: _accessibility.isReady,
            ),
            const SizedBox(height: 8),
            StatusIndicator(
              title: 'Target app installed',
              detail: target == null
                  ? 'Not checked yet — tap "Check Target App"'
                  : target.summary,
              ok: target != null && target.installed && target.allowlisted,
              busy: _busy,
            ),
            const SizedBox(height: 20),

            Text('Credentials', style: theme.textTheme.titleMedium),
            const SizedBox(height: 4),
            Text(
              'Use dedicated test credentials on the UAT build only.',
              style: theme.textTheme.bodySmall?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
            const SizedBox(height: 14),

            CredentialField(
              controller: _packageController,
              label: 'Target application package',
              helper: 'Allowlist: ${_config.allowedPackages.join(", ")}',
              enabled: !_running,
              validator: (value) => (value == null || value.trim().isEmpty)
                  ? 'Enter the target package name'
                  : null,
            ),
            const SizedBox(height: 14),
            CredentialField(
              controller: _clientIdController,
              label: 'Client ID',
              hint: 'UAT test client code',
              enabled: !_running,
              validator: (value) => (value == null || value.trim().isEmpty)
                  ? 'Enter the Client ID'
                  : null,
            ),
            const SizedBox(height: 14),
            CredentialField(
              controller: _otpController,
              label: 'OTP',
              helper:
                  'Typed in by hand. The tool never reads SMS or notifications.',
              digitsOnly: true,
              maxLength: 6,
              enabled: !_running,
              validator: (value) => (value == null || value.trim().isEmpty)
                  ? 'Enter the OTP'
                  : null,
            ),
            const SizedBox(height: 14),
            CredentialField(
              controller: _mpinController,
              label: 'MPIN',
              obscure: true,
              digitsOnly: true,
              maxLength: 6,
              enabled: !_running,
              textInputAction: TextInputAction.done,
              validator: (value) =>
                  (value == null || value.isEmpty) ? 'Enter the MPIN' : null,
            ),
            const SizedBox(height: 6),
            SwitchListTile(
              contentPadding: EdgeInsets.zero,
              value: _remember,
              onChanged: _running
                  ? null
                  : (value) => setState(() => _remember = value),
              title: const Text('Remember Client ID and MPIN'),
              subtitle: const Text(
                'Stored in the Android Keystore. The OTP is never stored.',
              ),
            ),
            Align(
              alignment: Alignment.centerLeft,
              child: TextButton.icon(
                onPressed: _running ? null : _clearSaved,
                icon: const Icon(Icons.delete_outline, size: 18),
                label: const Text('Clear saved Client ID and MPIN'),
              ),
            ),

            const SizedBox(height: 12),
            FilledButton.icon(
              onPressed: _running ? null : _start,
              icon: const Icon(Icons.play_arrow),
              label: const Text('Start Login Automation'),
            ),
            const SizedBox(height: 8),
            OutlinedButton.icon(
              onPressed: _running ? _stop : null,
              icon: const Icon(Icons.stop),
              label: const Text('Stop Automation'),
            ),
            const SizedBox(height: 8),
            OutlinedButton.icon(
              onPressed: _busy ? null : _checkTargetApp,
              icon: const Icon(Icons.search),
              label: const Text('Check Target App'),
            ),
            const SizedBox(height: 8),
            OutlinedButton.icon(
              onPressed: _openAccessibilitySettings,
              icon: const Icon(Icons.settings_accessibility),
              label: const Text('Open Accessibility Settings'),
            ),
            const SizedBox(height: 8),
            OutlinedButton.icon(
              onPressed: _openLastReport,
              icon: const Icon(Icons.description_outlined),
              label: const Text('View Last Report'),
            ),
            if (_config.requiresRestrictedSettingsGuidance) ...[
              const SizedBox(height: 8),
              TextButton.icon(
                onPressed: widget.automation.openAppInfoSettings,
                icon: const Icon(Icons.info_outline, size: 18),
                label: const Text('Open App Info (restricted settings)'),
              ),
            ],
            const SizedBox(height: 24),
          ],
        ),
      ),
    );
  }
}

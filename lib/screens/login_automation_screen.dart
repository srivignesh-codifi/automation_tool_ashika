import 'package:flutter/material.dart';

import '../models/automation_step.dart';
import '../models/test_run.dart';
import '../services/automation_service.dart';
import '../services/report_service.dart';
import '../services/secure_storage_service.dart';
import '../widgets/credential_field.dart';
import '../widgets/module_picker.dart';
import '../widgets/status_indicator.dart';
import 'report_screen.dart';
import 'setup_screen.dart';
import 'running_test_screen.dart';

/// Target app picker, credentials form and pre-flight checks for an automation
/// run. The two apps ask for different credentials (Ashika: Client ID, OTP,
/// MPIN; Tradion: User ID, Password, OTP), so the form follows the picker.
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

  /// The MPIN (Ashika) or password (Tradion), whichever the picked app needs.
  final _secretController = TextEditingController();

  AccessibilityStatus _accessibility = const AccessibilityStatus.unknown();
  AutomationConfigInfo _config = const AutomationConfigInfo.fallback();
  late TargetAppInfo _app = _config.targets.first;
  TargetAppStatus? _target;
  bool _remember = false;

  /// Record the screen for the whole run (asks Android for permission first).
  bool _recordScreen = true;

  /// The modules this run covers. Every module starts selected.
  Set<String> _modules = <String>{};
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
    // Whatever is typed leaves memory with the screen; a remembered value is
    // read back from storage next time.
    _otpController.clear();
    _otpController.dispose();
    _secretController.dispose();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _refreshStatuses();
      // The operator may have installed the app under test while away.
      if (!_running) _checkTargetApp(announce: false);
    }
  }

  Future<void> _load() async {
    final config = await widget.automation.config();
    final savedPackage = await widget.storage.readTargetPackage();
    final app = config.targets.firstWhere(
      (t) => t.packageName == (savedPackage ?? config.defaultTargetPackage),
      orElse: () => config.targets.first,
    );

    if (!mounted) return;
    setState(() {
      _config = config;
      _app = app;
      _modules = app.modules.map((m) => m.id).toSet();
      _packageController.text = app.packageName;
    });
    await _loadSaved(app);
    if (!mounted) return;
    setState(() => _busy = false);
    await _refreshStatuses();
    // Look for the app as soon as the page opens, without a message: the
    // status tile shows the result.
    await _checkTargetApp(announce: false);
  }

  /// Fills the user id and secret last saved for [app], if the operator asked
  /// for them to be remembered; otherwise leaves the form empty.
  Future<void> _loadSaved(TargetAppInfo app) async {
    final remember = await widget.storage.readRemember(appId: app.id);
    final clientId = remember
        ? await widget.storage.readClientId(appId: app.id)
        : null;
    final secret = remember
        ? await widget.storage.readSecret(appId: app.id)
        : null;
    final otp = remember ? await widget.storage.readOtp(appId: app.id) : null;
    if (!mounted) return;
    setState(() {
      _remember = remember;
      _clientIdController.text = clientId ?? '';
      _secretController.text = secret ?? '';
      _otpController.text = otp ?? '';
    });
  }

  Future<void> _selectApp(TargetAppInfo app) async {
    if (app.id == _app.id) return;
    setState(() {
      _app = app;
      _modules = app.modules.map((m) => m.id).toSet();
      _packageController.text = app.packageName;
      _target = null;
    });
    await _loadSaved(app);
    await widget.storage.saveTargetPackage(app.packageName);
    await _checkTargetApp(announce: false);
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

  /// Looks for the picked app. [announce] adds a message when it is missing or
  /// not allowed; the automatic checks (page open, app switch, return to the
  /// tool) leave that to the status tile.
  Future<void> _checkTargetApp({bool announce = true}) async {
    final packageName = _packageController.text.trim();
    setState(() => _busy = true);
    final status = await widget.automation.targetAppStatus(packageName);
    if (!mounted) return;
    // The operator switched app while this was in flight: that answer is stale.
    if (packageName != _packageController.text.trim()) return;
    setState(() {
      _target = status;
      _busy = false;
    });
    if (!announce) return;
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

    final app = _app;
    final packageName = app.packageName;
    final clientId = _clientIdController.text.trim();
    final otp = _otpController.text.trim();
    final secret = _secretController.text;

    await _refreshStatuses();
    if (!mounted) return;

    if (!_accessibility.isReady) {
      _snack(
        'Enable the Automation Tool accessibility service before starting.',
      );
      await Navigator.of(context).push<bool>(
        MaterialPageRoute<bool>(
          builder: (_) => SetupScreen(
            automation: widget.automation,
            storage: widget.storage,
          ),
        ),
      );
      if (mounted) await _refreshStatuses();
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
    await widget.storage.setRemember(_remember, appId: app.id);
    if (_remember) {
      await widget.storage.saveCredentials(
        clientId: clientId,
        secret: secret,
        otp: otp,
        appId: app.id,
      );
    } else {
      await widget.storage.clearCredentials(appId: app.id);
    }
    if (!mounted) return;

    // Android asks for screen-recording permission every time; the answer is
    // good for this run only, so ask last, right before the run begins.
    var recordScreen = false;
    if (_recordScreen) {
      final consent = await widget.automation.requestScreenRecording();
      if (!mounted) return;
      recordScreen = consent == ScreenRecordingConsent.granted;
      if (!recordScreen && !await _confirmRunWithoutRecording(consent)) return;
    }

    // The running screen subscribes to the event stream first, then invokes this
    // starter, so no early step event can be missed.
    final run = await Navigator.of(context).push<TestRun>(
      MaterialPageRoute<TestRun>(
        builder: (_) => RunningTestScreen(
          automation: widget.automation,
          reports: widget.reports,
          targetPackage: packageName,
          testName: app.testName,
          // Only Ashika's checklist is mirrored in Dart; for the others the
          // driver announces its own the moment the run starts.
          initialSteps: app.id == 'ashika' ? null : const <AutomationStep>[],
          starter: () => widget.automation.startLoginAutomation(
            targetPackage: packageName,
            clientId: clientId,
            otp: otp,
            mpin: app.secret == TargetSecret.mpin ? secret : '',
            password: app.secret == TargetSecret.password ? secret : '',
            recordScreen: recordScreen,
            modules: _app.modules.isEmpty
                ? null
                : _app.modules
                      .map((m) => m.id)
                      .where(_modules.contains)
                      .toList(growable: false),
          ),
        ),
      ),
    );

    if (!mounted) return;
    // A one-time OTP is spent by the run; a remembered one is kept for the next.
    if (!_remember) _otpController.clear();
    await _refreshStatuses();
    if (!mounted) return;
    if (run != null) await _openReport(run);
  }

  Future<bool> _confirmRunWithoutRecording(ScreenRecordingConsent why) async {
    final unsupported = why == ScreenRecordingConsent.unsupported;
    final proceed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(
          unsupported
              ? 'Recording is not in this install'
              : 'Screen recording not allowed',
        ),
        content: Text(
          unsupported
              ? 'This installed copy of the tool was updated without its '
                    'native part (a hot reload or restart cannot add it). '
                    'Stop it and install again with a full run or build. '
                    'Run now without a video?'
              : 'Without it the report has no video of the run. Run anyway?',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(true),
            child: const Text('Run without video'),
          ),
        ],
      ),
    );
    return proceed ?? false;
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
      _snack('No report yet. Run the automation first.');
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
    await widget.storage.clearCredentials(appId: _app.id);
    if (!mounted) return;
    setState(() {
      _clientIdController.clear();
      _secretController.clear();
      _otpController.clear();
      _remember = false;
    });
    _snack(
      'Saved ${_app.userIdLabel}, ${_app.secret.label} and OTP cleared.',
    );
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
      appBar: AppBar(title: const Text('Run Automation')),
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

            Text('Target application', style: theme.textTheme.titleMedium),
            const SizedBox(height: 8),
            SegmentedButton<String>(
              segments: [
                for (final app in _config.targets)
                  ButtonSegment<String>(
                    value: app.id,
                    label: Text(app.displayName),
                  ),
              ],
              selected: <String>{_app.id},
              showSelectedIcon: false,
              onSelectionChanged: _running
                  ? null
                  : (selection) => _selectApp(
                      _config.targets.firstWhere(
                        (t) => t.id == selection.first,
                      ),
                    ),
            ),
            const SizedBox(height: 20),

            Text('Credentials', style: theme.textTheme.titleMedium),
            const SizedBox(height: 4),
            Text(
              'Use dedicated test credentials only.',
              style: theme.textTheme.bodySmall?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
            const SizedBox(height: 14),

            CredentialField(
              controller: _packageController,
              label: 'Target application package',
              helper: 'Allowlist: ${_config.allowedPackages.join(", ")}',
              enabled: false,
            ),
            if (_app.modules.isNotEmpty) ...[
              const SizedBox(height: 14),
              ModuleField(
                modules: _app.modules,
                selected: _modules,
                enabled: !_running,
                onChanged: (next) => setState(() => _modules = next),
              ),
            ],
            const SizedBox(height: 14),
            CredentialField(
              controller: _clientIdController,
              label: _app.userIdLabel,
              hint: 'Test ${_app.userIdLabel}',
              enabled: !_running,
              validator: (value) => (value == null || value.trim().isEmpty)
                  ? 'Enter the ${_app.userIdLabel}'
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
              controller: _secretController,
              label: _app.secret.label,
              obscure: true,
              // An MPIN is digits; a password is anything.
              digitsOnly: _app.secret == TargetSecret.mpin,
              maxLength: _app.secret == TargetSecret.mpin ? 6 : null,
              enabled: !_running,
              textInputAction: TextInputAction.done,
              validator: (value) => (value == null || value.isEmpty)
                  ? 'Enter the ${_app.secret.label}'
                  : null,
            ),
            const SizedBox(height: 6),
            SwitchListTile(
              contentPadding: EdgeInsets.zero,
              value: _remember,
              onChanged: _running
                  ? null
                  : (value) => setState(() => _remember = value),
              title: Text(
                'Remember ${_app.userIdLabel}, ${_app.secret.label} and OTP',
              ),
              subtitle: const Text(
                'Stored in the Android Keystore. Only useful for the OTP if '
                'your test account accepts the same one each time.',
              ),
            ),
            SwitchListTile(
              contentPadding: EdgeInsets.zero,
              value: _recordScreen,
              onChanged: _running
                  ? null
                  : (value) => setState(() => _recordScreen = value),
              title: const Text('Record the screen'),
              subtitle: const Text(
                'Saves a video of the whole run next to the report. Android '
                'asks to share the screen each time; the tool taps "Share '
                'screen" for you (tap it yourself if it stays). The video '
                'shows everything on screen, so use a test account.',
              ),
            ),
            Align(
              alignment: Alignment.centerLeft,
              child: TextButton.icon(
                onPressed: _running ? null : _clearSaved,
                icon: const Icon(Icons.delete_outline, size: 18),
                label: Text(
                  'Clear saved ${_app.userIdLabel}, ${_app.secret.label} and OTP',
                ),
              ),
            ),

            const SizedBox(height: 12),
            FilledButton.icon(
              onPressed: _running ? null : _start,
              icon: const Icon(Icons.play_arrow),
              label: Text('Start ${_app.displayName} Automation'),
            ),
            const SizedBox(height: 6),
            Text(
              _app.runSummary,
              style: theme.textTheme.bodySmall?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
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

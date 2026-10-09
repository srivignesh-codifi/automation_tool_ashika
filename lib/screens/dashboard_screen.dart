import 'dart:async';

import 'package:flutter/material.dart';

import '../models/test_run.dart';
import '../services/automation_service.dart';
import '../services/report_service.dart';
import '../services/secure_storage_service.dart';
import '../widgets/status_indicator.dart';
import 'login_automation_screen.dart';
import 'report_screen.dart';

/// Landing screen: readiness of the two prerequisites, the entry point into the
/// login automation, and the history of previous runs.
class DashboardScreen extends StatefulWidget {
  const DashboardScreen({
    required this.automation,
    required this.reports,
    required this.storage,
    super.key,
  });

  final AutomationService automation;
  final ReportService reports;
  final SecureStorageService storage;

  @override
  State<DashboardScreen> createState() => _DashboardScreenState();
}

class _DashboardScreenState extends State<DashboardScreen>
    with WidgetsBindingObserver {
  AccessibilityStatus _accessibility = const AccessibilityStatus.unknown();
  AutomationConfigInfo _config = const AutomationConfigInfo.fallback();
  TargetAppStatus _target = const TargetAppStatus.unknown('com.codifi.dhanush');
  List<TestRun> _history = const <TestRun>[];
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _refresh();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    // The operator leaves the app to toggle the accessibility service, so
    // re-check the moment they come back.
    if (state == AppLifecycleState.resumed) _refresh();
  }

  Future<void> _refresh() async {
    final config = await widget.automation.config();
    final savedPackage = await widget.storage.readTargetPackage();
    final packageName = savedPackage ?? config.defaultTargetPackage;
    final results = await (
      widget.automation.accessibilityStatus(),
      widget.automation.targetAppStatus(packageName),
      widget.reports.history(),
    ).wait;

    if (!mounted) return;
    setState(() {
      _config = config;
      _accessibility = results.$1;
      _target = results.$2;
      _history = results.$3;
      _loading = false;
    });
  }

  Future<void> _openLoginAutomation() async {
    await Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => LoginAutomationScreen(
          automation: widget.automation,
          reports: widget.reports,
          storage: widget.storage,
        ),
      ),
    );
    await _refresh();
  }

  Future<void> _openRun(TestRun run) async {
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

  Future<void> _openLastReport() async {
    if (_history.isNotEmpty) {
      await _openRun(_history.first);
      return;
    }
    // Nothing saved yet — the driver may still be holding an unsaved run.
    final result = await widget.automation.lastReport();
    if (!mounted) return;
    if (result == null) {
      _snack('No report yet. Run the login automation first.');
      return;
    }
    final run = await widget.reports.save(result);
    if (!mounted) return;
    await _openRun(run);
    await _refresh();
  }

  void _snack(String message) {
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(SnackBar(content: Text(message)));
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final ready = _accessibility.isReady && _target.installed;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Mobile Automation Tool'),
        actions: [
          IconButton(
            onPressed: _loading ? null : _refresh,
            icon: const Icon(Icons.refresh),
            tooltip: 'Re-check',
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: _refresh,
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            Text('Device readiness', style: theme.textTheme.titleMedium),
            const SizedBox(height: 10),
            StatusIndicator(
              title: 'Accessibility service',
              detail: _accessibility.summary,
              ok: _accessibility.isReady,
              busy: _loading,
            ),
            const SizedBox(height: 8),
            StatusIndicator(
              title: 'Target application',
              detail: '${_target.packageName} — ${_target.summary}',
              ok: _target.installed && _target.allowlisted,
              busy: _loading,
            ),
            const SizedBox(height: 20),

            FilledButton.icon(
              onPressed: _openLoginAutomation,
              icon: const Icon(Icons.play_arrow),
              label: const Text('Login Automation'),
            ),
            const SizedBox(height: 8),
            OutlinedButton.icon(
              onPressed: _openLastReport,
              icon: const Icon(Icons.description_outlined),
              label: const Text('View Last Report'),
            ),

            if (!ready) ...[
              const SizedBox(height: 16),
              _SetupCard(
                accessibilityReady: _accessibility.isReady,
                targetInstalled: _target.installed,
                targetPackage: _target.packageName,
                showRestrictedSettingsHint:
                    _config.requiresRestrictedSettingsGuidance,
              ),
            ],

            const SizedBox(height: 24),
            Row(
              children: [
                Expanded(
                  child: Text(
                    'Test history',
                    style: theme.textTheme.titleMedium,
                  ),
                ),
                if (_history.isNotEmpty)
                  TextButton(
                    onPressed: () async {
                      await widget.reports.clearHistory();
                      await _refresh();
                    },
                    child: const Text('Clear'),
                  ),
              ],
            ),
            const SizedBox(height: 4),
            if (_history.isEmpty)
              Text(
                'No runs yet.',
                style: theme.textTheme.bodySmall?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              )
            else
              ..._history.map(
                (run) => Card(
                  margin: const EdgeInsets.only(bottom: 8),
                  child: ListTile(
                    onTap: () => _openRun(run),
                    title: Text(run.testName),
                    subtitle: Text(
                      '${_stamp(run.startedAt)} · '
                      '${run.completedSteps}/${run.totalSteps} steps',
                    ),
                    trailing: VerdictBadge(verdict: run.verdict),
                  ),
                ),
              ),

            const SizedBox(height: 24),
            _SafetyCard(allowedPackages: _config.allowedPackages),
            const SizedBox(height: 24),
          ],
        ),
      ),
    );
  }

  static String _two(int v) => v.toString().padLeft(2, '0');

  static String _stamp(DateTime t) =>
      '${t.year}-${_two(t.month)}-${_two(t.day)} '
      '${_two(t.hour)}:${_two(t.minute)}';
}

class _SetupCard extends StatelessWidget {
  const _SetupCard({
    required this.accessibilityReady,
    required this.targetInstalled,
    required this.targetPackage,
    required this.showRestrictedSettingsHint,
  });

  final bool accessibilityReady;
  final bool targetInstalled;
  final String targetPackage;
  final bool showRestrictedSettingsHint;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final steps = <String>[
      if (!targetInstalled)
        'Install the UAT build of $targetPackage on this device.',
      if (!accessibilityReady)
        'Enable Automation Tool under Settings > Accessibility > '
            'Installed apps.',
      if (!accessibilityReady && showRestrictedSettingsHint)
        'Android 13+: if that toggle is greyed out, open App info for '
            'Automation Tool, tap the top-right menu, choose "Allow restricted '
            'settings", then go back to Accessibility.',
    ];

    return Card(
      color: theme.colorScheme.secondaryContainer.withValues(alpha: 0.5),
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(Icons.build_outlined, size: 18, color: theme.colorScheme.onSurfaceVariant),
                const SizedBox(width: 8),
                Text('Setup required', style: theme.textTheme.titleSmall),
              ],
            ),
            const SizedBox(height: 8),
            for (final step in steps)
              Padding(
                padding: const EdgeInsets.only(bottom: 6),
                child: Text('• $step', style: theme.textTheme.bodySmall),
              ),
          ],
        ),
      ),
    );
  }
}

class _SafetyCard extends StatelessWidget {
  const _SafetyCard({required this.allowedPackages});

  final List<String> allowedPackages;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(
                  Icons.shield_outlined,
                  size: 18,
                  color: theme.colorScheme.onSurfaceVariant,
                ),
                const SizedBox(width: 8),
                Text('Scope and safety', style: theme.textTheme.titleSmall),
              ],
            ),
            const SizedBox(height: 8),
            Text(
              'Internal UAT tool. It automates one thing: a successful login on '
              'the allowlisted test build, using dedicated test credentials.\n\n'
              'Allowlist: ${allowedPackages.join(", ")}\n\n'
              'It cannot place orders, buy, sell or move funds. It does not read '
              'SMS or notifications, so the OTP must be typed in by hand. It does '
              'not automate fingerprint or face authentication — it only taps '
              'Skip. Credential values are never logged or written to a report.',
              style: theme.textTheme.bodySmall,
            ),
          ],
        ),
      ),
    );
  }
}

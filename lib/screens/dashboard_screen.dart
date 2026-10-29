import 'dart:async';

import 'package:flutter/material.dart';

import '../branding/app_brand.dart';
import '../build_info.dart';
import '../models/test_run.dart';
import '../services/auth_service.dart';
import '../services/automation_service.dart';
import '../services/report_service.dart';
import '../services/secure_storage_service.dart';
import '../theme/codifi_theme.dart';
import '../widgets/codifi_widgets.dart';
import '../widgets/send_to_admin_button.dart';
import '../widgets/status_indicator.dart';
import 'login_automation_screen.dart';
import 'report_screen.dart';
import 'setup_screen.dart';

/// Home: one logo tile per app to test (tapping it opens that app's run), the
/// device readiness, and the history of previous runs. Apps are shown by logo
/// and name, never by package.
class DashboardScreen extends StatefulWidget {
  const DashboardScreen({
    required this.automation,
    required this.reports,
    required this.storage,
    this.user,
    this.onSignOut,
    super.key,
  });

  final AutomationService automation;
  final ReportService reports;
  final SecureStorageService storage;

  /// Who signed in to the tool; null when the screen is shown without sign-in.
  final AuthUser? user;
  final VoidCallback? onSignOut;

  @override
  State<DashboardScreen> createState() => _DashboardScreenState();
}

class _DashboardScreenState extends State<DashboardScreen>
    with WidgetsBindingObserver {
  AccessibilityStatus _accessibility = const AccessibilityStatus.unknown();
  AutomationConfigInfo _config = const AutomationConfigInfo.fallback();

  /// Whether each app is on this phone, by app id.
  Map<String, TargetAppStatus> _targets = const <String, TargetAppStatus>{};
  List<TestRun> _history = const <TestRun>[];
  final Set<String> _downloadingRunIds = <String>{};
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
    final results = await (
      widget.automation.accessibilityStatus(),
      Future.wait(
        config.targets.map(
          (t) => widget.automation.targetAppStatus(t.packageName),
        ),
      ),
      widget.reports.history(),
    ).wait;

    if (!mounted) return;
    setState(() {
      _config = config;
      _accessibility = results.$1;
      _targets = {
        for (var i = 0; i < config.targets.length; i++)
          config.targets[i].id: results.$2[i],
      };
      _history = results.$3;
      _loading = false;
    });
  }

  /// Opens the run of [app], with its credentials form and module choices.
  Future<void> _openApp(TargetAppInfo app) async {
    await Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => LoginAutomationScreen(
          automation: widget.automation,
          reports: widget.reports,
          storage: widget.storage,
          initialAppId: app.id,
          user: widget.user,
        ),
      ),
    );
    await _refresh();
  }

  Future<void> _openSetup() async {
    await Navigator.of(context).push<bool>(
      MaterialPageRoute<bool>(
        builder: (_) =>
          SetupScreen(
            automation: widget.automation,
            storage: widget.storage,
            onLogout: widget.onSignOut == null
                ? null
                : () {
                    Navigator.of(context).pop();
                    widget.onSignOut!();
                  },
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

  Future<void> _downloadRun(TestRun run) async {
    if (_downloadingRunIds.contains(run.runId)) return;
    setState(() => _downloadingRunIds.add(run.runId));
    try {
      final downloaded = await widget.reports.download(run);
      if (mounted) _snack(downloaded.message);
    } catch (_) {
      if (mounted) _snack('The report files could not be downloaded.');
    } finally {
      if (mounted) {
        setState(() => _downloadingRunIds.remove(run.runId));
      }
    }
  }

  void _snack(String message) {
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(SnackBar(content: Text(message)));
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final ready = _accessibility.isReady;
    final name = widget.user?.userId;

    return Scaffold(
      appBar: AppBar(
        title: const CodifiLogo(height: 30),
        actions: [
          IconButton(
            onPressed: _loading ? null : _refresh,
            icon: const Icon(Icons.refresh),
            tooltip: 'Re-check',
          ),
          if (widget.onSignOut != null)
            PopupMenuButton<String>(
              tooltip: 'Account',
              icon: const Icon(Icons.account_circle_outlined),
              onSelected: (_) => widget.onSignOut!(),
              itemBuilder: (_) => [
                if (name != null)
                  PopupMenuItem<String>(
                    enabled: false,
                    child: Text('Signed in as $name'),
                  ),
                const PopupMenuItem<String>(
                  value: 'sign_out',
                  child: ListTile(
                    contentPadding: EdgeInsets.zero,
                    leading: Icon(Icons.logout),
                    title: Text('Sign out'),
                  ),
                ),
              ],
            ),
        ],
      ),
      body: CodifiBackdrop(
        child: RefreshIndicator(
          onRefresh: _refresh,
          child: ListView(
            // Edge-to-edge (Android 15+): the list ends above the navigation bar while
            // the background still runs under it.
            padding: EdgeInsets.fromLTRB(
              16,
              16,
              16,
              16 + MediaQuery.paddingOf(context).bottom,
            ),
            children: [
              if (name != null)
                Text(
                  'Hello, $name',
                  style: theme.textTheme.bodyMedium?.copyWith(
                    color: CodifiColors.textMuted,
                  ),
                ),
              const SizedBox(height: 4),
              Text.rich(
                TextSpan(
                  children: [
                    const TextSpan(text: 'Choose an app to '),
                    TextSpan(
                      text: 'test',
                      style: TextStyle(color: CodifiColors.orange),
                    ),
                  ],
                ),
                style: theme.textTheme.headlineSmall?.copyWith(
                  fontWeight: FontWeight.w600,
                ),
              ),
              const SizedBox(height: 16),
              LayoutBuilder(
                builder: (context, box) {
                  final columns = box.maxWidth >= 560 ? 3 : 2;
                  return GridView.count(
                    crossAxisCount: columns,
                    shrinkWrap: true,
                    physics: const NeverScrollableScrollPhysics(),
                    mainAxisSpacing: 12,
                    crossAxisSpacing: 12,
                    childAspectRatio: 0.92,
                    children: [
                      for (final app in _config.targets)
                        _AppTile(
                          app: app,
                          status: _targets[app.id],
                          busy: _loading,
                          onTap: () => _openApp(app),
                        ),
                    ],
                  );
                },
              ),
              const SizedBox(height: 24),
              Text('Device readiness', style: theme.textTheme.titleMedium),
              const SizedBox(height: 10),
              StatusIndicator(
                title: 'Accessibility service',
                detail: _accessibility.summary,
                ok: _accessibility.isReady,
                busy: _loading,
              ),
              Align(
                alignment: Alignment.centerLeft,
                child: TextButton.icon(
                  onPressed: _openSetup,
                  icon: const Icon(Icons.verified_user_outlined, size: 18),
                  label: const Text('Permissions setup'),
                ),
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
                ..._history.map((run) {
                  final downloading = _downloadingRunIds.contains(run.runId);
                  return Card(
                    margin: const EdgeInsets.only(bottom: 8),
                    child: ListTile(
                      onTap: () => _openRun(run),
                      title: Text(run.testName),
                      // The verdict sits with the date: the right side keeps
                      // room for Download and Send to admin on a phone.
                      subtitle: Padding(
                        padding: const EdgeInsets.only(top: 4),
                        child: Wrap(
                          spacing: 8,
                          runSpacing: 4,
                          crossAxisAlignment: WrapCrossAlignment.center,
                          children: [
                            VerdictBadge(verdict: run.verdict),
                            Text(
                              '${_stamp(run.startedAt)} · '
                              '${run.completedSteps}/${run.totalSteps} steps',
                            ),
                          ],
                        ),
                      ),
                      trailing: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          IconButton(
                            onPressed: downloading
                                ? null
                                : () => _downloadRun(run),
                            icon: downloading
                                ? const SizedBox.square(
                                    dimension: 18,
                                    child: CircularProgressIndicator(
                                      strokeWidth: 2,
                                    ),
                                  )
                                : const Icon(Icons.download_outlined),
                            tooltip: 'Download Reports',
                          ),
                          SendToAdminButton(
                            key: ValueKey<String>('send_${run.runId}'),
                            run: run,
                            compact: true,
                          ),
                        ],
                      ),
                    ),
                  );
                }),

              const SizedBox(height: 24),
              _SafetyCard(
                appNames: _config.targets.map((t) => t.displayName).toList(),
              ),
              const SizedBox(height: 12),
              Center(
                child: Text(
                  'Version ${BuildInfo.fullVersion} · built ${BuildInfo.buildDateTime}',
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
              ),
              const SizedBox(height: 24),
            ],
          ),
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
    required this.showRestrictedSettingsHint,
  });

  final bool accessibilityReady;
  final bool showRestrictedSettingsHint;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final steps = <String>[
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
                Icon(
                  Icons.build_outlined,
                  size: 18,
                  color: theme.colorScheme.onSurfaceVariant,
                ),
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
  const _SafetyCard({required this.appNames});

  final List<String> appNames;

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
              'Internal test tool. It logs in to the allowlisted apps and checks '
              'their screens, using dedicated test credentials.\n\n'
              'Apps it may test: ${appNames.join(", ")}\n\n'
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

/// One app to test on the home screen: its logo, name and whether it is on
/// this phone. Tapping it opens the app's run.
class _AppTile extends StatelessWidget {
  const _AppTile({
    required this.app,
    required this.status,
    required this.busy,
    required this.onTap,
  });

  final TargetAppInfo app;
  final TargetAppStatus? status;
  final bool busy;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final installed = status?.installed == true && status?.allowlisted == true;
    final modules = app.modules.length;
    final tagline = modules == 0
        ? null
        : '$modules module${modules == 1 ? '' : 's'}';
    return DecoratedBox(
      decoration: BoxDecoration(
        gradient: CodifiColors.brand,
        borderRadius: BorderRadius.circular(20),
      ),
      child: Padding(
        padding: const EdgeInsets.all(1.2),
        child: Material(
          color: CodifiColors.surface,
          borderRadius: BorderRadius.circular(19),
          child: InkWell(
            key: ValueKey<String>('app_tile_${app.id}'),
            borderRadius: BorderRadius.circular(19),
            onTap: onTap,
            child: Padding(
              padding: const EdgeInsets.fromLTRB(14, 16, 14, 12),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  AppLogo(appId: app.id, name: app.displayName, size: 56),
                  const Spacer(),
                  Text(
                    app.displayName,
                    style: theme.textTheme.titleMedium?.copyWith(
                      fontWeight: FontWeight.w600,
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  if (tagline != null)
                    Text(
                      tagline,
                      style: theme.textTheme.bodySmall?.copyWith(
                        color: CodifiColors.textMuted,
                      ),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  const SizedBox(height: 10),
                  Row(
                    children: [
                      // Shrinks (with an ellipsis) on a narrow tile or with large text.
                      Flexible(
                        child: _StatusChip(
                          label: busy && status == null
                              ? 'Checking'
                              : installed
                              ? 'Installed'
                              : 'Not installed',
                          color: installed
                              ? CodifiColors.success
                              : CodifiColors.textMuted,
                        ),
                      ),
                      const SizedBox(width: 6),
                      const Spacer(),
                      const Icon(
                        Icons.arrow_forward,
                        size: 18,
                        color: CodifiColors.orangeLight,
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _StatusChip extends StatelessWidget {
  const _StatusChip({required this.label, required this.color});

  final String label;
  final Color color;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
    decoration: BoxDecoration(
      color: color.withValues(alpha: 0.14),
      borderRadius: BorderRadius.circular(20),
    ),
    child: Text(
      label,
      maxLines: 1,
      overflow: TextOverflow.ellipsis,
      style: TextStyle(color: color, fontSize: 11, fontWeight: FontWeight.w600),
    ),
  );
}

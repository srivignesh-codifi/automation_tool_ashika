import 'package:flutter/material.dart';

import '../services/automation_service.dart';
import '../services/secure_storage_service.dart';

/// First-launch setup: everything the tool needs from the operator, in one
/// place, so that nothing interrupts a test run later.
///
/// What Android allows decides the shape. The accessibility service can only be
/// switched on by the operator in Settings, so this screen walks them there and
/// notices when they return. The tool's notification permission is an ordinary
/// dialog. Screen recording cannot be granted once for good on Android 14+, so
/// it is tested here (proving the device can record) and asked again, in one
/// tap the tool makes itself, at the start of each run.
class SetupScreen extends StatefulWidget {
  const SetupScreen({
    required this.automation,
    required this.storage,
    this.firstRun = false,
    super.key,
  });

  final AutomationService automation;
  final SecureStorageService storage;

  /// Shown automatically at first launch: no back arrow, and a way to skip.
  final bool firstRun;

  @override
  State<SetupScreen> createState() => _SetupScreenState();
}

class _SetupScreenState extends State<SetupScreen> with WidgetsBindingObserver {
  AccessibilityStatus _accessibility = const AccessibilityStatus.unknown();
  AutomationConfigInfo _config = const AutomationConfigInfo.fallback();
  final Map<String, TargetAppStatus> _targets = <String, TargetAppStatus>{};
  bool _notifications = true;
  bool _notificationsAsked = false;
  bool _testing = false;
  bool _loading = true;
  ({bool ok, String detail})? _recording;

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
    // The operator leaves to flip a Settings switch; notice when they return.
    if (state == AppLifecycleState.resumed) _refresh();
  }

  Future<void> _refresh() async {
    final config = await widget.automation.config();
    final accessibility = await widget.automation.accessibilityStatus();
    final notifications = await widget.automation.notificationsGranted();
    final targets = <String, TargetAppStatus>{};
    for (final app in config.targets) {
      targets[app.id] = await widget.automation.targetAppStatus(
        app.packageName,
      );
    }
    if (!mounted) return;
    setState(() {
      _config = config;
      _accessibility = accessibility;
      _notifications = notifications;
      _targets
        ..clear()
        ..addAll(targets);
      _loading = false;
    });
  }

  Future<void> _requestNotifications() async {
    final granted = await widget.automation.requestNotifications();
    if (!mounted) return;
    setState(() {
      _notifications = granted;
      _notificationsAsked = true;
    });
  }

  Future<void> _testRecording() async {
    setState(() {
      _testing = true;
      _recording = null;
    });
    final consent = await widget.automation.requestScreenRecording();
    if (!mounted) return;
    ({bool ok, String detail}) outcome;
    switch (consent) {
      case ScreenRecordingConsent.granted:
        outcome = await widget.automation.testScreenRecording();
      case ScreenRecordingConsent.declined:
        outcome = (
          ok: false,
          detail: 'Screen sharing was not allowed. Try again and choose '
              '"Share screen".',
        );
      case ScreenRecordingConsent.unsupported:
        outcome = (
          ok: false,
          detail: 'This install has no screen-recording support. Install the '
              'tool again with a full build (a hot reload cannot add it).',
        );
    }
    if (!mounted) return;
    setState(() {
      _testing = false;
      _recording = outcome;
    });
  }

  Future<void> _finish() async {
    await widget.storage.setSetupDone();
    if (mounted) Navigator.of(context).pop(true);
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final ready = _accessibility.isReady;

    return PopScope(
      canPop: !widget.firstRun,
      child: Scaffold(
        appBar: AppBar(
          title: const Text('Set up the tool'),
          automaticallyImplyLeading: !widget.firstRun,
          actions: [
            IconButton(
              onPressed: _loading ? null : _refresh,
              icon: const Icon(Icons.refresh),
              tooltip: 'Re-check',
            ),
          ],
        ),
        body: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            Text(
              'Allow these once, now, so nothing asks for permission in the '
              'middle of a test run.',
              style: theme.textTheme.bodyMedium,
            ),
            const SizedBox(height: 16),

            _StepCard(
              title: 'Accessibility service',
              badge: 'Required',
              done: ready,
              detail: ready
                  ? 'Enabled and running.'
                  : 'The tool drives the app under test through this. Android '
                        'only lets you switch it on yourself: ${_accessibility.summary}.',
              actions: ready
                  ? const <Widget>[]
                  : [
                      if (_config.requiresRestrictedSettingsGuidance &&
                          !_accessibility.enabledInSettings)
                        OutlinedButton.icon(
                          onPressed: widget.automation.openAppInfoSettings,
                          icon: const Icon(Icons.info_outline),
                          label: const Text('1. Allow restricted settings'),
                        ),
                      FilledButton.icon(
                        onPressed: widget.automation.openAccessibilitySettings,
                        icon: const Icon(Icons.settings_accessibility),
                        label: Text(
                          _config.requiresRestrictedSettingsGuidance &&
                                  !_accessibility.enabledInSettings
                              ? '2. Open Accessibility settings'
                              : 'Open Accessibility settings',
                        ),
                      ),
                    ],
              footnote: ready
                  ? null
                  : 'Installed apps > Automation Tool > turn on. On Android 13+, '
                        'if the switch is greyed out, open App info first, tap the '
                        'menu (top right) and choose "Allow restricted settings". '
                        'Come back here afterwards.',
            ),

            _StepCard(
              title: 'Notifications',
              badge: 'Recommended',
              done: _notifications,
              detail: _notifications
                  ? 'Allowed.'
                  : 'Lets Android show the "recording" notice while a run is '
                        'captured. Runs work without it.',
              actions: _notifications
                  ? const <Widget>[]
                  : [
                      FilledButton.icon(
                        onPressed: _requestNotifications,
                        icon: const Icon(Icons.notifications_outlined),
                        label: const Text('Allow notifications'),
                      ),
                      if (_notificationsAsked)
                        OutlinedButton.icon(
                          onPressed: widget.automation.openAppInfoSettings,
                          icon: const Icon(Icons.info_outline),
                          label: const Text('Open App info'),
                        ),
                    ],
              footnote: _notificationsAsked && !_notifications
                  ? 'Android may not ask again after a refusal; allow it from '
                        'App info > Notifications.'
                  : null,
            ),

            _StepCard(
              title: 'Screen recording',
              badge: 'Recommended',
              done: _recording?.ok == true,
              detail: _recording == null
                  ? 'Try a 3-second recording now to be sure this device can '
                        'record. The report then carries a video of each run.'
                  : _recording!.ok
                  ? 'Works: ${_recording!.detail}.'
                  : 'Did not work: ${_recording!.detail}',
              actions: [
                FilledButton.icon(
                  onPressed: _testing ? null : _testRecording,
                  icon: _testing
                      ? const SizedBox.square(
                          dimension: 18,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Icon(Icons.videocam_outlined),
                  label: Text(_testing ? 'Testing…' : 'Test screen recording'),
                ),
              ],
              footnote: 'Android 14 and newer ask for screen sharing again at '
                  'the start of each run and cannot remember the answer. Once '
                  'the accessibility service is on, the tool taps "Share '
                  'screen" for you, before the test begins and never during it.',
            ),

            _StepCard(
              title: 'Apps to test',
              badge: 'Info',
              done: _targets.isNotEmpty &&
                  _targets.values.every((t) => t.installed),
              detail: _targets.isEmpty
                  ? 'Checking…'
                  : [
                      for (final app in _config.targets)
                        '${app.displayName}: '
                            '${_targets[app.id]?.summary ?? 'unknown'}',
                    ].join('\n'),
              actions: const <Widget>[],
              footnote: 'Install the current builds of the apps: they skip their '
                  'notification prompt when this tool launches them, so no build '
                  'flag is needed.',
            ),

            const SizedBox(height: 8),
            FilledButton(
              onPressed: ready ? _finish : null,
              child: const Text('Finish setup'),
            ),
            if (!ready)
              Padding(
                padding: const EdgeInsets.only(top: 6),
                child: Text(
                  'Finish setup unlocks once the accessibility service is on.',
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
              ),
            if (widget.firstRun)
              TextButton(
                onPressed: () => Navigator.of(context).pop(false),
                child: const Text('Skip for now'),
              ),
            const SizedBox(height: 16),
          ],
        ),
      ),
    );
  }
}

class _StepCard extends StatelessWidget {
  const _StepCard({
    required this.title,
    required this.badge,
    required this.done,
    required this.detail,
    required this.actions,
    this.footnote,
  });

  final String title;
  final String badge;
  final bool done;
  final String detail;
  final List<Widget> actions;
  final String? footnote;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final muted = theme.colorScheme.onSurfaceVariant;
    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(
                  done ? Icons.check_circle : Icons.radio_button_unchecked,
                  color: done ? Colors.green : muted,
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(title, style: theme.textTheme.titleSmall),
                ),
                Text(badge, style: theme.textTheme.labelSmall),
              ],
            ),
            const SizedBox(height: 8),
            Text(detail, style: theme.textTheme.bodyMedium),
            if (actions.isNotEmpty) ...[
              const SizedBox(height: 10),
              for (final action in actions) ...[
                action,
                const SizedBox(height: 8),
              ],
            ],
            if (footnote != null)
              Text(
                footnote!,
                style: theme.textTheme.bodySmall?.copyWith(color: muted),
              ),
          ],
        ),
      ),
    );
  }
}

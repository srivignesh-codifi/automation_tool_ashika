import 'dart:async';

import 'package:flutter/material.dart';

import '../constants/automation_ids.dart';
import '../models/automation_result.dart';
import '../models/automation_step.dart';
import '../models/test_run.dart';
import '../services/automation_service.dart';
import '../services/report_service.dart';
import '../widgets/status_indicator.dart';
import '../widgets/test_step_tile.dart';

/// Live view of a run.
///
/// Subscribes to the event stream *before* invoking [starter], so the first
/// `automation_started` / `step_started` events cannot be missed.
class RunningTestScreen extends StatefulWidget {
  const RunningTestScreen({
    required this.automation,
    required this.reports,
    required this.targetPackage,
    required this.starter,
    super.key,
  });

  final AutomationService automation;
  final ReportService reports;
  final String targetPackage;

  /// Kicks off the run. Resolves to null on success, or a reason on refusal.
  final Future<String?> Function() starter;

  @override
  State<RunningTestScreen> createState() => _RunningTestScreenState();
}

class _RunningTestScreenState extends State<RunningTestScreen> {
  StreamSubscription<AutomationEvent>? _subscription;

  late List<AutomationStep> _steps = AutomationChecklist.steps
      .map((s) => AutomationStep(id: s.id, name: s.name))
      .toList();

  String? _startError;
  bool _finished = false;
  AutomationResult? _result;
  TestRun? _savedRun;
  bool _saving = false;

  @override
  void initState() {
    super.initState();
    _subscription = widget.automation.events.listen(
      _onEvent,
      onError: (Object error) {
        if (mounted) {
          setState(() => _startError = 'Event stream error: $error');
        }
      },
    );
    _begin();
  }

  @override
  void dispose() {
    _subscription?.cancel();
    super.dispose();
  }

  Future<void> _begin() async {
    final error = await widget.starter();
    if (!mounted || error == null) return;
    setState(() {
      _startError = error;
      _finished = true;
    });
  }

  void _onEvent(AutomationEvent event) {
    if (!mounted) return;
    switch (event.name) {
      case 'automation_started':
        final declared = event.declaredSteps;
        if (declared != null && declared.isNotEmpty) {
          setState(() => _steps = declared);
        }

      case 'step_started':
        _updateStep(
          event.stepId,
          (step) => step.copyWith(
            status: StepStatus.running,
            startedAtMs: event.stepId == null
                ? null
                : DateTime.now().millisecondsSinceEpoch,
          ),
        );

      case 'step_passed':
      case 'step_failed':
        _updateStep(
          event.stepId,
          (step) => step.copyWith(
            status:
                event.status ??
                (event.name == 'step_failed'
                    ? StepStatus.failed
                    : StepStatus.passed),
            finishedAtMs: DateTime.now().millisecondsSinceEpoch,
            durationMs: event.durationMs,
            note: event.note,
            failureReason: event.reason,
          ),
        );

      case 'automation_completed':
      case 'automation_stopped':
        final result = event.result;
        setState(() {
          _finished = true;
          if (result != null) {
            _result = result;
            if (result.steps.isNotEmpty) _steps = result.steps;
          }
        });
        if (result != null) _persist(result);
    }
  }

  void _updateStep(
    String? stepId,
    AutomationStep Function(AutomationStep) transform,
  ) {
    if (stepId == null) return;
    final index = _steps.indexWhere((s) => s.id == stepId);
    if (index < 0) return;
    setState(() {
      final updated = List<AutomationStep>.of(_steps);
      updated[index] = transform(updated[index]);
      _steps = updated;
    });
  }

  Future<void> _persist(AutomationResult result) async {
    if (_saving) return;
    _saving = true;
    try {
      final run = await widget.reports.save(result);
      if (mounted) setState(() => _savedRun = run);
    } finally {
      _saving = false;
    }
  }

  Future<void> _stop() async {
    await widget.automation.stopAutomation();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final done = _steps
        .where(
          (s) =>
              s.status == StepStatus.passed || s.status == StepStatus.skipped,
        )
        .length;
    final failed = _steps.any((s) => s.status == StepStatus.failed);
    final result = _result;

    return PopScope(
      // Leaving mid-run would orphan the live view; stop the run first.
      canPop: _finished,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop && !_finished) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Stop the automation before going back.'),
            ),
          );
        }
      },
      child: Scaffold(
        appBar: AppBar(
          title: Text(_finished ? 'Run finished' : 'Running…'),
          automaticallyImplyLeading: _finished,
        ),
        body: Column(
          children: [
            LinearProgressIndicator(
              value: _finished ? 1 : done / _steps.length,
              minHeight: 4,
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 14, 16, 4),
              child: Row(
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          AutomationChecklist.testName,
                          style: theme.textTheme.titleMedium,
                        ),
                        const SizedBox(height: 2),
                        Text(
                          '${widget.targetPackage} · $done/${_steps.length} steps',
                          style: theme.textTheme.bodySmall?.copyWith(
                            color: theme.colorScheme.onSurfaceVariant,
                          ),
                        ),
                      ],
                    ),
                  ),
                  if (result != null) VerdictBadge(verdict: result.verdict),
                ],
              ),
            ),
            if (!_finished)
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
                child: Text(
                  'Keep the target application in the foreground. Do not touch '
                  'the screen while the run is in progress.',
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
              ),
            if (_startError != null)
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 12, 16, 0),
                child: Card(
                  color: theme.colorScheme.errorContainer,
                  child: Padding(
                    padding: const EdgeInsets.all(12),
                    child: Text(
                      _startError!,
                      style: TextStyle(
                        color: theme.colorScheme.onErrorContainer,
                      ),
                    ),
                  ),
                ),
              ),
            if (result?.failureMessage != null && !failed)
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 12, 16, 0),
                child: Card(
                  child: Padding(
                    padding: const EdgeInsets.all(12),
                    child: Text(result!.failureMessage!),
                  ),
                ),
              ),
            Expanded(
              child: ListView.builder(
                padding: const EdgeInsets.fromLTRB(16, 14, 16, 20),
                itemCount: _steps.length,
                itemBuilder: (_, index) =>
                    TestStepTile(step: _steps[index], index: index),
              ),
            ),
            SafeArea(
              top: false,
              child: Padding(
                padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
                child: _finished
                    ? Row(
                        children: [
                          Expanded(
                            child: OutlinedButton(
                              onPressed: () =>
                                  Navigator.of(context).pop<TestRun>(),
                              child: const Text('Close'),
                            ),
                          ),
                          const SizedBox(width: 10),
                          Expanded(
                            child: FilledButton(
                              onPressed: _savedRun == null
                                  ? null
                                  : () => Navigator.of(
                                      context,
                                    ).pop<TestRun>(_savedRun),
                              child: const Text('View report'),
                            ),
                          ),
                        ],
                      )
                    : OutlinedButton.icon(
                        onPressed: _stop,
                        icon: const Icon(Icons.stop),
                        label: const Text('Stop Automation'),
                      ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

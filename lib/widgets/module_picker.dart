import 'package:flutter/material.dart';

import '../services/automation_service.dart';

/// The checklist of modules a run covers. All of them start selected; the
/// operator unticks what a run should leave out, and those steps are skipped.
class ModulePicker extends StatelessWidget {
  const ModulePicker({
    required this.modules,
    required this.selected,
    required this.onChanged,
    this.enabled = true,
    super.key,
  });

  final List<AutomationModule> modules;
  final Set<String> selected;
  final ValueChanged<Set<String>> onChanged;
  final bool enabled;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final muted = theme.colorScheme.onSurfaceVariant;
    final all = modules.length;
    final count = modules.where((m) => selected.contains(m.id)).length;

    void toggle(AutomationModule module, bool on) {
      final next = Set<String>.of(selected);
      on ? next.add(module.id) : next.remove(module.id);
      onChanged(next);
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Expanded(
              child: Text(
                'Modules to test ($count of $all)',
                style: theme.textTheme.titleMedium,
              ),
            ),
            TextButton(
              onPressed: !enabled || count == all
                  ? null
                  : () => onChanged(modules.map((m) => m.id).toSet()),
              child: const Text('All'),
            ),
            TextButton(
              onPressed: !enabled || count == 0
                  ? null
                  : () => onChanged(<String>{}),
              child: const Text('None'),
            ),
          ],
        ),
        for (final module in modules)
          CheckboxListTile(
            key: ValueKey<String>('module_${module.id}'),
            contentPadding: EdgeInsets.zero,
            dense: true,
            controlAffinity: ListTileControlAffinity.leading,
            value: selected.contains(module.id),
            onChanged: enabled ? (v) => toggle(module, v ?? false) : null,
            title: Text(module.label),
            subtitle: module.description.isEmpty
                ? null
                : Text(module.description),
          ),
        Padding(
          padding: const EdgeInsets.only(top: 4),
          child: Text(
            count == 0
                ? 'Nothing selected: only signing in and signing out run.'
                : 'Signing in and signing out always run. A module left out is '
                      'skipped and listed as skipped in the report.',
            style: theme.textTheme.bodySmall?.copyWith(color: muted),
          ),
        ),
      ],
    );
  }
}

/// A field that shows which modules a run covers and opens the checklist in a
/// bottom sheet when tapped, so it can sit with the other form fields.
class ModuleField extends StatelessWidget {
  const ModuleField({
    required this.modules,
    required this.selected,
    required this.onChanged,
    this.enabled = true,
    super.key,
  });

  final List<AutomationModule> modules;
  final Set<String> selected;
  final ValueChanged<Set<String>> onChanged;
  final bool enabled;

  /// "All modules (8)", "None: ...", or "3 of 8: Login, Orders, Profile".
  static String summary(List<AutomationModule> modules, Set<String> selected) {
    final chosen = modules.where((m) => selected.contains(m.id)).toList();
    if (chosen.length == modules.length) return 'All modules (${modules.length})';
    if (chosen.isEmpty) return 'None: only sign-in and sign-out run';
    return '${chosen.length} of ${modules.length}: '
        '${chosen.map((m) => m.label).join(', ')}';
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return InkWell(
      borderRadius: BorderRadius.circular(4),
      onTap: enabled
          ? () => showModuleSheet(
              context,
              modules: modules,
              selected: selected,
              onChanged: onChanged,
            )
          : null,
      child: InputDecorator(
        decoration: InputDecoration(
          labelText: 'Modules to test',
          enabled: enabled,
          suffixIcon: const Icon(Icons.keyboard_arrow_up),
        ),
        child: Text(
          summary(modules, selected),
          maxLines: 2,
          overflow: TextOverflow.ellipsis,
          style: theme.textTheme.bodyLarge,
        ),
      ),
    );
  }
}

/// Shows the module checklist in a bottom sheet. Each change is reported through
/// [onChanged] straight away, so closing the sheet needs no confirm step.
Future<void> showModuleSheet(
  BuildContext context, {
  required List<AutomationModule> modules,
  required Set<String> selected,
  required ValueChanged<Set<String>> onChanged,
}) {
  var current = Set<String>.of(selected);
  return showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    showDragHandle: true,
    builder: (sheetContext) {
      return StatefulBuilder(
        builder: (context, setSheetState) {
          return SafeArea(
            child: ConstrainedBox(
              constraints: BoxConstraints(
                maxHeight: MediaQuery.of(context).size.height * 0.85,
              ),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Flexible(
                    child: SingleChildScrollView(
                      padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
                      child: ModulePicker(
                        modules: modules,
                        selected: current,
                        onChanged: (next) {
                          setSheetState(() => current = next);
                          onChanged(next);
                        },
                      ),
                    ),
                  ),
                  Padding(
                    padding: const EdgeInsets.fromLTRB(16, 4, 16, 12),
                    child: FilledButton(
                      onPressed: () => Navigator.of(sheetContext).pop(),
                      child: const Text('Done'),
                    ),
                  ),
                ],
              ),
            ),
          );
        },
      );
    },
  );
}

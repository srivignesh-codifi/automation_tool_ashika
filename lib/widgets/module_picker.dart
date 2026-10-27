import 'package:flutter/material.dart';

import '../models/order_config.dart';
import '../models/profile_config.dart';
import '../services/automation_service.dart';
import 'order_config_panel.dart';
import 'profile_config_panel.dart';
import 'read_more.dart';

/// The checklist of modules a run covers. All of them start selected; the
/// operator unticks what a run should leave out, and those steps are skipped.
class ModulePicker extends StatelessWidget {
  const ModulePicker({
    required this.modules,
    required this.selected,
    required this.onChanged,
    this.enabled = true,
    this.directLogin = false,
    this.onDirectLoginChanged,
    this.orderConfig,
    this.onOrderConfigChanged,
    this.profileConfig,
    this.onProfileConfigChanged,
    super.key,
  });

  final List<AutomationModule> modules;
  final Set<String> selected;
  final ValueChanged<Set<String>> onChanged;
  final bool enabled;

  /// The Order placement settings, shown under that module when [onOrderConfigChanged] is given.
  final OrderConfig? orderConfig;
  final ValueChanged<OrderConfig>? onOrderConfigChanged;

  /// The Profile module settings (which of its checks run), shown under it when [onProfileConfigChanged] is given.
  final ProfileConfig? profileConfig;
  final ValueChanged<ProfileConfig>? onProfileConfigChanged;

  /// "Direct login only": the Login module signs in straight through and skips its detours (Guest User, Unblock
  /// Account, Join Now, Forgot Password, Switch Account). Shown under Login when [onDirectLoginChanged] is given.
  final bool directLogin;
  final ValueChanged<bool>? onDirectLoginChanged;

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
        for (final module in modules) ...[
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
                : ReadMoreText(
                    key: ValueKey<String>('module_subtitle_${module.id}'),
                    title: module.label,
                    text: module.description,
                  ),
          ),
          if (module.id == 'login' && onDirectLoginChanged != null)
            Padding(
              padding: const EdgeInsets.only(left: 40),
              child: SwitchListTile(
                key: const ValueKey<String>('login_direct_only'),
                contentPadding: EdgeInsets.zero,
                dense: true,
                title: const Text('Direct login only'),
                subtitle: const Text(
                  'Sign in straight through: skip the Guest User, Unblock Account, '
                  'Join Now, Forgot Password and Switch Account checks.',
                ),
                value: directLogin,
                onChanged: enabled && selected.contains('login')
                    ? onDirectLoginChanged
                    : null,
              ),
            ),
          if (module.id == 'order_placement' &&
              orderConfig != null &&
              onOrderConfigChanged != null)
            OrderConfigPanel(
              config: orderConfig!,
              onChanged: onOrderConfigChanged!,
              enabled: enabled && selected.contains('order_placement'),
            ),
          if (module.id == 'profile' &&
              profileConfig != null &&
              onProfileConfigChanged != null)
            ProfileConfigPanel(
              config: profileConfig!,
              onChanged: onProfileConfigChanged!,
              enabled: enabled && selected.contains('profile'),
            ),
        ],
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
    this.directLogin = false,
    this.onDirectLoginChanged,
    this.orderConfig,
    this.onOrderConfigChanged,
    this.profileConfig,
    this.onProfileConfigChanged,
    super.key,
  });

  final List<AutomationModule> modules;
  final Set<String> selected;
  final ValueChanged<Set<String>> onChanged;
  final bool enabled;
  final bool directLogin;
  final ValueChanged<bool>? onDirectLoginChanged;
  final OrderConfig? orderConfig;
  final ValueChanged<OrderConfig>? onOrderConfigChanged;
  final ProfileConfig? profileConfig;
  final ValueChanged<ProfileConfig>? onProfileConfigChanged;

  /// "All modules (8)", "None: ...", or "3 of 8: Login, Orders, Profile".
  static String summary(
    List<AutomationModule> modules,
    Set<String> selected, {
    bool directLogin = false,
  }) {
    final chosen = modules.where((m) => selected.contains(m.id)).toList();
    final direct = directLogin && selected.contains('login')
        ? ' · direct login only'
        : '';
    if (chosen.length == modules.length) {
      return 'All modules (${modules.length})$direct';
    }
    if (chosen.isEmpty) return 'None: only sign-in and sign-out run';
    return '${chosen.length} of ${modules.length}: '
        '${chosen.map((m) => m.label).join(', ')}$direct';
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
              directLogin: directLogin,
              onDirectLoginChanged: onDirectLoginChanged,
              orderConfig: orderConfig,
              onOrderConfigChanged: onOrderConfigChanged,
              profileConfig: profileConfig,
              onProfileConfigChanged: onProfileConfigChanged,
            )
          : null,
      child: InputDecorator(
        decoration: InputDecoration(
          labelText: 'Modules to test',
          enabled: enabled,
          suffixIcon: const Icon(Icons.keyboard_arrow_up),
        ),
        child: Text(
          summary(modules, selected, directLogin: directLogin),
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
  bool directLogin = false,
  ValueChanged<bool>? onDirectLoginChanged,
  OrderConfig? orderConfig,
  ValueChanged<OrderConfig>? onOrderConfigChanged,
  ProfileConfig? profileConfig,
  ValueChanged<ProfileConfig>? onProfileConfigChanged,
}) {
  var current = Set<String>.of(selected);
  var currentDirect = directLogin;
  var currentOrders = orderConfig;
  var currentProfile = profileConfig;
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
                        directLogin: currentDirect,
                        onDirectLoginChanged: onDirectLoginChanged == null
                            ? null
                            : (on) {
                                setSheetState(() => currentDirect = on);
                                onDirectLoginChanged(on);
                              },
                        orderConfig: currentOrders,
                        onOrderConfigChanged: onOrderConfigChanged == null
                            ? null
                            : (next) {
                                setSheetState(() => currentOrders = next);
                                onOrderConfigChanged(next);
                              },
                        profileConfig: currentProfile,
                        onProfileConfigChanged: onProfileConfigChanged == null
                            ? null
                            : (next) {
                                setSheetState(() => currentProfile = next);
                                onProfileConfigChanged(next);
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

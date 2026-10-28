import 'package:flutter/material.dart';

import '../models/profile_config.dart';

/// The settings of the Profile module, shown under it on the module page: which checks of the app's Profile and
/// Settings tabs a run makes.
class ProfileConfigPanel extends StatelessWidget {
  const ProfileConfigPanel({
    required this.config,
    required this.onChanged,
    this.enabled = true,
    super.key,
  });

  final ProfileConfig config;
  final ValueChanged<ProfileConfig> onChanged;
  final bool enabled;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final muted = theme.colorScheme.onSurfaceVariant;
    // Collapsed until opened, like the Order settings: the line under the title says what is set.
    return Container(
      key: const ValueKey<String>('profile_config_panel'),
      margin: const EdgeInsets.only(left: 40, bottom: 8),
      decoration: BoxDecoration(
        border: Border.all(color: theme.colorScheme.outlineVariant),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Theme(
        data: theme.copyWith(dividerColor: Colors.transparent),
        child: ExpansionTile(
          key: const ValueKey<String>('profile_config_header'),
          initiallyExpanded: false,
          dense: true,
          tilePadding: const EdgeInsets.symmetric(horizontal: 12),
          childrenPadding: const EdgeInsets.fromLTRB(12, 0, 12, 8),
          expandedCrossAxisAlignment: CrossAxisAlignment.start,
          title: Text('Profile settings', style: theme.textTheme.titleSmall),
          subtitle: Text(
            config.summary,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: theme.textTheme.bodySmall?.copyWith(color: muted),
          ),
          children: [
            Text(
              'Untick a check to leave it out; it is listed as NOT TESTED in the report. Switches and the font size '
              'are always put back as they were found.',
              style: theme.textTheme.bodySmall?.copyWith(color: muted),
            ),
            _section(
              context,
              'Profile tab',
              'profile',
              ProfileConfig.profileTab,
            ),
            _section(
              context,
              'Settings tab',
              'settings',
              ProfileConfig.settingsTab,
            ),
          ],
        ),
      ),
    );
  }

  Widget _section(
    BuildContext context,
    String title,
    String key,
    List<ProfileCheck> checks,
  ) {
    final theme = Theme.of(context);
    final muted = theme.colorScheme.onSurfaceVariant;
    final on = checks.where((c) => config.isOn(c.id)).length;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Divider(height: 16),
        Row(
          children: [
            Expanded(
              child: Text(
                '$title ($on of ${checks.length})',
                style: theme.textTheme.titleSmall,
              ),
            ),
            TextButton(
              key: ValueKey<String>('profile_${key}_all'),
              onPressed: !enabled || on == checks.length
                  ? null
                  : () => onChanged(config.withAll(checks, true)),
              child: const Text('All'),
            ),
            TextButton(
              key: ValueKey<String>('profile_${key}_none'),
              onPressed: !enabled || on == 0
                  ? null
                  : () => onChanged(config.withAll(checks, false)),
              child: const Text('None'),
            ),
          ],
        ),
        for (final check in checks)
          CheckboxListTile(
            key: ValueKey<String>('profile_check_${check.id}'),
            contentPadding: EdgeInsets.zero,
            dense: true,
            visualDensity: VisualDensity.compact,
            controlAffinity: ListTileControlAffinity.leading,
            value: config.isOn(check.id),
            onChanged: enabled
                ? (v) => onChanged(config.withCheck(check.id, v ?? false))
                : null,
            title: Text('${check.code}  ${check.label}'),
            subtitle: Text(
              check.hint,
              style: theme.textTheme.bodySmall?.copyWith(color: muted),
            ),
          ),
      ],
    );
  }
}

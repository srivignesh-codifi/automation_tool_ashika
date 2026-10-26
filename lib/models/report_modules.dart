import 'automation_step.dart';

/// The names of the modules (areas) a report is split into, by module id.
const Map<String, String> _moduleLabels = <String, String>{
  'login': 'Login',
  'watchlist': 'Watchlist',
  'order_placement': 'Order placement',
  'orders': 'Orders',
  'dashboard': 'Dashboard',
  'portfolio': 'Portfolio',
  'profile': 'Profile',
  'home': 'Home',
  'research': 'Research',
};

/// The module a step is listed under. The sign-in and sign-out steps carry no module and belong to Login. Reports saved
/// before the Watchlist groups were merged name them `watchlist_search`, `watchlist_edit` and `watchlist_settings`;
/// they are Watchlist too.
String moduleLabelOf(AutomationStep step) {
  final id = step.module;
  if (id == null || id.isEmpty) return 'Login';
  if (id.startsWith('watchlist')) return 'Watchlist';
  return _moduleLabels[id] ??
      '${id[0].toUpperCase()}${id.substring(1).replaceAll('_', ' ')}';
}

/// How one module's steps came out, for the summary at the top of a report.
class ModuleStats {
  ModuleStats(this.label);

  final String label;
  int total = 0;
  int passed = 0;
  int failed = 0;

  /// Skipped, blocked, not applicable and not tested.
  int skipped = 0;

  /// Never reached (the run ended first).
  int pending = 0;

  /// FAIL when any step failed, PASS when every step passed, SKIPPED when none ran, PARTIAL otherwise.
  String get outcome {
    if (failed > 0) return 'FAIL';
    if (passed == total) return 'PASS';
    if (passed == 0) return 'SKIPPED';
    return 'PARTIAL';
  }
}

/// One row per module, in the order the modules first appear in the checklist.
List<ModuleStats> summarizeModules(List<AutomationStep> steps) {
  final byLabel = <String, ModuleStats>{};
  for (final step in steps) {
    final stats = byLabel.putIfAbsent(
      moduleLabelOf(step),
      () => ModuleStats(moduleLabelOf(step)),
    );
    stats.total++;
    switch (step.status) {
      case StepStatus.passed:
        stats.passed++;
      case StepStatus.failed:
        stats.failed++;
      case StepStatus.skipped:
        stats.skipped++;
      case StepStatus.pending:
      case StepStatus.running:
        stats.pending++;
    }
  }
  return byLabel.values.toList();
}

/// Whether [step] matches what was typed in the checklist search (case-insensitive, every word must be found).
bool stepMatchesQuery(AutomationStep step, String query) {
  final words = query
      .toLowerCase()
      .split(RegExp(r'\s+'))
      .where((w) => w.isNotEmpty);
  if (words.isEmpty) return true;
  final haystack = <String?>[
    step.name,
    step.code,
    step.failureReason,
    step.note,
    step.expected,
    step.outcomeLabel,
    moduleLabelOf(step),
  ].whereType<String>().join(' ').toLowerCase();
  return words.every(haystack.contains);
}

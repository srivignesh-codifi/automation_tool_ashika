import 'automation_step.dart';

/// The tool user who started a run, as its report names them.
class TestedBy {
  const TestedBy({
    required this.userId,
    this.role,
    this.signInMethod,
    this.signedInAtMs,
  });

  final String userId;
  final String? role;

  /// `password` or `biometric`.
  final String? signInMethod;
  final int? signedInAtMs;

  DateTime? get signedInAt => signedInAtMs == null
      ? null
      : DateTime.fromMillisecondsSinceEpoch(signedInAtMs!);

  String get methodLabel => switch (signInMethod) {
    'biometric' => 'Biometric (fingerprint)',
    'password' => 'User ID and password',
    null => '—',
    final other => other,
  };

  /// Null unless [raw] names a user.
  static TestedBy? fromMap(Object? raw) {
    if (raw is! Map) return null;
    final id = raw['userId'];
    if (id is! String || id.trim().isEmpty) return null;
    return TestedBy(
      userId: id,
      role: raw['role'] as String?,
      signInMethod: raw['signInMethod'] as String?,
      signedInAtMs: (raw['signedInAtMs'] as num?)?.toInt(),
    );
  }

  Map<String, Object?> toJson() => <String, Object?>{
    'userId': userId,
    'role': role,
    'signInMethod': signInMethod,
    'signedInAtMs': signedInAtMs,
  };
}

/// The phone's network as the run started: how it was connected, its own IP
/// addresses, and the public IP address the internet saw it by.
class RunNetwork {
  const RunNetwork({
    this.capturedAtMs,
    this.transport = 'unknown',
    this.interfaceName,
    this.ipv4 = const <String>[],
    this.ipv6 = const <String>[],
    this.publicIp,
    this.publicIpSource,
    this.publicIpError,
  });

  final int? capturedAtMs;

  /// Wi-Fi, Mobile data, VPN, ... (joined with +), or offline.
  final String transport;
  final String? interfaceName;
  final List<String> ipv4;
  final List<String> ipv6;
  final String? publicIp;
  final String? publicIpSource;

  /// Why there is no [publicIp].
  final String? publicIpError;

  DateTime? get capturedAt => capturedAtMs == null
      ? null
      : DateTime.fromMillisecondsSinceEpoch(capturedAtMs!);

  /// The phone's own address: IPv4 first, else IPv6.
  String get deviceIp => ipv4.isNotEmpty
      ? ipv4.join(', ')
      : ipv6.isNotEmpty
      ? ipv6.join(', ')
      : '—';

  String get connection =>
      interfaceName == null ? transport : '$transport, $interfaceName';

  String get publicIpLabel =>
      publicIp ??
      'not available${publicIpError == null ? '' : ' ($publicIpError)'}';

  static RunNetwork? fromMap(Object? raw) {
    if (raw is! Map) return null;
    List<String> strings(Object? v) =>
        v is List ? v.whereType<String>().toList(growable: false) : const [];
    return RunNetwork(
      capturedAtMs: (raw['capturedAtMs'] as num?)?.toInt(),
      transport: (raw['transport'] as String?) ?? 'unknown',
      interfaceName: raw['interface'] as String?,
      ipv4: strings(raw['ipv4']),
      ipv6: strings(raw['ipv6']),
      publicIp: raw['publicIp'] as String?,
      publicIpSource: raw['publicIpSource'] as String?,
      publicIpError: raw['publicIpError'] as String?,
    );
  }

  Map<String, Object?> toJson() => <String, Object?>{
    'capturedAtMs': capturedAtMs,
    'transport': transport,
    'interface': interfaceName,
    'ipv4': ipv4,
    'ipv6': ipv6,
    'publicIp': publicIp,
    'publicIpSource': publicIpSource,
    'publicIpError': publicIpError,
  };
}

/// One instrument the run added to a watchlist (or tried to): what, from
/// where, into which watchlist, and whether it got there and was taken out
/// again.
class WatchlistAddition {
  const WatchlistAddition({
    required this.scrip,
    required this.from,
    this.code,
    this.exchange,
    this.type,
    this.watchlist,
    this.alreadyThere = false,
    this.added = false,
    this.listed,
    this.removed,
    this.detail,
  });

  final String? code;
  final String scrip;
  final String? exchange;

  /// EQ, FUT, CE, PE when the search row said.
  final String? type;
  final String from;
  final String? watchlist;

  /// It was the user's own, already in the watchlist; it is left there.
  final bool alreadyThere;

  /// The search row turned from + to the tick.
  final bool added;

  /// The watchlist showed it afterwards; null when not checked.
  final bool? listed;

  /// The run took it out again; null when kept on purpose or not tried.
  final bool? removed;
  final String? detail;

  String get addedLabel => alreadyThere
      ? (added ? 'YES (was there)' : 'ALREADY THERE')
      : (added ? 'YES' : 'NO');

  String get listedLabel => switch (listed) {
    true => 'YES',
    false => 'NO',
    null => added || alreadyThere ? 'NOT CHECKED' : '—',
  };

  String get removedLabel {
    if (alreadyThere) return 'KEPT (user\'s)';
    if (!added) return '—';
    return switch (removed) {
      true => 'YES',
      false => 'NO, STILL THERE',
      null => 'KEPT',
    };
  }

  /// Added (or already there) and shown, and not left behind by mistake.
  bool get ok => (added || alreadyThere) && listed != false && removed != false;

  static WatchlistAddition fromMap(Map<Object?, Object?> raw) =>
      WatchlistAddition(
        code: raw['code'] as String?,
        scrip: (raw['scrip'] as String?) ?? '?',
        exchange: raw['exchange'] as String?,
        type: raw['type'] as String?,
        from: (raw['from'] as String?) ?? '',
        watchlist: raw['watchlist'] as String?,
        alreadyThere: raw['alreadyThere'] == true,
        added: raw['added'] == true,
        listed: raw['listed'] as bool?,
        removed: raw['removed'] as bool?,
        detail: raw['detail'] as String?,
      );

  Map<String, Object?> toJson() => <String, Object?>{
    'code': code,
    'scrip': scrip,
    'exchange': exchange,
    'type': type,
    'from': from,
    'watchlist': watchlist,
    'alreadyThere': alreadyThere,
    'added': added,
    'listed': listed,
    'removed': removed,
    'detail': detail,
  };
}

/// One kind of order an app offers (or does not).
class OrderTypeInfo {
  const OrderTypeInfo({
    required this.id,
    required this.group,
    required this.label,
    this.meaning = '',
    this.exchanges = const <String>[],
    this.offered = true,
    this.why,
    this.stepIds = const <String>[],
  });

  final String id;
  final String group;
  final String label;
  final String meaning;
  final List<String> exchanges;
  final bool offered;

  /// Why the app does not offer it.
  final String? why;

  /// The cases that test it (GTT, Basket), when it is not placed from the pad.
  final List<String> stepIds;

  /// "SL-M" for "SL-M (stop-loss market)".
  String get shortLabel {
    final i = label.indexOf(' (');
    return i < 0 ? label : label.substring(0, i);
  }

  static OrderTypeInfo fromMap(Map<Object?, Object?> raw) {
    List<String> strings(Object? v) =>
        v is List ? v.whereType<String>().toList(growable: false) : const [];
    return OrderTypeInfo(
      id: (raw['id'] as String?) ?? '',
      group: (raw['group'] as String?) ?? '',
      label: (raw['label'] as String?) ?? '',
      meaning: (raw['meaning'] as String?) ?? '',
      exchanges: strings(raw['exchanges']),
      offered: raw['offered'] != false,
      why: raw['why'] as String?,
      stepIds: strings(raw['stepIds']),
    );
  }

  Map<String, Object?> toJson() => <String, Object?>{
    'id': id,
    'group': group,
    'label': label,
    'meaning': meaning,
    'exchanges': exchanges,
    'offered': offered,
    'why': why,
    'stepIds': stepIds,
  };
}

/// One order the module tried to place.
class OrderAttempt {
  const OrderAttempt({
    required this.exchange,
    required this.side,
    required this.kind,
    required this.product,
    required this.outcome,
    this.detail = '',
  });

  final String exchange;
  final String side;
  final String kind;
  final String product;

  /// refused, accepted, failed, skipped, off, not_applicable or not_offered
  /// (the app has it, but not for this account).
  final String outcome;
  final String detail;

  String get where => '$exchange $side';

  /// The reason after the order's own name (`NSE BUY Market: <reason>`),
  /// without the "not sent, " the driver starts a skipped order's with.
  String get reason {
    final i = detail.indexOf(': ');
    final text = i < 0 ? detail : detail.substring(i + 2);
    return text.startsWith('not sent, ') ? text.substring(10) : text;
  }

  static OrderAttempt fromMap(Map<Object?, Object?> raw) => OrderAttempt(
    exchange: (raw['exchange'] as String?) ?? '',
    side: (raw['side'] as String?) ?? '',
    kind: (raw['kind'] as String?) ?? '',
    product: (raw['product'] as String?) ?? '',
    outcome: (raw['outcome'] as String?) ?? 'skipped',
    detail: (raw['detail'] as String?) ?? '',
  );

  Map<String, Object?> toJson() => <String, Object?>{
    'exchange': exchange,
    'side': side,
    'kind': kind,
    'product': product,
    'outcome': outcome,
    'detail': detail,
  };
}

/// How one order type came out in a run: a label, the CSS class that colours
/// it (passed, failed, skipped) and what lies behind it.
typedef OrderTypeOutcome = ({String label, String css, String detail});

/// The kinds of order the app under test offers, and what the run did with
/// each: the report's "Order types" section.
class OrderTypesReport {
  const OrderTypesReport({
    required this.app,
    required this.moduleRun,
    required this.types,
    this.attempts = const <OrderAttempt>[],
  });

  final String app;

  /// Whether the Order placement module was selected for the run.
  final bool moduleRun;
  final List<OrderTypeInfo> types;
  final List<OrderAttempt> attempts;

  List<OrderTypeInfo> get offered => types.where((t) => t.offered).toList();
  List<OrderTypeInfo> get notOffered => types.where((t) => !t.offered).toList();

  /// The attempts that were orders of [type]: by kind, or by product.
  /// An order of another kind that was never tried (switched off, not in the
  /// app) says nothing about its product.
  List<OrderAttempt> attemptsOf(OrderTypeInfo type) => attempts
      .where(
        (a) =>
            a.kind == type.id ||
            (a.product == type.id &&
                a.outcome != 'off' &&
                a.outcome != 'not_applicable'),
      )
      .toList();

  /// "NSE BUY", or "NSE BUY Market" for an order counted by its product.
  String _name(OrderAttempt a, OrderTypeInfo type) {
    if (a.kind == type.id) return a.where;
    final kind = types.where((t) => t.id == a.kind).firstOrNull;
    return '${a.where} ${kind?.shortLabel ?? a.kind.toUpperCase()}';
  }

  OrderTypeOutcome outcomeOf(OrderTypeInfo type, List<AutomationStep> steps) {
    if (!type.offered) {
      return (
        label: 'NOT IN APP',
        css: 'skipped',
        detail: type.why ?? 'not offered by $app',
      );
    }
    if (type.stepIds.isNotEmpty) return _fromCases(type, steps);
    final tried = attemptsOf(type);
    if (tried.isEmpty) {
      return (
        label: 'NOT TESTED',
        css: 'skipped',
        detail: moduleRun
            ? 'no order of this type was tried: its exchanges or "Place orders" were off'
            : 'Order placement was not selected for this run',
      );
    }
    String list(String outcome) => tried
        .where((a) => a.outcome == outcome)
        .map((a) => _name(a, type))
        .toSet()
        .join(', ');
    // Why the ones not sent were not sent, once (they share the reason, the margin gate, as a rule).
    String why(String outcome) {
      final first = tried.where((a) => a.outcome == outcome).firstOrNull;
      return first == null ? '' : ' (${first.reason})';
    }

    final parts = <String>[
      for (final (outcome, said) in const <(String, String)>[
        ('accepted', 'NOT refused'),
        ('failed', 'failed'),
        ('refused', 'sent and refused'),
        ('skipped', 'not sent'),
        ('off', 'off in settings'),
        ('not_applicable', 'not applicable'),
        ('not_offered', 'not offered to this account'),
      ])
        if (list(outcome).isNotEmpty)
          '$said: ${list(outcome)}'
              '${outcome == 'skipped' || outcome == 'not_offered' ? why(outcome) : ''}',
    ];
    final detail = parts.join('; ');
    bool any(String outcome) => tried.any((a) => a.outcome == outcome);
    if (any('accepted')) {
      return (label: 'ACCEPTED', css: 'failed', detail: detail);
    }
    if (any('failed')) return (label: 'FAILED', css: 'failed', detail: detail);
    if (any('refused')) {
      return (label: 'SENT · REFUSED', css: 'passed', detail: detail);
    }
    if (tried.every((a) => a.outcome == 'off')) {
      return (label: 'OFF', css: 'skipped', detail: detail);
    }
    if (tried.every((a) => a.outcome == 'not_applicable')) {
      return (label: 'NOT IN APP', css: 'skipped', detail: detail);
    }
    if (tried.every((a) => a.outcome == 'not_offered')) {
      return (label: 'NOT FOR ACCOUNT', css: 'skipped', detail: detail);
    }
    return (label: 'NOT SENT', css: 'skipped', detail: detail);
  }

  /// GTT and Basket: the outcome of their cases.
  OrderTypeOutcome _fromCases(OrderTypeInfo type, List<AutomationStep> steps) {
    final cases = steps.where((s) => type.stepIds.contains(s.id)).toList();
    if (cases.isEmpty) {
      return (label: 'NOT TESTED', css: 'skipped', detail: 'not in this run');
    }
    final failed = cases.where((s) => s.status == StepStatus.failed).toList();
    final passed = cases.where((s) => s.status == StepStatus.passed).length;
    final codes = cases.map((s) => s.code ?? s.id).join(', ');
    if (failed.isNotEmpty) {
      return (
        label: 'FAILED',
        css: 'failed',
        detail:
            '${failed.length} of ${cases.length} cases failed: '
            '${failed.map((s) => s.code ?? s.id).join(', ')}',
      );
    }
    if (passed == 0) {
      final note = cases.first.note ?? cases.first.failureReason;
      return (
        label: 'NOT TESTED',
        css: 'skipped',
        detail: note ?? 'no case of $codes ran',
      );
    }
    return (
      label: 'PASSED',
      css: 'passed',
      detail: passed == cases.length
          ? 'all ${cases.length} cases passed ($codes)'
          : '$passed of ${cases.length} cases passed, the others were not run ($codes)',
    );
  }

  static OrderTypesReport? fromMap(Object? raw) {
    if (raw is! Map) return null;
    final types = (raw['types'] is List ? raw['types'] as List : const [])
        .whereType<Map<Object?, Object?>>()
        .map(OrderTypeInfo.fromMap)
        .where((t) => t.id.isNotEmpty)
        .toList(growable: false);
    if (types.isEmpty) return null;
    return OrderTypesReport(
      app: (raw['app'] as String?) ?? 'the app',
      moduleRun: raw['moduleRun'] == true,
      types: types,
      attempts: (raw['attempts'] is List ? raw['attempts'] as List : const [])
          .whereType<Map<Object?, Object?>>()
          .map(OrderAttempt.fromMap)
          .toList(growable: false),
    );
  }

  Map<String, Object?> toJson() => <String, Object?>{
    'app': app,
    'moduleRun': moduleRun,
    'types': types.map((t) => t.toJson()).toList(),
    'attempts': attempts.map((a) => a.toJson()).toList(),
  };
}

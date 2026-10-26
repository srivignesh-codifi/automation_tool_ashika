/// What the Order placement module does on one exchange.
class OrderSegmentConfig {
  const OrderSegmentConfig({
    required this.enabled,
    required this.place,
    required this.script,
  });

  /// Open the order pad of this exchange's instrument and check it.
  final bool enabled;

  /// Also place orders there. Only NSE and BSE can; the margin gate still applies.
  final bool place;

  /// The instrument to search for and open the pad of (IDEA, CRUDEOIL, ...).
  final String script;

  OrderSegmentConfig copyWith({bool? enabled, bool? place, String? script}) =>
      OrderSegmentConfig(
        enabled: enabled ?? this.enabled,
        place: place ?? this.place,
        script: script ?? this.script,
      );

  Map<String, Object?> toJson() => <String, Object?>{
    'enabled': enabled,
    'place': place,
    'script': script,
  };
}

/// The Order placement settings of a run, shown under the module on the module page: for each exchange whether its pad
/// is checked, whether orders are placed there, and which instrument is used.
///
/// The default places orders on NSE only, with IDEA, and checks the MCX pad with CRUDEOIL.
class OrderConfig {
  const OrderConfig(this.segments);

  /// In the order the module runs them.
  static const List<String> keys = <String>['nse', 'bse', 'nfo', 'bfo', 'mcx'];

  static const Map<String, String> labels = <String, String>{
    'nse': 'NSE',
    'bse': 'BSE',
    'nfo': 'NFO',
    'bfo': 'BFO',
    'mcx': 'MCX',
  };

  /// Orders are never placed outside these, whatever is chosen.
  static const Set<String> placeable = <String>{'nse', 'bse'};

  static const Map<String, String> defaultScripts = <String, String>{
    'nse': 'IDEA',
    'bse': 'IDEA',
    'nfo': 'IDEA',
    'bfo': 'IDEA',
    'mcx': 'CRUDEOIL',
  };

  factory OrderConfig.defaults() =>
      const OrderConfig(<String, OrderSegmentConfig>{
        'nse': OrderSegmentConfig(enabled: true, place: true, script: 'IDEA'),
        'bse': OrderSegmentConfig(enabled: false, place: false, script: 'IDEA'),
        'nfo': OrderSegmentConfig(enabled: false, place: false, script: 'IDEA'),
        'bfo': OrderSegmentConfig(enabled: false, place: false, script: 'IDEA'),
        'mcx': OrderSegmentConfig(
          enabled: true,
          place: false,
          script: 'CRUDEOIL',
        ),
      });

  final Map<String, OrderSegmentConfig> segments;

  OrderSegmentConfig of(String key) =>
      segments[key] ?? OrderConfig.defaults().segments[key]!;

  /// A typed instrument name made safe: upper case, letters, digits, `&` and `-`, at most 24; an empty one is [fallback].
  static String cleanScript(String raw, String fallback) {
    final cleaned = raw
        .toUpperCase()
        .replaceAll(RegExp(r'[^A-Z0-9&-]'), '')
        .trim();
    final cut = cleaned.length > 24 ? cleaned.substring(0, 24) : cleaned;
    return cut.isEmpty ? fallback : cut;
  }

  OrderConfig withSegment(String key, OrderSegmentConfig segment) {
    final next = Map<String, OrderSegmentConfig>.of(segments);
    final fixed = placeable.contains(key)
        ? segment
        : segment.copyWith(place: false);
    next[key] = fixed.enabled ? fixed : fixed.copyWith(place: false);
    return OrderConfig(next);
  }

  /// Reads a saved or sent map; anything missing or odd is the default for that exchange.
  factory OrderConfig.fromJson(Object? raw) {
    final defaults = OrderConfig.defaults();
    if (raw is! Map) return defaults;
    final out = <String, OrderSegmentConfig>{};
    for (final key in keys) {
      final base = defaults.segments[key]!;
      final m = raw[key];
      if (m is! Map) {
        out[key] = base;
        continue;
      }
      final enabled = m['enabled'] is bool
          ? m['enabled'] as bool
          : base.enabled;
      final place = m['place'] is bool ? m['place'] as bool : base.place;
      out[key] = OrderSegmentConfig(
        enabled: enabled,
        place: enabled && placeable.contains(key) && place,
        script: cleanScript(
          m['script'] is String ? m['script'] as String : '',
          base.script,
        ),
      );
    }
    return OrderConfig(out);
  }

  Map<String, Object?> toJson() => <String, Object?>{
    for (final key in keys) key: of(key).toJson(),
  };

  /// "NSE IDEA (orders) · MCX CRUDEOIL", for a line under the module.
  String get summary {
    final on = keys.where((k) => of(k).enabled).map((k) {
      final s = of(k);
      return '${labels[k]} ${s.script}${s.place ? ' (orders)' : ''}';
    }).toList();
    return on.isEmpty ? 'no exchange selected' : on.join(' · ');
  }
}

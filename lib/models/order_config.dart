/// What the Order placement module does on one exchange.
class OrderSegmentConfig {
  const OrderSegmentConfig({
    required this.enabled,
    required this.place,
    required this.script,
  });

  /// Open the order pad of this exchange's instrument and check it.
  final bool enabled;

  /// Also place orders there (any exchange). The margin gate still applies to every order.
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

/// A kind of order the module places, as the Order settings offer it.
class OrderKind {
  const OrderKind(this.id, this.label);

  /// The driver's name for it (OrderPlan.KINDS).
  final String id;
  final String label;
}

/// The Order placement settings of a run, shown under the module on the module page: for each exchange whether its pad
/// is checked, whether orders are placed there, and which instrument is used; and which kinds of order are placed.
///
/// The default places orders on NSE only, with IDEA, and checks the MCX pad with CRUDEOIL. Every kind of order is on.
class OrderConfig {
  const OrderConfig(
    this.segments, {
    this.gtt = true,
    this.gttScript = 'IDEA',
    this.basket = true,
    this.basketScript = 'IDEA',
    this.placeOff = const <String>{},
  });

  /// In the order the module runs them.
  static const List<String> keys = <String>['nse', 'bse', 'nfo', 'bfo', 'mcx'];

  static const Map<String, String> labels = <String, String>{
    'nse': 'NSE',
    'bse': 'BSE',
    'nfo': 'NFO',
    'bfo': 'BFO',
    'mcx': 'MCX',
  };

  /// The order types: each placed as a BUY and as a SELL, with the MIS product.
  static const List<OrderKind> orderTypes = <OrderKind>[
    OrderKind('market', 'Market'),
    OrderKind('limit', 'Limit'),
    OrderKind('sl', 'SL'),
    OrderKind('slm', 'SL-M'),
    OrderKind('co', 'CO'),
    OrderKind('bo', 'BO'),
    OrderKind('amo', 'AMO'),
  ];

  /// The products other than MIS: a Limit BUY each (NFO, BFO and MCX offer NRML only).
  static const List<OrderKind> products = <OrderKind>[
    OrderKind('nrml', 'NRML'),
    OrderKind('cnc', 'CNC'),
    OrderKind('mtf', 'MTF'),
  ];

  /// Advanced Options: a Limit BUY with Validity IOC, and one with a Disclosed Quantity (NSE, BSE and MCX).
  static const List<OrderKind> advanced = <OrderKind>[
    OrderKind('ioc', 'Validity IOC'),
    OrderKind('disclosed', 'Disclosed Qty'),
  ];

  static List<OrderKind> get kinds => <OrderKind>[
    ...orderTypes,
    ...products,
    ...advanced,
  ];

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

  /// Also create, modify and cancel a GTT order on the NSE instrument (a trigger that cannot be reached, cancelled at
  /// the end), from Orders > GTT.
  final bool gtt;

  /// The instrument the GTT test uses (an NSE stock), set apart from the NSE order settings.
  final String gttScript;

  /// Also test a basket from Orders > Basket: create one, add the instrument, modify the scrip, execute it (only while
  /// the pad shows Avail. Margin 0.00) and cancel what is left pending.
  final bool basket;

  /// The instrument the basket test adds.
  final String basketScript;

  /// The kinds of order ([kinds]) switched off; stored this way so a kind added later starts switched on.
  final Set<String> placeOff;

  OrderConfig _copy({
    Map<String, OrderSegmentConfig>? segments,
    bool? gtt,
    String? gttScript,
    bool? basket,
    String? basketScript,
    Set<String>? placeOff,
  }) => OrderConfig(
    segments ?? this.segments,
    gtt: gtt ?? this.gtt,
    gttScript: gttScript ?? this.gttScript,
    basket: basket ?? this.basket,
    basketScript: basketScript ?? this.basketScript,
    placeOff: placeOff ?? this.placeOff,
  );

  bool places(String kind) => !placeOff.contains(kind);

  OrderConfig withKind(String kind, bool on) {
    final next = Set<String>.of(placeOff);
    on ? next.remove(kind) : next.add(kind);
    return _copy(placeOff: next);
  }

  OrderConfig withGtt(bool value) => _copy(gtt: value);

  OrderConfig withGttScript(String raw) =>
      _copy(gttScript: cleanScript(raw, 'IDEA'));

  OrderConfig withBasket(bool value) => _copy(basket: value);

  OrderConfig withBasketScript(String raw) =>
      _copy(basketScript: cleanScript(raw, 'IDEA'));

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
    next[key] = segment.enabled ? segment : segment.copyWith(place: false);
    return _copy(segments: next);
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
        place: enabled && place,
        script: cleanScript(
          m['script'] is String ? m['script'] as String : '',
          base.script,
        ),
      );
    }
    return OrderConfig(
      out,
      gtt: raw['gtt'] is bool ? raw['gtt'] as bool : true,
      gttScript: cleanScript(
        raw['gttScript'] is String ? raw['gttScript'] as String : '',
        'IDEA',
      ),
      basket: raw['basket'] is bool ? raw['basket'] as bool : true,
      basketScript: cleanScript(
        raw['basketScript'] is String ? raw['basketScript'] as String : '',
        'IDEA',
      ),
      placeOff: raw['placeOff'] is List
          ? (raw['placeOff'] as List)
                .whereType<String>()
                .where(kinds.map((k) => k.id).contains)
                .toSet()
          : const <String>{},
    );
  }

  Map<String, Object?> toJson() => <String, Object?>{
    for (final key in keys) key: of(key).toJson(),
    'gtt': gtt,
    'gttScript': gttScript,
    'basket': basket,
    'basketScript': basketScript,
    'placeOff': (placeOff.toList()..sort()),
  };

  /// "NSE IDEA (orders) · MCX CRUDEOIL", for a line under the module; "· 9 of 12 kinds of order" when some are off.
  String get summary {
    final on = keys.where((k) => of(k).enabled).map((k) {
      final s = of(k);
      return '${labels[k]} ${s.script}${s.place ? ' (orders)' : ''}';
    }).toList();
    if (gtt) on.add('GTT $gttScript');
    if (basket) on.add('Basket $basketScript');
    final placing = keys.any((k) => of(k).place);
    final kindsOn = kinds.where((k) => places(k.id)).length;
    if (placing && kindsOn < kinds.length) {
      on.add('$kindsOn of ${kinds.length} kinds of order');
    }
    return on.isEmpty ? 'no exchange selected' : on.join(' · ');
  }
}

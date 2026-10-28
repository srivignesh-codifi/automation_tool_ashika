/// When a run happens. In live trade time the tool also measures the
/// market-data feed (websocket) over the whole run and reports it on its own.
enum MarketSession {
  /// Before the market opens (beginning of day): prices do not tick, so the
  /// feed is not measured. The default.
  bod('bod', 'BOD', 'Before the market opens: the live feed is not measured.'),

  /// Trading hours: connection, subscription errors, disconnections, ticks and
  /// gaps of the feed are measured until the testing is complete.
  live(
    'live',
    'Live trade time',
    'Market open: the websocket feed is measured for the whole run and reported in its own section.',
  );

  const MarketSession(this.wire, this.label, this.hint);

  final String wire;
  final String label;
  final String hint;

  static MarketSession fromWire(String? raw) =>
      raw == 'live' ? MarketSession.live : MarketSession.bod;
}

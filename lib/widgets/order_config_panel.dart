import 'package:flutter/material.dart';

import '../models/order_config.dart';

/// The settings of the Order placement module, shown under it on the module page: for each exchange whether its order
/// pad is opened and checked, which instrument opens it, and whether orders are placed there; and which kinds of order
/// are placed.
class OrderConfigPanel extends StatefulWidget {
  const OrderConfigPanel({
    required this.config,
    required this.onChanged,
    this.enabled = true,
    super.key,
  });

  final OrderConfig config;
  final ValueChanged<OrderConfig> onChanged;
  final bool enabled;

  @override
  State<OrderConfigPanel> createState() => _OrderConfigPanelState();
}

class _OrderConfigPanelState extends State<OrderConfigPanel> {
  late final Map<String, TextEditingController> _scripts =
      <String, TextEditingController>{
        for (final key in OrderConfig.keys)
          key: TextEditingController(text: widget.config.of(key).script),
      };

  late final TextEditingController _gttScript = TextEditingController(
    text: widget.config.gttScript,
  );
  late final TextEditingController _basketScript = TextEditingController(
    text: widget.config.basketScript,
  );

  @override
  void dispose() {
    _gttScript.dispose();
    _basketScript.dispose();
    for (final c in _scripts.values) {
      c.dispose();
    }
    super.dispose();
  }

  void _update(String key, OrderSegmentConfig segment) =>
      widget.onChanged(widget.config.withSegment(key, segment));

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final muted = theme.colorScheme.onSurfaceVariant;
    // Collapsed until opened: the module list stays short, and the line under the title says what is set.
    return Container(
      key: const ValueKey<String>('order_config_panel'),
      margin: const EdgeInsets.only(left: 40, bottom: 8),
      decoration: BoxDecoration(
        border: Border.all(color: theme.colorScheme.outlineVariant),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Theme(
        data: theme.copyWith(dividerColor: Colors.transparent),
        child: ExpansionTile(
          key: const ValueKey<String>('order_config_header'),
          initiallyExpanded: false,
          dense: true,
          tilePadding: const EdgeInsets.symmetric(horizontal: 12),
          childrenPadding: const EdgeInsets.fromLTRB(12, 0, 12, 8),
          expandedCrossAxisAlignment: CrossAxisAlignment.start,
          title: Text('Order settings', style: theme.textTheme.titleSmall),
          subtitle: Text(
            widget.config.summary,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: theme.textTheme.bodySmall?.copyWith(color: muted),
          ),
          children: [
            Text(
              'Untick an exchange to leave it out. "Place orders" sends real orders on a live account: on every '
              'exchange an order is only sent while the pad shows Avail. Margin 0.00 and a margin above it, so the '
              'exchange refuses it; otherwise it is skipped.',
              style: theme.textTheme.bodySmall?.copyWith(color: muted),
            ),
            for (final key in OrderConfig.keys) ...[
              const SizedBox(height: 6),
              _row(key, theme),
            ],
            const Divider(height: 20),
            Text('Orders placed', style: theme.textTheme.titleSmall),
            Text(
              'On each exchange with "Place orders" on. Untick a kind to leave it out; it is listed as skipped.',
              style: theme.textTheme.bodySmall?.copyWith(color: muted),
            ),
            _kinds(
              theme,
              'Order types (BUY and SELL, MIS)',
              OrderConfig.orderTypes,
            ),
            _kinds(
              theme,
              'Products (a Limit BUY each; NFO, BFO and MCX offer NRML only)',
              OrderConfig.products,
            ),
            _kinds(
              theme,
              'Advanced Options (a Limit BUY each; Disclosed Qty on NSE, BSE and MCX)',
              OrderConfig.advanced,
            ),
            const Divider(height: 20),
            Text('GTT orders', style: theme.textTheme.titleSmall),
            SwitchListTile(
              key: const ValueKey<String>('order_gtt_switch'),
              contentPadding: EdgeInsets.zero,
              dense: true,
              title: const Text('Test GTT orders'),
              subtitle: const Text(
                'From Orders > GTT only: create one order with a trigger that cannot be reached, modify it, cancel it. '
                'No watchlist and no order pad are used.',
              ),
              value: widget.config.gtt,
              onChanged: widget.enabled
                  ? (v) => widget.onChanged(widget.config.withGtt(v))
                  : null,
            ),
            TextField(
              key: const ValueKey<String>('order_gtt_script'),
              controller: _gttScript,
              enabled: widget.enabled && widget.config.gtt,
              textCapitalization: TextCapitalization.characters,
              decoration: const InputDecoration(
                isDense: true,
                labelText: 'GTT instrument (NSE stock)',
                border: OutlineInputBorder(),
              ),
              onChanged: (raw) =>
                  widget.onChanged(widget.config.withGttScript(raw)),
            ),
            const Divider(height: 20),
            Text('Basket orders', style: theme.textTheme.titleSmall),
            SwitchListTile(
              key: const ValueKey<String>('order_basket_switch'),
              contentPadding: EdgeInsets.zero,
              dense: true,
              title: const Text('Test basket orders'),
              subtitle: const Text(
                'From Orders > Basket: create a basket, add the instrument, modify the scrip, execute it (only while '
                'Avail. Margin is 0.00) and cancel what stays pending.',
              ),
              value: widget.config.basket,
              onChanged: widget.enabled
                  ? (v) => widget.onChanged(widget.config.withBasket(v))
                  : null,
            ),
            TextField(
              key: const ValueKey<String>('order_basket_script'),
              controller: _basketScript,
              enabled: widget.enabled && widget.config.basket,
              textCapitalization: TextCapitalization.characters,
              decoration: const InputDecoration(
                isDense: true,
                labelText: 'Basket instrument (NSE stock)',
                border: OutlineInputBorder(),
              ),
              onChanged: (raw) =>
                  widget.onChanged(widget.config.withBasketScript(raw)),
            ),
          ],
        ),
      ),
    );
  }

  Widget _kinds(ThemeData theme, String title, List<OrderKind> kinds) {
    return Padding(
      padding: const EdgeInsets.only(top: 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(title, style: theme.textTheme.bodySmall),
          const SizedBox(height: 4),
          Wrap(
            spacing: 6,
            runSpacing: 4,
            children: [
              for (final kind in kinds)
                FilterChip(
                  key: ValueKey<String>('order_kind_${kind.id}'),
                  visualDensity: VisualDensity.compact,
                  label: Text(kind.label),
                  selected: widget.config.places(kind.id),
                  onSelected: widget.enabled
                      ? (v) =>
                            widget.onChanged(widget.config.withKind(kind.id, v))
                      : null,
                ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _row(String key, ThemeData theme) {
    final segment = widget.config.of(key);
    final label = OrderConfig.labels[key]!;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Checkbox(
              key: ValueKey<String>('order_seg_$key'),
              visualDensity: VisualDensity.compact,
              value: segment.enabled,
              onChanged: widget.enabled
                  ? (v) => _update(key, segment.copyWith(enabled: v ?? false))
                  : null,
            ),
            SizedBox(width: 44, child: Text(label)),
            Expanded(
              child: TextField(
                key: ValueKey<String>('order_script_$key'),
                controller: _scripts[key],
                enabled: widget.enabled && segment.enabled,
                textCapitalization: TextCapitalization.characters,
                decoration: const InputDecoration(
                  isDense: true,
                  labelText: 'Instrument',
                  border: OutlineInputBorder(),
                ),
                onChanged: (raw) => _update(
                  key,
                  segment.copyWith(
                    script: OrderConfig.cleanScript(
                      raw,
                      OrderConfig.defaultScripts[key]!,
                    ),
                  ),
                ),
              ),
            ),
          ],
        ),
        Padding(
          padding: const EdgeInsets.only(left: 48),
          child: SwitchListTile(
            key: ValueKey<String>('order_place_$key'),
            contentPadding: EdgeInsets.zero,
            dense: true,
            title: Text('Place orders on $label'),
            value: segment.place && segment.enabled,
            onChanged: widget.enabled && segment.enabled
                ? (v) => _update(key, segment.copyWith(place: v))
                : null,
          ),
        ),
      ],
    );
  }
}

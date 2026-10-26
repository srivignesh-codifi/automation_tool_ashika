import 'package:flutter/material.dart';

import '../models/order_config.dart';

/// The settings of the Order placement module, shown under it on the module page: for each exchange whether its order
/// pad is opened and checked, which instrument opens it, and (NSE and BSE only) whether orders are placed there.
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

  @override
  void dispose() {
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
    return Container(
      key: const ValueKey<String>('order_config_panel'),
      margin: const EdgeInsets.only(left: 40, bottom: 8),
      padding: const EdgeInsets.fromLTRB(12, 8, 12, 8),
      decoration: BoxDecoration(
        border: Border.all(color: theme.colorScheme.outlineVariant),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('Order settings', style: theme.textTheme.titleSmall),
          const SizedBox(height: 2),
          Text(
            'Untick an exchange to leave it out. "Place orders" is only offered for NSE and BSE and still needs '
            'Avail. Margin 0.00 on the pad.',
            style: theme.textTheme.bodySmall?.copyWith(color: muted),
          ),
          for (final key in OrderConfig.keys) ...[
            const SizedBox(height: 6),
            _row(key, theme),
          ],
        ],
      ),
    );
  }

  Widget _row(String key, ThemeData theme) {
    final segment = widget.config.of(key);
    final label = OrderConfig.labels[key]!;
    final canPlace = OrderConfig.placeable.contains(key);
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
        if (canPlace)
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

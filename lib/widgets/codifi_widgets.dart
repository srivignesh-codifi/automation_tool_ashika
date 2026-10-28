import 'package:flutter/material.dart';

import '../theme/codifi_theme.dart';

/// The CodiFi wordmark (white text, for the dark theme), or just the C.
class CodifiLogo extends StatelessWidget {
  const CodifiLogo({this.height = 40, this.markOnly = false, super.key});

  final double height;
  final bool markOnly;

  @override
  Widget build(BuildContext context) => Image.asset(
    markOnly
        ? 'assets/brand/codifi_mark.png'
        : 'assets/brand/codifi_logo_light.png',
    height: height,
    fit: BoxFit.contain,
    semanticLabel: 'CodiFi',
  );
}

/// The page behind the CodiFi screens: CodiFi's near-black, flat (no streaks
/// or glow).
class CodifiBackdrop extends StatelessWidget {
  const CodifiBackdrop({required this.child, super.key});

  final Widget child;

  @override
  Widget build(BuildContext context) =>
      ColoredBox(color: CodifiColors.background, child: child);
}

/// The coral pill button of codifi.in ("CONTACT US"), with a gradient fill and no glow.
class CodifiPillButton extends StatelessWidget {
  const CodifiPillButton({
    required this.label,
    required this.onPressed,
    this.icon,
    this.busy = false,
    super.key,
  });

  final String label;
  final VoidCallback? onPressed;
  final IconData? icon;
  final bool busy;

  @override
  Widget build(BuildContext context) {
    final enabled = onPressed != null && !busy;
    return Opacity(
      opacity: enabled ? 1 : 0.55,
      child: DecoratedBox(
        decoration: BoxDecoration(
          gradient: CodifiColors.action,
          borderRadius: BorderRadius.circular(30),
        ),
        child: Material(
          type: MaterialType.transparency,
          child: InkWell(
            borderRadius: BorderRadius.circular(30),
            onTap: enabled ? onPressed : null,
            child: SizedBox(
              height: 52,
              child: Center(
                child: busy
                    ? const SizedBox.square(
                        dimension: 22,
                        child: CircularProgressIndicator(
                          strokeWidth: 2.4,
                          color: Colors.white,
                        ),
                      )
                    : Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Text(
                            label,
                            style: const TextStyle(
                              color: Colors.white,
                              fontWeight: FontWeight.w600,
                              fontSize: 15,
                              letterSpacing: 0.6,
                            ),
                          ),
                          if (icon != null) ...[
                            const SizedBox(width: 10),
                            Icon(icon, color: Colors.white, size: 20),
                          ],
                        ],
                      ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

import 'package:flutter/material.dart';

/// How an app under test is shown: its logo, instead of its package name.
///
/// Keyed by the native `TargetApp` id. An app without a logo here still works:
/// its tile shows the first letter of its name.
class AppBrand {
  const AppBrand({required this.logo});

  /// Asset path of a square logo (see assets/apps/).
  final String logo;

  static const Map<String, AppBrand> _brands = <String, AppBrand>{
    'ashika': AppBrand(logo: 'assets/apps/ashika.png'),
    'tradion': AppBrand(logo: 'assets/apps/tradion.png'),
    'itrade': AppBrand(logo: 'assets/apps/itrade.png'),
  };

  static AppBrand? of(String appId) => _brands[appId];
}

/// The square logo of the app [appId] on a white rounded tile, or its initial.
class AppLogo extends StatelessWidget {
  const AppLogo({
    required this.appId,
    required this.name,
    this.size = 64,
    super.key,
  });

  final String appId;
  final String name;
  final double size;

  @override
  Widget build(BuildContext context) {
    final brand = AppBrand.of(appId);
    final radius = BorderRadius.circular(size * 0.24);
    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(color: Colors.white, borderRadius: radius),
      clipBehavior: Clip.antiAlias,
      padding: EdgeInsets.all(brand == null ? 0 : size * 0.08),
      alignment: Alignment.center,
      child: brand == null
          ? Text(
              name.isEmpty ? '?' : name.characters.first.toUpperCase(),
              style: TextStyle(
                fontSize: size * 0.45,
                fontWeight: FontWeight.w700,
                color: const Color(0xFF111118),
              ),
            )
          : Image.asset(
              brand.logo,
              fit: BoxFit.contain,
              semanticLabel: '$name logo',
            ),
    );
  }
}

import 'package:flutter/material.dart';

/// CodiFi's look: a near-black page, coral-orange actions, and restrained
/// purple / blue accents.
class CodifiColors {
  CodifiColors._();

  static const Color background = Color(0xFF060609);
  static const Color surface = Color(0xFF111118);
  static const Color surfaceHigh = Color(0xFF1A1A24);
  static const Color outline = Color(0xFF2B2B38);

  static const Color orange = Color(0xFFEE6A4B);
  static const Color orangeLight = Color(0xFFF7926F);
  static const Color purple = Color(0xFF8E5CF7);
  static const Color blue = Color(0xFF4A7DFF);
  static const Color pink = Color(0xFFE0569B);

  static const Color text = Color(0xFFF4F4F7);
  static const Color textMuted = Color(0xFFA3A3B5);

  static const Color success = Color(0xFF3DD68C);
  static const Color danger = Color(0xFFFF5D6C);

  /// The pill buttons ("CONTACT US").
  static const LinearGradient action = LinearGradient(
    colors: [orange, orangeLight],
  );

  /// The logo's C, for accents (tile borders, highlights).
  static const LinearGradient brand = LinearGradient(
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
    colors: [orangeLight, pink, purple, blue],
  );
}

class CodifiTheme {
  CodifiTheme._();

  static ThemeData dark() {
    const scheme = ColorScheme(
      brightness: Brightness.dark,
      primary: CodifiColors.orange,
      onPrimary: Colors.white,
      primaryContainer: Color(0xFF3A1B14),
      onPrimaryContainer: CodifiColors.orangeLight,
      secondary: CodifiColors.purple,
      onSecondary: Colors.white,
      secondaryContainer: Color(0xFF2A1C4A),
      onSecondaryContainer: Color(0xFFD9C8FF),
      tertiary: CodifiColors.blue,
      onTertiary: Colors.white,
      error: CodifiColors.danger,
      onError: Colors.white,
      surface: CodifiColors.surface,
      onSurface: CodifiColors.text,
      onSurfaceVariant: CodifiColors.textMuted,
      surfaceContainerLowest: CodifiColors.background,
      surfaceContainerLow: Color(0xFF0D0D13),
      surfaceContainer: CodifiColors.surface,
      surfaceContainerHigh: CodifiColors.surfaceHigh,
      surfaceContainerHighest: Color(0xFF22222E),
      outline: CodifiColors.outline,
      outlineVariant: Color(0xFF20202A),
    );
    final pill = RoundedRectangleBorder(
      borderRadius: BorderRadius.circular(28),
    );
    return ThemeData(
      useMaterial3: true,
      brightness: Brightness.dark,
      colorScheme: scheme,
      scaffoldBackgroundColor: CodifiColors.background,
      appBarTheme: const AppBarTheme(
        backgroundColor: CodifiColors.background,
        foregroundColor: CodifiColors.text,
        elevation: 0,
        scrolledUnderElevation: 0,
        centerTitle: false,
      ),
      cardTheme: CardThemeData(
        color: CodifiColors.surface,
        elevation: 0,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(16),
          side: const BorderSide(color: CodifiColors.outline),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: CodifiColors.surfaceHigh,
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(14),
          borderSide: const BorderSide(color: CodifiColors.outline),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(14),
          borderSide: const BorderSide(color: CodifiColors.outline),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(14),
          borderSide: const BorderSide(color: CodifiColors.orange, width: 1.6),
        ),
        labelStyle: const TextStyle(color: CodifiColors.textMuted),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          minimumSize: const Size.fromHeight(50),
          backgroundColor: CodifiColors.orange,
          foregroundColor: Colors.white,
          shape: pill,
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          minimumSize: const Size.fromHeight(48),
          foregroundColor: CodifiColors.text,
          side: const BorderSide(color: CodifiColors.outline),
          shape: pill,
        ),
      ),
      textButtonTheme: TextButtonThemeData(
        style: TextButton.styleFrom(foregroundColor: CodifiColors.orangeLight),
      ),
      segmentedButtonTheme: SegmentedButtonThemeData(
        style: SegmentedButton.styleFrom(
          selectedBackgroundColor: CodifiColors.orange.withValues(alpha: 0.18),
          selectedForegroundColor: CodifiColors.orangeLight,
          side: const BorderSide(color: CodifiColors.outline),
        ),
      ),
      switchTheme: SwitchThemeData(
        thumbColor: WidgetStateProperty.resolveWith(
          (s) => s.contains(WidgetState.selected)
              ? Colors.white
              : CodifiColors.textMuted,
        ),
        trackColor: WidgetStateProperty.resolveWith(
          (s) => s.contains(WidgetState.selected)
              ? CodifiColors.orange
              : CodifiColors.surfaceHigh,
        ),
      ),
      dividerTheme: const DividerThemeData(color: CodifiColors.outline),
      snackBarTheme: const SnackBarThemeData(
        backgroundColor: CodifiColors.surfaceHigh,
        contentTextStyle: TextStyle(color: CodifiColors.text),
        behavior: SnackBarBehavior.floating,
      ),
    );
  }
}

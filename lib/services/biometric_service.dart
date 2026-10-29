import 'package:flutter/services.dart';

/// Whether this phone can sign in to the tool with a fingerprint, and whether
/// that is turned on (and for whom).
class BiometricStatus {
  const BiometricStatus({
    required this.available,
    required this.enabled,
    this.userId,
    this.reason,
  });

  const BiometricStatus.unavailable([this.reason])
    : available = false,
      enabled = false,
      userId = null;

  final bool available;
  final bool enabled;

  /// Whose password is kept for biometric sign-in, when it is on.
  final String? userId;

  /// Why it cannot be used on this phone, in words for the tester.
  final String? reason;
}

/// What came of one biometric prompt.
class BiometricOutcome {
  const BiometricOutcome({
    required this.ok,
    this.error,
    this.message,
    this.userId,
    this.password,
  });

  final bool ok;

  /// cancelled, locked_out, invalidated, unavailable or failed.
  final String? error;
  final String? message;

  /// What [BiometricService.unlock] gives back.
  final String? userId;
  final String? password;

  bool get cancelled => error == 'cancelled';
}

/// Biometric sign-in to the tool itself, through the native BiometricSignIn:
/// the password is kept encrypted under an Android Keystore key that only a
/// fingerprint unlocks, and comes back only after the phone has recognised the
/// tester. A phone without a usable fingerprint simply does not offer it.
class BiometricService {
  BiometricService({MethodChannel? commands})
    : _commands =
          commands ?? const MethodChannel('com.mobileautomation.tool/commands');

  final MethodChannel _commands;

  Future<BiometricStatus> status() async {
    final raw = await _call('biometricStatus');
    if (raw == null) return const BiometricStatus.unavailable();
    return BiometricStatus(
      available: raw['available'] == true,
      enabled: raw['enabled'] == true,
      userId: raw['userId'] as String?,
      reason: raw['reason'] as String?,
    );
  }

  /// Asks for a fingerprint and keeps [password] for [userId] under it.
  Future<BiometricOutcome> enable({
    required String userId,
    required String password,
  }) async => _outcome(
    await _call('biometricEnable', <String, Object?>{
      'userId': userId,
      'password': password,
    }),
  );

  /// Asks for a fingerprint and gives back the user ID and password kept.
  Future<BiometricOutcome> unlock() async =>
      _outcome(await _call('biometricUnlock'));

  /// Forgets the kept password.
  Future<void> disable() async => _call('biometricDisable');

  static BiometricOutcome _outcome(Map<Object?, Object?>? raw) {
    if (raw == null) {
      return const BiometricOutcome(
        ok: false,
        error: 'unavailable',
        message: 'This install of the tool has no biometric sign-in.',
      );
    }
    return BiometricOutcome(
      ok: raw['ok'] == true,
      error: raw['error'] as String?,
      message: raw['message'] as String?,
      userId: raw['userId'] as String?,
      password: raw['password'] as String?,
    );
  }

  Future<Map<Object?, Object?>?> _call(
    String method, [
    Map<String, Object?>? args,
  ]) async {
    try {
      final raw = await _commands.invokeMethod<Object?>(method, args);
      return raw is Map<Object?, Object?> ? raw : null;
    } on PlatformException {
      return null;
    } on MissingPluginException {
      return null;
    }
  }
}

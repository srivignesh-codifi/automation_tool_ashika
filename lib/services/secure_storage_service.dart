import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Storage for the UAT test credentials the operator types into the tool.
///
/// Rules this class enforces:
///  * The OTP is kept only when the operator turns on "Remember", exactly like
///    the other credentials: it is meant for test accounts whose OTP is fixed.
///    A genuinely one-time OTP is useless the second time, so with Remember off
///    it lives in the form field and is cleared after the run.
///  * The user id and the MPIN / password are stored only in Android's
///    Keystore-backed `EncryptedSharedPreferences`, never in plain
///    SharedPreferences, and separately per target app.
///  * Nothing here is ever written to a log or into a report.
class SecureStorageService {
  SecureStorageService({FlutterSecureStorage? storage})
    : _storage =
          storage ??
          const FlutterSecureStorage(
            aOptions: AndroidOptions(encryptedSharedPreferences: true),
          );

  final FlutterSecureStorage _storage;

  static const String _kClientId = 'uat_client_id';
  static const String _kMpin = 'uat_mpin';
  static const String _kTargetPackage = 'uat_target_package';
  static const String _kRemember = 'uat_remember_credentials';
  static const String _kSetupDone = 'tool_setup_done';

  /// Ashika's keys predate the second app and stay as they were, so an operator
  /// who saved credentials keeps them. Every other app gets suffixed keys.
  static const String _legacyAppId = 'ashika';

  String _clientIdKey(String appId) =>
      appId == _legacyAppId ? _kClientId : '${_kClientId}_$appId';

  String _secretKey(String appId) =>
      appId == _legacyAppId ? _kMpin : 'uat_secret_$appId';

  String _otpKey(String appId) => 'uat_otp_$appId';

  String _rememberKey(String appId) =>
      appId == _legacyAppId ? _kRemember : '${_kRemember}_$appId';

  Future<String?> readClientId({String appId = _legacyAppId}) =>
      _read(_clientIdKey(appId));

  /// The saved MPIN (Ashika) or password (Tradion).
  Future<String?> readSecret({String appId = _legacyAppId}) =>
      _read(_secretKey(appId));

  Future<String?> readOtp({String appId = _legacyAppId}) =>
      _read(_otpKey(appId));

  Future<String?> readTargetPackage() => _read(_kTargetPackage);

  /// Whether the first-launch permission setup was finished. Not a secret; it
  /// only lives here because this is the tool's one small store.
  Future<bool> readSetupDone() async => (await _read(_kSetupDone)) == 'true';

  Future<void> setSetupDone() => _write(_kSetupDone, 'true');

  Future<bool> readRemember({String appId = _legacyAppId}) async =>
      (await _read(_rememberKey(appId))) == 'true';

  Future<void> saveTargetPackage(String value) =>
      _write(_kTargetPackage, value);

  Future<void> setRemember(bool value, {String appId = _legacyAppId}) =>
      _write(_rememberKey(appId), value ? 'true' : 'false');

  /// Persists the reusable credentials, and the OTP when one is given.
  Future<void> saveCredentials({
    required String clientId,
    required String secret,
    String? otp,
    String appId = _legacyAppId,
  }) async {
    await _write(_clientIdKey(appId), clientId);
    await _write(_secretKey(appId), secret);
    if (otp == null || otp.isEmpty) {
      await _delete(_otpKey(appId));
    } else {
      await _write(_otpKey(appId), otp);
    }
  }

  /// Wipes the saved user id, MPIN / password and OTP of one app. Exposed in the
  /// UI as "Clear saved credentials".
  Future<void> clearCredentials({String appId = _legacyAppId}) async {
    await _delete(_clientIdKey(appId));
    await _delete(_secretKey(appId));
    await _delete(_otpKey(appId));
    await _write(_rememberKey(appId), 'false');
  }

  Future<String?> _read(String key) async {
    try {
      return await _storage.read(key: key);
    } catch (_) {
      // A Keystore failure must not take the tool down; the operator can simply
      // retype the credentials.
      return null;
    }
  }

  Future<void> _write(String key, String value) async {
    try {
      await _storage.write(key: key, value: value);
    } catch (_) {
      // Ignored on purpose: persistence is a convenience, not a requirement.
    }
  }

  Future<void> _delete(String key) async {
    try {
      await _storage.delete(key: key);
    } catch (_) {
      // Ignored on purpose.
    }
  }
}

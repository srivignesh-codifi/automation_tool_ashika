import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Storage for the UAT test credentials the operator types into the tool.
///
/// Rules this class enforces:
///  * The OTP is never written anywhere. It is a one-time value; it lives in the
///    form field for the duration of a run and is cleared afterwards.
///  * The Client ID and MPIN are stored only in Android's Keystore-backed
///    `EncryptedSharedPreferences`, never in plain SharedPreferences.
///  * Nothing here is ever written to a log or into a report.
class SecureStorageService {
  SecureStorageService({FlutterSecureStorage? storage})
    : _storage =
          storage ??
          const FlutterSecureStorage(
            aOptions: AndroidOptions(
              encryptedSharedPreferences: true,
            ),
          );

  final FlutterSecureStorage _storage;

  static const String _kClientId = 'uat_client_id';
  static const String _kMpin = 'uat_mpin';
  static const String _kTargetPackage = 'uat_target_package';
  static const String _kRemember = 'uat_remember_credentials';

  Future<String?> readClientId() => _read(_kClientId);

  Future<String?> readMpin() => _read(_kMpin);

  Future<String?> readTargetPackage() => _read(_kTargetPackage);

  Future<bool> readRemember() async => (await _read(_kRemember)) == 'true';

  Future<void> saveTargetPackage(String value) =>
      _write(_kTargetPackage, value);

  Future<void> setRemember(bool value) =>
      _write(_kRemember, value ? 'true' : 'false');

  /// Persists the reusable credentials. The OTP is intentionally not a parameter.
  Future<void> saveCredentials({
    required String clientId,
    required String mpin,
  }) async {
    await _write(_kClientId, clientId);
    await _write(_kMpin, mpin);
  }

  /// Wipes the saved Client ID and MPIN. Exposed in the UI as
  /// "Clear saved credentials".
  Future<void> clearCredentials() async {
    await _delete(_kClientId);
    await _delete(_kMpin);
    await _write(_kRemember, 'false');
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

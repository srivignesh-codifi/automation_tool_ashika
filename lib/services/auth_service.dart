import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/foundation.dart';

import 'secure_storage_service.dart';

/// How the user proved who they are when signing in to the tool.
enum SignInMethod {
  password,

  /// A fingerprint unlocked the password kept for biometric sign-in.
  biometric,
}

/// The person signed in to the tool.
class AuthUser {
  const AuthUser({
    required this.userId,
    this.token = '',
    this.role,
    this.method = SignInMethod.password,
    this.signedInAt,
  });

  final String userId;

  /// What the sign-in API returned to authorise later calls (empty when it
  /// returned none).
  final String token;

  /// The role the sign-in API gave (HEAD, ...), when it gave one.
  final String? role;

  final SignInMethod method;
  final DateTime? signedInAt;

  /// Who started a run, for its report. Never the token.
  Map<String, Object?> toReport() => <String, Object?>{
    'userId': userId,
    if (role != null) 'role': role,
    'signInMethod': method.name,
    if (signedInAt != null) 'signedInAtMs': signedInAt!.millisecondsSinceEpoch,
  };
}

/// Why a sign-in was refused, in words the sign-in screen can show.
class AuthException implements Exception {
  const AuthException(this.message, {this.rejected = false});

  final String message;

  /// The server refused the user ID or password (rather than not answering).
  final bool rejected;

  @override
  String toString() => message;
}

/// Sends a JSON body to [url] and returns the HTTP status and response body.
typedef AuthTransport = Future<(int, String)> Function(Uri url, String body);

/// Signs the tool's user in against the built-in CodiFi login reply and
/// remembers who is signed in. The password is only checked, never stored.
class AuthService {
  AuthService(this._storage, {AuthTransport? transport, bool? skipCheck})
    : _transport = transport ?? _builtIn,
      skipCheck = skipCheck ?? _skipByDefault;

  final SecureStorageService _storage;
  final AuthTransport _transport;

  /// When true the built-in reply is not checked: SIGN IN opens the tool whatever was
  /// typed. Off: sign-in is checked against the built-in response. Build with
  /// `--dart-define=AUTH_SKIP=true` to skip it again.
  final bool skipCheck;

  static const bool _skipByDefault = bool.fromEnvironment(
    'AUTH_SKIP',
    defaultValue: false,
  );

  /// The server's reply to admin / admin123, as captured on 2026-10-28. Its
  /// token goes with every report sent to the admin; it expires at
  /// [expiresAt], after which the server refuses it.
  static const String _builtInResponse = '''
{
  "token": "YWRtaW58MTc5MzIyNjY3MA.f5rorFKJ3aFUgcNy2ei72jziK-Azo44e3Ny5yvTCt1w",
  "username": "admin",
  "expiresAt": "2026-10-28T22:31:10.543332400Z",
  "access": {
    "username": "admin",
    "role": "HEAD",
    "permissions": ["dashboard", "reports_view", "reports_delete", "brokers", "testcases_view", "testcases_edit", "run", "setup", "git"],
    "allBrokers": true,
    "brokerIds": ["com.codifi.dhanush", "com.codifi.rmoney", "com.codifi.itrade"]
  }
}''';

  /// What the sign-in screen tells the user about how sign-in is checked, or
  /// null when it is checked by the server.
  String? get notice => skipCheck
      ? 'Sign-in is switched off for now: tap SIGN IN to open the tool.'
      : _transport == _builtIn
      ? 'Sign-in uses a built-in server response for now: sign in with admin / admin123.'
      : null;

  /// Who the tool shows as signed in when sign-in is skipped with no user ID.
  static const String _skippedUser = 'tester';

  static const Duration _timeout = Duration(seconds: 15);

  /// Whether sign-in is checked by the server.
  bool get connected => !skipCheck;

  Future<AuthUser?> currentUser() async {
    final session = await _storage.readSession();
    if (session == null) return null;
    return AuthUser(userId: session.$1, token: session.$2);
  }

  Future<void> signOut() => _storage.clearSession();

  /// Signs [userId] in. Throws [AuthException] with a message for the user.
  /// [method] says how the password was given: typed, or unlocked by a
  /// fingerprint; it is checked the same way.
  Future<AuthUser> signIn({
    required String userId,
    required String password,
    SignInMethod method = SignInMethod.password,
  }) async {
    final id = userId.trim();
    if (skipCheck) {
      final user = AuthUser(
        userId: id.isEmpty ? _skippedUser : id,
        method: method,
        signedInAt: DateTime.now(),
      );
      await _storage.saveSession(userId: user.userId, token: '');
      return user;
    }
    if (id.isEmpty) throw const AuthException('Enter your user ID.');
    if (password.isEmpty) throw const AuthException('Enter your password.');
    final (token, role) = await _verify(id, password);
    // Kept for Send to admin (who tested, and the token for its request). The
    // tool still asks to sign in at every launch: start-up does not read it.
    await _storage.saveSession(userId: id, token: token);
    return AuthUser(
      userId: id,
      token: token,
      role: role,
      method: method,
      signedInAt: DateTime.now(),
    );
  }

  /// Applies the built-in login reply and returns its session token and the
  /// user's role.
  Future<(String, String?)> _verify(String userId, String password) async {
    final usingBuiltIn = _transport == _builtIn;
    debugPrint(
      '[Auth API] POST /api/admin/login${usingBuiltIn ? ' (built-in response)' : ''}',
    );
    debugPrint(
      '[Auth API] Request body: ${jsonEncode(<String, String>{'username': userId, 'password': '[REDACTED]'})}',
    );
    final (int status, String body) response;
    try {
      response = await _transport(
        Uri(path: '/api/admin/login'),
        jsonEncode(<String, String>{'username': userId, 'password': password}),
      ).timeout(_timeout);
    } on TimeoutException {
      throw AuthException(
        'The sign-in server did not answer in ${_timeout.inSeconds} s. Try again.',
      );
    } on SocketException {
      throw AuthException(
        'Cannot reach the sign-in server. Check that this phone is on the office network.',
      );
    } on HttpException {
      throw const AuthException(
        'The sign-in server sent a broken reply. Try again.',
      );
    }

    final (status, body) = response;
    final json = _decode(body);
    debugPrint('[Auth API] HTTP $status ${_logSafeResponse(json, body)}');
    final message = _message(json);
    if (status == 401 || status == 403) {
      throw AuthException(
        message ?? 'Wrong user ID or password.',
        rejected: true,
      );
    }
    if (status < 200 || status >= 300) {
      throw AuthException(
        message ?? 'Sign-in failed (server error $status). Try again.',
      );
    }
    // Some APIs answer 200 with a refusal in the body.
    if (json is Map &&
        (json['success'] == false ||
            _isFailure(json['status']) ||
            json['error'] != null)) {
      throw AuthException(
        message ?? 'Wrong user ID or password.',
        rejected: true,
      );
    }
    return (_token(json) ?? '', _role(json));
  }

  /// The role under `access`, at the top level, or inside `data` / `result`.
  static String? _role(Object? json) {
    if (json is! Map) return null;
    for (final holder in <Object?>[json['access'], json]) {
      final role = holder is Map ? holder['role'] : null;
      if (role is String && role.trim().isNotEmpty) return role.trim();
    }
    for (final key in const <String>['data', 'result']) {
      final nested = _role(json[key]);
      if (nested != null) return nested;
    }
    return null;
  }

  static Object? _decode(String body) {
    try {
      return jsonDecode(body);
    } catch (_) {
      return null;
    }
  }

  /// Log the response shape for troubleshooting without exposing session tokens.
  static String _logSafeResponse(Object? json, String raw) {
    if (json is Map) {
      Object? redact(Object? value) {
        if (value is Map) {
          return <String, Object?>{
            for (final entry in value.entries)
              entry.key.toString():
                  const <String>{
                    'token',
                    'accesstoken',
                    'access_token',
                    'jwt',
                    'authtoken',
                  }.contains(entry.key.toString().toLowerCase())
                  ? '[REDACTED]'
                  : redact(entry.value),
          };
        }
        if (value is List) return value.map(redact).toList();
        return value;
      }

      return jsonEncode(redact(json));
    }
    return raw;
  }

  static bool _isFailure(Object? status) =>
      status is String &&
      const <String>{
        'error',
        'fail',
        'failed',
        'failure',
      }.contains(status.toLowerCase());

  /// The token under the usual names, at the top level or inside `data` / `result`.
  static String? _token(Object? json) {
    if (json is! Map) return null;
    for (final key in const <String>[
      'token',
      'accessToken',
      'access_token',
      'jwt',
      'authToken',
    ]) {
      final value = json[key];
      if (value is String && value.isNotEmpty) return value;
    }
    for (final key in const <String>['data', 'result']) {
      final nested = _token(json[key]);
      if (nested != null) return nested;
    }
    return null;
  }

  /// The server's own explanation, when it sent one.
  static String? _message(Object? json) {
    if (json is! Map) return null;
    for (final key in const <String>['message', 'error', 'msg', 'detail']) {
      final value = json[key];
      if (value is String && value.trim().isNotEmpty) return value.trim();
      if (value is Map) {
        final nested = _message(value);
        if (nested != null) return nested;
      }
    }
    return null;
  }

  /// The sign-in server's answer, without the server: admin / admin123 get its
  /// recorded reply, anything else the refusal it gives.
  static Future<(int, String)> _builtIn(Uri url, String body) async {
    final sent = jsonDecode(body) as Map<String, Object?>;
    if (sent['username'] == 'admin' && sent['password'] == 'admin123') {
      return (200, _builtInResponse);
    }
    return (401, '{"message":"Invalid username or password"}');
  }
}

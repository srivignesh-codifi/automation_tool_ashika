import 'package:flutter/material.dart';

import '../services/auth_service.dart';
import '../services/biometric_service.dart';
import '../theme/codifi_theme.dart';
import '../widgets/codifi_widgets.dart';

/// The tool's own sign-in, in the CodiFi look: who is using the tool, before
/// any app is tested. See [AuthService] for what is checked.
///
/// With [biometrics], a phone with a fingerprint can also sign in by touch:
/// the tester turns it on with a password sign-in, and from then on the
/// fingerprint unlocks that password (see [BiometricService]).
class SignInScreen extends StatefulWidget {
  const SignInScreen({
    required this.auth,
    required this.onSignedIn,
    this.biometrics,
    this.promptBiometric = false,
    super.key,
  });

  final AuthService auth;
  final ValueChanged<AuthUser> onSignedIn;

  /// Biometric sign-in; none offered without it.
  final BiometricService? biometrics;

  /// Show the fingerprint prompt as soon as the screen opens, when biometric
  /// sign-in is on (at launch, not after signing out).
  final bool promptBiometric;

  @override
  State<SignInScreen> createState() => _SignInScreenState();
}

class _SignInScreenState extends State<SignInScreen> {
  final _userId = TextEditingController();
  final _password = TextEditingController();
  bool _hidden = true;
  bool _busy = false;
  String? _error;

  BiometricStatus _biometric = const BiometricStatus.unavailable();

  /// Turn on biometric sign-in with this password sign-in.
  bool _turnOnBiometric = false;

  /// While sign-in is skipped there is no password to keep.
  bool get _offerBiometric =>
      widget.biometrics != null && !widget.auth.skipCheck;

  @override
  void initState() {
    super.initState();
    if (_offerBiometric) _loadBiometric(prompt: widget.promptBiometric);
  }

  @override
  void dispose() {
    _userId.dispose();
    _password.dispose();
    super.dispose();
  }

  Future<void> _loadBiometric({bool prompt = false}) async {
    final status = await widget.biometrics!.status();
    if (!mounted) return;
    setState(() {
      _biometric = status;
      if (status.enabled && _userId.text.isEmpty) {
        _userId.text = status.userId ?? '';
      }
    });
    if (prompt && status.enabled) await _signInWithBiometric();
  }

  Future<void> _signIn() async {
    FocusScope.of(context).unfocus();
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final password = _password.text;
      final user = await widget.auth.signIn(
        userId: _userId.text,
        password: password,
      );
      _password.clear();
      if (_turnOnBiometric && _biometric.available) {
        await _enableBiometric(user.userId, password);
      }
      if (mounted) widget.onSignedIn(user);
    } on AuthException catch (e) {
      if (mounted) setState(() => _error = e.message);
    } catch (_) {
      if (mounted) {
        setState(
          () => _error = 'Sign-in failed. Check your connection and try again.',
        );
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  /// Keeps [password] for [userId] behind a fingerprint, after a password
  /// sign-in. A refusal does not stop the sign-in: it is only said.
  Future<void> _enableBiometric(String userId, String password) async {
    final outcome = await widget.biometrics!.enable(
      userId: userId,
      password: password,
    );
    if (!mounted || outcome.cancelled) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          outcome.ok
              ? 'Biometric sign-in is on for $userId.'
              : outcome.message ?? 'Biometric sign-in could not be turned on.',
        ),
      ),
    );
  }

  Future<void> _signInWithBiometric() async {
    FocusScope.of(context).unfocus();
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final outcome = await widget.biometrics!.unlock();
      if (!outcome.ok) {
        if (outcome.error == 'invalidated' || outcome.error == 'unavailable') {
          await _loadBiometric();
        }
        if (mounted && !outcome.cancelled) {
          setState(() => _error = outcome.message);
        }
        return;
      }
      final userId = outcome.userId ?? '';
      final password = outcome.password ?? '';
      setState(() {
        _userId.text = userId;
        _password.text = password;
      });
      final user = await widget.auth.signIn(
        userId: userId,
        password: password,
        method: SignInMethod.biometric,
      );
      if (mounted) widget.onSignedIn(user);
    } on AuthException catch (e) {
      // A password the server now refuses is of no more use behind a fingerprint.
      if (e.rejected) {
        await widget.biometrics!.disable();
        await _loadBiometric();
      }
      if (mounted) {
        setState(
          () => _error = e.rejected
              ? '${e.message} Biometric sign-in was turned off: sign in with '
                    'your password.'
              : e.message,
        );
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _turnOffBiometric() async {
    await widget.biometrics!.disable();
    await _loadBiometric();
    if (!mounted) return;
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(const SnackBar(content: Text('Biometric sign-in is off.')));
  }

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    return Scaffold(
      body: CodifiBackdrop(
        child: SafeArea(
          child: Center(
            child: SingleChildScrollView(
              padding: const EdgeInsets.fromLTRB(24, 24, 24, 32),
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 420),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    const Center(child: CodifiLogo(height: 56)),
                    const SizedBox(height: 40),
                    Text(
                      'Automation',
                      textAlign: TextAlign.center,
                      style: text.headlineMedium?.copyWith(
                        color: CodifiColors.text,
                        fontWeight: FontWeight.w500,
                      ),
                    ),
                    Text(
                      'Testing',
                      textAlign: TextAlign.center,
                      style: text.headlineMedium?.copyWith(
                        color: CodifiColors.orange,
                        fontWeight: FontWeight.w500,
                      ),
                    ),
                    const SizedBox(height: 12),
                    Text(
                      'Run regression tests of your trading apps on this device.',
                      textAlign: TextAlign.center,
                      style: text.bodyMedium?.copyWith(
                        color: CodifiColors.textMuted,
                      ),
                    ),
                    const SizedBox(height: 32),
                    _SignInCard(
                      child: AutofillGroup(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.stretch,
                          children: [
                            Text(
                              'Sign in',
                              style: text.titleLarge?.copyWith(
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                            const SizedBox(height: 18),
                            TextField(
                              key: const ValueKey<String>('sign_in_user_id'),
                              controller: _userId,
                              enabled: !_busy,
                              textInputAction: TextInputAction.next,
                              autofillHints: const [AutofillHints.username],
                              decoration: const InputDecoration(
                                labelText: 'User ID',
                                prefixIcon: Icon(Icons.person_outline),
                              ),
                            ),
                            const SizedBox(height: 14),
                            TextField(
                              key: const ValueKey<String>('sign_in_password'),
                              controller: _password,
                              enabled: !_busy,
                              obscureText: _hidden,
                              textInputAction: TextInputAction.done,
                              autofillHints: const [AutofillHints.password],
                              onSubmitted: (_) => _signIn(),
                              decoration: InputDecoration(
                                labelText: 'Password',
                                prefixIcon: const Icon(Icons.lock_outline),
                                suffixIcon: IconButton(
                                  onPressed: () =>
                                      setState(() => _hidden = !_hidden),
                                  icon: Icon(
                                    _hidden
                                        ? Icons.visibility_outlined
                                        : Icons.visibility_off_outlined,
                                  ),
                                  tooltip: _hidden
                                      ? 'Show password'
                                      : 'Hide password',
                                ),
                              ),
                            ),
                            if (_offerBiometric &&
                                _biometric.available &&
                                !_biometric.enabled) ...[
                              const SizedBox(height: 6),
                              SwitchListTile(
                                key: const ValueKey<String>(
                                  'sign_in_biometric_switch',
                                ),
                                contentPadding: EdgeInsets.zero,
                                dense: true,
                                secondary: const Icon(Icons.fingerprint),
                                title: const Text('Turn on biometric sign-in'),
                                subtitle: const Text(
                                  'Sign in with your fingerprint next time',
                                ),
                                value: _turnOnBiometric,
                                onChanged: _busy
                                    ? null
                                    : (on) =>
                                          setState(() => _turnOnBiometric = on),
                              ),
                            ],
                            if (_error != null) ...[
                              const SizedBox(height: 12),
                              Text(
                                _error!,
                                style: text.bodySmall?.copyWith(
                                  color: CodifiColors.danger,
                                ),
                              ),
                            ],
                            const SizedBox(height: 22),
                            CodifiPillButton(
                              key: const ValueKey<String>('sign_in_button'),
                              label: 'SIGN IN',
                              icon: Icons.arrow_forward,
                              busy: _busy,
                              onPressed: _signIn,
                            ),
                            if (_offerBiometric && _biometric.enabled) ...[
                              const SizedBox(height: 8),
                              TextButton(
                                key: const ValueKey<String>(
                                  'sign_in_biometric_off',
                                ),
                                onPressed: _busy ? null : _turnOffBiometric,
                                child: const Text('Turn off biometric sign-in'),
                              ),
                            ],
                          ],
                        ),
                      ),
                    ),
                    if (_offerBiometric &&
                        !_biometric.available &&
                        _biometric.reason != null) ...[
                      const SizedBox(height: 12),
                      Text(
                        'Biometric sign-in: ${_biometric.reason}',
                        textAlign: TextAlign.center,
                        style: text.bodySmall?.copyWith(
                          color: CodifiColors.textMuted,
                        ),
                      ),
                    ],
                    if (widget.auth.notice != null) ...[
                      const SizedBox(height: 16),
                      Text(
                        widget.auth.notice!,
                        textAlign: TextAlign.center,
                        style: text.bodySmall?.copyWith(
                          color: CodifiColors.textMuted,
                        ),
                      ),
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

/// An opaque, flat sign-in card with a quiet outline.
class _SignInCard extends StatelessWidget {
  const _SignInCard({required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) => DecoratedBox(
    decoration: BoxDecoration(
      color: CodifiColors.surface,
      border: Border.all(color: CodifiColors.outline),
      borderRadius: BorderRadius.circular(22),
    ),
    child: Padding(padding: const EdgeInsets.all(22), child: child),
  );
}

import 'package:flutter/material.dart';

import '../services/auth_service.dart';
import '../theme/codifi_theme.dart';
import '../widgets/codifi_widgets.dart';

/// The tool's own sign-in, in the CodiFi look: who is using the tool, before
/// any app is tested. See [AuthService] for what is checked.
class SignInScreen extends StatefulWidget {
  const SignInScreen({required this.auth, required this.onSignedIn, super.key});

  final AuthService auth;
  final ValueChanged<AuthUser> onSignedIn;

  @override
  State<SignInScreen> createState() => _SignInScreenState();
}

class _SignInScreenState extends State<SignInScreen> {
  final _userId = TextEditingController();
  final _password = TextEditingController();
  bool _hidden = true;
  bool _busy = false;
  String? _error;

  @override
  void dispose() {
    _userId.dispose();
    _password.dispose();
    super.dispose();
  }

  Future<void> _signIn() async {
    FocusScope.of(context).unfocus();
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final user = await widget.auth.signIn(
        userId: _userId.text,
        password: _password.text,
      );
      _password.clear();
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
                          ],
                        ),
                      ),
                    ),
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

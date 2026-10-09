import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// A text field for a UAT test credential.
///
/// Secret values ([obscure] true, used for the MPIN) are masked at all times and
/// have no reveal toggle: nothing in this tool ever renders an MPIN in clear
/// text. Autofill, autocorrect and suggestions are switched off so the value is
/// not offered to the platform's autofill or dictionary services.
class CredentialField extends StatelessWidget {
  const CredentialField({
    required this.controller,
    required this.label,
    super.key,
    this.hint,
    this.helper,
    this.obscure = false,
    this.digitsOnly = false,
    this.maxLength,
    this.enabled = true,
    this.textInputAction = TextInputAction.next,
    this.validator,
    this.onChanged,
  });

  final TextEditingController controller;
  final String label;
  final String? hint;
  final String? helper;
  final bool obscure;
  final bool digitsOnly;
  final int? maxLength;
  final bool enabled;
  final TextInputAction textInputAction;
  final String? Function(String?)? validator;
  final ValueChanged<String>? onChanged;

  @override
  Widget build(BuildContext context) {
    return TextFormField(
      controller: controller,
      enabled: enabled,
      obscureText: obscure,
      obscuringCharacter: '●',
      autocorrect: false,
      enableSuggestions: false,
      autofillHints: null,
      keyboardType: digitsOnly ? TextInputType.number : TextInputType.text,
      textInputAction: textInputAction,
      maxLength: maxLength,
      inputFormatters: digitsOnly
          ? <TextInputFormatter>[FilteringTextInputFormatter.digitsOnly]
          : null,
      validator: validator,
      onChanged: onChanged,
      decoration: InputDecoration(
        labelText: label,
        hintText: hint,
        helperText: helper,
        helperMaxLines: 3,
        border: const OutlineInputBorder(),
        counterText: obscure ? '' : null,
      ),
    );
  }
}

import 'package:flutter/material.dart';

import '../models/test_run.dart';
import '../services/report_upload_service.dart';

/// Sends a saved run's report zip to the admin server: a full-width button on
/// the report and finished-run screens, or an icon in a history row
/// ([compact]). Shows sending, sent and why it failed.
class SendToAdminButton extends StatefulWidget {
  const SendToAdminButton({
    required this.run,
    this.uploader,
    this.compact = false,
    super.key,
  });

  /// The run to send; null while the run is still being saved (disabled).
  final TestRun? run;

  /// A default [ReportUploadService] when not given.
  final ReportUploadService? uploader;
  final bool compact;

  @override
  State<SendToAdminButton> createState() => _SendToAdminButtonState();
}

class _SendToAdminButtonState extends State<SendToAdminButton> {
  late final ReportUploadService _uploader =
      widget.uploader ?? ReportUploadService();
  bool _sending = false;
  ReportUploadResult? _sent;

  Future<void> _send() async {
    final run = widget.run;
    if (run == null || _sending) return;
    setState(() => _sending = true);
    final messenger = ScaffoldMessenger.of(context);
    try {
      final sent = await _uploader.upload(run);
      if (mounted) setState(() => _sent = sent);
      messenger.showSnackBar(
        SnackBar(
          content: Text(
            'Report sent to admin (run ${sent.runId ?? run.runId}).',
          ),
        ),
      );
    } on ReportUploadException catch (e) {
      messenger.showSnackBar(SnackBar(content: Text(e.message)));
    } catch (_) {
      messenger.showSnackBar(
        const SnackBar(
          content: Text('The report could not be sent. Try again.'),
        ),
      );
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final enabled = widget.run != null && !_sending;
    final icon = _sending
        ? const SizedBox.square(
            dimension: 18,
            child: CircularProgressIndicator(strokeWidth: 2),
          )
        : Icon(
            _sent == null
                ? Icons.cloud_upload_outlined
                : Icons.cloud_done_outlined,
            color: _sent == null ? null : const Color(0xFF3DD68C),
          );
    if (widget.compact) {
      return IconButton(
        key: const ValueKey<String>('send_to_admin'),
        onPressed: enabled ? _send : null,
        icon: icon,
        tooltip: _sent == null ? 'Send to admin' : 'Sent to admin: send again',
      );
    }
    final sent = _sent;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        FilledButton.icon(
          key: const ValueKey<String>('send_to_admin'),
          onPressed: enabled ? _send : null,
          icon: icon,
          label: Text(
            _sending
                ? 'Sending to admin…'
                : sent == null
                ? 'Send to admin'
                : 'Send to admin again',
          ),
        ),
        if (sent != null)
          Padding(
            padding: const EdgeInsets.only(top: 6),
            child: Text(
              'Sent to admin · run ${sent.runId ?? widget.run!.runId}'
              '${sent.url == null ? '' : ' · ${sent.url}'}',
              style: Theme.of(
                context,
              ).textTheme.bodySmall?.copyWith(color: const Color(0xFF3DD68C)),
            ),
          ),
      ],
    );
  }
}

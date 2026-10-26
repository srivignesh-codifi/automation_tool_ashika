import 'package:flutter/material.dart';

/// A description kept to [maxLines] lines. A longer one is cut with an ellipsis and gets a "Read more" link that shows
/// the whole text in a dialog, so a screen full of explanations stays short. Without a [style] it takes the one it is
/// placed in (a list tile's subtitle, for instance).
class ReadMoreText extends StatelessWidget {
  const ReadMoreText({
    required this.title,
    required this.text,
    this.style,
    this.maxLines = 1,
    super.key,
  });

  /// The dialog's title.
  final String title;
  final String text;
  final TextStyle? style;
  final int maxLines;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final textStyle = style ?? DefaultTextStyle.of(context).style;
        final painter = TextPainter(
          text: TextSpan(text: text, style: textStyle),
          maxLines: maxLines,
          textDirection: Directionality.of(context),
          textScaler: MediaQuery.textScalerOf(context),
        )..layout(maxWidth: constraints.maxWidth);
        final overflows = painter.didExceedMaxLines;
        painter.dispose();
        if (!overflows) return Text(text, style: style);
        return Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              text,
              style: style,
              maxLines: maxLines,
              overflow: TextOverflow.ellipsis,
            ),
            InkWell(
              key: const ValueKey<String>('read_more_link'),
              onTap: () => showDialog<void>(
                context: context,
                builder: (dialogContext) => AlertDialog(
                  title: Text(title),
                  content: SingleChildScrollView(child: Text(text)),
                  actions: [
                    TextButton(
                      onPressed: () => Navigator.of(dialogContext).pop(),
                      child: const Text('Close'),
                    ),
                  ],
                ),
              ),
              child: Padding(
                padding: const EdgeInsets.symmetric(vertical: 4),
                child: Text(
                  'Read more',
                  style: textStyle.copyWith(
                    color: Theme.of(context).colorScheme.primary,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
            ),
          ],
        );
      },
    );
  }
}

package com.vista.pdg.quiz.material;

import org.springframework.stereotype.Component;

/** Markdown y texto plano: se conservan tal cual, con espacios normalizados. */
@Component
class PlainTextExtractor implements TextExtractor {
  @Override
  public boolean supports(MaterialFormat format) {
    return format == MaterialFormat.MARKDOWN || format == MaterialFormat.TEXT;
  }

  @Override
  public ExtractedText extract(byte[] bytes, String name) {
    if (TextCleanup.looksBinary(bytes)) {
      throw new MaterialException(name + " no es un archivo de texto.");
    }
    String text = TextCleanup.tidy(TextCleanup.utf8(bytes));
    String title = TextCleanup.firstHeading(text);
    return new ExtractedText(
        title == null ? name : title,
        text,
        1,
        name.toLowerCase(java.util.Locale.ROOT).endsWith(".txt")
            ? MaterialFormat.TEXT
            : MaterialFormat.MARKDOWN);
  }
}

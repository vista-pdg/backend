package com.vista.pdg.quiz.material;

import java.util.Locale;
import java.util.Optional;

/** Formatos que VISTA sabe leer como material de referencia (HU-36). */
public enum MaterialFormat {
  SITE("Sitio"),
  HTML("Página web"),
  PDF("PDF"),
  DOCX("DOCX"),
  MARKDOWN("Markdown"),
  TEXT("Texto"),
  NOTEBOOK("Cuaderno");

  private final String label;

  MaterialFormat(String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }

  /** Por la extensión del nombre o de la ruta del enlace. */
  public static Optional<MaterialFormat> byName(String name) {
    if (name == null) return Optional.empty();
    String lower = name.toLowerCase(Locale.ROOT);
    int query = lower.indexOf('?');
    if (query >= 0) lower = lower.substring(0, query);
    if (lower.endsWith(".pdf")) return Optional.of(PDF);
    if (lower.endsWith(".docx")) return Optional.of(DOCX);
    if (lower.endsWith(".md") || lower.endsWith(".markdown")) return Optional.of(MARKDOWN);
    if (lower.endsWith(".txt")) return Optional.of(TEXT);
    if (lower.endsWith(".ipynb")) return Optional.of(NOTEBOOK);
    if (lower.endsWith(".html") || lower.endsWith(".htm")) return Optional.of(HTML);
    return Optional.empty();
  }

  /** Por el tipo de contenido que declara el servidor del enlace. */
  public static Optional<MaterialFormat> byContentType(String contentType) {
    if (contentType == null) return Optional.empty();
    String type = contentType.toLowerCase(Locale.ROOT).split(";")[0].strip();
    return switch (type) {
      case "text/html", "application/xhtml+xml" -> Optional.of(HTML);
      case "application/pdf" -> Optional.of(PDF);
      case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ->
          Optional.of(DOCX);
      case "text/markdown", "text/x-markdown" -> Optional.of(MARKDOWN);
      case "text/plain" -> Optional.of(TEXT);
      case "application/x-ipynb+json" -> Optional.of(NOTEBOOK);
      default -> Optional.empty();
    };
  }

  /** Por la firma de los primeros bytes, para archivos sin extensión fiable. */
  public static Optional<MaterialFormat> bySignature(byte[] bytes) {
    if (bytes.length >= 4
        && bytes[0] == '%'
        && bytes[1] == 'P'
        && bytes[2] == 'D'
        && bytes[3] == 'F') return Optional.of(PDF);
    return Optional.empty();
  }
}

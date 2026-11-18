package com.vista.pdg.quiz.material;

import java.nio.charset.StandardCharsets;

/** Normalización común del texto extraído: espacios, líneas vacías y BOM. */
final class TextCleanup {
  private TextCleanup() {}

  static String utf8(byte[] bytes) {
    String text = new String(bytes, StandardCharsets.UTF_8);
    return text.startsWith("﻿") ? text.substring(1) : text;
  }

  static String tidy(String text) {
    if (text == null) return "";
    String unified = text.replace("\r\n", "\n").replace('\r', '\n').replace(' ', ' ');
    StringBuilder out = new StringBuilder(unified.length());
    int blank = 0;
    for (String line : unified.split("\n", -1)) {
      String clean = line.replaceAll("[\\t ]+", " ").strip();
      if (clean.isEmpty()) {
        if (++blank == 1 && !out.isEmpty()) out.append('\n');
        continue;
      }
      blank = 0;
      out.append(clean).append('\n');
    }
    return out.toString().strip();
  }

  /** Un archivo de texto con bytes nulos es binario con otra extensión. */
  static boolean looksBinary(byte[] bytes) {
    int limit = Math.min(bytes.length, 4096);
    for (int i = 0; i < limit; i++) if (bytes[i] == 0) return true;
    return false;
  }

  static String firstHeading(String markdown) {
    for (String line : markdown.split("\n")) {
      String clean = line.strip();
      if (clean.startsWith("#")) {
        String title = clean.replaceFirst("^#+", "").strip();
        if (!title.isEmpty()) return title;
      }
    }
    return null;
  }
}

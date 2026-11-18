package com.vista.pdg.quiz.material;

/** Texto leído de una fuente: título sugerido, contenido, páginas leídas y formato. */
public record ExtractedText(String title, String text, int pages, MaterialFormat format) {}

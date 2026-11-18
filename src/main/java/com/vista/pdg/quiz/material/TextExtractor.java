package com.vista.pdg.quiz.material;

/** Estrategia de lectura por formato; un formato nuevo es un componente nuevo, no un {@code if}. */
public interface TextExtractor {
  boolean supports(MaterialFormat format);

  /**
   * @throws MaterialException si el contenido no se puede leer o no tiene texto
   */
  ExtractedText extract(byte[] bytes, String name);
}

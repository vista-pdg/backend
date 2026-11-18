package com.vista.pdg.quiz.material;

import java.net.URI;

/** Descarga de un enlace de material. La implementación real aplica la guardia de red. */
public interface MaterialFetcher {
  record Fetched(URI uri, String contentType, byte[] body) {}

  /**
   * @throws MaterialException si el enlace no es público, no responde o supera {@code maxBytes}
   */
  Fetched fetch(URI uri, long maxBytes);
}

package com.vista.pdg.assistant.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Límite de tasa por usuario con ventana deslizante de 60 s, en memoria.
 *
 * <p>En memoria y por instancia a propósito: protege de ráfagas, y perderlo en un reinicio cuesta
 * como mucho una ráfaga. Persistirlo añadiría una escritura por mensaje sin ganancia para el
 * presupuesto, que lo cuida el contador diario. Si algún día hubiera varias instancias, este es el
 * componente que se movería a la base o a una caché compartida.
 */
@Component
public class RateLimiter {

  private static final Duration WINDOW = Duration.ofSeconds(60);

  private final Clock clock;
  private final int perMinute;
  private final Map<Long, Deque<Instant>> windows = new ConcurrentHashMap<>();

  public RateLimiter(Clock clock, @Value("${assistant.rate.per-minute:5}") int perMinute) {
    this.clock = clock;
    this.perMinute = perMinute;
  }

  public int perMinute() {
    return perMinute;
  }

  /**
   * Intenta consumir un cupo. Vacío si se concedió; si no, los segundos que faltan para que la
   * petición más antigua salga de la ventana, que es exactamente lo que va en {@code Retry-After}.
   */
  public OptionalLong tryAcquire(Long userId) {
    Instant now = clock.instant();
    Deque<Instant> window = windows.computeIfAbsent(userId, k -> new ArrayDeque<>());
    synchronized (window) {
      Instant cutoff = now.minus(WINDOW);
      while (!window.isEmpty() && !window.peekFirst().isAfter(cutoff)) window.pollFirst();
      if (window.size() < perMinute) {
        window.addLast(now);
        return OptionalLong.empty();
      }
      // Hacia arriba: un Retry-After que se quede corto haría que el cliente reintentara un
      // instante antes de tiempo y recibiera otro 429.
      long millis = Duration.between(now, window.peekFirst().plus(WINDOW)).toMillis();
      return OptionalLong.of(Math.max(1, (millis + 999) / 1000));
    }
  }

  /** Sólo para pruebas y para el reinicio del contexto. */
  public void reset() {
    windows.clear();
  }
}

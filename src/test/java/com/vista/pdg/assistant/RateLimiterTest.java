package com.vista.pdg.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import com.vista.pdg.assistant.service.RateLimiter;
import com.vista.pdg.testsupport.MutableClockConfig.MutableClock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.OptionalLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * HU-17 · CA-3 (límite de tasa), unitaria y con reloj propio: la ventana de 60 s se recorre
 * avanzando el reloj, no esperando.
 */
class RateLimiterTest {

  private MutableClock clock;
  private RateLimiter limiter;

  @BeforeEach
  void setUp() {
    clock = new MutableClock(ZoneId.of("America/Bogota"));
    limiter = new RateLimiter(clock, 5);
  }

  @Test
  @DisplayName("admite 5 mensajes en el minuto y rechaza el 6.º")
  void cincoPasanElSextoNo() {
    for (int i = 1; i <= 5; i++) {
      assertThat(limiter.tryAcquire(1L)).as("mensaje " + i).isEmpty();
    }
    assertThat(limiter.tryAcquire(1L)).isPresent();
  }

  /**
   * Escenario Gherkin: 5 mensajes en los últimos 30 s y un 6.º dentro del mismo minuto. Retry-After
   * debe ser lo que falta para que el primero salga de la ventana: 60 − 30 = 30 s.
   */
  @Test
  @DisplayName("Retry-After son los segundos hasta que la petición más antigua sale de la ventana")
  void retryAfterEsElRestoDeLaVentana() {
    limiter.tryAcquire(1L);
    clock.advance(Duration.ofSeconds(30));
    for (int i = 0; i < 4; i++) limiter.tryAcquire(1L);

    OptionalLong wait = limiter.tryAcquire(1L);
    assertThat(wait).isPresent();
    assertThat(wait.getAsLong()).isEqualTo(30);
  }

  @Test
  @DisplayName("pasado el minuto desde el primero, vuelve a haber cupo")
  void laVentanaDesliza() {
    for (int i = 0; i < 5; i++) limiter.tryAcquire(1L);
    assertThat(limiter.tryAcquire(1L)).isPresent();

    clock.advance(Duration.ofSeconds(61));
    assertThat(limiter.tryAcquire(1L)).as("el primero ya salió de la ventana").isEmpty();
  }

  @Test
  @DisplayName("la ventana es deslizante, no fija: libera de uno en uno, no todos a la vez")
  void liberaDeUnoEnUno() {
    limiter.tryAcquire(1L);
    clock.advance(Duration.ofSeconds(20));
    for (int i = 0; i < 4; i++) limiter.tryAcquire(1L);
    assertThat(limiter.tryAcquire(1L)).isPresent();

    // A los 61 s del primero sólo ha salido el primero: cabe exactamente uno más.
    clock.advance(Duration.ofSeconds(41));
    assertThat(limiter.tryAcquire(1L)).isEmpty();
    assertThat(limiter.tryAcquire(1L)).isPresent();
  }

  @Test
  @DisplayName("el límite es por usuario: la ráfaga de uno no afecta a otro")
  void porUsuario() {
    for (int i = 0; i < 5; i++) limiter.tryAcquire(1L);
    assertThat(limiter.tryAcquire(1L)).isPresent();
    assertThat(limiter.tryAcquire(2L)).isEmpty();
  }

  @Test
  @DisplayName("Retry-After nunca es 0: al menos 1 s para que el cliente no reintente en bucle")
  void retryAfterMinimoUno() {
    for (int i = 0; i < 5; i++) limiter.tryAcquire(1L);
    clock.advance(Duration.ofMillis(59_900));
    OptionalLong wait = limiter.tryAcquire(1L);
    assertThat(wait).isPresent();
    assertThat(wait.getAsLong()).isGreaterThanOrEqualTo(1);
  }

  @Test
  @DisplayName("expone el límite configurado para el contrato con el frontend")
  void exponePerMinute() {
    assertThat(limiter.perMinute()).isEqualTo(5);
    assertThat(new RateLimiter(clock, 12).perMinute()).isEqualTo(12);
  }
}

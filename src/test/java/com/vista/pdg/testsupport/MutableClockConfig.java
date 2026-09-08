package com.vista.pdg.testsupport;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Reloj desplazable para las pruebas de la cuota diaria.
 *
 * <p>Sustituye al {@code Clock} de la aplicación por uno que avanza cuando la prueba lo pide. Es lo
 * que permite comprobar el reinicio de medianoche (CA-5 de la HU-17) sin esperar a medianoche ni
 * manipular la base: la prueba agota la cuota, avanza un día y comprueba que vuelve a estar
 * disponible. Por defecto sigue la hora real en {@code America/Bogota}, así que el resto de la
 * suite no nota la diferencia.
 */
@TestConfiguration
public class MutableClockConfig {

  /** Único por JVM: los tests lo ajustan y lo devuelven a cero en {@code @AfterEach}. */
  public static final MutableClock CLOCK = new MutableClock(ZoneId.of("America/Bogota"));

  @Bean
  @Primary
  public Clock testClock() {
    return CLOCK;
  }

  public static final class MutableClock extends Clock {
    private final ZoneId zone;
    private volatile Duration offset = Duration.ZERO;

    public MutableClock(ZoneId zone) {
      this.zone = zone;
    }

    public void advance(Duration by) {
      offset = offset.plus(by);
    }

    public void reset() {
      offset = Duration.ZERO;
    }

    @Override
    public ZoneId getZone() {
      return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      MutableClock c = new MutableClock(zone);
      c.offset = offset;
      return c;
    }

    @Override
    public Instant instant() {
      return Instant.now().plus(offset);
    }
  }
}

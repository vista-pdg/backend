package com.vista.pdg.assistant.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Reloj de la aplicación en la zona horaria del curso.
 *
 * <p>La ventana diaria de la cuota se reinicia a medianoche en {@code America/Bogota}, no en UTC ni
 * en la zona del servidor. Inyectar el reloj en vez de llamar a {@code Instant.now()} es lo que
 * permite probar el reinicio sin esperar a medianoche: las pruebas sustituyen este bean por uno
 * fijo o desplazable.
 */
@Configuration
public class ClockConfig {

  @Bean
  public Clock clock(@Value("${assistant.timezone:America/Bogota}") String zone) {
    return Clock.system(ZoneId.of(zone));
  }
}

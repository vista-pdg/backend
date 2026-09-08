package com.vista.pdg.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vista.pdg.assistant.session.AssistantSessionService;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * HU-32 · CA-8: sin Redis el asistente sigue funcionando. Ningún fallo de la caché puede escapar
 * del servicio de sesión, porque el estudiante puede vivir sin memoria pero no sin generar.
 */
class AssistantSessionDegradationTest {

  /** Un Redis que siempre falla, que es como se comporta uno caído desde el cliente. */
  private final StringRedisTemplate broken =
      new StringRedisTemplate() {
        @Override
        public ValueOperations<String, String> opsForValue() {
          throw new RedisConnectionFailureException("Redis no disponible");
        }

        @Override
        public Boolean delete(String key) {
          throw new RedisConnectionFailureException("Redis no disponible");
        }

        @Override
        public Long getExpire(String key, TimeUnit timeUnit) {
          throw new RedisConnectionFailureException("Redis no disponible");
        }
      };

  private final AssistantSessionService sessions =
      new AssistantSessionService(broken, new ObjectMapper(), Duration.ofMinutes(30), 6);

  @Test
  @DisplayName("leer sin Redis devuelve una sesión vacía, no una excepción")
  void loadDegradesToEmpty() {
    assertThat(sessions.load(1L).hasStructure()).isFalse();
    assertThat(sessions.contextFor(1L).isEmpty()).isTrue();
  }

  @Test
  @DisplayName("guardar y borrar sin Redis devuelven falso, sin propagar el fallo")
  void writesReportFailureWithoutThrowing() {
    assertThat(sessions.remember(1L, "{}", "tree", "hola", "ok")).isFalse();
    assertThat(sessions.clear(1L)).isFalse();
  }

  @Test
  @DisplayName("el estado distingue «no disponible» de «sin sesión»")
  void statusSaysUnavailable() {
    assertThat(sessions.status(1L).available()).isFalse();
    assertThat(sessions.status(1L).active()).isFalse();
  }
}

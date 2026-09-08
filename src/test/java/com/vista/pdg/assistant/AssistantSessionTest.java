package com.vista.pdg.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vista.pdg.assistant.session.AssistantSession;
import com.vista.pdg.assistant.session.AssistantSessionService;
import com.vista.pdg.assistant.session.SessionStatus;
import com.vista.pdg.auth.IntegrationTestSupport;
import com.vista.pdg.auth.dto.AuthResponse;
import com.vista.pdg.auth.repository.UserRepository;
import com.vista.pdg.service.llm.def.ConversationContext;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * HU-32: la sesión de trabajo contra un Redis real (Testcontainers). Lo que se comprueba aquí es
 * comportamiento de la caché —caducidad, renovación, aislamiento, borrado—, que con un doble en
 * memoria se daría por bueno sin probarlo.
 */
class AssistantSessionTest extends IntegrationTestSupport {

  private static final String TREE =
      "{\"type\":\"tree\",\"subtype\":\"bst\",\"operations\":[{\"op\":\"insert\",\"values\":[1,2,3,5,6]}]}";

  @Autowired private AssistantSessionService sessions;
  @Autowired private StringRedisTemplate redis;
  @Autowired private ObjectMapper mapper;
  @Autowired private UserRepository users;

  private Long userId(AuthResponse session) {
    return users.findByEmail(session.email()).orElseThrow().getId();
  }

  @Test
  @DisplayName("guardar deja el contrato vigente y los turnos, y load lo devuelve como contexto")
  void remembersContractAndTurns() throws Exception {
    Long id = userId(registerStudent("memoria.basica"));

    assertThat(sessions.load(id).hasStructure()).isFalse();
    assertThat(sessions.contextFor(id).isEmpty()).isTrue();

    assertThat(sessions.remember(id, TREE, "tree", "árbol con 1,2,3,5,6", "tree/bst con 5 nodos"))
        .isTrue();

    AssistantSession stored = sessions.load(id);
    assertThat(stored.hasStructure()).isTrue();
    assertThat(stored.contract()).isEqualTo(TREE);
    assertThat(stored.structureType()).isEqualTo("tree");
    assertThat(stored.turns()).hasSize(2);
    assertThat(stored.asLines())
        .containsExactly("usuario: árbol con 1,2,3,5,6", "asistente: tree/bst con 5 nodos");

    ConversationContext ctx = sessions.contextFor(id);
    assertThat(ctx.currentContract()).isEqualTo(TREE);
    assertThat(ctx.asPromptBlock()).contains("CURRENT STRUCTURE").contains("NEW INSTRUCTION");
  }

  @Test
  @DisplayName("la sesión no guarda correo ni nombre: sólo estructura y turnos")
  void storesNoIdentity() throws Exception {
    AuthResponse s = registerStudent("memoria.privacidad");
    Long id = userId(s);
    sessions.remember(id, TREE, "tree", "un árbol", "tree/bst con 5 nodos");

    String raw = redis.opsForValue().get("assistant:session:" + id);
    assertThat(raw).isNotNull();
    assertThat(raw.toLowerCase()).doesNotContain(s.email().toLowerCase()).doesNotContain("@");
  }

  @Test
  @DisplayName("CA-5: cada mensaje renueva el TTL")
  void ttlIsRenewedOnEveryMessage() throws Exception {
    Long id = userId(registerStudent("memoria.ttl"));
    sessions.remember(id, TREE, "tree", "uno", "ok");
    long first = redis.getExpire("assistant:session:" + id, TimeUnit.SECONDS);
    assertThat(first).isGreaterThan(0).isLessThanOrEqualTo(sessions.ttl().toSeconds());

    // Se consume un poco de TTL y el mensaje siguiente vuelve a dejarlo al máximo.
    redis.expire("assistant:session:" + id, Duration.ofSeconds(60));
    assertThat(redis.getExpire("assistant:session:" + id, TimeUnit.SECONDS))
        .isLessThanOrEqualTo(60);

    sessions.remember(id, TREE, "tree", "dos", "ok");
    assertThat(redis.getExpire("assistant:session:" + id, TimeUnit.SECONDS))
        .isGreaterThan(60)
        .isLessThanOrEqualTo(sessions.ttl().toSeconds());
  }

  @Test
  @DisplayName("CA-4: pasado el TTL la sesión desaparece y no se usa la estructura caducada")
  void sessionExpires() throws Exception {
    Long id = userId(registerStudent("memoria.caduca"));
    // Mismo Redis, TTL de un segundo: es la caducidad de verdad, no una simulación.
    AssistantSessionService shortLived =
        new AssistantSessionService(redis, mapper, Duration.ofSeconds(1), 6);
    shortLived.remember(id, TREE, "tree", "árbol", "ok");
    assertThat(shortLived.load(id).hasStructure()).isTrue();

    Thread.sleep(1_400);

    assertThat(shortLived.load(id).hasStructure()).isFalse();
    assertThat(shortLived.contextFor(id).isEmpty()).isTrue();
    assertThat(shortLived.status(id).active()).isFalse();
  }

  @Test
  @DisplayName("CA-6: la sesión de un estudiante no se ve desde la de otro")
  void sessionsAreIsolated() throws Exception {
    Long mine = userId(registerStudent("memoria.mia"));
    Long other = userId(registerStudent("memoria.otra"));
    sessions.remember(other, TREE, "tree", "árbol del otro", "ok");

    assertThat(sessions.contextFor(mine).isEmpty()).isTrue();
    assertThat(sessions.load(mine).hasStructure()).isFalse();
    assertThat(sessions.load(other).contract()).isEqualTo(TREE);
  }

  @Test
  @DisplayName("CA-7: borrar deja la sesión vacía; borrar dos veces no falla")
  void clearRemovesTheSession() throws Exception {
    Long id = userId(registerStudent("memoria.borrar"));
    sessions.remember(id, TREE, "tree", "árbol", "ok");
    assertThat(sessions.status(id).active()).isTrue();

    assertThat(sessions.clear(id)).isTrue();
    assertThat(sessions.load(id).hasStructure()).isFalse();
    assertThat(sessions.clear(id)).isTrue();
  }

  @Test
  @DisplayName("sólo se conservan los últimos turnos configurados")
  void turnsAreCapped() throws Exception {
    Long id = userId(registerStudent("memoria.turnos"));
    for (int i = 1; i <= 5; i++)
      sessions.remember(id, TREE, "tree", "mensaje " + i, "respuesta " + i);

    AssistantSession stored = sessions.load(id);
    assertThat(stored.turns()).hasSize(6);
    assertThat(stored.asLines().getFirst()).isEqualTo("usuario: mensaje 3");
    assertThat(stored.asLines().getLast()).isEqualTo("asistente: respuesta 5");
  }

  @Test
  @DisplayName("el estado informa si hay estructura y cuánto le queda")
  void statusReportsRemainingTime() throws Exception {
    Long id = userId(registerStudent("memoria.estado"));
    assertThat(sessions.status(id)).isEqualTo(SessionStatus.idle());

    sessions.remember(id, TREE, "tree", "árbol", "ok");
    SessionStatus status = sessions.status(id);
    assertThat(status.available()).isTrue();
    assertThat(status.active()).isTrue();
    assertThat(status.structureType()).isEqualTo("tree");
    assertThat(status.secondsRemaining())
        .isGreaterThan(sessions.ttl().toSeconds() - 30)
        .isLessThanOrEqualTo(sessions.ttl().toSeconds());
  }
}

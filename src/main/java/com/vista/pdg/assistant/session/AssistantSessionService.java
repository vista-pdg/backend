package com.vista.pdg.assistant.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vista.pdg.service.llm.def.ConversationContext;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Memoria conversacional del asistente (HU-32).
 *
 * <p>Una clave por cuenta con TTL: cada mensaje reescribe la sesión y renueva la caducidad, así que
 * el estudiante que trabaja no la pierde y el que se va la pierde sola. Se guarda como JSON con
 * {@link StringRedisTemplate} en lugar de serialización de objetos Java: lo que hay en Redis se
 * puede leer con {@code redis-cli} y un cambio del record no invalida lo ya escrito.
 *
 * <p><b>Ningún fallo de Redis se propaga.</b> La memoria es una comodidad, no un requisito: si la
 * caché no responde, el asistente genera igual que antes de esta historia y el cliente se entera
 * por {@link SessionStatus#available()}. Por eso todas las operaciones atrapan y registran.
 */
@Service
@Slf4j
public class AssistantSessionService {

  static final String KEY_PREFIX = "assistant:session:";

  private final StringRedisTemplate redis;
  private final ObjectMapper mapper;
  private final Duration ttl;
  private final int maxTurns;

  public AssistantSessionService(
      StringRedisTemplate redis,
      ObjectMapper mapper,
      @Value("${assistant.session.ttl:PT30M}") Duration ttl,
      @Value("${assistant.session.max-turns:6}") int maxTurns) {
    this.redis = redis;
    this.mapper = mapper;
    this.ttl = ttl;
    this.maxTurns = Math.max(2, maxTurns);
  }

  static String key(Long userId) {
    return KEY_PREFIX + userId;
  }

  /** La sesión vigente, o una vacía si no hay o si Redis no responde. */
  public AssistantSession load(Long userId) {
    try {
      String raw = redis.opsForValue().get(key(userId));
      if (raw == null || raw.isBlank()) return AssistantSession.empty();
      return mapper.readValue(raw, AssistantSession.class);
    } catch (Exception e) {
      log.warn("No se pudo leer la sesión del asistente ({}): {}", userId, e.getMessage());
      return AssistantSession.empty();
    }
  }

  /** Contexto para el modelo. Vacío si no hay estructura vigente: no se inventa memoria. */
  public ConversationContext contextFor(Long userId) {
    AssistantSession session = load(userId);
    if (!session.hasStructure()) return ConversationContext.empty();
    return new ConversationContext(session.contract(), session.asLines());
  }

  /**
   * Guarda el resultado de un mensaje y renueva el TTL. Devuelve {@code false} si Redis falló, que
   * es lo que el controlador convierte en «memoria no disponible» para el cliente.
   */
  public boolean remember(
      Long userId,
      String contractJson,
      String structureType,
      String userText,
      String assistantText) {
    try {
      AssistantSession previous = load(userId);
      List<AssistantSession.Turn> turns = new ArrayList<>(previous.turns());
      turns.add(new AssistantSession.Turn("usuario", userText));
      turns.add(new AssistantSession.Turn("asistente", assistantText));
      if (turns.size() > maxTurns)
        turns = new ArrayList<>(turns.subList(turns.size() - maxTurns, turns.size()));

      AssistantSession session =
          new AssistantSession(contractJson, structureType, turns, Instant.now().toString());
      redis.opsForValue().set(key(userId), mapper.writeValueAsString(session), ttl);
      return true;
    } catch (Exception e) {
      log.warn("No se pudo guardar la sesión del asistente ({}): {}", userId, e.getMessage());
      return false;
    }
  }

  /** Borrado explícito («Limpiar»). Devuelve {@code false} sólo si Redis falló. */
  public boolean clear(Long userId) {
    try {
      redis.delete(key(userId));
      return true;
    } catch (Exception e) {
      log.warn("No se pudo borrar la sesión del asistente ({}): {}", userId, e.getMessage());
      return false;
    }
  }

  /** Estado para el cliente, con los segundos que le quedan a la sesión. */
  public SessionStatus status(Long userId) {
    try {
      String raw = redis.opsForValue().get(key(userId));
      if (raw == null || raw.isBlank()) return SessionStatus.idle();
      AssistantSession session = mapper.readValue(raw, AssistantSession.class);
      Long seconds = redis.getExpire(key(userId), TimeUnit.SECONDS);
      long remaining = seconds == null || seconds < 0 ? ttl.toSeconds() : seconds;
      return new SessionStatus(true, session.hasStructure(), session.structureType(), remaining);
    } catch (Exception e) {
      log.warn("No se pudo consultar la sesión del asistente ({}): {}", userId, e.getMessage());
      return SessionStatus.unavailable();
    }
  }

  public Duration ttl() {
    return ttl;
  }
}

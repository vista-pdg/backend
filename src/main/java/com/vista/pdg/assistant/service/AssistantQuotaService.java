package com.vista.pdg.assistant.service;

import com.vista.pdg.assistant.dto.QuotaStatus;
import com.vista.pdg.assistant.entity.AssistantUsage;
import com.vista.pdg.assistant.repository.AssistantUsageRepository;
import com.vista.pdg.auth.entity.User;
import com.vista.pdg.exception.DailyQuotaExceededException;
import com.vista.pdg.exception.RateLimitedException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.OptionalLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cuota diaria persistida + límite de tasa, delante del modelo generativo.
 *
 * <p>{@link #reserve(User)} se llama <b>antes</b> de invocar al modelo y consume el cupo pase lo
 * que pase después: ese es el momento en que se factura. Un rechazo aquí nunca llega al adaptador,
 * que es lo que CA-2 exige («no se genera ninguna llamada facturable»).
 */
@Service
@Slf4j
public class AssistantQuotaService {

  private final AssistantUsageRepository usageRepository;
  private final RateLimiter rateLimiter;
  private final Clock clock;
  private final int defaultDailyQuota;
  private final double warningRatio;

  public AssistantQuotaService(
      AssistantUsageRepository usageRepository,
      RateLimiter rateLimiter,
      Clock clock,
      @Value("${assistant.quota.daily-default:40}") int defaultDailyQuota,
      @Value("${assistant.quota.warning-ratio:0.2}") double warningRatio) {
    this.usageRepository = usageRepository;
    this.rateLimiter = rateLimiter;
    this.clock = clock;
    this.defaultDailyQuota = defaultDailyQuota;
    this.warningRatio = warningRatio;
  }

  public int defaultDailyQuota() {
    return defaultDailyQuota;
  }

  /**
   * Cuota que rige para el usuario: la de su curso si la tiene configurada, si no la por defecto.
   */
  public int limitFor(User user) {
    if (user.getCourse() != null && user.getCourse().getDailyQuota() != null) {
      return user.getCourse().getDailyQuota();
    }
    return defaultDailyQuota;
  }

  @Transactional(readOnly = true)
  public QuotaStatus status(User user) {
    int limit = limitFor(user);
    int used =
        usageRepository
            .findByUserIdAndUsageDate(user.getId(), today())
            .map(AssistantUsage::getCount)
            .orElse(0);
    return build(limit, used);
  }

  /**
   * Consume un mensaje. Primero la ventana de un minuto (barata, en memoria, frena las ráfagas
   * antes de tocar la base); después la reserva diaria, atómica en una sola sentencia. Corre en su
   * propia transacción para que la reserva quede confirmada aunque la generación falle después: la
   * llamada al modelo ya se habrá hecho, y eso es lo que se cuenta.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public QuotaStatus reserve(User user) {
    int limit = limitFor(user);
    LocalDate today = today();

    // Primero el estado diario: quien ya agotó su cuota recibe siempre el mensaje de cuota (CA-2),
    // aunque además esté enviando en ráfaga. Es una lectura; no consume cupo de tasa.
    int usedSoFar =
        usageRepository
            .findByUserIdAndUsageDate(user.getId(), today)
            .map(AssistantUsage::getCount)
            .orElse(0);
    if (usedSoFar >= limit) {
      throw new DailyQuotaExceededException(nextMidnight());
    }

    OptionalLong retryAfter = rateLimiter.tryAcquire(user.getId());
    if (retryAfter.isPresent()) {
      throw new RateLimitedException(retryAfter.getAsLong());
    }

    ensureRow(user.getId(), today);

    int reserved = usageRepository.incrementIfBelow(user.getId(), today, limit, clock.instant());
    if (reserved == 0) {
      throw new DailyQuotaExceededException(nextMidnight());
    }

    int used =
        usageRepository
            .findByUserIdAndUsageDate(user.getId(), today)
            .map(AssistantUsage::getCount)
            .orElse(limit);
    return build(limit, used);
  }

  /**
   * Crea la fila del día si no existe. Dos peticiones simultáneas pueden intentar crearla a la vez;
   * la restricción de unicidad hace que una falle, y esa simplemente sigue con la fila de la otra.
   */
  private void ensureRow(Long userId, LocalDate date) {
    if (usageRepository.findByUserIdAndUsageDate(userId, date).isPresent()) return;
    try {
      usageRepository.saveAndFlush(
          AssistantUsage.builder()
              .userId(userId)
              .usageDate(date)
              .count(0)
              .updatedAt(clock.instant())
              .build());
    } catch (DataIntegrityViolationException raced) {
      log.debug("Fila de uso creada concurrentemente para el usuario {}", userId);
    }
  }

  private QuotaStatus build(int limit, int used) {
    int remaining = Math.max(0, limit - used);
    int threshold = (int) Math.ceil(limit * warningRatio);
    return new QuotaStatus(
        limit,
        used,
        remaining,
        nextMidnight(),
        rateLimiter.perMinute(),
        remaining > 0 && remaining <= threshold,
        threshold);
  }

  private LocalDate today() {
    return LocalDate.now(clock);
  }

  /** Próxima medianoche en la zona del reloj (America/Bogota), como instante absoluto. */
  private Instant nextMidnight() {
    ZoneId zone = clock.getZone();
    return today().plusDays(1).atStartOfDay(zone).toInstant();
  }
}

package com.vista.pdg.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vista.pdg.academic.entity.AcademicTerm;
import com.vista.pdg.academic.entity.Course;
import com.vista.pdg.academic.repository.AcademicTermRepository;
import com.vista.pdg.academic.repository.CourseRepository;
import com.vista.pdg.assistant.entity.AssistantUsage;
import com.vista.pdg.assistant.repository.AssistantUsageRepository;
import com.vista.pdg.assistant.repository.QuotaChangeRepository;
import com.vista.pdg.assistant.service.RateLimiter;
import com.vista.pdg.auth.IntegrationTestSupport;
import com.vista.pdg.auth.dto.AuthResponse;
import com.vista.pdg.auth.dto.RegisterRequest;
import com.vista.pdg.auth.entity.User;
import com.vista.pdg.auth.repository.UserRepository;
import com.vista.pdg.exception.DailyQuotaExceededException;
import com.vista.pdg.testsupport.FakeLlmConfig;
import com.vista.pdg.testsupport.MutableClockConfig;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * HU-17 · CA-1, CA-2, CA-4, CA-5 y CA-6 contra Postgres real y el reloj desplazable.
 *
 * <p>CA-3 (ráfaga) vive en {@code RateLimiterTest} con su propio reloj y en {@code
 * HttpStatusContractTest} contra un servidor real con el límite por defecto; aquí el límite de tasa
 * está subido para que las suites puedan generar seguido.
 *
 * <p>Los antecedentes Gherkin («hoy he enviado 10 mensajes») se materializan escribiendo la fila de
 * uso del día, que es exactamente lo que habrían dejado 10 mensajes reales. Cada prueba usa un
 * estudiante recién registrado para no depender del orden ni del estado de las demás.
 */
class AssistantQuotaTest extends IntegrationTestSupport {

  private static final String GENERATE = "/api/generate";
  private static final String QUOTA = "/api/assistant/quota";

  @Autowired private AssistantUsageRepository usage;
  @Autowired private QuotaChangeRepository changes;
  @Autowired private UserRepository users;
  @Autowired private CourseRepository courses;
  @Autowired private AcademicTermRepository terms;
  @Autowired private RateLimiter rateLimiter;
  @Autowired private Clock clock;

  @AfterEach
  void restoreClockAndLimiter() {
    MutableClockConfig.CLOCK.reset();
    rateLimiter.reset();
  }

  private LocalDate today() {
    return LocalDate.now(clock);
  }

  /** «Hoy he enviado N mensajes»: deja la fila del día como la habrían dejado N mensajes. */
  private void sentToday(String email, int n) {
    User u = users.findByEmail(email).orElseThrow();
    AssistantUsage row =
        usage
            .findByUserIdAndUsageDate(u.getId(), today())
            .orElseGet(() -> AssistantUsage.builder().userId(u.getId()).usageDate(today()).build());
    row.setCount(n);
    row.setUpdatedAt(clock.instant());
    usage.save(row);
  }

  private String bearer(AuthResponse s) {
    return "Bearer " + s.accessToken();
  }

  // ── CA-1 ─────────────────────────────────────────────────────────────────

  @Test
  @DisplayName("CA-1: con 10 enviados, el 11.º se procesa y quedan 29 (cabecera y endpoint)")
  void consumoDentroDeLosLimites() throws Exception {
    AuthResponse s = registerStudent("cuota.ca1");
    sentToday(s.email(), 10);

    mockMvc
        .perform(
            post(GENERATE)
                .header("Authorization", bearer(s))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"prompt\":\"K3\"}"))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Quota-Limit", "40"))
        .andExpect(header().string("X-Quota-Remaining", "29"))
        .andExpect(header().exists("X-Quota-Reset"));

    mockMvc
        .perform(get(QUOTA).header("Authorization", bearer(s)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.limit").value(40))
        .andExpect(jsonPath("$.used").value(11))
        .andExpect(jsonPath("$.remaining").value(29))
        .andExpect(jsonPath("$.warning").value(false))
        .andExpect(jsonPath("$.ratePerMinute").isNumber());
  }

  @Test
  @DisplayName("al entrar por primera vez el contador está completo: 40 de 40")
  void contadorInicial() throws Exception {
    AuthResponse s = registerStudent("cuota.inicial");
    mockMvc
        .perform(get(QUOTA).header("Authorization", bearer(s)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.used").value(0))
        .andExpect(jsonPath("$.remaining").value(40));
  }

  // ── CA-2 ─────────────────────────────────────────────────────────────────

  @Test
  @DisplayName("CA-2: con 40 enviados, el 41.º responde 429 con el literal y no invoca al modelo")
  void agotamientoDiarioSinLlamadaFacturable() throws Exception {
    AuthResponse s = registerStudent("cuota.ca2");
    sentToday(s.email(), 40);
    int llmCallsBefore = FakeLlmConfig.CALLS.get();

    mockMvc
        .perform(
            post(GENERATE)
                .header("Authorization", bearer(s))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"prompt\":\"K3\"}"))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("DAILY_QUOTA_EXCEEDED"))
        .andExpect(jsonPath("$.message").value(DailyQuotaExceededException.MESSAGE))
        .andExpect(header().string("X-Quota-Remaining", "0"))
        .andExpect(header().exists("X-Quota-Reset"));

    assertThat(FakeLlmConfig.CALLS.get())
        .as("«no se genera ninguna llamada facturable a la API del modelo»")
        .isEqualTo(llmCallsBefore);
  }

  /** El resto de la aplicación no se ve afectado: el lienzo sigue utilizable sin el asistente. */
  @Test
  @DisplayName("CA-2: con la cuota agotada, los pasos de algoritmo (sin modelo) siguen funcionando")
  void lienzoUtilizableSinAsistente() throws Exception {
    AuthResponse s = registerStudent("cuota.lienzo");
    sentToday(s.email(), 40);

    mockMvc
        .perform(
            post("/api/algorithm/steps")
                .header("Authorization", bearer(s))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"type\":\"tree\",\"subtype\":\"avl\",\"operation\":\"insert\",\"values\":[3,2,1]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.error").value(false));
  }

  @Test
  @DisplayName("el 40.º mensaje aún entra y deja el contador en 0; el 41.º ya no")
  void elUltimoMensajeEntra() throws Exception {
    AuthResponse s = registerStudent("cuota.borde");
    sentToday(s.email(), 39);

    mockMvc
        .perform(
            post(GENERATE)
                .header("Authorization", bearer(s))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"prompt\":\"K3\"}"))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Quota-Remaining", "0"));

    mockMvc
        .perform(
            post(GENERATE)
                .header("Authorization", bearer(s))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"prompt\":\"K3\"}"))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("DAILY_QUOTA_EXCEEDED"));
  }

  // ── CA-4 ─────────────────────────────────────────────────────────────────

  @Test
  @DisplayName("CA-4: con 32 enviados, el siguiente se procesa y quedan 7 con aviso")
  void avisoPreventivo() throws Exception {
    AuthResponse s = registerStudent("cuota.ca4");
    sentToday(s.email(), 32);

    mockMvc
        .perform(
            post(GENERATE)
                .header("Authorization", bearer(s))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"prompt\":\"K3\"}"))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Quota-Remaining", "7"));

    mockMvc
        .perform(get(QUOTA).header("Authorization", bearer(s)))
        .andExpect(jsonPath("$.remaining").value(7))
        .andExpect(jsonPath("$.warning").value(true))
        .andExpect(jsonPath("$.warningThreshold").value(8));
  }

  @Test
  @DisplayName("el aviso no se enciende con 29 restantes ni con la cuota agotada")
  void avisoSoloEnLaFranja() throws Exception {
    AuthResponse a = registerStudent("cuota.aviso.a");
    sentToday(a.email(), 11);
    mockMvc
        .perform(get(QUOTA).header("Authorization", bearer(a)))
        .andExpect(jsonPath("$.remaining").value(29))
        .andExpect(jsonPath("$.warning").value(false));

    AuthResponse b = registerStudent("cuota.aviso.b");
    sentToday(b.email(), 40);
    mockMvc
        .perform(get(QUOTA).header("Authorization", bearer(b)))
        .andExpect(jsonPath("$.remaining").value(0))
        .andExpect(jsonPath("$.warning").value(false));
  }

  // ── CA-5 ─────────────────────────────────────────────────────────────────

  @Test
  @DisplayName("CA-5: agotada ayer, al día siguiente el contador vuelve a 40 y se puede enviar")
  void reinicioDeLaVentanaDiaria() throws Exception {
    AuthResponse s = registerStudent("cuota.ca5");
    sentToday(s.email(), 40);
    mockMvc
        .perform(get(QUOTA).header("Authorization", bearer(s)))
        .andExpect(jsonPath("$.remaining").value(0));

    MutableClockConfig.CLOCK.advance(Duration.ofDays(1));

    mockMvc
        .perform(get(QUOTA).header("Authorization", bearer(s)))
        .andExpect(jsonPath("$.used").value(0))
        .andExpect(jsonPath("$.remaining").value(40));
    mockMvc
        .perform(
            post(GENERATE)
                .header("Authorization", bearer(s))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"prompt\":\"K3\"}"))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Quota-Remaining", "39"));
  }

  /** La ventana es el día calendario de Bogotá: resetsAt es la próxima medianoche allí (05:00Z). */
  @Test
  @DisplayName("resetsAt es la próxima medianoche en America/Bogota")
  void resetsAtEsMedianocheBogota() throws Exception {
    AuthResponse s = registerStudent("cuota.reset");
    String body =
        mockMvc
            .perform(get(QUOTA).header("Authorization", bearer(s)))
            .andReturn()
            .getResponse()
            .getContentAsString();
    Instant resetsAt = Instant.parse(objectMapper.readTree(body).get("resetsAt").asText());

    Instant expected = today().plusDays(1).atStartOfDay(clock.getZone()).toInstant();
    assertThat(resetsAt).isEqualTo(expected);
    assertThat(resetsAt.atZone(clock.getZone()).getHour()).isZero();
    assertThat(resetsAt).isAfter(clock.instant());
  }

  // ── CA-6 ─────────────────────────────────────────────────────────────────

  @Test
  @DisplayName(
      "CA-6: el administrador sube la cuota de un curso y aplica a sus estudiantes, con auditoría")
  void ajusteDeCuotaPorElAdministrador() throws Exception {
    // Curso propio para no interferir con las demás pruebas, que asumen 40 en CEDI-G1.
    AcademicTerm term = terms.findFirstByActiveTrue().orElseThrow();
    courses
        .findByCode("CEDI-Q6")
        .orElseGet(
            () ->
                courses.save(
                    Course.builder()
                        .code("CEDI-Q6")
                        .name("Curso con cuota propia")
                        .term(term)
                        .build()));
    String email = uniqueEmail("cuota.ca6");
    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        new RegisterRequest(
                            "Cuota Seis", email, "clave12345", "clave12345", "CEDI-Q6"))))
        .andExpect(status().isCreated());
    AuthResponse student = login(email, "clave12345");
    AuthResponse admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);

    mockMvc
        .perform(get(QUOTA).header("Authorization", bearer(student)))
        .andExpect(jsonPath("$.limit").value(40));

    Instant before = clock.instant();
    mockMvc
        .perform(
            put("/api/admin/courses/CEDI-Q6/quota")
                .header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("dailyQuota", 60))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dailyQuota").value(60))
        .andExpect(jsonPath("$.effectiveDailyQuota").value(60));

    // Aplica a todos los estudiantes del curso: se resuelve en cada petición, no se copia al
    // usuario.
    mockMvc
        .perform(get(QUOTA).header("Authorization", bearer(student)))
        .andExpect(jsonPath("$.limit").value(60))
        .andExpect(jsonPath("$.remaining").value(60));

    // Queda registrado con marca de tiempo y autor.
    var history = changes.findByCourse_CodeOrderByChangedAtDesc("CEDI-Q6");
    assertThat(history).isNotEmpty();
    var last = history.getFirst();
    assertThat(last.getNewQuota()).isEqualTo(60);
    assertThat(last.getChangedBy()).isEqualTo(ADMIN_EMAIL);
    assertThat(last.getChangedAt())
        .isBetween(before.minusSeconds(1), clock.instant().plusSeconds(1));

    mockMvc
        .perform(
            get("/api/admin/courses/CEDI-Q6/quota-history").header("Authorization", bearer(admin)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].newQuota").value(60))
        .andExpect(jsonPath("$[0].changedBy").value(ADMIN_EMAIL))
        .andExpect(jsonPath("$[0].changedAt").exists());
  }

  @Test
  @DisplayName("sólo ADMIN ajusta cuotas: docente y estudiante reciben 403")
  void ajusteSoloAdmin() throws Exception {
    for (AuthResponse s :
        new AuthResponse[] {
          login(TEACHER_EMAIL, TEACHER_PASSWORD), login(STUDENT_EMAIL, STUDENT_PASSWORD)
        }) {
      mockMvc
          .perform(
              put("/api/admin/courses/CEDI-G1/quota")
                  .header("Authorization", bearer(s))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(json(Map.of("dailyQuota", 999))))
          .andExpect(status().isForbidden());
    }
  }

  @Test
  @DisplayName("una cuota fuera de rango o un curso inexistente se rechazan sin auditoría")
  void ajusteInvalido() throws Exception {
    AuthResponse admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);
    long before = changes.count();

    mockMvc
        .perform(
            put("/api/admin/courses/CEDI-G1/quota")
                .header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("dailyQuota", 0))))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.fieldErrors.dailyQuota").exists());

    mockMvc
        .perform(
            put("/api/admin/courses/NOPE-9/quota")
                .header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("dailyQuota", 10))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("COURSE_NOT_FOUND"));

    assertThat(changes.count()).isEqualTo(before);
  }

  @Test
  @DisplayName("GET /api/admin/courses lista cursos con su cuota configurada y la efectiva")
  void listadoDeCursos() throws Exception {
    AuthResponse admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);
    mockMvc
        .perform(get("/api/admin/courses").header("Authorization", bearer(admin)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.code=='CEDI-G1')].effectiveDailyQuota").isNotEmpty());
  }
}

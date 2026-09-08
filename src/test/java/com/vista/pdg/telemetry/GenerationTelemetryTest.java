package com.vista.pdg.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vista.pdg.auth.IntegrationTestSupport;
import com.vista.pdg.auth.dto.AuthResponse;
import com.vista.pdg.auth.entity.User;
import com.vista.pdg.auth.repository.UserRepository;
import com.vista.pdg.telemetry.entity.GenerationEvent;
import com.vista.pdg.telemetry.repository.GenerationEventRepository;
import com.vista.pdg.telemetry.service.Pseudonymizer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * HU-16 · CA-5 (trazabilidad seudonimizada).
 *
 * <p>La aserción de privacidad se hace sobre la <b>fila cruda</b> leída con JDBC, no sobre la
 * entidad: si mañana alguien añadiera una columna con el correo, la entidad seguiría sin exponerlo
 * en sus getters y la prueba basada en ella seguiría en verde. Leer la fila completa cierra esa
 * puerta.
 */
class GenerationTelemetryTest extends IntegrationTestSupport {

  private static final String GENERATE = "/api/generate";

  @Autowired private GenerationEventRepository events;
  @Autowired private UserRepository users;
  @Autowired private Pseudonymizer pseudonymizer;
  @Autowired private JdbcTemplate jdbc;

  private void generateAs(String accessToken) throws Exception {
    mockMvc
        .perform(
            post(GENERATE)
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"prompt\":\"grafo completo de 3 vértices\"}"))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName(
      "CA-5: al generar, el evento persistido lleva seudónimo y el código de curso CEDI-G1")
  void eventoConSeudonimoYCurso() throws Exception {
    AuthResponse session = login(STUDENT_EMAIL, STUDENT_PASSWORD);
    User student = users.findByEmail(STUDENT_EMAIL).orElseThrow();
    String expected = pseudonymizer.pseudonymFor(student.getId());
    int before = events.findByPseudonym(expected).size();

    generateAs(session.accessToken());

    List<GenerationEvent> mine = events.findByPseudonym(expected);
    assertThat(mine).hasSize(before + 1);
    GenerationEvent ev = mine.getLast();
    assertThat(ev.getCourseCode()).isEqualTo("CEDI-G1");
    assertThat(ev.getTermCode()).isEqualTo("2026-1");
    assertThat(ev.getStructureType()).isEqualTo("graph");
    assertThat(ev.getNodeCount()).isEqualTo(3);
    assertThat(ev.getCreatedAt()).isNotNull();
  }

  @Test
  @DisplayName("CA-5: la fila cruda no contiene el correo ni el nombre en texto plano")
  void filaCrudaSinDatosIdentificables() throws Exception {
    AuthResponse session = login(STUDENT_EMAIL, STUDENT_PASSWORD);
    User student = users.findByEmail(STUDENT_EMAIL).orElseThrow();
    generateAs(session.accessToken());

    List<Map<String, Object>> rows =
        jdbc.queryForList(
            "select * from generation_events where pseudonym = ?",
            pseudonymizer.pseudonymFor(student.getId()));
    assertThat(rows).isNotEmpty();

    String raw = rows.toString().toLowerCase();
    assertThat(raw).doesNotContain(student.getEmail().toLowerCase());
    assertThat(raw).doesNotContain(student.getDisplayName().toLowerCase());
    assertThat(raw).doesNotContain("estudiante@");
    // Tampoco el identificador numérico de la cuenta como columna: sería la vuelta atrás trivial.
    assertThat(rows.getFirst().keySet()).noneMatch(k -> k.equalsIgnoreCase("user_id"));
  }

  @Test
  @DisplayName("CA-5: el seudónimo es estable entre generaciones de la misma cuenta")
  void seudonimoEstable() throws Exception {
    AuthResponse session = login(STUDENT_EMAIL, STUDENT_PASSWORD);
    User student = users.findByEmail(STUDENT_EMAIL).orElseThrow();
    String p = pseudonymizer.pseudonymFor(student.getId());
    int before = events.findByPseudonym(p).size();

    generateAs(session.accessToken());
    generateAs(session.accessToken());

    assertThat(events.findByPseudonym(p)).hasSize(before + 2);
  }

  @Test
  @DisplayName("CA-5: cuentas distintas producen seudónimos distintos")
  void seudonimosDistintosPorCuenta() throws Exception {
    AuthResponse a = registerStudent("telemetria.a");
    AuthResponse b = registerStudent("telemetria.b");
    Long idA = users.findByEmail(a.email()).orElseThrow().getId();
    Long idB = users.findByEmail(b.email()).orElseThrow().getId();

    generateAs(a.accessToken());
    generateAs(b.accessToken());

    String pa = pseudonymizer.pseudonymFor(idA);
    String pb = pseudonymizer.pseudonymFor(idB);
    assertThat(pa).isNotEqualTo(pb);
    assertThat(events.findByPseudonym(pa)).hasSize(1);
    assertThat(events.findByPseudonym(pb)).hasSize(1);
    assertThat(events.findByPseudonym(pa).getFirst().getCourseCode()).isEqualTo("CEDI-G1");
  }

  /** El docente no tiene curso; el evento se registra igual, con la cohorte en nulo. */
  @Test
  @DisplayName("una cuenta sin curso genera un evento con courseCode nulo, no falla")
  void cuentaSinCursoRegistraSinCohorte() throws Exception {
    AuthResponse teacher = login(TEACHER_EMAIL, TEACHER_PASSWORD);
    Long id = users.findByEmail(TEACHER_EMAIL).orElseThrow().getId();
    String p = pseudonymizer.pseudonymFor(id);
    int before = events.findByPseudonym(p).size();

    generateAs(teacher.accessToken());

    List<GenerationEvent> mine = events.findByPseudonym(p);
    assertThat(mine).hasSize(before + 1);
    assertThat(mine.getLast().getCourseCode()).isNull();
  }
}

package com.vista.pdg.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vista.pdg.academic.entity.AcademicTerm;
import com.vista.pdg.academic.entity.Course;
import com.vista.pdg.academic.repository.AcademicTermRepository;
import com.vista.pdg.academic.repository.CourseRepository;
import com.vista.pdg.auth.IntegrationTestSupport;
import com.vista.pdg.auth.dto.RegisterRequest;
import com.vista.pdg.auth.entity.User;
import com.vista.pdg.auth.repository.UserRepository;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * HU-16 · CA-1 (registro vinculado a curso) y CA-2 (rechazo de correo no institucional).
 *
 * <p>Antecedentes Gherkin: existe el curso {@code CEDI-G1} y el periodo activo es {@code 2026-1}.
 * Ambos los siembra {@code DataSeeder} al levantar el contexto.
 */
class CourseRegistrationTest extends IntegrationTestSupport {

  private static final String REGISTER = "/api/auth/register";
  private static final String LITERAL_CA2 = "Debes registrarte con tu correo institucional Icesi";

  @Autowired private UserRepository userRepository;
  @Autowired private CourseRepository courseRepository;
  @Autowired private AcademicTermRepository termRepository;

  private String body(String email, String courseCode) {
    return json(
        new RegisterRequest("Ricardo Urbina", email, "clave12345", "clave12345", courseCode));
  }

  // ── Antecedentes ─────────────────────────────────────────────────────────

  @Test
  @DisplayName("antecedentes: existe CEDI-G1 y el periodo activo es 2026-1")
  void antecedentesSembrados() {
    AcademicTerm active = termRepository.findFirstByActiveTrue().orElseThrow();
    assertThat(active.getCode()).isEqualTo("2026-1");

    Course cedi = courseRepository.findByCode("CEDI-G1").orElseThrow();
    assertThat(cedi.getName()).isEqualTo("Computación y Estructuras Discretas I");
    assertThat(cedi.getTerm().getCode()).isEqualTo("2026-1");
  }

  @Test
  @DisplayName("GET /api/courses es público y lista sólo los cursos del periodo activo")
  void listaCursosDelPeriodoActivo() throws Exception {
    AcademicTerm closed =
        termRepository
            .findByCode("2025-2")
            .orElseGet(
                () ->
                    termRepository.save(
                        AcademicTerm.builder().code("2025-2").active(false).build()));
    courseRepository
        .findByCode("CEDI-OLD")
        .orElseGet(
            () ->
                courseRepository.save(
                    Course.builder().code("CEDI-OLD").name("Curso cerrado").term(closed).build()));

    mockMvc
        .perform(get("/api/courses"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.code=='CEDI-G1')].termCode").value("2026-1"))
        .andExpect(jsonPath("$[?(@.code=='CEDI-OLD')]").isEmpty());
  }

  // ── CA-1 ─────────────────────────────────────────────────────────────────

  @Test
  @DisplayName(
      "CA-1: el registro con correo institucional y curso crea la cuenta STUDENT vinculada")
  void registroExitosoVinculado() throws Exception {
    String email = uniqueEmail("ricardo.urbina");

    mockMvc
        .perform(
            post(REGISTER).contentType(MediaType.APPLICATION_JSON).content(body(email, "CEDI-G1")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.roles[0]").value("STUDENT"))
        .andExpect(jsonPath("$.courseCode").value("CEDI-G1"))
        .andExpect(jsonPath("$.termCode").value("2026-1"));

    User saved = userRepository.findByEmail(email).orElseThrow();
    assertThat(saved.getCourse()).isNotNull();
    assertThat(saved.getCourse().getCode()).isEqualTo("CEDI-G1");
    assertThat(saved.getCourse().getTerm().getCode()).isEqualTo("2026-1");
  }

  @Test
  @DisplayName("CA-1: el código de curso se acepta sin distinguir mayúsculas ni espacios")
  void codigoDeCursoNormalizado() throws Exception {
    mockMvc
        .perform(
            post(REGISTER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(uniqueEmail("normalizado"), "  cedi-g1 ")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.courseCode").value("CEDI-G1"));
  }

  @Test
  @DisplayName("sin curso el registro se rechaza con 422 señalando courseCode y no crea la cuenta")
  void sinCursoNoRegistra() throws Exception {
    String email = uniqueEmail("sin.curso");

    mockMvc
        .perform(
            post(REGISTER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "displayName", "Sin Curso",
                            "email", email,
                            "password", "clave12345",
                            "confirmPassword", "clave12345"))))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.fieldErrors.courseCode").exists());

    assertThat(userRepository.findByEmail(email)).isEmpty();
  }

  @Test
  @DisplayName("un curso inexistente se rechaza con 422")
  void cursoInexistente() throws Exception {
    mockMvc
        .perform(
            post(REGISTER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(uniqueEmail("inexistente"), "NOPE-1")))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.fieldErrors.courseCode").exists());
  }

  /**
   * Un registro nuevo tiene que quedar en la cohorte vigente; un curso de otro periodo no sirve.
   */
  @Test
  @DisplayName("un curso de un periodo cerrado se rechaza igual que uno inexistente")
  void cursoDePeriodoCerrado() throws Exception {
    AcademicTerm closed =
        termRepository
            .findByCode("2025-2")
            .orElseGet(
                () ->
                    termRepository.save(
                        AcademicTerm.builder().code("2025-2").active(false).build()));
    courseRepository
        .findByCode("CEDI-OLD")
        .orElseGet(
            () ->
                courseRepository.save(
                    Course.builder().code("CEDI-OLD").name("Curso cerrado").term(closed).build()));

    mockMvc
        .perform(
            post(REGISTER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(uniqueEmail("cerrado"), "CEDI-OLD")))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.fieldErrors.courseCode").exists());
  }

  // ── CA-2 ─────────────────────────────────────────────────────────────────

  @Test
  @DisplayName("CA-2: el correo no institucional se rechaza con el literal exacto del criterio")
  void correoNoInstitucionalLiteral() throws Exception {
    mockMvc
        .perform(
            post(REGISTER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("ricardo@gmail.com", "CEDI-G1")))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.fieldErrors.email").value(LITERAL_CA2));
  }

  /** «Y no se crea ningún registro en la base de datos» — se comprueba la base, no la respuesta. */
  @Test
  @DisplayName("CA-2: el rechazo no deja ningún registro en la base")
  void correoNoInstitucionalNoDejaRegistro() throws Exception {
    long usersBefore = userRepository.count();

    mockMvc
        .perform(
            post(REGISTER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("ricardo@gmail.com", "CEDI-G1")))
        .andExpect(status().isUnprocessableEntity());

    assertThat(userRepository.findByEmail("ricardo@gmail.com")).isEmpty();
    assertThat(userRepository.count()).isEqualTo(usersBefore);
  }
}

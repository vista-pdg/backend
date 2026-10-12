package com.vista.pdg.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.vista.pdg.academic.entity.AcademicTerm;
import com.vista.pdg.academic.repository.AcademicTermRepository;
import com.vista.pdg.academic.repository.CourseRepository;
import com.vista.pdg.auth.IntegrationTestSupport;
import com.vista.pdg.auth.repository.UserRepository;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class CourseManagementTest extends IntegrationTestSupport {
  @Autowired private CourseRepository courses;
  @Autowired private UserRepository users;
  @Autowired private AcademicTermRepository terms;

  private String admin() throws Exception {
    return "Bearer " + login(ADMIN_EMAIL, ADMIN_PASSWORD).accessToken();
  }

  @Test
  void adminCreatesCourseAndRegistrationCatalogUpdatesImmediately() throws Exception {
    String admin = admin();
    String code = "CEDI-HU33-A";
    mockMvc
        .perform(get("/api/admin/terms").header("Authorization", admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.code=='2026-1')].active").value(true));
    mockMvc
        .perform(
            post("/api/admin/courses")
                .header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "code",
                            " cedi-hu33-a ",
                            "name",
                            " Grupo de prueba ",
                            "termCode",
                            "2026-1"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.code").value(code))
        .andExpect(jsonPath("$.name").value("Grupo de prueba"));
    mockMvc
        .perform(get("/api/courses"))
        .andExpect(jsonPath("$[?(@.code=='CEDI-HU33-A')].termCode").value("2026-1"));
    assertThat(courses.findByCode(code)).isPresent();

    mockMvc
        .perform(
            post("/api/admin/courses")
                .header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("code", code, "name", "Duplicado", "termCode", "2026-1"))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("COURSE_ALREADY_EXISTS"));
  }

  @Test
  void adminCanMoveStudentAndRevokeEnrollmentWithoutChangingOtherAccounts() throws Exception {
    String admin = admin();
    String email = registerStudent("hu33move").email();
    Long id = users.findByEmail(email).orElseThrow().getId();
    String code = "CEDI-HU33-B";
    mockMvc
        .perform(
            post("/api/admin/courses")
                .header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("code", code, "name", "Grupo B", "termCode", "2026-1"))))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            put("/api/admin/students/{id}/course", id)
                .header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("courseCode", code))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.courseCode").value(code));
    mockMvc
        .perform(get("/api/admin/courses/{code}/students", code).header("Authorization", admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.email=='" + email + "')].courseCode").value(code));
    mockMvc
        .perform(get("/api/admin/students").header("Authorization", admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.email=='" + email + "')].courseCode").value(code));
    assertThat(users.findByEmail(email).orElseThrow().getCourse().getCode()).isEqualTo(code);

    mockMvc
        .perform(delete("/api/admin/students/{id}/course", id).header("Authorization", admin))
        .andExpect(status().isNoContent());
    assertThat(users.findByEmail(email).orElseThrow().getCourse()).isNull();
    mockMvc
        .perform(get("/api/admin/courses/{code}/students", code).header("Authorization", admin))
        .andExpect(jsonPath("$[?(@.email=='" + email + "')]").isEmpty());
  }

  @Test
  void onlyAdminCanManageCoursesAndEnrollment() throws Exception {
    String body = json(Map.of("code", "CEDI-HU33-C", "name", "Curso C", "termCode", "2026-1"));
    mockMvc
        .perform(post("/api/admin/courses").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isUnauthorized());
    String student = "Bearer " + login(STUDENT_EMAIL, STUDENT_PASSWORD).accessToken();
    String teacher = "Bearer " + login(TEACHER_EMAIL, TEACHER_PASSWORD).accessToken();
    for (String actor : new String[] {student, teacher}) {
      mockMvc
          .perform(
              post("/api/admin/courses")
                  .header("Authorization", actor)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isForbidden());
      mockMvc
          .perform(get("/api/admin/students").header("Authorization", actor))
          .andExpect(status().isForbidden());
    }
    assertThat(courses.findByCode("CEDI-HU33-C")).isEmpty();
  }

  @Test
  void rejectsTeacherAsStudentAndUnknownOrClosedCourses() throws Exception {
    String admin = admin();
    Long teacherId = users.findByEmail(TEACHER_EMAIL).orElseThrow().getId();
    String body = json(Map.of("courseCode", "CEDI-G1"));
    mockMvc
        .perform(
            put("/api/admin/students/{id}/course", teacherId)
                .header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest());
    Long studentId = users.findByEmail(STUDENT_EMAIL).orElseThrow().getId();
    mockMvc
        .perform(
            put("/api/admin/students/{id}/course", studentId)
                .header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("courseCode", "NO-EXISTE"))))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            post("/api/admin/courses")
                .header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(Map.of("code", "CEDI-HU33-D", "name", "Curso D", "termCode", "NO-TERM"))))
        .andExpect(status().isBadRequest());

    var closed = terms.save(AcademicTerm.builder().code("HU33-OLD").active(false).build());
    courses.save(
        com.vista.pdg.academic.entity.Course.builder()
            .code("CEDI-HU33-OLD")
            .name("Antiguo")
            .term(closed)
            .build());
    mockMvc
        .perform(
            put("/api/admin/students/{id}/course", studentId)
                .header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("courseCode", "CEDI-HU33-OLD"))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void teacherAssignmentChangesAuthorizedAnalyticsCoursesImmediately() throws Exception {
    String admin = admin();
    String teacher = "Bearer " + login(TEACHER_EMAIL, TEACHER_PASSWORD).accessToken();
    String code = "CEDI-HU33-E";
    Long teacherId = users.findByEmail(TEACHER_EMAIL).orElseThrow().getId();
    mockMvc
        .perform(
            post("/api/admin/courses")
                .header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("code", code, "name", "Grupo E", "termCode", "2026-1"))))
        .andExpect(status().isCreated());
    mockMvc
        .perform(get("/api/analytics/courses").header("Authorization", teacher))
        .andExpect(jsonPath("$[?(@.code=='CEDI-HU33-E')]").isEmpty());
    mockMvc
        .perform(
            put("/api/admin/courses/{code}/teachers/{id}", code, teacherId)
                .header("Authorization", admin))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(get("/api/analytics/courses").header("Authorization", teacher))
        .andExpect(jsonPath("$[?(@.code=='CEDI-HU33-E')].name").value("Grupo E"));
    mockMvc
        .perform(
            delete("/api/admin/courses/{code}/teachers/{id}", code, teacherId)
                .header("Authorization", admin))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(get("/api/analytics/courses").header("Authorization", teacher))
        .andExpect(jsonPath("$[?(@.code=='CEDI-HU33-E')]").isEmpty());
  }
}

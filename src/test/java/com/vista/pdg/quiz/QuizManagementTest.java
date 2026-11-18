package com.vista.pdg.quiz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.vista.pdg.exception.LlmUnavailableException;
import com.vista.pdg.testsupport.FakeLlmConfig;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** HU-34 · CA-1, CA-2, CA-3 (publicación) y CA-10: gestión docente del quiz y su banco. */
class QuizManagementTest extends QuizTestSupport {

  @Test
  void teacherCreatesQuizAndBankSizeFollowsStudentsTimesQuestions() throws Exception {
    String course = newCourse();
    assignTeacher(course);
    student(course, "qzm");
    student(course, "qzm");
    String teacher = teacher();

    mockMvc
        .perform(authed(get("/api/quizzes/manage/courses"), teacher))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.code=='" + course + "')].enrolledStudents").value(2));
    mockMvc
        .perform(authed(get("/api/quizzes/manage/topics"), teacher))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$[?(@.key=='" + AVL + "')].label").value("Árbol AVL — AVL · Inserción"))
        .andExpect(jsonPath("$[?(@.key=='" + BFS + "')].familyLabel").value("Grafos"));

    // Sin número de estudiantes rige la matrícula; con él, lo que el docente diga.
    create(teacher, quizBody(course, 3, null))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("DRAFT"))
        .andExpect(jsonPath("$.phase").value("DRAFT"))
        .andExpect(jsonPath("$.expectedStudents").value(2))
        .andExpect(jsonPath("$.bankTarget").value(6))
        .andExpect(jsonPath("$.bankSize").value(0));
    create(teacher, quizBody(course, 3, 30))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.bankTarget").value(90))
        .andExpect(jsonPath("$.topics[0].key").value(AVL));

    mockMvc
        .perform(authed(get("/api/quizzes/manage"), teacher).param("courseCode", course))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));
  }

  @Test
  void rejectsInvalidQuizzesPointingAtTheField() throws Exception {
    String course = newCourse();
    String admin = admin();

    Map<String, Object> noTopics = quizBody(course, 3, 10);
    noTopics.put("topics", List.of());
    create(admin, noTopics)
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.fieldErrors.topics").exists());

    Map<String, Object> unknownTopic = quizBody(course, 3, 10);
    unknownTopic.put("topics", List.of("tree/avl/teletransportar"));
    create(admin, unknownTopic)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_QUIZ"))
        .andExpect(jsonPath("$.fieldErrors.topics").exists());

    Map<String, Object> inverted = quizBody(course, 3, 10);
    inverted.put("closesAt", now().minus(2, ChronoUnit.HOURS).toString());
    create(admin, inverted)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.closesAt").exists());

    Map<String, Object> past = quizBody(course, 3, 10);
    past.put("opensAt", now().minus(3, ChronoUnit.DAYS).toString());
    past.put("closesAt", now().minus(1, ChronoUnit.DAYS).toString());
    create(admin, past)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.closesAt").exists());

    // Curso sin matriculados y sin número manual: no hay con qué dimensionar el banco.
    create(admin, quizBody(course, 3, null))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.studentCount").exists());
    create(admin, quizBody(course, 20, 300))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.studentCount").exists());

    Map<String, Object> zeroQuestions = quizBody(course, 0, 10);
    create(admin, zeroQuestions)
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.fieldErrors.questionsPerStudent").exists());
    Map<String, Object> blankTitle = quizBody(course, 3, 10);
    blankTitle.put("title", "  ");
    create(admin, blankTitle)
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.fieldErrors.title").exists());
    create(admin, quizBody("NO-EXISTE", 3, 10))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.courseCode").exists());
  }

  @Test
  void generatesBankInBatchesAcrossTopicsAndPublishesOnlyWhenComplete() throws Exception {
    String course = newCourse();
    String admin = admin();
    long quiz = createQuiz(admin, quizBody(course, 3, 2));

    mockMvc
        .perform(authed(post("/api/quizzes/manage/{id}/publish", quiz), admin))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("QUIZ_BANK_INCOMPLETE"));

    mockMvc
        .perform(authed(post("/api/quizzes/manage/{id}/bank/generate", quiz), admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.generated").value(3))
        .andExpect(jsonPath("$.discarded").value(0))
        .andExpect(jsonPath("$.bankSize").value(3))
        .andExpect(jsonPath("$.bankTarget").value(6))
        .andExpect(jsonPath("$.complete").value(false));
    fillBank(admin, quiz);

    JsonNode bank = bank(admin, quiz);
    assertThat(bank).hasSize(6);
    // El banco reparte los temas elegidos y cada pregunta trae escenario, 4 opciones y la correcta.
    assertThat(bank.findValuesAsText("topicKey")).contains(AVL, BFS);
    for (JsonNode question : bank) {
      assertThat(question.path("options")).hasSize(4);
      assertThat(question.path("correctIndex").asInt()).isBetween(0, 3);
      assertThat(question.path("scenario").asText()).isNotBlank();
      assertThat(question.path("explanation").asText()).isNotBlank();
    }
    assertThat(bank.findValuesAsText("scenario")).anyMatch(s -> s.startsWith("Grafo no dirigido"));

    // El docente descarta una pregunta: el banco queda incompleto hasta generar el reemplazo.
    long discarded = bank.get(0).path("id").asLong();
    mockMvc
        .perform(authed(delete("/api/quizzes/manage/{id}/questions/{q}", quiz, discarded), admin))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(authed(post("/api/quizzes/manage/{id}/publish", quiz), admin))
        .andExpect(status().isConflict());
    fillBank(admin, quiz);
    mockMvc
        .perform(authed(post("/api/quizzes/manage/{id}/publish", quiz), admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PUBLISHED"))
        .andExpect(jsonPath("$.phase").value("OPEN"));

    // Publicado: el banco queda fijo.
    mockMvc
        .perform(authed(post("/api/quizzes/manage/{id}/bank/generate", quiz), admin))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("QUIZ_ALREADY_PUBLISHED"));
    long kept = bank(admin, quiz).get(0).path("id").asLong();
    mockMvc
        .perform(authed(delete("/api/quizzes/manage/{id}/questions/{q}", quiz, kept), admin))
        .andExpect(status().isConflict());
    mockMvc
        .perform(authed(post("/api/quizzes/manage/{id}/publish", quiz), admin))
        .andExpect(status().isConflict());
  }

  @Test
  void modelFailuresNeverCorruptTheBank() throws Exception {
    String course = newCourse();
    String admin = admin();
    long quiz = createQuiz(admin, quizBody(course, 2, 2));

    FakeLlmConfig.QUIZ_RESPONDER.set(
        prompt -> {
          throw new LlmUnavailableException("El proveedor no respondió", null);
        });
    mockMvc
        .perform(authed(post("/api/quizzes/manage/{id}/bank/generate", quiz), admin))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("QUIZ_GENERATION_UNAVAILABLE"));

    FakeLlmConfig.QUIZ_RESPONDER.set(prompt -> "lo siento, no puedo ayudar con eso");
    mockMvc
        .perform(authed(post("/api/quizzes/manage/{id}/bank/generate", quiz), admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.generated").value(0))
        .andExpect(jsonPath("$.discarded").value(2))
        .andExpect(jsonPath("$.bankSize").value(0));
    assertThat(bank(admin, quiz)).isEmpty();

    mockMvc
        .perform(authed(delete("/api/quizzes/manage/{id}", quiz), admin))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(authed(get("/api/quizzes/manage/{id}", quiz), admin))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("QUIZ_NOT_FOUND"));
  }

  @Test
  void onlyAssignedTeachersAndAdminsManageACourseQuiz() throws Exception {
    String mine = newCourse();
    String foreign = newCourse();
    assignTeacher(mine);
    String teacher = teacher();
    String admin = admin();
    String student = bearer(student(mine, "qzperm"));
    long foreignQuiz = createQuiz(admin, quizBody(foreign, 2, 2));
    long myQuiz = createQuiz(teacher, quizBody(mine, 2, 2));

    // Sin sesión: 401. Estudiante: 403 en toda la gestión.
    mockMvc.perform(get("/api/quizzes/manage")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/quizzes")).andExpect(status().isUnauthorized());
    for (String path :
        List.of("", "/courses", "/topics", "/" + myQuiz, "/" + myQuiz + "/results")) {
      mockMvc
          .perform(authed(get("/api/quizzes/manage" + path), student))
          .andExpect(status().isForbidden());
    }
    create(student, quizBody(mine, 2, 2)).andExpect(status().isForbidden());

    // Docente: solo sus cursos.
    create(teacher, quizBody(foreign, 2, 2))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("QUIZ_FORBIDDEN"));
    mockMvc
        .perform(authed(get("/api/quizzes/manage"), teacher).param("courseCode", foreign))
        .andExpect(status().isForbidden());
    for (String path : List.of("", "/results", "/results.csv", "/results.xlsx")) {
      mockMvc
          .perform(authed(get("/api/quizzes/manage/" + foreignQuiz + path), teacher))
          .andExpect(status().isForbidden());
    }
    mockMvc
        .perform(authed(post("/api/quizzes/manage/{id}/bank/generate", foreignQuiz), teacher))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(authed(post("/api/quizzes/manage/{id}/publish", foreignQuiz), teacher))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(authed(delete("/api/quizzes/manage/{id}", foreignQuiz), teacher))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(authed(get("/api/quizzes/manage"), teacher))
        .andExpect(jsonPath("$[?(@.id==" + foreignQuiz + ")]").isEmpty())
        .andExpect(jsonPath("$[?(@.id==" + myQuiz + ")].courseCode").value(mine));
    mockMvc
        .perform(authed(get("/api/quizzes/manage/courses"), teacher))
        .andExpect(jsonPath("$[?(@.code=='" + foreign + "')]").isEmpty());

    // Administrador: cualquier curso.
    mockMvc
        .perform(authed(get("/api/quizzes/manage/{id}", myQuiz), admin))
        .andExpect(status().isOk());
    mockMvc
        .perform(authed(get("/api/quizzes/manage/courses"), admin))
        .andExpect(jsonPath("$[?(@.code=='" + foreign + "')]").isNotEmpty());
    // HU-35 · CA-3: al retirar la asignación desde Administración, el docente pierde el curso.
    Long teacherId = users.findByEmail(TEACHER_EMAIL).orElseThrow().getId();
    mockMvc
        .perform(
            delete("/api/admin/courses/{code}/teachers/{id}", mine, teacherId)
                .header("Authorization", admin))
        .andExpect(status().isNoContent());
    create(teacher, quizBody(mine, 2, 2)).andExpect(status().isForbidden());
    mockMvc
        .perform(authed(get("/api/quizzes/manage/{id}", myQuiz), teacher))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(authed(get("/api/quizzes/manage/courses"), teacher))
        .andExpect(jsonPath("$[?(@.code=='" + mine + "')]").isEmpty());
    // El administrador sigue gestionando el curso y su quiz.
    create(admin, quizBody(mine, 2, 2)).andExpect(status().isCreated());
  }
}

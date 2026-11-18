package com.vista.pdg.telemetry.service;

import com.vista.pdg.academic.dto.CourseDto;
import com.vista.pdg.academic.entity.Course;
import com.vista.pdg.academic.service.TeachingAssignmentService;
import com.vista.pdg.auth.entity.User;
import com.vista.pdg.quiz.service.QuizAccess;
import com.vista.pdg.quiz.service.QuizTopics;
import com.vista.pdg.service.algorithm.AlgorithmDispatcher;
import com.vista.pdg.service.algorithm.def.AlgorithmDescriptor;
import com.vista.pdg.telemetry.dto.PedagogicalReport;
import com.vista.pdg.telemetry.dto.PedagogicalReport.QuizRef;
import com.vista.pdg.telemetry.repository.LearningAnalyticsRepository;
import com.vista.pdg.telemetry.repository.LearningAnalyticsRepository.QuizRow;
import com.vista.pdg.telemetry.repository.PedagogicalAnalyticsRepository;
import com.vista.pdg.telemetry.repository.PedagogicalAnalyticsRepository.FirstPractice;
import com.vista.pdg.telemetry.repository.PedagogicalAnalyticsRepository.PracticeCount;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PedagogicalAnalyticsService {
  private final TeachingAssignmentService assignments;
  private final PedagogicalAnalyticsRepository repository;
  private final LearningAnalyticsRepository learning;
  private final QuizAccess quizAccess;
  private final AlgorithmDispatcher algorithms;
  private final Pseudonymizer pseudonymizer;
  private final Clock clock;
  private final double threshold;

  public PedagogicalAnalyticsService(
      TeachingAssignmentService assignments,
      PedagogicalAnalyticsRepository repository,
      LearningAnalyticsRepository learning,
      QuizAccess quizAccess,
      AlgorithmDispatcher algorithms,
      Pseudonymizer pseudonymizer,
      Clock clock,
      @Value("${analytics.retry-threshold:0.30}") double threshold) {
    if (!Double.isFinite(threshold) || threshold < 0 || threshold > 1) {
      throw new IllegalArgumentException("analytics.retry-threshold debe estar entre 0 y 1");
    }
    this.assignments = assignments;
    this.repository = repository;
    this.learning = learning;
    this.quizAccess = quizAccess;
    this.algorithms = algorithms;
    this.pseudonymizer = pseudonymizer;
    this.clock = clock;
    this.threshold = threshold;
  }

  /** Una instantánea consistente evita denominadores de consultas con poblaciones distintas. */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public PedagogicalReport report(User actor, AnalyticsFilter filter) {
    Course course = assignments.requireAccess(actor, filter.courseCode());
    String term = course.getTerm().getCode();
    List<QuizRow> quizzes = learning.publishedQuizzes(course.getId(), filter.quizId());
    QuizRef quiz = null;
    if (filter.quizId() != null) {
      QuizRow row =
          quizzes.stream()
              .findFirst()
              .orElseThrow(() -> AnalyticsRequestException.invalid("Ese quiz no es de este curso"));
      quiz = new QuizRef(row.id(), row.title());
    }

    Set<String> active = new HashSet<>(repository.activePseudonyms(filter, term));
    learning.activeStudents(course.getId(), filter).stream()
        .map(pseudonymizer::pseudonymFor)
        .forEach(active::add);

    Map<String, AlgorithmDescriptor> byEvent = topicsByEvent();
    Map<String, Long> practice = new HashMap<>();
    for (PracticeCount p : repository.practice(filter, term)) {
      AlgorithmDescriptor d = byEvent.get(eventKey(p.structureType(), p.subtype(), p.algorithm()));
      if (d != null) practice.merge(d.key(), p.executions(), Long::sum);
    }
    var items = learning.quizItems(course.getId(), filter);
    Map<String, Long> studentsByPseudonym = new HashMap<>();
    items.forEach(
        i -> studentsByPseudonym.put(pseudonymizer.pseudonymFor(i.studentId()), i.studentId()));
    Map<Long, Map<String, Instant>> firstPractice = new HashMap<>();
    for (FirstPractice f : repository.firstPractice(course.getCode(), term)) {
      Long student = studentsByPseudonym.get(f.pseudonym());
      AlgorithmDescriptor d = byEvent.get(eventKey(f.structureType(), f.subtype(), f.algorithm()));
      if (student == null || d == null) continue;
      firstPractice
          .computeIfAbsent(student, k -> new HashMap<>())
          .merge(d.key(), f.first(), (a, b) -> a.isBefore(b) ? a : b);
    }
    Map<String, String> labels = new HashMap<>();
    algorithms.catalog().forEach(d -> labels.put(d.key(), QuizTopics.label(d)));

    LearningInsights.Result insights =
        LearningInsights.compute(
            new LearningInsights.Inputs(
                items,
                learning.finishedAttempts(course.getId(), filter),
                quizzes,
                learning.study(course.getId(), filter),
                practice,
                firstPractice,
                labels,
                quizAccess.enrolled(course).size(),
                clock.instant()));
    return AnalyticsDisclosure.disclose(
        CourseDto.from(course),
        filter,
        quiz,
        threshold,
        new AnalyticsDisclosure.Measured(
            active.size(),
            quizAccess.enrolled(course).size(),
            repository.structures(filter, term),
            repository.algorithms(filter, term),
            repository.retries(filter, term),
            insights));
  }

  /** Quizzes publicados del curso, para el filtro opcional del panel. */
  @Transactional(readOnly = true)
  public List<QuizRef> quizzes(User actor, String courseCode) {
    Course course = assignments.requireAccess(actor, courseCode);
    return learning.publishedQuizzes(course.getId(), null).stream()
        .map(q -> new QuizRef(q.id(), q.title()))
        .toList();
  }

  /**
   * Los eventos de algoritmo guardan el tipo como lo lee un docente (v2) o el del contrato (v1),
   * más el subtipo y la operación; ambos se traducen a la clave del tema.
   */
  private Map<String, AlgorithmDescriptor> topicsByEvent() {
    Map<String, AlgorithmDescriptor> map = new HashMap<>();
    for (AlgorithmDescriptor d : algorithms.catalog()) {
      map.putIfAbsent(
          eventKey(StructureKind.of(d.type(), d.subtype()), d.subtype(), d.operation()), d);
      map.putIfAbsent(eventKey(d.type(), d.subtype(), d.operation()), d);
    }
    return map;
  }

  private static String eventKey(String structure, String subtype, String operation) {
    return (structure + "|" + (subtype == null ? "" : subtype) + "|" + operation)
        .toLowerCase(Locale.ROOT);
  }
}

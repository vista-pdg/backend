package com.vista.pdg.telemetry.service;

import com.vista.pdg.academic.dto.CourseDto;
import com.vista.pdg.academic.service.TeachingAssignmentService;
import com.vista.pdg.auth.entity.User;
import com.vista.pdg.telemetry.dto.PedagogicalReport;
import com.vista.pdg.telemetry.repository.PedagogicalAnalyticsRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PedagogicalAnalyticsService {
  private final TeachingAssignmentService assignments;
  private final PedagogicalAnalyticsRepository repository;
  private final double threshold;

  public PedagogicalAnalyticsService(
      TeachingAssignmentService assignments,
      PedagogicalAnalyticsRepository repository,
      @Value("${analytics.retry-threshold:0.30}") double threshold) {
    if (!Double.isFinite(threshold) || threshold < 0 || threshold > 1) {
      throw new IllegalArgumentException("analytics.retry-threshold debe estar entre 0 y 1");
    }
    this.assignments = assignments;
    this.repository = repository;
    this.threshold = threshold;
  }

  /** Una instantánea consistente evita denominadores de consultas con poblaciones distintas. */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public PedagogicalReport report(User actor, AnalyticsFilter filter) {
    var course = assignments.requireAccess(actor, filter.courseCode());
    String term = course.getTerm().getCode();
    return AnalyticsDisclosure.disclose(
        CourseDto.from(course),
        filter,
        threshold,
        repository.structures(filter, term),
        repository.algorithms(filter, term),
        repository.retries(filter, term));
  }
}

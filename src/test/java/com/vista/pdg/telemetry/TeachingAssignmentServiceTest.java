package com.vista.pdg.telemetry;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.vista.pdg.academic.entity.*;
import com.vista.pdg.academic.repository.*;
import com.vista.pdg.academic.service.TeachingAssignmentService;
import com.vista.pdg.auth.entity.*;
import com.vista.pdg.auth.repository.UserRepository;
import com.vista.pdg.telemetry.service.AnalyticsRequestException;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class TeachingAssignmentServiceTest {
  @Mock CourseRepository courses;
  @Mock TeachingAssignmentRepository assignments;
  @Mock UserRepository users;
  @InjectMocks TeachingAssignmentService service;

  User user(String role) {
    return User.builder().id(9L).roles(Set.of(Role.builder().name(role).build())).build();
  }

  Course course() {
    return Course.builder()
        .id(1L)
        .code("CEDI-G1")
        .name("CEDI")
        .term(AcademicTerm.builder().code("2026-1").build())
        .build();
  }

  @Test
  void teacherSeesOnlyAssignedCourses() {
    when(assignments.findByTeacher_IdOrderByCourse_CodeAsc(9L))
        .thenReturn(List.of(TeachingAssignment.builder().course(course()).build()));
    assertThat(service.availableCourses(user("TEACHER")))
        .extracting(c -> c.code())
        .containsExactly("CEDI-G1");
    verifyNoInteractions(courses);
  }

  @Test
  void adminCanSeeAllCoursesButStudentAndDisabledUsersCannot() {
    when(courses.findAll(Sort.by("code"))).thenReturn(List.of(course()));
    assertThat(service.availableCourses(user("ADMIN"))).hasSize(1);
    assertThatThrownBy(() -> service.availableCourses(user("STUDENT")))
        .isInstanceOf(AnalyticsRequestException.class);
    User disabled = user("TEACHER");
    disabled.setEnabled(false);
    assertThatThrownBy(() -> service.availableCourses(disabled))
        .isInstanceOf(AnalyticsRequestException.class);
    assertThatThrownBy(() -> service.availableCourses(null))
        .isInstanceOf(AnalyticsRequestException.class);
  }

  @Test
  void authorizationIsRecheckedAfterRevocation() {
    when(assignments.existsByCourse_CodeAndTeacher_Id("CEDI-G1", 9L)).thenReturn(true, false);
    when(courses.findByCode("CEDI-G1")).thenReturn(Optional.of(course()));
    User teacher = user("TEACHER");
    assertThat(service.requireAccess(teacher, "CEDI-G1").getCode()).isEqualTo("CEDI-G1");
    assertThatThrownBy(() -> service.requireAccess(teacher, "CEDI-G1"))
        .isInstanceOf(AnalyticsRequestException.class)
        .hasMessage("No tienes acceso a este curso");
    verify(courses, times(1)).findByCode("CEDI-G1");
  }

  @Test
  void unknownCourseDoesNotExposeAnExistenceOracle() {
    when(courses.findByCode("UNKNOWN")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.requireAccess(user("ADMIN"), "UNKNOWN"))
        .isInstanceOf(AnalyticsRequestException.class)
        .hasMessage("No tienes acceso a este curso");
  }

  @Test
  void assignmentsAreIdempotentAndRequireActiveTeacher() {
    when(courses.findByCode("CEDI-G1")).thenReturn(Optional.of(course()));
    when(users.findById(9L)).thenReturn(Optional.of(user("TEACHER")));
    when(assignments.existsByCourse_CodeAndTeacher_Id("CEDI-G1", 9L)).thenReturn(false, true);
    service.assign(user("ADMIN"), "CEDI-G1", 9L);
    service.assign(user("ADMIN"), "CEDI-G1", 9L);
    verify(assignments, times(1)).save(any());
    when(users.findById(9L)).thenReturn(Optional.of(user("STUDENT")));
    assertThatThrownBy(() -> service.assign(user("ADMIN"), "CEDI-G1", 9L))
        .isInstanceOf(AnalyticsRequestException.class);
  }

  @Test
  void administrativeListsAndRevocationRequireAdmin() {
    var teacher = user("TEACHER");
    teacher.setDisplayName("Docente");
    teacher.setEmail("docente@example.test");
    when(courses.findByCode("CEDI-G1")).thenReturn(Optional.of(course()));
    when(assignments.findByCourse_CodeOrderByTeacher_DisplayNameAsc("CEDI-G1"))
        .thenReturn(
            List.of(TeachingAssignment.builder().teacher(teacher).course(course()).build()));
    assertThat(service.teachers(user("ADMIN"), "CEDI-G1"))
        .singleElement()
        .satisfies(
            t -> {
              assertThat(t.id()).isEqualTo(9L);
              assertThat(t.displayName()).isEqualTo("Docente");
            });
    service.revoke(user("ADMIN"), "CEDI-G1", 9L);
    verify(assignments).deleteByCourse_CodeAndTeacher_Id("CEDI-G1", 9L);
    assertThatThrownBy(() -> service.revoke(teacher, "CEDI-G1", 9L))
        .isInstanceOf(AnalyticsRequestException.class);
    assertThatThrownBy(() -> service.teachers(teacher, "CEDI-G1"))
        .isInstanceOf(AnalyticsRequestException.class);
  }
}

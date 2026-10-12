package com.vista.pdg.academic.dto;

import jakarta.validation.constraints.NotBlank;

public record AssignStudentCourseRequest(
    @NotBlank(message = "Selecciona un curso") String courseCode) {}

package com.vista.pdg.academic.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateCourseRequest(
    @NotBlank(message = "Indica el código del curso") String code,
    @NotBlank(message = "Indica el nombre del curso") String name,
    @NotBlank(message = "Selecciona un periodo") String termCode) {}

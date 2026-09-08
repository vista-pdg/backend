package com.vista.pdg.telemetry.dto;

import java.util.Map;

/** Métricas agregadas. Ningún campo identifica a una persona ni lista seudónimos individuales. */
public record AnalyticsSummary(
    long totalGenerations,
    long distinctUsers,
    Map<String, Long> generationsByCourse,
    Map<String, Long> generationsByStructureType) {}

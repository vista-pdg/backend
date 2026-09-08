package com.vista.pdg.telemetry.dto;

import java.util.Map;

/**
 * Métricas agregadas. Ningún campo identifica a una persona ni lista seudónimos individuales.
 *
 * <p>{@code eventsByVisualizationMode} (HU-18 · CA-7) cuenta generaciones y ejecuciones de
 * algoritmo por el modo que el cliente tenía activo; los eventos que no informaron modo no aparecen
 * en ese desglose.
 */
public record AnalyticsSummary(
    long totalGenerations,
    long totalAlgorithmRuns,
    long distinctUsers,
    Map<String, Long> generationsByCourse,
    Map<String, Long> generationsByStructureType,
    Map<String, Long> eventsByVisualizationMode) {}

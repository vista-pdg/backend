package com.vista.pdg.telemetry.dto;

/**
 * Una secuencia de reintento (HU-21 · CA-4): varios intentos sobre el mismo tipo de estructura
 * dentro de la misma sesión de trabajo y en poco tiempo. Es la señal de que el estudiante no
 * consiguió lo que buscaba, que es justo lo que un docente quiere ver. No lleva seudónimo: agrupa
 * por sesión, no por persona. Las marcas de tiempo viajan como texto ISO-8601, igual que en el
 * resto de la API (el {@code ObjectMapper} de la aplicación no registra el módulo de Java 8).
 */
public record RetrySequence(
    String sessionId, String structureType, long attempts, String firstAt, String lastAt) {}

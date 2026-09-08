package com.vista.pdg.telemetry.dto;

/**
 * Un evento como lo ve el docente (HU-21 · CA-3: los prompts fuera de alcance «quedan almacenados
 * para revisión docente»).
 *
 * <p><b>No lleva seudónimo.</b> El docente revisa qué se pidió y cómo terminó, no quién lo pidió;
 * mantener fuera el identificador conserva la promesa de HU-16 · CA-4, que exige que la respuesta
 * del docente no exponga seudónimos. {@code sessionId} sí viaja: agrupa intentos, no personas, y
 * rota con cada sesión de trabajo.
 *
 * <p>{@code at} viaja como texto ISO-8601 y no como {@code Instant} porque el {@code ObjectMapper}
 * de la aplicación no registra el módulo de fechas de Java 8; misma decisión que en HU-32.
 */
public record EventView(
    String structureType,
    String algorithm,
    String source,
    String outcome,
    Integer stepCount,
    String visualizationMode,
    String sessionId,
    String promptText,
    String at) {}

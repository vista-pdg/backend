package com.vista.pdg.assistant.dto;

import java.time.Instant;

/**
 * Estado de la cuota del usuario. {@code warning} se enciende cuando quedan pocos mensajes (CA-4) y
 * {@code resetsAt} es la próxima medianoche en la zona del curso, para que el cliente pueda decir
 * cuándo vuelve la cuota sin tener que conocer la zona horaria.
 */
public record QuotaStatus(
    int limit,
    int used,
    int remaining,
    Instant resetsAt,
    int ratePerMinute,
    boolean warning,
    int warningThreshold) {}

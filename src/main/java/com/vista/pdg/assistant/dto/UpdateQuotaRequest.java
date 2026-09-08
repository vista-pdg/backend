package com.vista.pdg.assistant.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateQuotaRequest(
    @NotNull(message = "La cuota diaria es obligatoria")
        @Min(value = 1, message = "La cuota diaria debe ser al menos 1")
        @Max(value = 1000, message = "La cuota diaria no puede superar 1000")
        Integer dailyQuota) {}

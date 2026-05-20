package com.vista.pdg.model.contract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record VisualOptions(String layout, String colorBy) {
}

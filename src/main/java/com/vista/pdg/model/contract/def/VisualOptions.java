package com.vista.pdg.model.contract.def;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record VisualOptions(String layout, String colorBy) {}

package com.vista.pdg.controller.dto;

import java.util.List;

public record AlgorithmRequest(
    String type, String subtype, String operation, List<Integer> values) {}

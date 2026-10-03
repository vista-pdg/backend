package com.vista.pdg.model.response;

import java.util.List;
import java.util.Map;

/**
 * Code is read-only data. Keys are logical pseudocode lines; values are explicit 1-based Java
 * lines.
 */
public record CodeRepresentation(
    String language,
    String label,
    String fileName,
    List<String> code,
    Map<Integer, List<Integer>> lineMap,
    String sourceLabel,
    String sourceUrl) {}

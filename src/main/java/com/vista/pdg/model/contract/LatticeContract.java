package com.vista.pdg.model.contract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LatticeContract(
    String type,
    VisualOptions visual,
    String subtype,
    Map<String, Object> params,
    List<String> elements,
    List<List<String>> order
) implements StructureContract {
}

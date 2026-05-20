package com.vista.pdg.model.contract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RelationContract(
    String type,
    VisualOptions visual,
    List<Object> set,
    String rule,
    List<List<Object>> pairs,
    List<String> check
) implements StructureContract {
}

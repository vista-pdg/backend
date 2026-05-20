package com.vista.pdg.model.contract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GraphContract(
    String type,
    VisualOptions visual,
    boolean directed,
    boolean weighted,
    List<String> labels,
    MatrixDef matrix
) implements StructureContract {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MatrixDef(
        String kind,
        List<List<Integer>> data,
        List<EdgeMeta> edges
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EdgeMeta(String id, String label, Integer weight) {}
}

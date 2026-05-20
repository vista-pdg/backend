package com.vista.pdg.model.contract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.vista.pdg.service.sdd.def.ContractValidatorVisitor;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TreeContract(
    String type,
    VisualOptions visual,
    String subtype,
    List<Operation> operations,
    List<NodeDef> nodes
) implements StructureContract {

    @Override
    public void accept(ContractValidatorVisitor visitor) {
        visitor.visit(this);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Operation(String op, List<Integer> values) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NodeDef(String id, int value, String parent, String side) {}
}

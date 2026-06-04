package com.vista.pdg.model.contract.def;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.vista.pdg.model.contract.GraphContract;
import com.vista.pdg.model.contract.HashTableContract;
import com.vista.pdg.model.contract.LinkedListContract;
import com.vista.pdg.model.contract.TreeContract;
import com.vista.pdg.service.sdd.def.ContractValidatorVisitor;
import com.vista.pdg.service.sdd.def.Validatable;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type",
    visible = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = GraphContract.class, name = "graph"),
  @JsonSubTypes.Type(value = TreeContract.class, name = "tree"),
  @JsonSubTypes.Type(value = LinkedListContract.class, name = "linked-list"),
  @JsonSubTypes.Type(value = HashTableContract.class, name = "hash-table")
})
public interface StructureContract extends Validatable {
  String type();

  VisualOptions visual();

  @Override
  void accept(ContractValidatorVisitor visitor);
}

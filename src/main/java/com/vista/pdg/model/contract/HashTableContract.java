package com.vista.pdg.model.contract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.vista.pdg.model.contract.def.StructureContract;
import com.vista.pdg.model.contract.def.VisualOptions;
import com.vista.pdg.service.sdd.def.ContractValidatorVisitor;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HashTableContract(
    String type,
    VisualOptions visual,
    int size, // number of buckets
    List<Integer> values,
    String hashFunction) // "modular" (default)
    implements StructureContract {

  @Override
  public void accept(ContractValidatorVisitor visitor) {
    visitor.visit(this);
  }
}

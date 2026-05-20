package com.vista.pdg.model.contract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.vista.pdg.model.contract.def.StructureContract;
import com.vista.pdg.model.contract.def.VisualOptions;
import com.vista.pdg.service.sdd.def.ContractValidatorVisitor;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RelationContract(
    String type,
    VisualOptions visual,
    List<Object> set,
    String rule,
    List<List<Object>> pairs,
    List<String> check)
    implements StructureContract {

  @Override
  public void accept(ContractValidatorVisitor visitor) {
    visitor.visit(this);
  }
}

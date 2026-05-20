package com.vista.pdg.model.contract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.vista.pdg.model.contract.def.StructureContract;
import com.vista.pdg.model.contract.def.VisualOptions;
import com.vista.pdg.service.sdd.def.ContractValidatorVisitor;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LatticeContract(
    String type,
    VisualOptions visual,
    String subtype,
    Map<String, Object> params,
    List<String> elements,
    List<List<String>> order)
    implements StructureContract {

  @Override
  public void accept(ContractValidatorVisitor visitor) {
    visitor.visit(this);
  }
}

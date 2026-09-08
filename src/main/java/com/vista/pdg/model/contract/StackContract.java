package com.vista.pdg.model.contract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.vista.pdg.model.contract.def.StructureContract;
import com.vista.pdg.model.contract.def.VisualOptions;
import com.vista.pdg.service.sdd.def.ContractValidatorVisitor;
import java.util.List;

/** Pila LIFO (HU-19). {@code values} va de la base al tope: el último es el tope. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StackContract(String type, VisualOptions visual, List<Integer> values)
    implements StructureContract {

  @Override
  public void accept(ContractValidatorVisitor visitor) {
    visitor.visit(this);
  }
}

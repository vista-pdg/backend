package com.vista.pdg.service.sdd.impl.validator;

import com.vista.pdg.exception.InvalidContractException;
import com.vista.pdg.model.contract.StackContract;
import com.vista.pdg.service.sdd.def.SpecificValidator;
import org.springframework.stereotype.Component;

@Component
public class StackContractValidator implements SpecificValidator<StackContract> {

  @Override
  public void validate(StackContract c) {
    if (c.values() == null || c.values().isEmpty())
      throw new InvalidContractException("Stack must have at least one value");
    if (c.values().stream().anyMatch(v -> v == null))
      throw new InvalidContractException("Stack values must not contain nulls");
  }
}

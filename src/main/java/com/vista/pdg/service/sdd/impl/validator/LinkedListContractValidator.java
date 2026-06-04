package com.vista.pdg.service.sdd.impl.validator;

import com.vista.pdg.exception.InvalidContractException;
import com.vista.pdg.model.contract.LinkedListContract;
import com.vista.pdg.service.sdd.def.SpecificValidator;
import org.springframework.stereotype.Component;

@Component
public class LinkedListContractValidator implements SpecificValidator<LinkedListContract> {

  @Override
  public void validate(LinkedListContract c) {
    if (c.values() == null || c.values().isEmpty())
      throw new InvalidContractException("LinkedList must have at least one value");
  }
}

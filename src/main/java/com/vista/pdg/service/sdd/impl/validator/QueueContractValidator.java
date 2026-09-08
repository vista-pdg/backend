package com.vista.pdg.service.sdd.impl.validator;

import com.vista.pdg.exception.InvalidContractException;
import com.vista.pdg.model.contract.QueueContract;
import com.vista.pdg.service.sdd.def.SpecificValidator;
import org.springframework.stereotype.Component;

@Component
public class QueueContractValidator implements SpecificValidator<QueueContract> {

  @Override
  public void validate(QueueContract c) {
    if (c.values() == null || c.values().isEmpty())
      throw new InvalidContractException("Queue must have at least one value");
    if (c.values().stream().anyMatch(v -> v == null))
      throw new InvalidContractException("Queue values must not contain nulls");
  }
}

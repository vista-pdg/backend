package com.vista.pdg.service.sdd.impl.validator;

import com.vista.pdg.exception.InvalidContractException;
import com.vista.pdg.model.contract.HashTableContract;
import com.vista.pdg.service.sdd.def.SpecificValidator;
import org.springframework.stereotype.Component;

@Component
public class HashTableContractValidator implements SpecificValidator<HashTableContract> {

  @Override
  public void validate(HashTableContract c) {
    if (c.size() <= 0)
      throw new InvalidContractException("HashTable size must be a positive integer");
    if (c.values() == null || c.values().isEmpty())
      throw new InvalidContractException("HashTable must have at least one value");
  }
}

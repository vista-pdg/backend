package com.vista.pdg.service.sdd.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vista.pdg.exception.InvalidContractException;
import com.vista.pdg.model.contract.def.StructureContract;
import org.springframework.stereotype.Component;

@Component
public class ContractBuilder {

  private final ObjectMapper mapper;
  private final ContractValidator validator;

  public ContractBuilder(ObjectMapper mapper, ContractValidator validator) {
    this.mapper = mapper;
    this.validator = validator;
  }

  public StructureContract build(String json) {
    try {
      StructureContract contract = mapper.readValue(json, StructureContract.class);
      validator.validate(contract);
      return contract;

    } catch (InvalidContractException e) {
      throw e;
    } catch (Exception e) {
      throw new InvalidContractException("Failed to parse contract JSON: " + e.getMessage());
    }
  }
}

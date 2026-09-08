package com.vista.pdg.service.sdd.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vista.pdg.exception.InvalidContractException;
import com.vista.pdg.model.contract.def.StructureContract;
import org.springframework.stereotype.Component;

@Component
public class ContractBuilder {

  private final ObjectMapper mapper;
  private final ContractValidator validator;
  private final ContractNormalizer normalizer;

  public ContractBuilder(
      ObjectMapper mapper, ContractValidator validator, ContractNormalizer normalizer) {
    this.mapper = mapper;
    this.validator = validator;
    this.normalizer = normalizer;
  }

  /** Normaliza (formas laxas → contrato estricto), deserializa y valida. */
  public StructureContract build(String json) {
    try {
      StructureContract contract =
          mapper.readValue(normalizer.normalize(json), StructureContract.class);
      validator.validate(contract);
      return contract;

    } catch (InvalidContractException e) {
      throw e;
    } catch (Exception e) {
      throw new InvalidContractException("Failed to parse contract JSON: " + e.getMessage());
    }
  }
}

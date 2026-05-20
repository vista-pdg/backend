package com.vista.pdg.service.generator.def;

import com.vista.pdg.model.contract.def.StructureContract;
import com.vista.pdg.model.generated.GeneratedStructure;

public interface StructureGenerator<T extends StructureContract> {
  String supportedType();

  GeneratedStructure generate(T contract);
}

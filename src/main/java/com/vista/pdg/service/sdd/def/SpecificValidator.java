package com.vista.pdg.service.sdd.def;

import com.vista.pdg.model.contract.StructureContract;

public interface SpecificValidator<T extends StructureContract> {
    void validate(T contract);
}

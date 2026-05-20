package com.vista.pdg.service.sdd.def;

public interface Validatable {
    void accept(ContractValidatorVisitor visitor);
}

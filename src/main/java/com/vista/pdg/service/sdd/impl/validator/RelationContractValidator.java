package com.vista.pdg.service.sdd.impl.validator;

import com.vista.pdg.exception.InvalidContractException;
import com.vista.pdg.model.contract.RelationContract;
import org.springframework.stereotype.Component;

import com.vista.pdg.service.sdd.def.SpecificValidator;

@Component
public class RelationContractValidator implements SpecificValidator<RelationContract> {

    @Override
    public void validate(RelationContract r) {
        if (r.set() == null || r.set().isEmpty())
            throw new InvalidContractException("Relation set must not be empty");
        boolean hasRule  = r.rule()  != null && !r.rule().isBlank();
        boolean hasPairs = r.pairs() != null && !r.pairs().isEmpty();
        if (!hasRule && !hasPairs)
            throw new InvalidContractException("Relation must specify either a rule or explicit pairs");
    }
}

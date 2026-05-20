package com.vista.pdg.service.sdd.impl.validator;

import com.vista.pdg.exception.InvalidContractException;
import com.vista.pdg.model.contract.TreeContract;
import org.springframework.stereotype.Component;

import com.vista.pdg.service.sdd.def.SpecificValidator;

@Component
public class TreeContractValidator implements SpecificValidator<TreeContract> {

    @Override
    public void validate(TreeContract t) {
        boolean hasOps   = t.operations() != null && !t.operations().isEmpty();
        boolean hasNodes = t.nodes()      != null && !t.nodes().isEmpty();
        if (!hasOps && !hasNodes)
            throw new InvalidContractException("Tree must have either operations or pre-built nodes");
    }
}

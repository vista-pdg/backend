package com.vista.pdg.service.sdd.impl;

import com.vista.pdg.model.contract.GraphContract;
import com.vista.pdg.model.contract.LatticeContract;
import com.vista.pdg.model.contract.RelationContract;
import com.vista.pdg.model.contract.StructureContract;
import com.vista.pdg.model.contract.TreeContract;
import com.vista.pdg.service.sdd.def.ContractValidatorVisitor;
import com.vista.pdg.service.sdd.def.SpecificValidator;

import org.springframework.stereotype.Component;

@Component
public class ContractValidator implements ContractValidatorVisitor {

    private final SpecificValidator<GraphContract>    graphValidator;
    private final SpecificValidator<TreeContract>     treeValidator;
    private final SpecificValidator<LatticeContract>  latticeValidator;
    private final SpecificValidator<RelationContract> relationValidator;

    public ContractValidator(SpecificValidator<GraphContract> graphValidator,
                              SpecificValidator<TreeContract> treeValidator,
                              SpecificValidator<LatticeContract> latticeValidator,
                              SpecificValidator<RelationContract> relationValidator) {
        this.graphValidator    = graphValidator;
        this.treeValidator     = treeValidator;
        this.latticeValidator  = latticeValidator;
        this.relationValidator = relationValidator;
    }

    public void validate(StructureContract contract) {
        contract.accept(this);
    }

    @Override
    public void visit(GraphContract g)    { graphValidator.validate(g);    }

    @Override
    public void visit(TreeContract t)     { treeValidator.validate(t);     }

    @Override
    public void visit(LatticeContract l)  { latticeValidator.validate(l);  }

    @Override
    public void visit(RelationContract r) { relationValidator.validate(r); }
}

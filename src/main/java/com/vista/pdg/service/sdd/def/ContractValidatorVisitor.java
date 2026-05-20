package com.vista.pdg.service.sdd.def;

import com.vista.pdg.model.contract.GraphContract;
import com.vista.pdg.model.contract.LatticeContract;
import com.vista.pdg.model.contract.RelationContract;
import com.vista.pdg.model.contract.TreeContract;

public interface ContractValidatorVisitor {
    void visit(GraphContract contract);
    void visit(TreeContract contract);
    void visit(LatticeContract contract);
    void visit(RelationContract contract);
}
    
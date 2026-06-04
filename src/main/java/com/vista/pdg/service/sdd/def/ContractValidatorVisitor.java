package com.vista.pdg.service.sdd.def;

import com.vista.pdg.model.contract.GraphContract;
import com.vista.pdg.model.contract.HashTableContract;
import com.vista.pdg.model.contract.LinkedListContract;
import com.vista.pdg.model.contract.TreeContract;

public interface ContractValidatorVisitor {
  void visit(GraphContract contract);

  void visit(TreeContract contract);

  void visit(LinkedListContract contract);

  void visit(HashTableContract contract);
}

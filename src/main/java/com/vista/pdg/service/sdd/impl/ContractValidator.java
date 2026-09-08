package com.vista.pdg.service.sdd.impl;

import com.vista.pdg.model.contract.GraphContract;
import com.vista.pdg.model.contract.HashTableContract;
import com.vista.pdg.model.contract.LinkedListContract;
import com.vista.pdg.model.contract.QueueContract;
import com.vista.pdg.model.contract.StackContract;
import com.vista.pdg.model.contract.TreeContract;
import com.vista.pdg.model.contract.def.StructureContract;
import com.vista.pdg.service.sdd.def.ContractValidatorVisitor;
import com.vista.pdg.service.sdd.def.SpecificValidator;
import org.springframework.stereotype.Component;

@Component
public class ContractValidator implements ContractValidatorVisitor {

  private final SpecificValidator<GraphContract> graphValidator;
  private final SpecificValidator<TreeContract> treeValidator;
  private final SpecificValidator<LinkedListContract> linkedListValidator;
  private final SpecificValidator<HashTableContract> hashTableValidator;
  private final SpecificValidator<StackContract> stackValidator;
  private final SpecificValidator<QueueContract> queueValidator;

  public ContractValidator(
      SpecificValidator<GraphContract> graphValidator,
      SpecificValidator<TreeContract> treeValidator,
      SpecificValidator<LinkedListContract> linkedListValidator,
      SpecificValidator<HashTableContract> hashTableValidator,
      SpecificValidator<StackContract> stackValidator,
      SpecificValidator<QueueContract> queueValidator) {
    this.graphValidator = graphValidator;
    this.treeValidator = treeValidator;
    this.linkedListValidator = linkedListValidator;
    this.hashTableValidator = hashTableValidator;
    this.stackValidator = stackValidator;
    this.queueValidator = queueValidator;
  }

  public void validate(StructureContract contract) {
    contract.accept(this);
  }

  @Override
  public void visit(GraphContract g) {
    graphValidator.validate(g);
  }

  @Override
  public void visit(TreeContract t) {
    treeValidator.validate(t);
  }

  @Override
  public void visit(LinkedListContract c) {
    linkedListValidator.validate(c);
  }

  @Override
  public void visit(HashTableContract c) {
    hashTableValidator.validate(c);
  }

  @Override
  public void visit(StackContract c) {
    stackValidator.validate(c);
  }

  @Override
  public void visit(QueueContract c) {
    queueValidator.validate(c);
  }
}

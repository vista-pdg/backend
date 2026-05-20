package com.vista.pdg.service.sdd.impl.validator;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vista.pdg.exception.InvalidContractException;
import com.vista.pdg.model.contract.TreeContract;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TreeContractValidatorTest {

  private TreeContractValidator validator;

  @BeforeEach
  void setUp() {
    validator = new TreeContractValidator();
  }

  @Test
  void withOperations_passes() {
    TreeContract t =
        new TreeContract(
            "tree",
            null,
            "avl",
            List.of(new TreeContract.Operation("insert", List.of(10, 5, 15))),
            null);
    assertThatNoException().isThrownBy(() -> validator.validate(t));
  }

  @Test
  void withPrebuiltNodes_passes() {
    TreeContract t =
        new TreeContract(
            "tree", null, "bst", null, List.of(new TreeContract.NodeDef("n0", 10, null, null)));
    assertThatNoException().isThrownBy(() -> validator.validate(t));
  }

  @Test
  void noOperationsNoNodes_throws() {
    TreeContract t = new TreeContract("tree", null, "avl", null, null);
    assertThatThrownBy(() -> validator.validate(t))
        .isInstanceOf(InvalidContractException.class)
        .hasMessageContaining("operations or pre-built nodes");
  }

  @Test
  void emptyOperationsEmptyNodes_throws() {
    TreeContract t = new TreeContract("tree", null, "avl", List.of(), List.of());
    assertThatThrownBy(() -> validator.validate(t))
        .isInstanceOf(InvalidContractException.class)
        .hasMessageContaining("operations or pre-built nodes");
  }
}

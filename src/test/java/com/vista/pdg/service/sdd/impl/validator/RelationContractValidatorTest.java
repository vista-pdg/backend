package com.vista.pdg.service.sdd.impl.validator;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vista.pdg.exception.InvalidContractException;
import com.vista.pdg.model.contract.RelationContract;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RelationContractValidatorTest {

  private RelationContractValidator validator;

  @BeforeEach
  void setUp() {
    validator = new RelationContractValidator();
  }

  @Test
  void withRule_passes() {
    RelationContract r =
        new RelationContract(
            "relation", null, List.of(1, 2, 3), "divides", null, List.of("reflexive"));
    assertThatNoException().isThrownBy(() -> validator.validate(r));
  }

  @Test
  void withExplicitPairs_passes() {
    RelationContract r =
        new RelationContract("relation", null, List.of(1, 2), null, List.of(List.of(1, 2)), null);
    assertThatNoException().isThrownBy(() -> validator.validate(r));
  }

  @Test
  void emptySet_throws() {
    RelationContract r = new RelationContract("relation", null, List.of(), "divides", null, null);
    assertThatThrownBy(() -> validator.validate(r))
        .isInstanceOf(InvalidContractException.class)
        .hasMessageContaining("set must not be empty");
  }

  @Test
  void noRuleNoPairs_throws() {
    RelationContract r = new RelationContract("relation", null, List.of(1, 2), null, null, null);
    assertThatThrownBy(() -> validator.validate(r))
        .isInstanceOf(InvalidContractException.class)
        .hasMessageContaining("rule or explicit pairs");
  }
}

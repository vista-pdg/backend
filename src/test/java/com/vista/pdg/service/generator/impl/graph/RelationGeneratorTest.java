package com.vista.pdg.service.generator.impl.graph;

import static org.assertj.core.api.Assertions.assertThat;

import com.vista.pdg.model.contract.RelationContract;
import com.vista.pdg.model.generated.GeneratedStructure;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RelationGeneratorTest {

  private RelationGenerator generator;

  @BeforeEach
  void setUp() {
    generator = new RelationGenerator();
  }

  @Test
  void dividesRule_computesPairsCorrectly() {
    RelationContract r =
        new RelationContract(
            "relation",
            null,
            List.of(1, 2, 3, 6),
            "divides",
            null,
            List.of("reflexive", "antisymmetric", "transitive"));

    GeneratedStructure result = generator.generate(r);

    // 1|1, 1|2, 1|3, 1|6, 2|2, 2|6, 3|3, 3|6, 6|6 = 9 pairs
    assertThat(result.edges()).hasSize(9);
  }

  @Test
  void dividesRelation_isPartialOrder() {
    RelationContract r =
        new RelationContract(
            "relation",
            null,
            List.of(1, 2, 3, 6),
            "divides",
            null,
            List.of("reflexive", "antisymmetric", "transitive"));

    GeneratedStructure result = generator.generate(r);

    assertThat(result.computedProperties().get("reflexive")).isEqualTo(true);
    assertThat(result.computedProperties().get("antisymmetric")).isEqualTo(true);
    assertThat(result.computedProperties().get("transitive")).isEqualTo(true);
  }

  @Test
  void explicitPairs_usedDirectly() {
    RelationContract r =
        new RelationContract(
            "relation", null, List.of(1, 2, 3), null, List.of(List.of(1, 2), List.of(2, 3)), null);

    GeneratedStructure result = generator.generate(r);

    assertThat(result.edges()).hasSize(2);
  }

  @Test
  void equivalenceRelation_computesClasses() {
    // Congruence mod 2 on {1,2,3,4} — reflexive, symmetric, transitive
    RelationContract r =
        new RelationContract(
            "relation",
            null,
            List.of(1, 2, 3, 4),
            "congruent_mod_n",
            null,
            List.of("reflexive", "symmetric", "transitive"));

    GeneratedStructure result = generator.generate(r);

    assertThat(result.computedProperties()).containsKey("equivalenceClasses");
    @SuppressWarnings("unchecked")
    List<List<Object>> classes =
        (List<List<Object>>) result.computedProperties().get("equivalenceClasses");
    assertThat(classes).hasSize(2); // odds {1,3} and evens {2,4}
  }

  @Test
  void lessThanRule_isStrictOrder() {
    RelationContract r =
        new RelationContract(
            "relation",
            null,
            List.of(1, 2, 3),
            "less_than",
            null,
            List.of("reflexive", "antisymmetric"));

    GeneratedStructure result = generator.generate(r);

    // less_than is not reflexive
    assertThat(result.computedProperties().get("reflexive")).isEqualTo(false);
  }
}

package com.vista.pdg.service.generator.impl.graph;

import com.vista.pdg.model.contract.RelationContract;
import com.vista.pdg.model.generated.Edge3D;
import com.vista.pdg.model.generated.GeneratedStructure;
import com.vista.pdg.model.generated.Node3D;
import com.vista.pdg.service.generator.def.StructureGenerator;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class RelationGenerator implements StructureGenerator<RelationContract> {

  @Override
  public String supportedType() {
    return "relation";
  }

  @Override
  public GeneratedStructure generate(RelationContract contract) {
    List<Object> set = contract.set();
    Map<Object, String> idMap = new LinkedHashMap<>();
    List<Node3D> nodes = new ArrayList<>();
    for (int i = 0; i < set.size(); i++) {
      String id = "n" + i;
      idMap.put(set.get(i), id);
      nodes.add(new Node3D(id, String.valueOf(set.get(i)), 0, 0, 0, 0, null, Map.of()));
    }

    List<List<Object>> pairs = computePairs(contract, set);
    List<Edge3D> edges = new ArrayList<>();
    int edgeIdx = 0;
    for (List<Object> pair : pairs) {
      String from = idMap.get(pair.get(0)), to = idMap.get(pair.get(1));
      if (from != null && to != null) edges.add(new Edge3D("e" + edgeIdx++, from, to, null, true));
    }

    Map<String, Object> props = computeProperties(contract, set, pairs);
    return new GeneratedStructure(contract, nodes, edges, props);
  }

  private List<List<Object>> computePairs(RelationContract contract, List<Object> set) {
    if (contract.pairs() != null && !contract.pairs().isEmpty()) return contract.pairs();
    List<List<Object>> result = new ArrayList<>();
    for (Object a : set)
      for (Object b : set) if (satisfies(contract.rule(), a, b)) result.add(List.of(a, b));
    return result;
  }

  private boolean satisfies(String rule, Object a, Object b) {
    int ia = toInt(a), ib = toInt(b);
    return switch (rule) {
      case "divides" -> ia != 0 && ib % ia == 0;
      case "less_than" -> ia < ib;
      case "congruent_mod_n" -> ia % 2 == ib % 2;
      default -> false;
    };
  }

  private int toInt(Object o) {
    if (o instanceof Number n) return n.intValue();
    try {
      return Integer.parseInt(o.toString());
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private Map<String, Object> computeProperties(
      RelationContract contract, List<Object> set, List<List<Object>> pairs) {
    if (contract.check() == null || contract.check().isEmpty()) return Map.of();
    Set<String> pairSet = new HashSet<>();
    for (List<Object> p : pairs) pairSet.add(p.get(0) + ":" + p.get(1));

    Map<String, Object> result = new LinkedHashMap<>();
    for (String prop : contract.check()) {
      result.put(
          prop,
          switch (prop) {
            case "reflexive" -> isReflexive(set, pairSet);
            case "symmetric" -> isSymmetric(pairs, pairSet);
            case "antisymmetric" -> isAntisymmetric(pairs, pairSet);
            case "transitive" -> isTransitive(pairs, pairSet);
            default -> false;
          });
    }

    boolean isEquivalence =
        Boolean.TRUE.equals(result.get("reflexive"))
            && Boolean.TRUE.equals(result.get("symmetric"))
            && Boolean.TRUE.equals(result.get("transitive"));
    if (isEquivalence) result.put("equivalenceClasses", computeClasses(set, pairs));
    return result;
  }

  private boolean isReflexive(List<Object> set, Set<String> pairs) {
    return set.stream().allMatch(e -> pairs.contains(e + ":" + e));
  }

  private boolean isSymmetric(List<List<Object>> pairs, Set<String> pairSet) {
    return pairs.stream().allMatch(p -> pairSet.contains(p.get(1) + ":" + p.get(0)));
  }

  private boolean isAntisymmetric(List<List<Object>> pairs, Set<String> pairSet) {
    return pairs.stream()
        .allMatch(p -> p.get(0).equals(p.get(1)) || !pairSet.contains(p.get(1) + ":" + p.get(0)));
  }

  private boolean isTransitive(List<List<Object>> pairs, Set<String> pairSet) {
    for (List<Object> p1 : pairs)
      for (List<Object> p2 : pairs)
        if (p1.get(1).equals(p2.get(0)) && !pairSet.contains(p1.get(0) + ":" + p2.get(1)))
          return false;
    return true;
  }

  private List<List<Object>> computeClasses(List<Object> set, List<List<Object>> pairs) {
    Map<Object, Object> rep = new LinkedHashMap<>();
    for (Object e : set) rep.put(e, e);
    for (List<Object> p : pairs) {
      Object ra = find(rep, p.get(0)), rb = find(rep, p.get(1));
      if (!ra.equals(rb)) rep.put(ra, rb);
    }
    Map<Object, List<Object>> classes = new LinkedHashMap<>();
    for (Object e : set) classes.computeIfAbsent(find(rep, e), k -> new ArrayList<>()).add(e);
    return new ArrayList<>(classes.values());
  }

  private Object find(Map<Object, Object> rep, Object e) {
    while (!rep.get(e).equals(e)) e = rep.get(e);
    return e;
  }
}

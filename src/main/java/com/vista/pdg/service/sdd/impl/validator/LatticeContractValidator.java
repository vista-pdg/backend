package com.vista.pdg.service.sdd.impl.validator;

import com.vista.pdg.exception.InvalidContractException;
import com.vista.pdg.model.contract.LatticeContract;
import com.vista.pdg.service.sdd.def.SpecificValidator;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class LatticeContractValidator implements SpecificValidator<LatticeContract> {

  @Override
  public void validate(LatticeContract l) {
    if (l.subtype() == null || l.subtype().isBlank())
      throw new InvalidContractException("Lattice subtype is required");
    if ("custom".equals(l.subtype())) {
      validateCustomElements(l);
      if (l.order() != null) validateNoCycles(l.elements(), l.order());
    }
  }

  private void validateCustomElements(LatticeContract l) {
    if (l.elements() == null || l.elements().isEmpty())
      throw new InvalidContractException("Custom lattice requires elements");
  }

  private void validateNoCycles(List<String> elements, List<List<String>> order) {
    Map<String, List<String>> adj = new HashMap<>();
    for (String e : elements) adj.put(e, new ArrayList<>());
    for (List<String> pair : order)
      if (pair.size() == 2)
        adj.computeIfAbsent(pair.get(0), k -> new ArrayList<>()).add(pair.get(1));

    Set<String> visited = new HashSet<>(), inStack = new HashSet<>();
    for (String node : elements)
      if (!visited.contains(node) && hasCycle(node, adj, visited, inStack))
        throw new InvalidContractException("Order relation contains a cycle involving: " + node);
  }

  private boolean hasCycle(
      String node, Map<String, List<String>> adj, Set<String> visited, Set<String> inStack) {
    visited.add(node);
    inStack.add(node);
    for (String neighbor : adj.getOrDefault(node, List.of())) {
      if (inStack.contains(neighbor)) return true;
      if (!visited.contains(neighbor) && hasCycle(neighbor, adj, visited, inStack)) return true;
    }
    inStack.remove(node);
    return false;
  }
}

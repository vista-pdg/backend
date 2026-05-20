package com.vista.pdg.service.sdd.impl;

import com.vista.pdg.exception.InvalidContractException;
import com.vista.pdg.model.contract.GraphContract;
import com.vista.pdg.model.contract.LatticeContract;
import com.vista.pdg.model.contract.RelationContract;
import com.vista.pdg.model.contract.StructureContract;
import com.vista.pdg.model.contract.TreeContract;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ContractValidator {

    public void validate(StructureContract contract) {
        switch (contract) {
            case GraphContract g    -> validateGraph(g);
            case TreeContract t     -> validateTree(t);
            case LatticeContract l  -> validateLattice(l);
            case RelationContract r -> validateRelation(r);
            default -> throw new InvalidContractException("Unknown contract type: " + contract.type());
        }
    }

    private void validateGraph(GraphContract g) {
        if (g.labels() == null || g.labels().isEmpty())
            throw new InvalidContractException("Graph must have at least one label");
        if (g.matrix() == null || g.matrix().data() == null)
            throw new InvalidContractException("Graph matrix data is required");

        List<List<Integer>> data = g.matrix().data();
        int n = g.labels().size();

        if (data.size() != n)
            throw new InvalidContractException("Matrix row count (" + data.size() + ") != label count (" + n + ")");

        for (int i = 0; i < data.size(); i++)
            if (data.get(i).size() != n)
                throw new InvalidContractException("Matrix row " + i + " has " + data.get(i).size() + " columns, expected " + n);

        String kind = g.matrix().kind();
        if ("adjacency".equals(kind) && !g.directed()) {
            for (int i = 0; i < n; i++)
                for (int j = i + 1; j < n; j++)
                    if (!data.get(i).get(j).equals(data.get(j).get(i)))
                        throw new InvalidContractException("Asymmetric adjacency matrix at [" + i + "][" + j + "]");
        } else if ("incidence".equals(kind)) {
            int cols = data.get(0).size();
            for (int j = 0; j < cols; j++) {
                int sum = 0;
                for (int i = 0; i < n; i++) sum += data.get(i).get(j);
                int expected = g.directed() ? 0 : 2;
                if (sum != expected)
                    throw new InvalidContractException("Incidence column " + j + " sums to " + sum + " (expected " + expected + ")");
            }
        }
    }

    private void validateTree(TreeContract t) {
        boolean hasOps   = t.operations() != null && !t.operations().isEmpty();
        boolean hasNodes = t.nodes()      != null && !t.nodes().isEmpty();
        if (!hasOps && !hasNodes)
            throw new InvalidContractException("Tree must have either operations or pre-built nodes");
    }

    private void validateLattice(LatticeContract l) {
        if (l.subtype() == null || l.subtype().isBlank())
            throw new InvalidContractException("Lattice subtype is required");
        if ("custom".equals(l.subtype())) {
            if (l.elements() == null || l.elements().isEmpty())
                throw new InvalidContractException("Custom lattice requires elements");
            if (l.order() != null) validateNoCycles(l.elements(), l.order());
        }
    }

    private void validateRelation(RelationContract r) {
        if (r.set() == null || r.set().isEmpty())
            throw new InvalidContractException("Relation set must not be empty");
        boolean hasRule  = r.rule()  != null && !r.rule().isBlank();
        boolean hasPairs = r.pairs() != null && !r.pairs().isEmpty();
        if (!hasRule && !hasPairs)
            throw new InvalidContractException("Relation must specify either a rule or explicit pairs");
    }

    private void validateNoCycles(List<String> elements, List<List<String>> order) {
        Map<String, List<String>> adj = new HashMap<>();
        for (String e : elements) adj.put(e, new ArrayList<>());
        for (List<String> pair : order)
            if (pair.size() == 2) adj.computeIfAbsent(pair.get(0), k -> new ArrayList<>()).add(pair.get(1));

        Set<String> visited = new HashSet<>(), inStack = new HashSet<>();
        for (String node : elements)
            if (!visited.contains(node) && hasCycle(node, adj, visited, inStack))
                throw new InvalidContractException("Order relation contains a cycle involving: " + node);
    }

    private boolean hasCycle(String node, Map<String, List<String>> adj,
                               Set<String> visited, Set<String> inStack) {
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

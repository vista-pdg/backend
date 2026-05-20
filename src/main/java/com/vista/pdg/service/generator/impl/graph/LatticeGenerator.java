package com.vista.pdg.service.generator.impl.graph;

import com.vista.pdg.model.contract.LatticeContract;
import com.vista.pdg.model.generated.Edge3D;
import com.vista.pdg.model.generated.GeneratedStructure;
import com.vista.pdg.model.generated.Node3D;
import com.vista.pdg.service.generator.def.StructureGenerator;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class LatticeGenerator implements StructureGenerator<LatticeContract> {

    @Override
    public String supportedType() {
        return "lattice";
    }

    @Override
    public GeneratedStructure generate(LatticeContract contract) {
        return switch (contract.subtype()) {
            case "divisors"  -> buildDivisors(contract);
            case "power_set" -> buildPowerSet(contract);
            case "boolean"   -> buildBoolean(contract);
            default          -> buildCustom(contract);
        };
    }

    // ── Divisors of n ────────────────────────────────────────────────────

    private GeneratedStructure buildDivisors(LatticeContract contract) {
        int n = ((Number) contract.params().get("n")).intValue();
        List<Integer> divs = divisors(n);
        Map<Integer, String> idMap = new LinkedHashMap<>();
        for (int i = 0; i < divs.size(); i++) idMap.put(divs.get(i), "n" + i);

        List<Node3D> nodes = new ArrayList<>();
        for (int d : divs) nodes.add(new Node3D(idMap.get(d), String.valueOf(d), 0, 0, 0, 0, null, Map.of()));

        List<Edge3D> edges = new ArrayList<>();
        int edgeIdx = 0;
        for (int a : divs) {
            for (int b : divs) {
                if (b > a && b % a == 0 && isPrime(b / a)) {
                    boolean direct = divs.stream().noneMatch(c -> c != a && c != b && b % c == 0 && c % a == 0);
                    if (direct) edges.add(new Edge3D("e" + edgeIdx++, idMap.get(a), idMap.get(b), null, true));
                }
            }
        }
        return new GeneratedStructure(contract, nodes, edges, Map.of());
    }

    private List<Integer> divisors(int n) {
        List<Integer> result = new ArrayList<>();
        for (int i = 1; i <= n; i++) if (n % i == 0) result.add(i);
        return result;
    }

    private boolean isPrime(int n) {
        if (n < 2) return false;
        for (int i = 2; i * i <= n; i++) if (n % i == 0) return false;
        return true;
    }

    // ── Power set ────────────────────────────────────────────────────────

    private GeneratedStructure buildPowerSet(LatticeContract contract) {
        int n = ((Number) contract.params().get("n")).intValue();
        int size = 1 << n;
        List<Node3D> nodes = new ArrayList<>();
        List<Edge3D> edges = new ArrayList<>();
        int edgeIdx = 0;

        for (int mask = 0; mask < size; mask++) {
            int depth = Integer.bitCount(mask);
            nodes.add(new Node3D("n" + mask, subsetLabel(mask, n), 0, 0, 0, depth, null, Map.of()));
        }
        for (int a = 0; a < size; a++) {
            for (int b = 0; b < size; b++) {
                if ((a & b) == a && Integer.bitCount(b) == Integer.bitCount(a) + 1) {
                    edges.add(new Edge3D("e" + edgeIdx++, "n" + a, "n" + b, null, true));
                }
            }
        }
        return new GeneratedStructure(contract, nodes, edges, Map.of());
    }

    private String subsetLabel(int mask, int n) {
        if (mask == 0) return "∅";
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < n; i++) {
            if ((mask & (1 << i)) != 0) {
                if (sb.length() > 1) sb.append(",");
                sb.append((char) ('a' + i));
            }
        }
        return sb.append("}").toString();
    }

    // ── Boolean algebra = power set ───────────────────────────────────────

    private GeneratedStructure buildBoolean(LatticeContract contract) {
        return buildPowerSet(contract);
    }

    // ── Custom (elements + order pairs) ──────────────────────────────────

    private GeneratedStructure buildCustom(LatticeContract contract) {
        List<String> elements = contract.elements();
        Map<String, String> idMap = new LinkedHashMap<>();
        for (int i = 0; i < elements.size(); i++) idMap.put(elements.get(i), "n" + i);

        List<Node3D> nodes = new ArrayList<>();
        for (String e : elements) nodes.add(new Node3D(idMap.get(e), e, 0, 0, 0, 0, null, Map.of()));

        List<Edge3D> edges = new ArrayList<>();
        if (contract.order() != null) {
            int edgeIdx = 0;
            for (List<String> pair : contract.order()) {
                if (pair.size() == 2) {
                    String from = idMap.get(pair.get(0)), to = idMap.get(pair.get(1));
                    if (from != null && to != null) edges.add(new Edge3D("e" + edgeIdx++, from, to, null, true));
                }
            }
        }
        return new GeneratedStructure(contract, nodes, edges, Map.of());
    }
}

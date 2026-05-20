package com.vista.pdg.service.generator.impl.graph;

import com.vista.pdg.model.contract.GraphContract;
import com.vista.pdg.model.generated.Edge3D;
import com.vista.pdg.model.generated.GeneratedStructure;
import com.vista.pdg.model.generated.Node3D;
import com.vista.pdg.service.generator.def.StructureGenerator;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class GraphGenerator implements StructureGenerator<GraphContract> {

    @Override
    public String supportedType() {
        return "graph";
    }

    @Override
    public GeneratedStructure generate(GraphContract contract) {
        List<Node3D> nodes = new ArrayList<>();
        for (int i = 0; i < contract.labels().size(); i++) {
            nodes.add(new Node3D("n" + i, contract.labels().get(i), 0, 0, 0, 0, null, Map.of()));
        }

        List<Edge3D> edges = "incidence".equals(contract.matrix().kind())
            ? buildIncidenceEdges(contract)
            : buildAdjacencyEdges(contract);

        return new GeneratedStructure(contract, nodes, edges, Map.of());
    }

    private List<Edge3D> buildAdjacencyEdges(GraphContract contract) {
        List<Edge3D> edges = new ArrayList<>();
        List<List<Integer>> data = contract.matrix().data();
        int n = contract.labels().size();
        int edgeIdx = 0;

        for (int i = 0; i < n; i++) {
            int jStart = contract.directed() ? 0 : i + 1;
            for (int j = jStart; j < n; j++) {
                int val = data.get(i).get(j);
                if (val != 0) {
                    Integer weight = contract.weighted() ? val : null;
                    edges.add(new Edge3D("e" + edgeIdx++, "n" + i, "n" + j, weight, contract.directed()));
                }
            }
        }
        return edges;
    }

    private List<Edge3D> buildIncidenceEdges(GraphContract contract) {
        List<Edge3D> edges = new ArrayList<>();
        List<List<Integer>> data = contract.matrix().data();
        int n = contract.labels().size();
        int cols = data.get(0).size();

        for (int j = 0; j < cols; j++) {
            int from = -1, to = -1;
            for (int i = 0; i < n; i++) {
                int val = data.get(i).get(j);
                if (val != 0) {
                    if (contract.directed()) {
                        if (val == 1) from = i; else to = i;
                    } else {
                        if (from == -1) from = i; else to = i;
                    }
                }
            }
            if (from != -1 && to != -1) {
                Integer weight = null;
                if (contract.matrix().edges() != null && j < contract.matrix().edges().size()) {
                    weight = contract.matrix().edges().get(j).weight();
                }
                edges.add(new Edge3D("e" + j, "n" + from, "n" + to, weight, contract.directed()));
            }
        }
        return edges;
    }
}

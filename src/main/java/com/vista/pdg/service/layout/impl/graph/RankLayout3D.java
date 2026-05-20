package com.vista.pdg.service.layout.impl.graph;

import com.vista.pdg.model.generated.Edge3D;
import com.vista.pdg.model.generated.GeneratedStructure;
import com.vista.pdg.model.generated.Node3D;
import com.vista.pdg.model.math.Vec3;
import com.vista.pdg.service.layout.def.LayoutStrategy;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class RankLayout3D implements LayoutStrategy {

    private static final double Y_STEP = 2.5;
    private static final double RADIUS = 3.0;

    @Override
    public String supportedLayout() {
        return "levels3d";
    }

    @Override
    public Map<String, Vec3> compute(GeneratedStructure structure) {
        Map<String, Vec3> positions = new HashMap<>();
        if (structure.nodes().isEmpty()) return positions;

        Map<String, Integer> inDegree   = new HashMap<>();
        Map<String, List<String>> succs = new HashMap<>();
        for (Node3D n : structure.nodes()) { inDegree.put(n.id(), 0); succs.put(n.id(), new ArrayList<>()); }
        for (Edge3D e : structure.edges()) {
            inDegree.put(e.to(), inDegree.getOrDefault(e.to(), 0) + 1);
            succs.get(e.from()).add(e.to());
        }

        // BFS to compute rank (longest path from sources)
        Map<String, Integer> rank = new HashMap<>();
        Queue<String> queue = new LinkedList<>();
        for (Node3D n : structure.nodes()) if (inDegree.get(n.id()) == 0) { queue.add(n.id()); rank.put(n.id(), 0); }
        while (!queue.isEmpty()) {
            String cur = queue.poll();
            for (String next : succs.get(cur)) {
                int newRank = rank.get(cur) + 1;
                if (!rank.containsKey(next) || rank.get(next) < newRank) {
                    rank.put(next, newRank);
                    queue.add(next);
                }
            }
        }
        for (Node3D n : structure.nodes()) rank.putIfAbsent(n.id(), 0);

        // Group by rank and place in circles
        Map<Integer, List<String>> byRank = new TreeMap<>();
        for (Node3D n : structure.nodes()) byRank.computeIfAbsent(rank.get(n.id()), k -> new ArrayList<>()).add(n.id());

        for (Map.Entry<Integer, List<String>> entry : byRank.entrySet()) {
            int r = entry.getKey();
            List<String> ids = entry.getValue();
            double y = r * Y_STEP;
            double radius = RADIUS * Math.max(1, ids.size() / (2 * Math.PI));
            for (int i = 0; i < ids.size(); i++) {
                double angle = 2 * Math.PI * i / ids.size();
                positions.put(ids.get(i), Vec3.of(radius * Math.cos(angle), y, radius * Math.sin(angle)));
            }
        }
        return positions;
    }
}

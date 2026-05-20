package com.vista.pdg.service.layout.impl.graph;

import com.vista.pdg.model.generated.GeneratedStructure;
import com.vista.pdg.model.generated.Node3D;
import com.vista.pdg.model.math.Vec3;
import com.vista.pdg.service.layout.def.LayoutStrategy;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ClusterLayout3D implements LayoutStrategy {

    private static final double CLUSTER_SPREAD = 5.0;
    private static final double NODE_SPREAD    = 1.5;

    @Override
    public String supportedLayout() {
        return "cluster3d";
    }

    @Override
    public Map<String, Vec3> compute(GeneratedStructure structure) {
        Map<String, Vec3> positions = new HashMap<>();
        List<Node3D> nodes = structure.nodes();
        if (nodes.isEmpty()) return positions;

        List<List<String>> classes = extractClasses(structure.computedProperties().get("equivalenceClasses"), nodes);

        for (int ci = 0; ci < classes.size(); ci++) {
            double clusterAngle = 2 * Math.PI * ci / classes.size();
            double cx = CLUSTER_SPREAD * Math.cos(clusterAngle);
            double cz = CLUSTER_SPREAD * Math.sin(clusterAngle);
            List<String> members = classes.get(ci);
            for (int ni = 0; ni < members.size(); ni++) {
                double nodeAngle = 2 * Math.PI * ni / Math.max(1, members.size());
                positions.put(members.get(ni), Vec3.of(
                    cx + NODE_SPREAD * Math.cos(nodeAngle), 0,
                    cz + NODE_SPREAD * Math.sin(nodeAngle)));
            }
        }
        return positions;
    }

    private List<List<String>> extractClasses(Object raw, List<Node3D> nodes) {
        if (raw instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof List<?>) {
            List<List<String>> result = new ArrayList<>();
            for (Object cls : list) {
                List<String> group = new ArrayList<>();
                for (Object member : (List<?>) cls) group.add(String.valueOf(member));
                result.add(group);
            }
            return result;
        }
        List<List<String>> result = new ArrayList<>();
        for (Node3D n : nodes) result.add(List.of(n.id()));
        return result;
    }
}

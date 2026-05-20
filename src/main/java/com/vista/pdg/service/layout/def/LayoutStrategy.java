package com.vista.pdg.service.layout.def;

import com.vista.pdg.model.generated.GeneratedStructure;
import com.vista.pdg.model.math.Vec3;
import java.util.Map;

public interface LayoutStrategy {
  String supportedLayout();

  Map<String, Vec3> compute(GeneratedStructure structure);
}

package com.vista.pdg.service.generator.impl;

import com.vista.pdg.exception.UnsupportedStructureException;
import com.vista.pdg.model.contract.StructureContract;
import com.vista.pdg.model.generated.GeneratedStructure;
import com.vista.pdg.service.generator.def.StructureGenerator;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class GeneratorDispatcher {

    private final Map<String, StructureGenerator<?>> generators;

    public GeneratorDispatcher(List<StructureGenerator<?>> all) {
        this.generators = all.stream()
            .collect(Collectors.toMap(StructureGenerator::supportedType, g -> g));
    }

    @SuppressWarnings("unchecked")
    public GeneratedStructure dispatch(StructureContract contract) {
        StructureGenerator<StructureContract> gen =
            (StructureGenerator<StructureContract>) generators.get(contract.type());
        if (gen == null) throw new UnsupportedStructureException(contract.type());
        return gen.generate(contract);
    }
}

package com.vista.pdg.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vista.pdg.service.generator.impl.GeneratorDispatcher;
import com.vista.pdg.service.generator.impl.HashTableGenerator;
import com.vista.pdg.service.generator.impl.LinkedListGenerator;
import com.vista.pdg.service.generator.impl.QueueGenerator;
import com.vista.pdg.service.generator.impl.StackGenerator;
import com.vista.pdg.service.generator.impl.graph.GraphGenerator;
import com.vista.pdg.service.generator.impl.graph.TreeGenerator;
import com.vista.pdg.service.layout.impl.BucketLayout3D;
import com.vista.pdg.service.layout.impl.LayoutDispatcher;
import com.vista.pdg.service.layout.impl.LinearLayout3D;
import com.vista.pdg.service.layout.impl.StackLayout3D;
import com.vista.pdg.service.layout.impl.graph.CircularLayout3D;
import com.vista.pdg.service.layout.impl.graph.ClusterLayout3D;
import com.vista.pdg.service.layout.impl.graph.ForceDirectedLayout3D;
import com.vista.pdg.service.layout.impl.graph.RankLayout3D;
import com.vista.pdg.service.layout.impl.graph.TreeLayout3D;
import com.vista.pdg.service.sdd.impl.ContractBuilder;
import com.vista.pdg.service.sdd.impl.ContractNormalizer;
import com.vista.pdg.service.sdd.impl.ContractValidator;
import com.vista.pdg.service.sdd.impl.validator.GraphContractValidator;
import com.vista.pdg.service.sdd.impl.validator.HashTableContractValidator;
import com.vista.pdg.service.sdd.impl.validator.LinkedListContractValidator;
import com.vista.pdg.service.sdd.impl.validator.QueueContractValidator;
import com.vista.pdg.service.sdd.impl.validator.StackContractValidator;
import com.vista.pdg.service.sdd.impl.validator.TreeContractValidator;
import java.util.List;

/**
 * La cadena de generación cableada a mano: sin contexto de Spring, sin base de datos, sin modelo.
 * Lo que aquí se arma es exactamente lo que Spring arma en producción (los mismos beans), pero se
 * puede ejecutar en cualquier máquina de CI en milisegundos.
 */
public final class PipelineFixtures {

  private PipelineFixtures() {}

  public static ContractBuilder contractBuilder() {
    ObjectMapper mapper = new ObjectMapper();
    ContractValidator validator =
        new ContractValidator(
            new GraphContractValidator(),
            new TreeContractValidator(),
            new LinkedListContractValidator(),
            new HashTableContractValidator(),
            new StackContractValidator(),
            new QueueContractValidator());
    return new ContractBuilder(mapper, validator, new ContractNormalizer(mapper));
  }

  public static GeneratorDispatcher generators() {
    return new GeneratorDispatcher(
        List.of(
            new GraphGenerator(),
            new TreeGenerator(),
            new LinkedListGenerator(),
            new HashTableGenerator(),
            new StackGenerator(),
            new QueueGenerator()));
  }

  public static LayoutDispatcher layouts() {
    return new LayoutDispatcher(
        List.of(
            new ForceDirectedLayout3D(),
            new TreeLayout3D(),
            new CircularLayout3D(),
            new ClusterLayout3D(),
            new RankLayout3D(),
            new LinearLayout3D(),
            new BucketLayout3D(),
            new StackLayout3D()));
  }
}

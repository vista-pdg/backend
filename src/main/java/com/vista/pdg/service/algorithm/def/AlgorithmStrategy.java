package com.vista.pdg.service.algorithm.def;

import com.vista.pdg.controller.dto.AlgorithmRequest;
import com.vista.pdg.model.response.StepsResponse;

/**
 * Un algoritmo ejecutable paso a paso (HU-19). Cada implementación es un {@code @Service}: el
 * despachador las descubre por su descriptor y el catálogo se deriva de ellas, así que añadir un
 * algoritmo no toca ningún {@code if}.
 */
public interface AlgorithmStrategy {
  AlgorithmDescriptor descriptor();

  StepsResponse generate(AlgorithmRequest request);
}

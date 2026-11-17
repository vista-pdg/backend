package com.vista.pdg.quiz.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;

/** Guarda una lista corta de textos como JSON en una sola columna. */
@Converter
public class StringListJsonConverter implements AttributeConverter<List<String>, String> {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final TypeReference<List<String>> TYPE = new TypeReference<>() {};

  @Override
  public String convertToDatabaseColumn(List<String> attribute) {
    try {
      return MAPPER.writeValueAsString(attribute == null ? List.of() : attribute);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("No se pudo serializar la lista", e);
    }
  }

  @Override
  public List<String> convertToEntityAttribute(String dbData) {
    if (dbData == null || dbData.isBlank()) return List.of();
    try {
      return MAPPER.readValue(dbData, TYPE);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("No se pudo leer la lista almacenada", e);
    }
  }
}

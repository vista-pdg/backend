package com.vista.pdg.quiz.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Guarda la preparación en VISTA como JSON; las preguntas anteriores a HU-35 no la tienen. */
@Converter
public class VistaSetupJsonConverter implements AttributeConverter<VistaSetup, String> {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Override
  public String convertToDatabaseColumn(VistaSetup attribute) {
    if (attribute == null) return null;
    try {
      return MAPPER.writeValueAsString(attribute);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("No se pudo serializar la preparación en VISTA", e);
    }
  }

  @Override
  public VistaSetup convertToEntityAttribute(String dbData) {
    if (dbData == null || dbData.isBlank()) return null;
    try {
      return MAPPER.readValue(dbData, VistaSetup.class);
    } catch (JsonProcessingException e) {
      // Una preparación ilegible no debe impedir presentar la pregunta: queda sin botones.
      return null;
    }
  }
}

package com.vista.pdg.telemetry.service;

import com.vista.pdg.auth.entity.User;
import java.util.Locale;

/**
 * Lo que un punto de captura sabe de una interacción (HU-21). Se construye donde ocurre el hecho y
 * el servicio de telemetría lo convierte en fila: así los puntos de captura no conocen la entidad
 * ni la seudonimización, y añadir un campo al esquema no cambia sus firmas.
 */
public record InteractionEvent(
    User user,
    String kind,
    String structureKind,
    String subtype,
    String algorithm,
    Source source,
    Outcome outcome,
    int nodeCount,
    Integer stepCount,
    String visualizationMode,
    String sessionId,
    String promptText) {

  /** De dónde salió la interacción. */
  public enum Source {
    ASISTENTE_NLP("asistente_nlp"),
    CATALOGO_ALGORITMOS("catalogo_algoritmos");

    private final String value;

    Source(String value) {
      this.value = value;
    }

    public String value() {
      return value;
    }
  }

  /**
   * Cómo terminó. {@code FUERA_DE_ALCANCE} es señal pedagógica —el estudiante pidió algo que la
   * plataforma no cubre— y {@code ERROR} es señal de operación: el proveedor del modelo falló.
   * Separarlos es lo que hace útil el registro.
   */
  public enum Outcome {
    EXITO("exito"),
    FUERA_DE_ALCANCE("fuera_de_alcance"),
    ERROR("error");

    private final String value;

    Outcome(String value) {
      this.value = value;
    }

    public String value() {
      return value;
    }
  }

  public static Builder of(User user, String kind) {
    return new Builder(user, kind);
  }

  /** Constructor fluido: los puntos de captura sólo rellenan lo que saben. */
  public static final class Builder {
    private final User user;
    private final String kind;
    private String structureKind = StructureKind.DESCONOCIDA;
    private String subtype;
    private String algorithm;
    private Source source = Source.ASISTENTE_NLP;
    private Outcome outcome = Outcome.EXITO;
    private int nodeCount;
    private Integer stepCount;
    private String visualizationMode;
    private String sessionId;
    private String promptText;

    private Builder(User user, String kind) {
      this.user = user;
      this.kind = kind;
    }

    public Builder structureKind(String v) {
      this.structureKind = v;
      return this;
    }

    public Builder subtype(String v) {
      this.subtype = v;
      return this;
    }

    /**
     * El nombre del algoritmo en mayúsculas: CA-2 lo pide como {@code BFS}, y el catálogo lo envía
     * como {@code bfs}. Normalizar al escribir evita que la analítica cuente «bfs» y «BFS» como dos
     * temas distintos.
     */
    public Builder algorithm(String v) {
      this.algorithm = v == null || v.isBlank() ? null : v.trim().toUpperCase(Locale.ROOT);
      return this;
    }

    public Builder source(Source v) {
      this.source = v;
      return this;
    }

    public Builder outcome(Outcome v) {
      this.outcome = v;
      return this;
    }

    public Builder nodeCount(int v) {
      this.nodeCount = v;
      return this;
    }

    public Builder stepCount(Integer v) {
      this.stepCount = v;
      return this;
    }

    public Builder visualizationMode(String v) {
      this.visualizationMode = v;
      return this;
    }

    public Builder sessionId(String v) {
      this.sessionId = v;
      return this;
    }

    public Builder promptText(String v) {
      this.promptText = v;
      return this;
    }

    public InteractionEvent build() {
      return new InteractionEvent(
          user,
          kind,
          structureKind,
          subtype,
          algorithm,
          source,
          outcome,
          nodeCount,
          stepCount,
          visualizationMode,
          sessionId,
          promptText);
    }
  }
}

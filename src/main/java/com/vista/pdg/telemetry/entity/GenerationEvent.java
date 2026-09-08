package com.vista.pdg.telemetry.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

/**
 * Un evento de generación de estructura, ya seudonimizado.
 *
 * <p>Deliberadamente <b>no</b> tiene clave foránea al usuario ni guarda correo o nombre. Quien lea
 * esta tabla —un reporte, un volcado, un docente con acceso a la analítica— ve cohortes y
 * actividad, pero no puede volver a la persona sin la clave del seudónimo, que vive fuera de la
 * base. Ese es el compromiso de privacidad que R03 exige.
 */
@Entity
@Table(
    name = "generation_events",
    indexes = {
      @Index(name = "idx_genev_course", columnList = "courseCode"),
      @Index(name = "idx_genev_pseudonym", columnList = "pseudonym"),
      @Index(name = "idx_genev_created", columnList = "createdAt")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GenerationEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** HMAC del identificador de cuenta, truncado. Estable por cuenta, no reversible sin la clave. */
  @Column(nullable = false, length = 32)
  private String pseudonym;

  /** Nulo para cuentas sin curso (docente, administrador). */
  @Column(length = 32)
  private String courseCode;

  @Column(length = 16)
  private String termCode;

  /**
   * HU-21: en el esquema v2 es el tipo <b>detallado</b> en español ({@code grafo_no_dirigido},
   * {@code arbol_avl}…). Las filas v1 (HU-16/18) guardan el tipo grueso del contrato ({@code
   * graph}); {@code schemaVersion} dice cómo leerlas.
   */
  @Column(nullable = false, length = 32)
  private String structureType;

  @Column(length = 32)
  private String subtype;

  @Column(nullable = false)
  private int nodeCount;

  /**
   * HU-18: qué produjo el evento. {@code generation} para {@code /api/generate}, {@code algorithm}
   * para una ejecución paso a paso. Nulo en filas anteriores a la HU-18, que eran todas
   * generaciones; las consultas lo tratan como tal.
   */
  @Column(length = 24)
  private String kind;

  /**
   * HU-18 · CA-7: modo de visualización activo en el cliente cuando pidió la operación ({@code 2D}
   * o {@code 3D}). Nulo si el cliente no lo informó. Es el campo del análisis de impacto de PdG II.
   */
  @Column(length = 4)
  private String visualizationMode;

  /** HU-21: algoritmo ejecutado ({@code BFS}, {@code inorder}…). Nulo en las generaciones. */
  @Column(length = 48)
  private String algorithm;

  /** HU-21: {@code asistente_nlp} o {@code catalogo_algoritmos}. */
  @Column(length = 32)
  private String interactionSource;

  /** HU-21: {@code exito}, {@code fuera_de_alcance} o {@code error}. */
  @Column(length = 24)
  private String outcome;

  /**
   * HU-21 · CA-4: sesión de trabajo (la de HU-32) bajo la que ocurrió la interacción. Agrupa los
   * reintentos. Nulo si la sesión no estaba disponible.
   */
  @Column(length = 36)
  private String sessionId;

  /** HU-21 · CA-2: pasos del rastro; en los eventos que reporta el cliente, los recorridos. */
  private Integer stepCount;

  /**
   * HU-21 · CA-3: instrucción del estudiante, <b>sólo</b> cuando el resultado no fue éxito, para
   * revisión docente. En los éxitos no se guarda: sería contenido almacenado sin necesidad (R03).
   */
  @Column(length = 500)
  private String promptText;

  /**
   * Versión del esquema del evento: 2 desde HU-21. Las filas escritas antes son de la v1 y llegan
   * con {@code null}, que es lo que hay que leer como «uno».
   *
   * <p>Es {@code Integer} y la columna admite nulos a propósito. Con {@code ddl-auto=update} y una
   * tabla que ya tiene filas, añadir una columna {@code not null} sin valor por defecto falla en
   * silencio y deja el esquema a medias; con nulos, la columna aparece y las filas viejas dicen la
   * verdad: se escribieron sin versión.
   */
  @Builder.Default private Integer schemaVersion = 2;

  /** La versión efectiva: sin valor guardado, la fila es de la v1. */
  public int effectiveSchemaVersion() {
    return schemaVersion == null ? 1 : schemaVersion;
  }

  @Column(nullable = false)
  @Builder.Default
  private Instant createdAt = Instant.now();
}

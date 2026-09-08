package com.vista.pdg.telemetry.service;

import com.vista.pdg.model.contract.GraphContract;
import com.vista.pdg.model.contract.LinkedListContract;
import com.vista.pdg.model.contract.TreeContract;
import com.vista.pdg.model.contract.def.StructureContract;
import java.util.Locale;

/**
 * Tipo de estructura tal como lo lee un docente (HU-21 · CA-1).
 *
 * <p>El tipo del contrato («graph») no dice lo que hace falta para decidir qué reforzar: un grafo
 * dirigido y uno no dirigido son temas distintos, y lo mismo pasa entre un AVL y un BST. Esta
 * función pura traduce el contrato al vocabulario del syllabus, en español, y es lo que guarda la
 * columna {@code structure_type} en el esquema v2.
 */
public final class StructureKind {

  public static final String DESCONOCIDA = "desconocida";

  private StructureKind() {}

  /** Tipo detallado de un contrato ya validado. */
  public static String of(StructureContract contract) {
    if (contract == null) return DESCONOCIDA;
    return switch (contract) {
      case GraphContract g -> g.directed() ? "grafo_dirigido" : "grafo_no_dirigido";
      case TreeContract t -> tree(t.subtype());
      case LinkedListContract l -> list(l.subtype());
      default -> byType(contract.type(), null);
    };
  }

  /** Tipo detallado cuando sólo se conocen las cadenas, como en la petición de un algoritmo. */
  public static String of(String type, String subtype) {
    return byType(type, subtype);
  }

  private static String byType(String type, String subtype) {
    if (type == null) return DESCONOCIDA;
    return switch (type.toLowerCase(Locale.ROOT)) {
      case "graph" -> "grafo_no_dirigido";
      case "tree" -> tree(subtype);
      case "linked-list" -> list(subtype);
      case "hash-table" -> "tabla_hash";
      case "stack" -> "pila";
      case "queue" -> "cola";
      default -> DESCONOCIDA;
    };
  }

  private static String tree(String subtype) {
    if (subtype == null) return "arbol_binario";
    return switch (subtype.toLowerCase(Locale.ROOT)) {
      case "avl" -> "arbol_avl";
      case "bst" -> "arbol_bst";
      case "heap" -> "heap";
      default -> "arbol_binario";
    };
  }

  private static String list(String subtype) {
    if (subtype == null) return "lista_simple";
    return switch (subtype.toLowerCase(Locale.ROOT)) {
      case "doubly" -> "lista_doble";
      case "circular" -> "lista_circular";
      default -> "lista_simple";
    };
  }
}

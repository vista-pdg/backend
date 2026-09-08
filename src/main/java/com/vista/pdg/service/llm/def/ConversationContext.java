package com.vista.pdg.service.llm.def;

import java.util.List;

/**
 * Contexto de la sesión de trabajo que acompaña a una instrucción del asistente (HU-32).
 *
 * <p>Es lo mínimo que el modelo necesita para refinar en vez de empezar de cero: el contrato JSON
 * que produjo la última vez y los últimos turnos en texto. Vive en {@code service.llm} —y no en el
 * módulo del asistente— para que el adaptador no dependa de dónde se guarda la sesión: quien la
 * construya (hoy Redis) es indiferente aquí.
 */
public record ConversationContext(String currentContract, List<String> recentTurns) {

  private static final ConversationContext EMPTY = new ConversationContext(null, List.of());

  public static ConversationContext empty() {
    return EMPTY;
  }

  public ConversationContext {
    recentTurns = recentTurns == null ? List.of() : List.copyOf(recentTurns);
  }

  public boolean isEmpty() {
    return (currentContract == null || currentContract.isBlank()) && recentTurns.isEmpty();
  }

  /**
   * Bloque que precede a la instrucción del usuario. El formato es fijo y explícito para que el
   * modelo distinguya el contexto de la orden nueva; las reglas de qué hacer con él están en el
   * prompt del sistema.
   */
  public String asPromptBlock() {
    if (isEmpty()) return "";
    StringBuilder sb = new StringBuilder();
    if (currentContract != null && !currentContract.isBlank()) {
      sb.append("CURRENT STRUCTURE (the contract you returned before):\n")
          .append(currentContract.strip())
          .append("\n\n");
    }
    if (!recentTurns.isEmpty()) {
      sb.append("RECENT TURNS (oldest first):\n");
      for (String t : recentTurns) sb.append("- ").append(t).append('\n');
      sb.append('\n');
    }
    sb.append("NEW INSTRUCTION:\n");
    return sb.toString();
  }
}

package com.vista.pdg.service.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.vista.pdg.service.llm.def.ConversationContext;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** HU-32: el bloque de contexto que precede a la instrucción del usuario. */
class ConversationContextTest {

  @Test
  @DisplayName("un contexto vacío no añade nada al prompt")
  void emptyContextAddsNothing() {
    assertThat(ConversationContext.empty().isEmpty()).isTrue();
    assertThat(ConversationContext.empty().asPromptBlock()).isEmpty();
    assertThat(new ConversationContext(null, null).isEmpty()).isTrue();
    assertThat(new ConversationContext("  ", List.of()).isEmpty()).isTrue();
  }

  @Test
  @DisplayName("con estructura vigente el bloque la nombra y separa la instrucción nueva")
  void blockCarriesContractAndTurns() {
    String contract = "{\"type\":\"tree\",\"subtype\":\"bst\"}";
    String block =
        new ConversationContext(
                contract, List.of("usuario: árbol 1,2,3", "asistente: tree con 3 nodos"))
            .asPromptBlock();

    assertThat(block)
        .contains("CURRENT STRUCTURE")
        .contains(contract)
        .contains("RECENT TURNS")
        .contains("- usuario: árbol 1,2,3")
        .contains("- asistente: tree con 3 nodos")
        .endsWith("NEW INSTRUCTION:\n");
    assertThat(block.indexOf("CURRENT STRUCTURE")).isLessThan(block.indexOf("RECENT TURNS"));
  }

  @Test
  @DisplayName("los turnos son inmutables y no se filtran al llamador")
  void turnsAreCopied() {
    List<String> mutable = new java.util.ArrayList<>(List.of("usuario: hola"));
    ConversationContext ctx = new ConversationContext("{}", mutable);
    mutable.add("usuario: otra cosa");
    assertThat(ctx.recentTurns()).containsExactly("usuario: hola");
  }
}

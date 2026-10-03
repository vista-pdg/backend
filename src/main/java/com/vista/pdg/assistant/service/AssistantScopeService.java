package com.vista.pdg.assistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vista.pdg.controller.dto.GenerateRequest;
import com.vista.pdg.exception.UnsupportedStructureException;
import com.vista.pdg.model.contract.LinkedListContract;
import com.vista.pdg.model.contract.TreeContract;
import com.vista.pdg.model.contract.def.StructureContract;
import com.vista.pdg.service.llm.def.ConversationContext;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/** Deterministic input/output limits; system instructions remain a separate defense layer. */
@Service
public class AssistantScopeService {
  private static final Set<String> TYPES =
      Set.of("graph", "tree", "stack", "queue", "linked-list", "hash-table");
  private static final Pattern DOMAIN =
      Pattern.compile(
          "\\b(grafo|graph|arbol|tree|avl|bst|heap|pila|stack|cola|queue|lista|list|hash|nodo|node|arista|edge|vertice|vertex|vertices|matriz|matrix|trie|k[0-9]+)\\b");
  private static final Pattern OPERATION =
      Pattern.compile(
          "\\b(inserta|insertar|insert|agrega|agregar|add|quita|remove|elimina|delete|dirigido|directed|ponderado|weighted|push|pop|enqueue|dequeue|conecta|connect)\\b");
  private static final Pattern ESCAPE =
      Pattern.compile(
          "(?s)(ignore|ignora|olvida|disregard|omite).{0,70}(instruction|instruccion|system|sistema|regla|previous|anterior)|system\\s*prompt|prompt\\s*(del\\s*)?sistema|api[_ -]?key|contrase[nñ]a|password|secret[o]?|<\\|?(system|assistant)|\\[(system|inst)\\]|(ejecuta|execute|run).{0,30}(shell|bash|comando|command)|malware|ransomware|phishing");
  private static final Pattern OFF_TOPIC =
      Pattern.compile(
          "\\b(receta|recipe|diagnostico|diagnosis|medicamento|medicine|presidente|president|politica|politics|poema|poem|horoscopo|horoscope)\\b");

  public void validate(GenerateRequest request, ConversationContext context) {
    String prompt = request.prompt();
    if (prompt == null
        || prompt.isBlank()
        || prompt.length() > 4000
        || prompt
            .codePoints()
            .anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t')) {
      reject("Describe una estructura u operación de VISTA con entre 1 y 4000 caracteres.");
    }
    validateSelection(request.type(), request.subtype());
    String normalized =
        Normalizer.normalize(prompt, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT);
    if (ESCAPE.matcher(normalized).find() || OFF_TOPIC.matcher(normalized).find()) {
      reject(
          "VISTA solo genera y modifica estructuras discretas compatibles; no ejecuta comandos ni revela instrucciones o secretos.");
    }
    boolean hasContext = request.type() != null || (context != null && !context.isEmpty());
    if (!DOMAIN.matcher(normalized).find()
        && !OPERATION.matcher(normalized).find()
        && !(hasContext && normalized.matches("[\\d\\s,;\\[\\]().-]+"))) {
      reject(
          "Describe un grafo, árbol, pila, cola, lista o tabla hash, o una operación sobre la estructura vigente.");
    }
  }

  private void validateSelection(String type, String subtype) {
    if (type == null) {
      if (subtype != null) reject("Selecciona la estructura antes de indicar un subtipo.");
      return;
    }
    if (!TYPES.contains(type)) reject("La estructura seleccionada no está soportada por VISTA.");
    if (subtype == null) return;
    Set<String> allowed =
        switch (type) {
          case "tree" -> Set.of("avl", "bst", "heap", "btree");
          case "linked-list" -> Set.of("singly", "doubly", "circular");
          case "graph", "stack", "queue" -> Set.of("simple");
          default -> Set.of();
        };
    if (!allowed.contains(subtype)) reject("El subtipo seleccionado no está soportado por VISTA.");
  }

  public ConversationContext selectedContext(GenerateRequest request, ConversationContext context) {
    if (request.type() == null || context == null || context.isEmpty()) return context;
    try {
      var contract = new ObjectMapper().readTree(context.currentContract());
      if (request.type().equals(contract.path("type").asText())
          && (request.subtype() == null
              || request.subtype().equals("simple")
              || request.subtype().equals(contract.path("subtype").asText()))) return context;
    } catch (Exception ignored) {
      // Missing/malformed memory must not override an explicit selected structure.
    }
    return ConversationContext.empty();
  }

  public String modelPrompt(GenerateRequest request) {
    if (request.type() == null) return request.prompt();
    // Values are restricted above, so arbitrary instructions cannot enter the selection block.
    return "SELECTED STRUCTURE: "
        + request.type()
        + (request.subtype() == null ? "" : "/" + request.subtype())
        + "\nUSER DATA (describe only this selected structure):\n"
        + request.prompt();
  }

  public void verifyResult(GenerateRequest request, StructureContract contract) {
    if (contract == null || !TYPES.contains(contract.type()))
      reject("El modelo no devolvió una estructura compatible con VISTA.");
    if (request.type() == null) return;
    if (!request.type().equals(contract.type()))
      reject(
          "La respuesta no corresponde a la estructura seleccionada. Cambia la selección para operar con otra familia.");
    String subtype =
        contract instanceof TreeContract tree
            ? tree.subtype()
            : contract instanceof LinkedListContract list ? list.subtype() : null;
    if (request.subtype() != null
        && !request.subtype().equals("simple")
        && !request.subtype().equals(subtype)) {
      reject("La respuesta no corresponde al subtipo seleccionado.");
    }
  }

  private static void reject(String message) {
    throw new UnsupportedStructureException(message);
  }
}

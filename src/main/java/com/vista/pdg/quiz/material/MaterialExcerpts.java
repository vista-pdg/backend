package com.vista.pdg.quiz.material;

import com.vista.pdg.service.algorithm.def.AlgorithmDescriptor;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Elige, para el tema de un lote, los fragmentos del material más relacionados (HU-36 · CA-2). Es
 * una selección léxica y explicable: un fragmento debe nombrar la estructura del tema y suma por
 * cada término de la operación que mencione. Si nada se relaciona, el lote no recibe material.
 */
@Component
public class MaterialExcerpts {
  public static final int BUDGET = 6000;
  static final int CHUNK = 1200;
  static final int MAX_EXCERPTS = 6;

  public record Source(String name, String content) {}

  public record Excerpt(String source, String text) {}

  private static final Map<String, List<String>> CORE =
      Map.ofEntries(
          Map.entry("tree/avl", List.of("avl", "rotacion", "factor de balance", "equilibrio")),
          Map.entry("tree/bst", List.of("bst", "arbol binario de busqueda", "binario de busqueda")),
          Map.entry("tree/btree", List.of("arbol b", "b-tree", "btree")),
          Map.entry("tree/heap", List.of("heap", "monticulo", "cola de prioridad", "heapify")),
          Map.entry("graph", List.of("grafo", "vertice", "arista")),
          Map.entry("stack", List.of("pila", "stack", "tope")),
          Map.entry("queue", List.of("cola", "queue", "frente", "enqueue", "dequeue")),
          Map.entry(
              "linked-list",
              List.of("lista enlazada", "linked list", "nodo siguiente", "enlazada")),
          Map.entry(
              "hash-table",
              List.of("hash", "dispersion", "cubeta", "bucket", "colision", "encadenamiento")),
          Map.entry("sorting", List.of("ordenamiento", "ordenar", "sort")));
  private static final Set<String> STOP =
      Set.of(
          "para",
          "como",
          "cada",
          "sobre",
          "entre",
          "desde",
          "hasta",
          "este",
          "esta",
          "estos",
          "estas",
          "todos",
          "todas",
          "donde",
          "cuando",
          "segun",
          "tras",
          "algoritmo",
          "estructura",
          "valores",
          "valor",
          "elemento",
          "elementos",
          "operacion",
          "recorrido",
          "pasos");

  public List<Excerpt> select(List<Source> sources, AlgorithmDescriptor topic, int budget) {
    List<String> core = core(topic);
    Set<String> terms = terms(topic);
    record Scored(int order, String source, String text, int score) {}
    List<Scored> scored = new ArrayList<>();
    int order = 0;
    for (Source source : sources) {
      for (String chunk : chunks(source.content())) {
        String plain = normalize(chunk);
        long coreHits = core.stream().filter(plain::contains).count();
        if (coreHits == 0) {
          order++;
          continue;
        }
        long termHits = terms.stream().filter(t -> plain.contains(t)).count();
        scored.add(new Scored(order++, source.name(), chunk, (int) (coreHits * 3 + termHits)));
      }
    }
    List<Scored> chosen = new ArrayList<>();
    int used = 0;
    for (Scored s :
        scored.stream()
            .sorted(Comparator.comparingInt(Scored::score).reversed().thenComparing(Scored::order))
            .toList()) {
      if (chosen.size() >= MAX_EXCERPTS || used + s.text().length() > budget) continue;
      chosen.add(s);
      used += s.text().length();
    }
    return chosen.stream()
        .sorted(Comparator.comparingInt(Scored::order))
        .map(s -> new Excerpt(s.source(), s.text()))
        .toList();
  }

  static List<String> chunks(String content) {
    List<String> chunks = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    for (String paragraph : content.split("\n\\s*\n")) {
      String clean = paragraph.strip();
      if (clean.isEmpty()) continue;
      while (clean.length() > CHUNK) {
        flush(chunks, current);
        chunks.add(clean.substring(0, CHUNK));
        clean = clean.substring(CHUNK);
      }
      if (current.length() + clean.length() + 2 > CHUNK) flush(chunks, current);
      if (!current.isEmpty()) current.append("\n\n");
      current.append(clean);
    }
    flush(chunks, current);
    return chunks;
  }

  private static void flush(List<String> chunks, StringBuilder current) {
    if (!current.isEmpty()) chunks.add(current.toString());
    current.setLength(0);
  }

  private static List<String> core(AlgorithmDescriptor topic) {
    List<String> core = new ArrayList<>();
    String type = topic.type() + "/" + topic.subtype();
    if ("sorting".equals(topic.family())) core.addAll(CORE.get("sorting"));
    if (CORE.containsKey(type)) core.addAll(CORE.get(type));
    else if (CORE.containsKey(topic.type())) core.addAll(CORE.get(topic.type()));
    if ("heap".equals(topic.family())) core.addAll(CORE.get("tree/heap"));
    if (core.isEmpty()) core.add(normalize(topic.type()));
    return core;
  }

  /** Raíces de las palabras de la operación (etiqueta, operación y descripción). */
  private static Set<String> terms(AlgorithmDescriptor topic) {
    Set<String> terms = new LinkedHashSet<>();
    String text =
        String.join(
            " ",
            topic.label(),
            topic.operation(),
            topic.description() == null ? "" : topic.description());
    for (String word : normalize(text).split("[^a-z0-9]+")) {
      if (word.length() < 4 || STOP.contains(word)) continue;
      terms.add(word.length() > 6 ? word.substring(0, 6) : word);
    }
    return new HashSet<>(terms);
  }

  static String normalize(String text) {
    return Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .toLowerCase(Locale.ROOT);
  }
}

package com.vista.pdg.service.algorithm;

import com.vista.pdg.model.response.CodeRepresentation;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;

/** Fixed educational listings, never model-generated code or executable user input. */
public final class AlgorithmCode {
  private static final String GUIDE =
      "https://github.com/juanmarcosdev/Estructuras-no-recursivas/tree/c395fb0b88608295d18e7dc84afaa1509a7c03eb";

  private AlgorithmCode() {}

  public static CodeRepresentation stack() {
    return listing(
        "VistaStackPop.java",
        Map.of(
            1, "public static <T> List<T> vaciar(LinkedStack<T> pila) {",
            2, "while (!pila.isEmpty()) {",
            3, "T tope = pila.peek();",
            4, "pila.pop(); retirados.add(tope);",
            5, "pila.pop(); retirados.add(tope);",
            6, "return retirados;"),
        true);
  }

  public static CodeRepresentation queue() {
    return listing(
        "VistaQueueDequeue.java",
        Map.of(
            1, "public static <T> List<T> vaciar(LinkedQueue<T> cola) {",
            2, "while (!cola.isEmpty()) {",
            3, "T frente = cola.front();",
            4, "cola.dequeue(); atendidos.add(frente);",
            5, "cola.dequeue(); atendidos.add(frente);",
            6, "return atendidos;"),
        true);
  }

  public static CodeRepresentation inorder() {
    return listing(
        "VistaInorder.java",
        Map.of(
            1, "public static void inorden(Nodo nodo, List<Integer> salida) {",
            2, "if (nodo == null) {",
            3, "return;",
            4, "inorden(nodo.izq(), salida);",
            5, "salida.add(nodo.valor());",
            6, "inorden(nodo.der(), salida);",
            7, "return;"),
        false);
  }

  public static CodeRepresentation bfs() {
    return listing(
        "VistaBfs.java",
        Map.of(
            1, "public static List<String> bfs(String inicio, Map<String, List<String>> vecinos) {",
            2, "cola.add(inicio); visitados.add(inicio);",
            3, "while (!cola.isEmpty()) {",
            4, "String u = cola.remove();",
            5, "orden.add(u);",
            6, "for (String v : vecinos.getOrDefault(u, List.of())) {",
            7, "if (visitados.add(v)) { cola.add(v); }",
            8, "return orden;"),
        false);
  }

  public static CodeRepresentation avl() {
    return listing(
        "VistaAvl.java",
        Map.of(
            1, "public static Nodo insertarTodos(int[] valores) {",
            2, "Nodo raiz = null;",
            3, "if (nodo == null) return new Nodo(valor);",
            4, "int balance = factor(nodo);",
            5, "private static Nodo izquierda(Nodo pivote) {",
            6, "private static Nodo derecha(Nodo pivote) {",
            7, "return balanceado;"),
        false);
  }

  private static CodeRepresentation listing(
      String fileName, Map<Integer, String> anchors, boolean guide) {
    try {
      List<String> code =
          new String(
                  new ClassPathResource("algorithm-code/" + fileName).getContentAsByteArray(),
                  StandardCharsets.UTF_8)
              .lines()
              .toList();
      Map<Integer, List<Integer>> map = new LinkedHashMap<>();
      for (var entry : anchors.entrySet()) {
        List<Integer> matches =
            java.util.stream.IntStream.range(0, code.size())
                .filter(i -> entry.getValue().equals(code.get(i).strip()))
                .map(i -> i + 1)
                .boxed()
                .toList();
        if (matches.isEmpty())
          throw new IllegalStateException(
              "Missing code anchor: " + fileName + ":" + entry.getKey());
        // Inorder's two returns belong to different branches, not both at once.
        if (fileName.equals("VistaInorder.java") && entry.getKey() == 3)
          matches = List.of(matches.getFirst());
        if (fileName.equals("VistaInorder.java") && entry.getKey() == 7)
          matches = List.of(matches.getLast());
        map.put(entry.getKey(), matches);
      }
      return new CodeRepresentation(
          "java",
          "Java",
          fileName,
          code,
          Map.copyOf(map),
          guide ? "Ejemplo VISTA · API del repositorio guía" : "Implementación educativa de VISTA",
          guide ? GUIDE : null);
    } catch (IOException e) {
      throw new IllegalStateException("Cannot load educational listing: " + fileName, e);
    }
  }
}

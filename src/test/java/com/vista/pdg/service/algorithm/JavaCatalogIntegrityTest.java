package com.vista.pdg.service.algorithm;

import static org.assertj.core.api.Assertions.*;

import com.vista.pdg.controller.dto.AlgorithmRequest;
import com.vista.pdg.model.contract.*;
import com.vista.pdg.model.generated.*;
import com.vista.pdg.model.response.*;
import com.vista.pdg.service.algorithm.def.*;
import com.vista.pdg.service.generator.impl.*;
import com.vista.pdg.service.generator.impl.graph.TreeGenerator;
import com.vista.pdg.service.layout.impl.*;
import com.vista.pdg.service.layout.impl.graph.TreeLayout3D;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/** Audit the real strategy registry, not a second hard-coded list of algorithms. */
class JavaCatalogIntegrityTest {
  @TempDir static Path compiled;
  static AnnotationConfigApplicationContext context;
  static AlgorithmDispatcher dispatcher;
  static URLClassLoader downloads;

  @BeforeAll
  static void setup() throws Exception {
    context = new AnnotationConfigApplicationContext();
    context.register(
        TreeGenerator.class,
        TreeLayout3D.class,
        StackLayout3D.class,
        LinearLayout3D.class,
        AvlStepsService.class,
        AlgorithmDispatcher.class);
    context.scan("com.vista.pdg.service.algorithm.impl");
    context.refresh();
    dispatcher = context.getBean(AlgorithmDispatcher.class);
    Set<String> files = new HashSet<>();
    List<String> args =
        new ArrayList<>(List.of("-proc:none", "--release", "17", "-d", compiled.toString()));
    for (var d : dispatcher.catalog()) {
      var java = dispatcher.run(request(d, false)).representations().getFirst();
      // Two legacy listings explicitly depend on the separately referenced guide library.
      if (java.sourceUrl() != null || !files.add(java.fileName())) continue;
      Path file = compiled.resolve(java.fileName());
      Files.writeString(file, String.join("\n", java.code()));
      args.add(file.toString());
    }
    assertThat(
            ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(String[]::new)))
        .isZero();
    downloads = new URLClassLoader(new java.net.URL[] {compiled.toUri().toURL()});
  }

  @AfterAll
  static void close() throws Exception {
    if (downloads != null) downloads.close();
    if (context != null) context.close();
  }

  @Test
  void everyPublishedAlgorithmExposesJavaAndEveryInstrumentedLineIsMapped() {
    assertThat(dispatcher.catalog()).hasSize(43);
    for (var d : dispatcher.catalog()) {
      for (boolean canvas : List.of(false, true)) {
        var r = dispatcher.run(request(d, canvas));
        assertThat(r.error()).as(d.key() + " canvas=" + canvas + ": " + r.message()).isFalse();
        assertThat(r.code()).as(d.key()).isNotEmpty();
        assertThat(r.representations()).as(d.key()).hasSize(1);
        var java = r.representations().getFirst();
        assertThat(java.language()).isEqualTo("java");
        assertThat(java.fileName()).endsWith(".java");
        assertThat(java.code()).isNotEmpty();
        assertThat(java.sourceLabel()).isNotBlank();
        for (var step : r.steps()) {
          if (step.line() == null) continue;
          assertThat(step.line()).as(d.key()).isBetween(1, r.code().size());
          assertThat(java.lineMap().get(step.line()))
              .as(d.key() + " line=" + step.line())
              .isNotEmpty()
              .allSatisfy(
                  n -> {
                    assertThat(n).isBetween(1, java.code().size());
                    assertThat(java.code().get(n - 1)).isNotBlank();
                  });
        }
      }
    }
    assertThat(dispatcher.run(new AlgorithmRequest("unknown", "unknown", "unknown", null)).error())
        .isTrue();
  }

  record Item(String id, int value) {}

  @Test
  void downloadedSortsMatchTraceValuesAndElementIdentityIncludingDuplicates() throws Exception {
    Random random = new Random(43);
    List<List<Integer>> examples =
        new ArrayList<>(
            List.of(
                List.of(1),
                List.of(1, 1, 1),
                List.of(Integer.MAX_VALUE, 0, Integer.MIN_VALUE, 0),
                List.of(5, 4, 3, 2, 1)));
    for (int k = 0; k < 12; k++) examples.add(random.ints(8, -4, 5).boxed().toList());
    for (var d : dispatcher.catalog()) {
      if (!d.family().equals("sorting")) continue;
      var listing = dispatcher.run(request(d, false)).representations().getFirst();
      var method = downloaded(listing).getMethod("ordenar", List.class, Comparator.class);
      for (var values : examples) {
        var trace =
            dispatcher.run(new AlgorithmRequest(d.type(), d.subtype(), d.operation(), values));
        List<Item> items = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) items.add(new Item("n" + i, values.get(i)));
        method.invoke(null, items, Comparator.comparingInt(Item::value));
        assertThat(items.stream().map(Item::value).toList()).as(d.key()).isSorted();
        assertThat(items.stream().map(Item::id).toList())
            .as(d.key() + values)
            .containsExactlyElementsOf(
                trace.steps().getLast().nodes().stream().map(Node3D::id).toList());
      }
    }
  }

  @Test
  void downloadedLinearAndHashOperationsPreserveTheirContracts() throws Exception {
    Deque<Integer> stack = new ArrayDeque<>(List.of(3, 7));
    downloaded("stack/simple/push")
        .getMethod("apilar", Deque.class, int[].class)
        .invoke(null, stack, new int[] {9, 9});
    assertThat(stack).containsExactly(3, 7, 9, 9);
    assertThat(
            downloaded("stack/simple/peek").getMethod("consultar", Deque.class).invoke(null, stack))
        .isEqualTo(9);
    assertThat(stack).hasSize(4);
    Deque<Integer> queue = new ArrayDeque<>(List.of(3, 7));
    downloaded("queue/simple/enqueue")
        .getMethod("encolar", Deque.class, int[].class)
        .invoke(null, queue, new int[] {9});
    assertThat(
            downloaded("queue/simple/peek").getMethod("consultar", Deque.class).invoke(null, queue))
        .isEqualTo(3);
    assertThat(queue).containsExactly(3, 7, 9);
    List<List<Integer>> table = new ArrayList<>();
    for (int i = 0; i < 7; i++) table.add(new ArrayList<>());
    var insert =
        downloaded("hash-table/chaining/insert").getMethod("ejecutar", List.class, int.class);
    for (int value : List.of(10, 17, 17, -10, Integer.MIN_VALUE)) insert.invoke(null, table, value);
    assertThat(table.get(3)).containsExactly(10, 17, 17, -10);
    var search =
        downloaded("hash-table/chaining/search").getMethod("ejecutar", List.class, int.class);
    assertThat(search.invoke(null, table, 24)).isEqualTo(false);
    assertThat(search.invoke(null, table, Integer.MIN_VALUE)).isEqualTo(true);
    assertThat(
            downloaded("hash-table/chaining/delete")
                .getMethod("ejecutar", List.class, int.class)
                .invoke(null, table, 17))
        .isEqualTo(true);
    assertThat(table.get(3)).containsExactly(10, 17, -10);
  }

  @Test
  void downloadedHeapsMatchTheTraceOnDuplicatesAndIntegerExtremes() throws Exception {
    for (var values :
        List.of(
            List.of(3), List.of(3, 9, 9, -1), List.of(Integer.MIN_VALUE, 0, Integer.MAX_VALUE))) {
      List<Integer> heap = new ArrayList<>(values);
      downloaded("tree/heap/heapify").getMethod("construir", List.class).invoke(null, heap);
      var initial =
          dispatcher.run(new AlgorithmRequest("tree", "heap", "heapify", values)).steps().getLast();
      assertThat(heap)
          .containsExactlyElementsOf(
              initial.nodes().stream().map(n -> Integer.valueOf(n.label())).toList());
      List<Integer> before = List.copyOf(heap);
      assertThat(downloaded("tree/heap/peek").getMethod("consultar", List.class).invoke(null, heap))
          .isEqualTo(Collections.max(values));
      assertThat(heap).containsExactlyElementsOf(before);
      for (String op : List.of("insert", "extract")) {
        List<Integer> actual = new ArrayList<>(before);
        var req =
            new AlgorithmRequest(
                "tree", "heap", op, null, initial.nodes(), initial.edges(), null, 16d);
        var expected = dispatcher.run(req).steps().getLast();
        if (op.equals("insert"))
          downloaded("tree/heap/insert")
              .getMethod("insertar", List.class, int.class)
              .invoke(null, actual, 16);
        else
          assertThat(
                  downloaded("tree/heap/extract")
                      .getMethod("extraer", List.class)
                      .invoke(null, actual))
              .isEqualTo(Collections.max(values));
        assertThat(actual)
            .containsExactlyElementsOf(
                expected.nodes().stream().map(n -> Integer.valueOf(n.label())).toList());
      }
    }
  }

  @Test
  void downloadedListsPreserveLinksAndIdentityInSinglyDoublyAndCircularLists() throws Exception {
    for (String subtype : List.of("singly", "doubly", "circular")) {
      var scene =
          new LinkedListGenerator()
              .generate(new LinkedListContract("linked-list", null, subtype, List.of(3, -1, 3)));
      for (String op : List.of("traverse", "search", "append", "delete", "reverse")) {
        Class<?> owner = downloaded("linked-list/simple/" + op);
        Class<?> listType = downloads.loadClass(owner.getName() + "$Lista");
        Class<?> nodeType = downloads.loadClass(owner.getName() + "$Nodo");
        Object list = listType.getConstructor().newInstance();
        List<Object> original = new ArrayList<>();
        Map<Object, String> ids = new IdentityHashMap<>();
        for (var n : scene.nodes()) {
          Object item = nodeType.getConstructor(int.class).newInstance(Integer.parseInt(n.label()));
          original.add(item);
          ids.put(item, n.id());
        }
        listType.getField("cabeza").set(list, original.getFirst());
        listType.getField("cola").set(list, original.getLast());
        listType.getField("doble").setBoolean(list, subtype.equals("doubly"));
        listType.getField("circular").setBoolean(list, subtype.equals("circular"));
        for (int i = 0; i < original.size(); i++) {
          nodeType
              .getField("siguiente")
              .set(
                  original.get(i),
                  i + 1 < original.size()
                      ? original.get(i + 1)
                      : subtype.equals("circular") ? original.getFirst() : null);
          if (subtype.equals("doubly"))
            nodeType.getField("anterior").set(original.get(i), i > 0 ? original.get(i - 1) : null);
        }
        String method =
            Map.of(
                    "traverse",
                    "recorrer",
                    "search",
                    "buscar",
                    "append",
                    "agregar",
                    "delete",
                    "eliminar",
                    "reverse",
                    "invertir")
                .get(op);
        boolean arg = List.of("search", "append", "delete").contains(op);
        Object result =
            arg
                ? owner.getMethod(method, listType, int.class).invoke(null, list, 3)
                : owner.getMethod(method, listType).invoke(null, list);
        var expected =
            dispatcher
                .run(
                    new AlgorithmRequest(
                        "linked-list", "simple", op, null, scene.nodes(), scene.edges(), null, 3d))
                .steps()
                .getLast();
        if (op.equals("search")) assertThat(result).isSameAs(original.getFirst());
        if (op.equals("traverse")) assertThat(result).isEqualTo(List.of(3, -1, 3));
        List<Integer> actualValues = new ArrayList<>();
        List<String> actualIds = new ArrayList<>();
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Object current = listType.getField("cabeza").get(list), previous = null;
        while (current != null && seen.add(current)) {
          actualValues.add(nodeType.getField("valor").getInt(current));
          if (ids.containsKey(current)) actualIds.add(ids.get(current));
          assertThat(nodeType.getField("anterior").get(current))
              .isSameAs(subtype.equals("doubly") ? previous : null);
          previous = current;
          current = nodeType.getField("siguiente").get(current);
        }
        assertThat(current)
            .isSameAs(subtype.equals("circular") ? listType.getField("cabeza").get(list) : null);
        assertThat(listType.getField("cola").get(list)).isSameAs(previous);
        assertThat(actualValues)
            .containsExactlyElementsOf(
                expected.nodes().stream().map(n -> Integer.valueOf(n.label())).toList());
        assertThat(actualIds)
            .containsExactlyElementsOf(
                expected.nodes().stream().map(Node3D::id).filter(ids.values()::contains).toList());
      }
    }
  }

  @Test
  void downloadedGraphAlgorithmsHandleDisconnectedGraphsAndNegativeCycles() throws Exception {
    List<String> nodes = List.of("a", "b", "c", "d", "isolated");
    for (String op : List.of("dijkstra", "floyd", "prim", "kruskal")) {
      Class<?> owner = downloaded("graph/simple/" + op);
      var edge =
          downloads
              .loadClass(owner.getName() + "$Arista")
              .getConstructor(String.class, String.class, Integer.class, boolean.class);
      List<Object> edges =
          List.of(
              edge.newInstance("a", "b", 4, false),
              edge.newInstance("a", "c", 1, false),
              edge.newInstance("c", "b", 2, false),
              edge.newInstance("b", "d", 1, false));
      Object result =
          List.of("dijkstra", "prim").contains(op)
              ? owner
                  .getMethod("calcular", List.class, List.class, String.class)
                  .invoke(null, nodes, edges, "a")
              : owner.getMethod("calcular", List.class, List.class).invoke(null, nodes, edges);
      if (op.equals("floyd")) {
        long[][] distances = (long[][]) result;
        assertThat(distances[0]).containsExactly(0, 3, 1, 4, Long.MAX_VALUE / 4);
        assertThatThrownBy(
                () ->
                    owner
                        .getMethod("calcular", List.class, List.class)
                        .invoke(null, List.of("a"), List.of(edge.newInstance("a", "a", -1, true))))
            .hasCauseInstanceOf(IllegalArgumentException.class);
      } else if (op.equals("dijkstra")) {
        assertThat(result.getClass().getMethod("distancias").invoke(result))
            .isEqualTo(Map.of("a", 0L, "b", 3L, "c", 1L, "d", 4L, "isolated", Long.MAX_VALUE / 4));
        assertThatThrownBy(
                () ->
                    owner
                        .getMethod("calcular", List.class, List.class, String.class)
                        .invoke(null, nodes, List.of(edge.newInstance("a", "b", -1, true)), "a"))
            .hasCauseInstanceOf(IllegalArgumentException.class);
      } else {
        assertThat(result.getClass().getMethod("costo").invoke(result)).isEqualTo(4L);
        assertThat(result.getClass().getMethod("componentes").invoke(result)).isEqualTo(2);
        if (op.equals("prim"))
          assertThatThrownBy(
                  () ->
                      owner
                          .getMethod("calcular", List.class, List.class, String.class)
                          .invoke(null, nodes, List.of(edge.newInstance("a", "b", 1, true)), "a"))
              .hasCauseInstanceOf(IllegalArgumentException.class);
        else
          assertThatThrownBy(
                  () ->
                      owner
                          .getMethod("calcular", List.class, List.class)
                          .invoke(null, nodes, List.of(edge.newInstance("a", "b", 1, true))))
              .hasCauseInstanceOf(IllegalArgumentException.class);
      }
    }
    var dfs = downloaded("graph/simple/dfs").getMethod("recorrer", String.class, Map.class);
    assertThat(
            dfs.invoke(
                null,
                "a",
                Map.of(
                    "a",
                    List.of("b", "c"),
                    "b",
                    List.of("d"),
                    "c",
                    List.of(),
                    "d",
                    List.of(),
                    "isolated",
                    List.of())))
        .isEqualTo(List.of("a", "b", "d", "c"));
  }

  @Test
  void downloadedTreeOperationsMatchTraversalSearchAndBstInsertion() throws Exception {
    for (String op : List.of("preorder", "postorder", "levelorder")) {
      Class<?> owner = downloaded("tree/bst/" + op);
      Class<?> nodeType = downloads.loadClass(owner.getName() + "$Nodo");
      Class<?> arrayType = java.lang.reflect.Array.newInstance(nodeType, 0).getClass();
      var constructor = nodeType.getConstructor(int.class, arrayType);
      Object empty = java.lang.reflect.Array.newInstance(nodeType, 0);
      Object left = constructor.newInstance(5, empty), right = constructor.newInstance(15, empty);
      Object children = java.lang.reflect.Array.newInstance(nodeType, 2);
      java.lang.reflect.Array.set(children, 0, left);
      java.lang.reflect.Array.set(children, 1, right);
      Object root = constructor.newInstance(10, children);
      Object result =
          op.equals("levelorder")
              ? owner.getMethod("recorrer", nodeType).invoke(null, root)
              : owner
                  .getMethod("recorrer", nodeType, List.class)
                  .invoke(null, root, new ArrayList<Integer>());
      assertThat(result)
          .isEqualTo(op.equals("postorder") ? List.of(5, 15, 10) : List.of(10, 5, 15));
    }
    for (String key : List.of("tree/bst/search", "tree/btree/search")) {
      Class<?> owner = downloaded(key), nodeType = downloads.loadClass(owner.getName() + "$Nodo");
      Object root, target;
      if (key.contains("btree")) {
        Class<?> arrayType = java.lang.reflect.Array.newInstance(nodeType, 0).getClass();
        var constructor = nodeType.getConstructor(int.class, arrayType);
        target = constructor.newInstance(15, java.lang.reflect.Array.newInstance(nodeType, 0));
        Object children = java.lang.reflect.Array.newInstance(nodeType, 1);
        java.lang.reflect.Array.set(children, 0, target);
        root = constructor.newInstance(10, children);
      } else {
        root = nodeType.getConstructor(int.class).newInstance(10);
        target = nodeType.getConstructor(int.class).newInstance(15);
        nodeType.getField("derecha").set(root, target);
      }
      assertThat(owner.getMethod("buscar", nodeType, int.class).invoke(null, root, 15))
          .isSameAs(target);
      assertThat(owner.getMethod("buscar", nodeType, int.class).invoke(null, root, 99)).isNull();
    }
    Class<?> bst = downloaded("tree/bst/insert"),
        bstNode = downloads.loadClass(bst.getName() + "$Nodo");
    var build = bst.getMethod("construir", bstNode, int[].class);
    Object root =
        build.invoke(null, null, new int[] {10, 5, 15, 5, Integer.MIN_VALUE, Integer.MAX_VALUE});
    Object same = build.invoke(null, root, new int[] {7, 10});
    assertThat(same).isSameAs(root);
    List<Integer> sorted = new ArrayList<>();
    readBst(root, bstNode, sorted);
    assertThat(sorted).containsExactly(Integer.MIN_VALUE, 5, 7, 10, 15, Integer.MAX_VALUE);
  }

  private static void readBst(Object node, Class<?> type, List<Integer> values) throws Exception {
    if (node == null) return;
    readBst(type.getField("izquierda").get(node), type, values);
    values.add(type.getField("valor").getInt(node));
    readBst(type.getField("derecha").get(node), type, values);
  }

  @Test
  void heapSlotsRemainValidAfterCameraCoordinatesAndInputOrderChange() {
    var initial =
        dispatcher
            .run(new AlgorithmRequest("tree", "heap", "heapify", List.of(3, 9, 9, -1)))
            .steps()
            .getLast();
    var moved =
        initial.nodes().reversed().stream().map(n -> n.withPosition(-n.x(), n.y(), n.z())).toList();
    for (String op : List.of("insert", "extract", "peek")) {
      var expected =
          dispatcher.run(
              new AlgorithmRequest(
                  "tree", "heap", op, null, initial.nodes(), initial.edges(), null, 16d));
      var actual =
          dispatcher.run(
              new AlgorithmRequest(
                  "tree", "heap", op, null, moved, initial.edges().reversed(), null, 16d));
      var bySlot =
          Comparator.comparingInt((Node3D n) -> ((Number) n.properties().get("index")).intValue());
      assertThat(actual.steps().getLast().nodes().stream().sorted(bySlot).map(Node3D::id).toList())
          .containsExactlyElementsOf(
              expected.steps().getLast().nodes().stream().sorted(bySlot).map(Node3D::id).toList());
      assertThat(actual.steps().getLast().variables().get("máximo"))
          .isEqualTo(expected.steps().getLast().variables().get("máximo"));
    }
    var bad =
        initial.nodes().stream()
            .map(
                n ->
                    new Node3D(
                        n.id(),
                        n.label(),
                        n.x(),
                        n.y(),
                        n.z(),
                        n.depth(),
                        n.parent(),
                        Map.of("index", 0)))
            .toList();
    assertThatThrownBy(
            () ->
                dispatcher.run(
                    new AlgorithmRequest(
                        "tree", "heap", "peek", null, bad, initial.edges(), null, null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("índices");
  }

  private static Class<?> downloaded(CodeRepresentation listing) throws Exception {
    return downloads.loadClass(listing.fileName().replace(".java", ""));
  }

  private static Class<?> downloaded(String key) throws Exception {
    return downloaded(AlgorithmCode.forAlgorithm(key));
  }

  static AlgorithmRequest request(AlgorithmDescriptor d, boolean canvas) {
    List<Integer> values = List.of(10, 5, 15);
    if (!canvas && d.input().equals("values"))
      return new AlgorithmRequest(d.type(), d.subtype(), d.operation(), values);
    GeneratedStructure scene;
    switch (d.type()) {
      case "stack" ->
          scene = new StackGenerator().generate(new StackContract("stack", null, values));
      case "queue" ->
          scene = new QueueGenerator().generate(new QueueContract("queue", null, values));
      case "linked-list" ->
          scene =
              new LinkedListGenerator()
                  .generate(new LinkedListContract("linked-list", null, "singly", values));
      case "hash-table" ->
          scene =
              new HashTableGenerator()
                  .generate(
                      new HashTableContract("hash-table", null, 7, List.of(10, 17, 4), "modular"));
      case "tree" -> {
        if (d.subtype().equals("heap")) {
          var heap =
              new com.vista.pdg.service.algorithm.impl.HeapBasicsAlgorithms.Heapify()
                  .generate(new AlgorithmRequest("tree", "heap", "heapify", values))
                  .steps()
                  .getLast();
          scene = new GeneratedStructure(null, heap.nodes(), heap.edges(), Map.of());
        } else
          scene =
              new TreeGenerator()
                  .generate(
                      new TreeContract(
                          "tree",
                          null,
                          "bst",
                          List.of(new TreeContract.Operation("insert", values)),
                          null));
      }
      default ->
          scene =
              new GeneratedStructure(
                  null,
                  List.of(node("a"), node("b"), node("c"), node("d")),
                  List.of(
                      new Edge3D("ab", "a", "b", 4, false),
                      new Edge3D("ac", "a", "c", 1, false),
                      new Edge3D("bc", "b", "c", 2, false)),
                  Map.of());
    }
    double argument = "value".equals(d.parameter()) ? 16 : d.type().equals("hash-table") ? 17 : 5;
    return new AlgorithmRequest(
        d.type(), d.subtype(), d.operation(), null, scene.nodes(), scene.edges(), null, argument);
  }

  private static Node3D node(String id) {
    return new Node3D(id, id.toUpperCase(), 0, 0, 0, 0, null, Map.of());
  }
}

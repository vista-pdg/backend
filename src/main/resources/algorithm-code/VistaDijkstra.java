// Implementacion educativa de VISTA. Java 17, sin dependencias externas.
import java.util.*;

public class VistaDijkstra {
    public static final long INF = Long.MAX_VALUE / 4;
    public record Resultado(Map<String, Long> distancias, Map<String, String> anteriores) {}
    public static Resultado calcular(List<String> nodos, List<Arista> aristas, String inicio) {
        Map<String, List<Arco>> vecinos = adyacencia(nodos, aristas);
        if (!vecinos.containsKey(inicio)) throw new IllegalArgumentException("Origen invalido");
        if (aristas.stream().anyMatch(e -> peso(e) < 0)) throw new IllegalArgumentException("Peso negativo");
        Map<String, Long> distancia = new LinkedHashMap<>();
        Map<String, String> anterior = new LinkedHashMap<>(); Set<String> fijos = new LinkedHashSet<>();
        for (String id : nodos) distancia.put(id, INF);
        distancia.put(inicio, 0L);
        while (true) {
            String u = null;
            for (String id : nodos) if (!fijos.contains(id) && distancia.get(id) < INF
                    && (u == null || distancia.get(id) < distancia.get(u))) u = id;
            if (u == null) break;
            fijos.add(u);
            for (Arco arco : vecinos.get(u)) {
                String v = arco.destino(); long candidato = distancia.get(u) + arco.peso();
                if (!fijos.contains(v) && candidato < distancia.get(v)) {
                    distancia.put(v, candidato); anterior.put(v, u);
                }
            }
        }
        return new Resultado(distancia, anterior);
    }
    public record Arista(String desde, String hasta, Integer peso, boolean dirigida) {}
    private static long peso(Arista e) { return e.peso() == null ? 1L : e.peso(); }
    private static void validar(List<String> nodos, List<Arista> aristas) {
        Set<String> ids = new HashSet<>(nodos);
        if (nodos.isEmpty() || ids.size() != nodos.size() || nodos.stream().anyMatch(n -> n == null || n.isBlank()))
            throw new IllegalArgumentException("Identificadores invalidos");
        for (Arista e : aristas) if (!ids.contains(e.desde()) || !ids.contains(e.hasta()))
            throw new IllegalArgumentException("Extremo fuera del grafo");
    }
    private record Arco(String destino, long peso) {}
    private static Map<String, List<Arco>> adyacencia(List<String> nodos, List<Arista> aristas) {
        validar(nodos, aristas);
        Map<String, List<Arco>> vecinos = new LinkedHashMap<>();
        for (String id : nodos) vecinos.put(id, new ArrayList<>());
        for (Arista e : aristas) {
            vecinos.get(e.desde()).add(new Arco(e.hasta(), peso(e)));
            if (!e.dirigida()) vecinos.get(e.hasta()).add(new Arco(e.desde(), peso(e)));
        }
        return vecinos;
    }
}

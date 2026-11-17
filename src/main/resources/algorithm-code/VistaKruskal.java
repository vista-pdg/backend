// Implementacion educativa de VISTA. Java 17, sin dependencias externas.
import java.util.*;

public class VistaKruskal {
    public record Resultado(List<Arista> bosque, long costo, int componentes) {}
    public static Resultado calcular(List<String> nodos, List<Arista> aristas) {
        validar(nodos, aristas);
        if (aristas.stream().anyMatch(Arista::dirigida)) throw new IllegalArgumentException("Requiere grafo no dirigido");
        Map<String, String> padre = new LinkedHashMap<>(); for (String id : nodos) padre.put(id, id);
        List<Arista> ordenadas = new ArrayList<>(aristas);
        ordenadas.sort(Comparator.comparingLong(VistaKruskal::peso));
        List<Arista> bosque = new ArrayList<>(); long costo = 0; int componentes = nodos.size();
        for (Arista e : ordenadas) {
            String a = encontrar(padre, e.desde()), b = encontrar(padre, e.hasta());
            if (a.equals(b)) continue;
            padre.put(a, b); bosque.add(e); costo += peso(e); componentes--;
        }
        return new Resultado(bosque, costo, componentes);
    }
    private static String encontrar(Map<String, String> padre, String id) {
        while (!padre.get(id).equals(id)) { padre.put(id, padre.get(padre.get(id))); id = padre.get(id); }
        return id;
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
}

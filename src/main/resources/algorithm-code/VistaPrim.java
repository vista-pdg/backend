// Implementacion educativa de VISTA. Java 17, sin dependencias externas.
import java.util.*;

public class VistaPrim {
    public record Resultado(List<Arista> bosque, long costo, int componentes) {}
    public static Resultado calcular(List<String> nodos, List<Arista> aristas, String inicio) {
        validar(nodos, aristas);
        if (!nodos.contains(inicio)) throw new IllegalArgumentException("Origen invalido");
        if (aristas.stream().anyMatch(Arista::dirigida)) throw new IllegalArgumentException("Requiere grafo no dirigido");
        Set<String> visitados = new LinkedHashSet<>(); List<Arista> bosque = new ArrayList<>(); long costo = 0;
        Set<String> raices = new LinkedHashSet<>(); raices.add(inicio); raices.addAll(nodos);
        int componentes = 0;
        for (String raiz : raices) {
            if (!visitados.add(raiz)) continue;
            componentes++;
            while (true) {
                Arista mejor = null;
                for (Arista e : aristas) {
                    if (visitados.contains(e.desde()) == visitados.contains(e.hasta())) continue;
                    if (mejor == null || peso(e) < peso(mejor)) mejor = e;
                }
                if (mejor == null) break;
                String siguiente = visitados.contains(mejor.desde()) ? mejor.hasta() : mejor.desde();
                bosque.add(mejor); visitados.add(siguiente); costo += peso(mejor);
            }
        }
        return new Resultado(bosque, costo, componentes);
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

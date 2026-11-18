// Implementacion educativa de VISTA. Java 17, sin dependencias externas.
import java.util.*;

public class VistaFloyd {
    public static final long INF = Long.MAX_VALUE / 4;
    public static long[][] calcular(List<String> nodos, List<Arista> aristas) {
        Map<String, List<Arco>> vecinos = adyacencia(nodos, aristas);
        int n = nodos.size(); long[][] d = new long[n][n];
        for (int i = 0; i < n; i++) { Arrays.fill(d[i], INF); d[i][i] = 0; }
        for (int i = 0; i < n; i++) for (Arco a : vecinos.get(nodos.get(i))) {
            int j = nodos.indexOf(a.destino()); d[i][j] = Math.min(d[i][j], a.peso());
        }
        comprobarCiclos(d);
        for (int k = 0; k < n; k++) {
            for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) {
                if (d[i][k] != INF && d[k][j] != INF) {
                    d[i][j] = Math.min(d[i][j], d[i][k] + d[k][j]);
                }
            }
            comprobarCiclos(d);
        }
        return d;
    }
    private static void comprobarCiclos(long[][] d) {
        for (int i = 0; i < d.length; i++) if (d[i][i] < 0) throw new IllegalArgumentException("Ciclo negativo");
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

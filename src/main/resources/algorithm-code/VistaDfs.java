// Implementacion educativa de VISTA. Java 17, sin dependencias externas.
import java.util.*;

public class VistaDfs {
    public static List<String> recorrer(String inicio, Map<String, List<String>> vecinos) {
        Deque<String> pila = new ArrayDeque<>(); pila.push(inicio);
        Set<String> visitados = new LinkedHashSet<>();
        while (!pila.isEmpty()) {
            String u = pila.pop(); if (!visitados.add(u)) continue;
            List<String> adyacentes = vecinos.getOrDefault(u, List.of());
            for (int i = adyacentes.size() - 1; i >= 0; i--) {
                String v = adyacentes.get(i);
                if (!visitados.contains(v)) pila.push(v);
            }
        }
        return new ArrayList<>(visitados);
    }
}

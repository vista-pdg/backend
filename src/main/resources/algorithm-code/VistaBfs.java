// Implementación educativa de VISTA; no procede del repositorio guía.
// La adyacencia respeta la dirección de las aristas y su orden en el lienzo.
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class VistaBfs {
    public static List<String> bfs(String inicio, Map<String, List<String>> vecinos) {
        Deque<String> cola = new ArrayDeque<>();
        Set<String> visitados = new LinkedHashSet<>();
        List<String> orden = new ArrayList<>();
        cola.add(inicio); visitados.add(inicio);
        while (!cola.isEmpty()) {
            String u = cola.remove();
            orden.add(u);
            for (String v : vecinos.getOrDefault(u, List.of())) {
                if (visitados.add(v)) { cola.add(v); }
            }
        }
        return orden;
    }
}

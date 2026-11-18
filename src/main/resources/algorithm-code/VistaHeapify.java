// Implementacion educativa de VISTA. Java 17, sin dependencias externas.
import java.util.*;

public class VistaHeapify {
    // La lista corresponde al arbol completo en orden por niveles.
    public static List<Integer> construir(List<Integer> heap) {
        Objects.requireNonNull(heap);
        for (int i = heap.size() / 2 - 1; i >= 0; i--) hundir(heap, i);
        return heap;
    }
    private static void hundir(List<Integer> heap, int i) {
        while (2 * i + 1 < heap.size()) {
            int izquierdo = 2 * i + 1, derecho = izquierdo + 1;
            int mayor = derecho < heap.size() && heap.get(derecho) > heap.get(izquierdo) ? derecho : izquierdo;
            if (heap.get(i) >= heap.get(mayor)) break;
            Collections.swap(heap, i, mayor);
            i = mayor;
        }
    }
}

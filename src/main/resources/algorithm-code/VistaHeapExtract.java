// Implementacion educativa de VISTA. Java 17, sin dependencias externas.
import java.util.*;

public class VistaHeapExtract {
    // Requiere un heap maximo no vacio. La misma lista conserva el heap restante.
    public static int extraer(List<Integer> heap) {
        int maximo = heap.get(0);
        int ultima = heap.remove(heap.size() - 1);
        if (!heap.isEmpty()) heap.set(0, ultima);
        if (!heap.isEmpty()) hundir(heap, 0);
        return maximo;
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

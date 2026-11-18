// Implementacion educativa de VISTA. Java 17, sin dependencias externas.
import java.util.*;

public class VistaHeapInsert {
    // Requiere un heap maximo existente, en orden por niveles.
    public static List<Integer> insertar(List<Integer> heap, int valor) {
        heap.add(valor);
        int i = heap.size() - 1;
        while (i > 0) {
            int padre = (i - 1) / 2;
            if (heap.get(padre) >= valor) break;
            Collections.swap(heap, i, padre);
            i = padre;
        }
        return heap;
    }
}

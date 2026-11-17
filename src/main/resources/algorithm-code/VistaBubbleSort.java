// Implementacion educativa de VISTA. Java 17, sin dependencias externas.
import java.util.*;

public class VistaBubbleSort {
    public static <T> List<T> ordenar(List<T> a, Comparator<? super T> c) {
        for (int fin = a.size() - 1; fin > 0; fin--) {
            boolean cambios = false;
            for (int j = 0; j < fin; j++) {
                if (c.compare(a.get(j), a.get(j + 1)) > 0) {
                    Collections.swap(a, j, j + 1); cambios = true;
                }
            }
            if (!cambios) break;
        }
        return a;
    }
}

// Implementacion educativa de VISTA. Java 17, sin dependencias externas.
import java.util.*;

public class VistaQuickSort {
    public static <T> List<T> ordenar(List<T> a, Comparator<? super T> c) {
        particionar(a, 0, a.size() - 1, c);
        return a;
    }
    private static <T> void particionar(List<T> a, int inicio, int fin, Comparator<? super T> c) {
        if (inicio >= fin) return;
        T pivote = a.get(fin); int frontera = inicio;
        for (int j = inicio; j < fin; j++) {
            if (c.compare(a.get(j), pivote) <= 0) {
                intercambiar(a, frontera++, j);
            }
        }
        intercambiar(a, frontera, fin);
        particionar(a, inicio, frontera - 1, c); particionar(a, frontera + 1, fin, c);
    }
    private static <T> void intercambiar(List<T> a, int i, int j) {
        if (i != j) Collections.swap(a, i, j);
    }
}

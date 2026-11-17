// Implementacion educativa de VISTA. Java 17, sin dependencias externas.
import java.util.*;

public class VistaHeapSort {
    public static <T> List<T> ordenar(List<T> a, Comparator<? super T> c) {
        for (int i = a.size() / 2 - 1; i >= 0; i--) hundir(a, a.size(), i, c);
        for (int fin = a.size() - 1; fin > 0; fin--) {
            Collections.swap(a, 0, fin);
            hundir(a, fin, 0, c);
        }
        return a;
    }
    private static <T> int comparar(List<T> a, int i, int j, Comparator<? super T> c) {
        return c.compare(a.get(i), a.get(j));
    }
    private static <T> void hundir(List<T> a, int n, int i, Comparator<? super T> c) {
        while (2 * i + 1 < n) {
            int izquierdo = 2 * i + 1, derecho = izquierdo + 1, mayor = izquierdo;
            if (derecho < n && comparar(a, derecho, izquierdo, c) > 0) mayor = derecho;
            if (comparar(a, i, mayor, c) >= 0) return;
            Collections.swap(a, i, mayor);
            i = mayor;
        }
    }
}

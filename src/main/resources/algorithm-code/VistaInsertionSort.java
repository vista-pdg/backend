// Implementacion educativa de VISTA. Java 17, sin dependencias externas.
import java.util.*;

public class VistaInsertionSort {
    public static <T> List<T> ordenar(List<T> a, Comparator<? super T> c) {
        for (int i = 1; i < a.size(); i++) {
            for (int j = i; j > 0; j--) {
                if (c.compare(a.get(j - 1), a.get(j)) <= 0) break;
                Collections.swap(a, j - 1, j);
            }
        }
        return a;
    }
}

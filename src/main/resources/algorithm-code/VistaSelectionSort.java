// Implementacion educativa de VISTA. Java 17, sin dependencias externas.
import java.util.*;

public class VistaSelectionSort {
    public static <T> List<T> ordenar(List<T> a, Comparator<? super T> c) {
        for (int i = 0; i < a.size() - 1; i++) {
            int minimo = i;
            for (int j = i + 1; j < a.size(); j++) {
                if (c.compare(a.get(j), a.get(minimo)) < 0) minimo = j;
            }
            if (i != minimo) Collections.swap(a, i, minimo);
        }
        return a;
    }
}

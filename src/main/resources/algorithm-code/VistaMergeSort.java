// Implementacion educativa de VISTA. Java 17, sin dependencias externas.
import java.util.*;

public class VistaMergeSort {
    public static <T> List<T> ordenar(List<T> a, Comparator<? super T> c) {
        mezclar(a, 0, a.size(), c);
        return a;
    }
    private static <T> void mezclar(List<T> a, int inicio, int fin, Comparator<? super T> c) {
        if (fin - inicio < 2) return;
        int medio = (inicio + fin) / 2;
        mezclar(a, inicio, medio, c); mezclar(a, medio, fin, c);
        List<T> izquierda = new ArrayList<>(a.subList(inicio, medio));
        List<T> derecha = new ArrayList<>(a.subList(medio, fin));
        List<T> buffer = new ArrayList<>(); int i = 0, j = 0;
        while (i < izquierda.size() && j < derecha.size()) {
            if (c.compare(izquierda.get(i), derecha.get(j)) <= 0) buffer.add(izquierda.get(i++));
            else buffer.add(derecha.get(j++));
        }
        while (i < izquierda.size()) buffer.add(izquierda.get(i++));
        while (j < derecha.size()) buffer.add(derecha.get(j++));
        for (int k = 0; k < buffer.size(); k++) a.set(inicio + k, buffer.get(k));
    }
}

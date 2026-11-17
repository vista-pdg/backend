// Implementacion educativa de VISTA. Java 17, sin dependencias externas.
import java.util.*;

public class VistaListAppend {
    public static Lista agregar(Lista lista, int valor) {
        List<Nodo> nodos = orden(lista);
        nodos.add(new Nodo(valor)); reenlazar(lista, nodos);
        return lista;
    }

    public static class Nodo {
        public final int valor;
        public Nodo siguiente, anterior;
        public Nodo(int valor) { this.valor = valor; }
    }
    public static class Lista {
        public Nodo cabeza, cola;
        public boolean doble, circular;
    }
    private static List<Nodo> orden(Lista lista) {
        List<Nodo> nodos = new ArrayList<>();
        Set<Nodo> vistos = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Nodo n = lista.cabeza; n != null && vistos.add(n); n = n.siguiente) nodos.add(n);
        return nodos;
    }
    private static void reenlazar(Lista lista, List<Nodo> nodos) {
        int n = nodos.size();
        lista.cabeza = n == 0 ? null : nodos.get(0);
        lista.cola = n == 0 ? null : nodos.get(n - 1);
        for (int i = 0; i < n; i++) {
            Nodo actual = nodos.get(i);
            actual.siguiente = i + 1 < n ? nodos.get(i + 1) : lista.circular ? lista.cabeza : null;
            actual.anterior = !lista.doble ? null : i > 0 ? nodos.get(i - 1) : lista.circular ? lista.cola : null;
        }
    }
}

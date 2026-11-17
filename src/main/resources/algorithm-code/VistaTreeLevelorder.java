// Implementacion educativa de VISTA. Java 17, sin dependencias externas.
import java.util.*;

public class VistaTreeLevelorder {
    // Los hijos se reciben de izquierda a derecha, tambien para nodos de mas de dos hijos.
    public static List<Integer> recorrer(Nodo raiz) {
        List<Integer> salida = new ArrayList<>();
        Deque<Nodo> cola = new ArrayDeque<>(); cola.add(raiz);
        while (!cola.isEmpty()) {
            Nodo nodo = cola.removeFirst(); salida.add(nodo.valor);
            cola.addAll(nodo.hijos);
        }
        return salida;
    }
    public static class Nodo {
        public final int valor;
        public final List<Nodo> hijos;
        public Nodo(int valor, Nodo... hijos) { this.valor = valor; this.hijos = List.of(hijos); }
    }
}

// Implementacion educativa de VISTA. Java 17, sin dependencias externas.
import java.util.*;

public class VistaBtreeSearch {
    // La demo representa cada clave como nodo: busqueda por recorrido, no descenso B-tree clasico.
    public static Nodo buscar(Nodo raiz, int objetivo) {
        Deque<Nodo> cola = new ArrayDeque<>(); cola.add(raiz);
        while (!cola.isEmpty()) {
            Nodo nodo = cola.removeFirst(); int clave = nodo.valor;
            if (clave == objetivo) return nodo;
            cola.addAll(nodo.hijos);
        }
        return null;
    }
    public static class Nodo {
        public final int valor;
        public final List<Nodo> hijos;
        public Nodo(int valor, Nodo... hijos) { this.valor = valor; this.hijos = List.of(hijos); }
    }
}

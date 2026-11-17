// Implementacion educativa de VISTA. Java 17, sin dependencias externas.
import java.util.*;

public class VistaBstInsert {
    // Para crear un BST se pasa null; para reutilizarlo se pasa la raiz actual.
    public static Nodo construir(Nodo raiz, int[] valores) {
        Nodo actual = raiz;
        for (int valor : valores) {
            actual = insertar(actual, valor);
        }
        return actual;
    }
    private static Nodo insertar(Nodo nodo, int valor) {
        if (nodo == null) return new Nodo(valor);
        int comparacion = Integer.compare(valor, nodo.valor);
        if (comparacion < 0) nodo.izquierda = insertar(nodo.izquierda, valor);
        else if (comparacion > 0) nodo.derecha = insertar(nodo.derecha, valor);
        return nodo; // Los duplicados conservan el nodo existente.
    }
    public static class Nodo {
        public final int valor;
        public Nodo izquierda, derecha;
        public Nodo(int valor) { this.valor = valor; }
    }
}

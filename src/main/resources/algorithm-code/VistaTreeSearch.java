// Implementacion educativa de VISTA. Java 17, sin dependencias externas.
import java.util.*;

public class VistaTreeSearch {
    public static Nodo buscar(Nodo raiz, int objetivo) {
        Nodo nodo = raiz;
        while (nodo != null) {
            int comparacion = Integer.compare(objetivo, nodo.valor);
            if (comparacion == 0) return nodo;
            nodo = comparacion < 0 ? nodo.izquierda : nodo.derecha;
        }
        return null;
    }
    public static class Nodo {
        public final int valor;
        public Nodo izquierda, derecha;
        public Nodo(int valor) { this.valor = valor; }
    }
}

// Implementacion educativa de VISTA. Java 17, sin dependencias externas.
import java.util.*;

public class VistaTreePostorder {
    // Los hijos se reciben de izquierda a derecha. La salida inicial puede ser new ArrayList<>.
    public static List<Integer> recorrer(Nodo nodo, List<Integer> salida) {
        if (nodo == null) return salida;

        for (Nodo hijo : nodo.hijos) recorrer(hijo, salida);
        salida.add(nodo.valor);
        return salida;
    }
    public static class Nodo {
        public final int valor;
        public final List<Nodo> hijos;
        public Nodo(int valor, Nodo... hijos) { this.valor = valor; this.hijos = List.of(hijos); }
    }
}

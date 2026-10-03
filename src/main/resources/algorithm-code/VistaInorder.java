// Implementación educativa de VISTA; no procede del repositorio guía.
import java.util.List;

public class VistaInorder {
    public record Nodo(int valor, Nodo izq, Nodo der) {}
    public static void inorden(Nodo nodo, List<Integer> salida) {
        if (nodo == null) {
            return;
        }
        inorden(nodo.izq(), salida);
        salida.add(nodo.valor());
        inorden(nodo.der(), salida);
        return;
    }
}

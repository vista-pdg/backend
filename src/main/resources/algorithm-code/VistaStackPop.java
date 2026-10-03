// Ejemplo de VISTA: usa la API del repositorio Estructuras-no-recursivas.
// Compilar junto a esa biblioteca Java 17; no incluye su implementación.
import com.cyedbooks.estructuras.stack.LinkedStack;
import java.util.ArrayList;
import java.util.List;

public class VistaStackPop {
    public static <T> List<T> vaciar(LinkedStack<T> pila) {
        List<T> retirados = new ArrayList<>();
        while (!pila.isEmpty()) {
            T tope = pila.peek();
            pila.pop(); retirados.add(tope);
        }
        return retirados;
    }
}

// Ejemplo de VISTA: usa la API del repositorio Estructuras-no-recursivas.
// Compilar junto a esa biblioteca Java 17; no incluye su implementación.
import com.cyedbooks.estructuras.queue.LinkedQueue;
import java.util.ArrayList;
import java.util.List;

public class VistaQueueDequeue {
    public static <T> List<T> vaciar(LinkedQueue<T> cola) {
        List<T> atendidos = new ArrayList<>();
        while (!cola.isEmpty()) {
            T frente = cola.front();
            cola.dequeue(); atendidos.add(frente);
        }
        return atendidos;
    }
}

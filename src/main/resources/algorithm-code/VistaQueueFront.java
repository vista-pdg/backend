// Implementacion educativa de VISTA. Java 17, sin dependencias externas.
import java.util.*;

public class VistaQueueFront {
    public static int consultar(Deque<Integer> estructura) {
        if (estructura.isEmpty()) throw new NoSuchElementException("Estructura vacia");
        return estructura.getFirst();
    }
}

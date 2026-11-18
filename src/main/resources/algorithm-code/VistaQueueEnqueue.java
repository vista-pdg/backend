// Implementacion educativa de VISTA. Java 17, sin dependencias externas.
import java.util.*;

public class VistaQueueEnqueue {
    // Pasar una estructura vacia para crearla o la existente para conservar sus elementos.
    public static Deque<Integer> encolar(Deque<Integer> estructura, int[] valores) {
        Objects.requireNonNull(estructura);
        for (int valor : valores) {
            estructura.addLast(valor);
        }
        return estructura;
    }
}

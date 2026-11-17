// Implementacion educativa de VISTA. Java 17, sin dependencias externas.
import java.util.*;

public class VistaHashInsert {
    // h(k) = |k % numeroDeBuckets|, tambien para claves negativas.
    // Insertar devuelve si la clave ya existia; siempre agrega la nueva ocurrencia.
    public static boolean ejecutar(List<List<Integer>> tabla, int valor) {
        if (tabla.isEmpty()) throw new IllegalArgumentException("Se requieren buckets");
        int bucket = Math.abs(valor % tabla.size());
        List<Integer> cadena = tabla.get(bucket);
        boolean encontrado = false;
        int posicion = -1;
        for (int i = 0; i < cadena.size(); i++) {
            if (cadena.get(i) == valor) encontrado = true;
        }
        cadena.add(valor); // Encadenamiento al final; permite duplicados.
        return encontrado;
    }
}

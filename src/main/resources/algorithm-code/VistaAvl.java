// Implementación educativa de VISTA; no procede del repositorio guía.
// El rastro agrupa llamadas recursivas: inserción, desequilibrio y cada rotación.
public class VistaAvl {
    public static class Nodo {
        public int valor, altura = 1;
        public Nodo izq, der;
        Nodo(int valor) { this.valor = valor; }
    }
    public static Nodo insertarTodos(int[] valores) {
        Nodo raiz = null;
        for (int valor : valores) raiz = insertar(raiz, valor);
        return raiz;
    }
    public static Nodo insertar(Nodo nodo, int valor) {
        if (nodo == null) return new Nodo(valor);
        if (valor < nodo.valor) nodo.izq = insertar(nodo.izq, valor);
        else if (valor > nodo.valor) nodo.der = insertar(nodo.der, valor);
        Nodo balanceado = balancear(nodo);
        return balanceado;
    }
    private static Nodo balancear(Nodo nodo) {
        actualizar(nodo);
        int balance = factor(nodo);
        if (balance > 1) {
            if (factor(nodo.izq) < 0) nodo.izq = izquierda(nodo.izq);
            return derecha(nodo);
        }
        if (balance < -1) {
            if (factor(nodo.der) > 0) nodo.der = derecha(nodo.der);
            return izquierda(nodo);
        }
        return nodo;
    }
    private static int altura(Nodo n) { return n == null ? 0 : n.altura; }
    private static int factor(Nodo n) { return altura(n.izq) - altura(n.der); }
    private static void actualizar(Nodo n) {
        n.altura = 1 + Math.max(altura(n.izq), altura(n.der));
    }
    private static Nodo derecha(Nodo pivote) {
        Nodo nuevaRaiz = pivote.izq;
        pivote.izq = nuevaRaiz.der;
        nuevaRaiz.der = pivote;
        actualizar(pivote); actualizar(nuevaRaiz);
        return nuevaRaiz;
    }
    private static Nodo izquierda(Nodo pivote) {
        Nodo nuevaRaiz = pivote.der;
        pivote.der = nuevaRaiz.izq;
        nuevaRaiz.izq = pivote;
        actualizar(pivote); actualizar(nuevaRaiz);
        return nuevaRaiz;
    }
}

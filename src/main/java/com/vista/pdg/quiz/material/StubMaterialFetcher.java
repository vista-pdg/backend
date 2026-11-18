package com.vista.pdg.quiz.material;

import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Perfil {@code e2e}: sin red. Sirve un libro, notas y un cuaderno de prueba bajo {@code
 * material.vista.invalid} y rechaza las direcciones privadas igual que la guardia real.
 */
@Component
@Profile("e2e")
public class StubMaterialFetcher implements MaterialFetcher {
  public static final String HOST = "material.vista.invalid";

  private static final Map<String, String[]> PAGES =
      Map.of(
          "/libro/",
          new String[] {
            "text/html",
            "<html><head><title>Libro de prueba de VISTA</title></head><body><nav>Menú</nav>"
                + "<article><h1>Libro de prueba</h1><p>Índice del curso de estructuras.</p>"
                + "<a href='/libro/avl'>Árboles AVL</a> <a href='/libro/hash'>Tablas hash</a>"
                + " <a href='https://otro.invalid/fuera'>Fuera del libro</a></article></body></html>"
          },
          "/libro/avl",
          new String[] {
            "text/html",
            "<html><head><title>Árboles AVL</title></head><body><article><h1>Árboles AVL</h1>"
                + "<p>Un árbol AVL mantiene el factor de balance de cada nodo entre -1 y 1. Tras"
                + " una inserción se recalculan los factores de balance de los ancestros y, si"
                + " alguno llega a 2, se aplica una rotación simple o doble.</p></article></body>"
                + "</html>"
          },
          "/libro/hash",
          new String[] {
            "text/html",
            "<html><head><title>Tablas hash</title></head><body><article><h1>Tablas hash</h1>"
                + "<p>Una tabla hash con encadenamiento guarda en cada cubeta una lista de las"
                + " claves que colisionan. La función h(k) = k mod m reparte las claves.</p>"
                + "</article></body></html>"
          },
          "/notas.md",
          new String[] {
            "text/markdown",
            "# Notas de pilas\n\nUna pila atiende primero al último elemento: la operación pop"
                + " retira el tope y push lo apila.\n"
          },
          "/cuaderno.ipynb",
          new String[] {
            "application/x-ipynb+json",
            "{\"cells\":[{\"cell_type\":\"markdown\",\"source\":[\"# Colas\\n\",\"Una cola"
                + " atiende por el frente: dequeue retira el primero.\"]},{\"cell_type\":\"code\","
                + "\"source\":\"q.dequeue()\"}]}"
          },
          "/vacio",
          new String[] {"text/html", "<html><body><nav>solo menú</nav></body></html>"});

  @Override
  public Fetched fetch(URI uri, long maxBytes) {
    String host = uri.getHost() == null ? "" : uri.getHost();
    if (host.matches("[0-9.]+|\\[?[0-9a-fA-F:]+]?")) {
      try {
        if (PublicAddressGuard.blocked(
            InetAddress.getByName(host.replace("[", "").replace("]", ""))))
          throw new MaterialException(PublicAddressGuard.PRIVATE);
      } catch (java.net.UnknownHostException e) {
        throw new MaterialException("No se encontró el sitio " + host + ".", e);
      }
    }
    if (!HOST.equals(host)) throw new MaterialException("El enlace no respondió a tiempo.");
    String[] page = PAGES.get(uri.getPath());
    if (page == null) {
      throw new MaterialException(host + " respondió 404. Revisa que el enlace sea público.");
    }
    return new Fetched(uri, page[0], page[1].getBytes(StandardCharsets.UTF_8));
  }
}

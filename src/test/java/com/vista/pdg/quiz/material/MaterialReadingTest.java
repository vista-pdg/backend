package com.vista.pdg.quiz.material;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.vista.pdg.service.algorithm.def.AlgorithmDescriptor;
import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** HU-36 · CA-2: lectura de cada formato, guardia de red, recorrido de un sitio y selección. */
class MaterialReadingTest {
  private HttpServer server;
  private String base;
  private final HtmlTextExtractor html = new HtmlTextExtractor();
  private final List<TextExtractor> extractors =
      List.of(
          html,
          new PdfTextExtractor(),
          new DocxTextExtractor(),
          new PlainTextExtractor(),
          new NotebookTextExtractor(new ObjectMapper()));

  @BeforeEach
  void serve() throws Exception {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    Map<String, String[]> pages =
        Map.of(
            "/libro/",
            new String[] {
              "text/html",
              "<html><head><title>Libro</title></head><body><nav>Menú global</nav><article>"
                  + "<h1>Índice</h1><p>Bienvenida.</p><a href='avl'>AVL</a> <a href='hash#x'>Hash"
                  + "</a> <a href='/otro/fuera'>Fuera</a> <a href='estilo.css'>css</a>"
                  + " <a href='https://example.invalid/x'>externo</a></article></body></html>"
            },
            "/libro/avl",
            new String[] {
              "text/html; charset=utf-8",
              "<html><head><title>AVL</title></head><body><article><h2>Rotaciones</h2><p>El"
                  + " factor de balance <span class='katex'><span class='katex-mathml'>"
                  + "<annotation encoding='application/x-tex'>b(n)</annotation></span><span"
                  + " class='katex-html'>b ( n )</span></span> decide la rotación.</p>"
                  + "<a href='/libro/'>volver</a></article></body></html>"
            },
            "/libro/hash",
            new String[] {"text/html", "<article><p>Cubetas y colisiones.</p></article>"},
            "/otro/fuera",
            new String[] {"text/html", "<article><p>No debe leerse.</p></article>"},
            "/notas",
            new String[] {"text/markdown", "# Pilas\n\nLa pila retira el tope."},
            "/salta",
            new String[] {"redirect", "/notas"},
            "/bucle",
            new String[] {"redirect", "/bucle"},
            "/raro",
            new String[] {"application/zip", "PK"});
    server.createContext(
        "/",
        exchange -> {
          String[] page = pages.get(exchange.getRequestURI().getPath());
          if (page == null) {
            exchange.sendResponseHeaders(404, -1);
          } else if (page[0].equals("redirect")) {
            exchange.getResponseHeaders().add("Location", page[1]);
            exchange.sendResponseHeaders(302, -1);
          } else {
            byte[] body = page[1].getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", page[0]);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
          }
          exchange.close();
        });
    server.start();
    base = "http://127.0.0.1:" + server.getAddress().getPort();
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  private MaterialReader reader(boolean allowPrivate) {
    return new MaterialReader(
        new HttpMaterialFetcher(allowPrivate, 5000), extractors, html, Clock.systemUTC(), 20000);
  }

  @Test
  void crawlsOnlyTheSameSectionAndKeepsTexFormulas() {
    ExtractedText site = reader(true).readLink(base + "/libro/", true);
    assertThat(site.format()).isEqualTo(MaterialFormat.SITE);
    assertThat(site.pages()).isEqualTo(3);
    assertThat(site.title()).isEqualTo("Libro");
    assertThat(site.text())
        .contains("Bienvenida.", "Cubetas y colisiones.", "$b(n)$", "## Rotaciones")
        .doesNotContain("No debe leerse", "Menú global", "b ( n )");
    ExtractedText single = reader(true).readLink(base + "/libro/", false);
    assertThat(single.format()).isEqualTo(MaterialFormat.HTML);
    assertThat(single.text()).doesNotContain("Cubetas");
  }

  @Test
  void followsRedirectsWithinLimitsAndReportsHttpErrors() {
    assertThat(reader(true).readLink(base + "/salta", false).text()).contains("retira el tope");
    assertThatThrownBy(() -> reader(true).readLink(base + "/bucle", false))
        .hasMessageContaining("redirige demasiadas veces");
    assertThatThrownBy(() -> reader(true).readLink(base + "/falta", false))
        .hasMessageContaining("404");
    assertThatThrownBy(() -> reader(true).readLink(base + "/raro", false))
        .hasMessageContaining("no es una página web");
  }

  @Test
  void guardRejectsLocalAndPrivateAddressesByDefault() throws Exception {
    assertThatThrownBy(() -> reader(false).readLink(base + "/notas", false))
        .isInstanceOf(MaterialException.class);
    assertThatThrownBy(() -> reader(false).readLink("http://127.0.0.1/notas", false))
        .hasMessage(PublicAddressGuard.PRIVATE);
    for (String address :
        List.of(
            "127.0.0.1",
            "10.1.2.3",
            "172.16.0.1",
            "192.168.0.10",
            "169.254.169.254",
            "100.64.0.1",
            "0.0.0.0",
            "::1",
            "fc00::1",
            "fe80::1",
            "::ffff:127.0.0.1",
            "224.0.0.1")) {
      assertThat(PublicAddressGuard.blocked(InetAddress.getByName(address))).as(address).isTrue();
    }
    assertThat(PublicAddressGuard.blocked(InetAddress.getByName("8.8.8.8"))).isFalse();
    assertThat(PublicAddressGuard.blocked(InetAddress.getByName("2001:4860:4860::8888"))).isFalse();
    assertThatThrownBy(() -> PublicAddressGuard.check(URI.create("http://8.8.8.8:8080/"), false))
        .hasMessageContaining("puertos");
    assertThatThrownBy(() -> PublicAddressGuard.check(URI.create("http://u:p@8.8.8.8/"), false))
        .isInstanceOf(MaterialException.class);
  }

  @Test
  void normalizesGithubBlobsAndRejectsOtherSchemes() {
    assertThat(
            MaterialReader.normalize(
                "https://github.com/CyEDBooks/CyED1/blob/main/notas/heaps.ipynb"))
        .hasToString("https://raw.githubusercontent.com/CyEDBooks/CyED1/main/notas/heaps.ipynb");
    assertThat(MaterialReader.normalize(" https://cyedbooks.github.io/CyED1/ "))
        .hasToString("https://cyedbooks.github.io/CyED1/");
    for (String bad : List.of("ftp://x/y", "javascript:alert(1)", "file:///etc/passwd", "", "x")) {
      assertThatThrownBy(() -> MaterialReader.normalize(bad)).isInstanceOf(MaterialException.class);
    }
  }

  @Test
  void readsEachFileFormat() throws Exception {
    MaterialReader reader = reader(false);
    ExtractedText notebook =
        reader.readFile(
            "colas.ipynb",
            ("{\"cells\":[{\"cell_type\":\"markdown\",\"source\":[\"# Colas\\n\",\"FIFO\"]},"
                    + "{\"cell_type\":\"code\",\"source\":\"q.dequeue()\"},"
                    + "{\"cell_type\":\"code\",\"source\":[]}]}")
                .getBytes(StandardCharsets.UTF_8));
    assertThat(notebook.title()).isEqualTo("Colas");
    assertThat(notebook.text()).contains("FIFO", "```\nq.dequeue()\n```");

    ExtractedText markdown =
        reader.readFile("n.md", "﻿# Título\r\n\r\n\r\n\r\nTexto  con   espacios".getBytes());
    assertThat(markdown.text()).isEqualTo("# Título\n\nTexto con espacios");
    assertThat(reader.readFile("n.txt", "hola".getBytes()).format()).isEqualTo(MaterialFormat.TEXT);
    assertThatThrownBy(() -> reader.readFile("n.md", new byte[] {0, 1, 2}))
        .hasMessageContaining("no es un archivo de texto");

    ByteArrayOutputStream docx = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(docx)) {
      zip.putNextEntry(new ZipEntry("word/document.xml"));
      zip.write(
          ("<!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><w:document"
                  + " xmlns:w=\"w\"><w:body><w:p><w:r><w:t>Primer</w:t></w:r><w:r><w:tab/>"
                  + "<w:t>párrafo</w:t></w:r></w:p><w:p><w:r><w:t>Segundo</w:t></w:r></w:p>"
                  + "</w:body></w:document>")
              .getBytes(StandardCharsets.UTF_8));
    }
    ExtractedText word = reader.readFile("apuntes.docx", docx.toByteArray());
    assertThat(word.text()).isEqualTo("Primer párrafo\nSegundo").doesNotContain("root");
    assertThatThrownBy(() -> reader.readFile("roto.docx", "no zip".getBytes()))
        .hasMessageContaining(".docx");

    ByteArrayOutputStream pdf = new ByteArrayOutputStream();
    try (PDDocument document = new PDDocument()) {
      PDPage page = new PDPage();
      document.addPage(page);
      try (PDPageContentStream content = new PDPageContentStream(document, page)) {
        content.beginText();
        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        content.newLineAtOffset(72, 700);
        content.showText("Arbol AVL y rotaciones");
        content.endText();
      }
      document.getDocumentInformation().setTitle("Taller AVL");
      document.save(pdf);
    }
    ExtractedText text = reader.readFile("taller.pdf", pdf.toByteArray());
    assertThat(text.title()).isEqualTo("Taller AVL");
    assertThat(text.pages()).isEqualTo(1);
    assertThat(text.text()).contains("Arbol AVL y rotaciones");

    ByteArrayOutputStream blank = new ByteArrayOutputStream();
    try (PDDocument document = new PDDocument()) {
      document.addPage(new PDPage());
      document.save(blank);
    }
    assertThatThrownBy(() -> reader.readFile("escaneo.pdf", blank.toByteArray()))
        .hasMessageContaining("escaneada");
    assertThatThrownBy(() -> reader.readFile("x.pptx", new byte[] {1}))
        .hasMessageContaining("formato no admitido");
    assertThatThrownBy(() -> reader.readFile("x.html", "<p>x</p>".getBytes()))
        .hasMessageContaining("formato no admitido");
  }

  @Test
  void excerptsRequireTheTopicStructureAndRespectTheBudget() {
    AlgorithmDescriptor avl =
        new AlgorithmDescriptor(
            "tree", "avl", "insert", "tree", "AVL · Inserción", "Inserta y rebalancea", "values");
    AlgorithmDescriptor bfs =
        new AlgorithmDescriptor(
            "graph",
            "simple",
            "bfs",
            "graph",
            "BFS · Recorrido en anchura",
            "Por niveles",
            "start");
    String content =
        "Las matrices dispersas no son tema.\n\n"
            + "x".repeat(1300)
            + "\n\nEn un árbol AVL la rotación corrige el factor de balance tras insertar.\n\n"
            + "El grafo se recorre por niveles desde un vértice con una cola.";
    var source = new MaterialExcerpts.Source("libro", content);
    var excerpts = new MaterialExcerpts().select(List.of(source), avl, 6000);
    assertThat(excerpts).hasSize(1);
    assertThat(excerpts.getFirst().text()).contains("rotación").doesNotContain("matrices");
    assertThat(excerpts.getFirst().source()).isEqualTo("libro");
    var graph = new MaterialExcerpts().select(List.of(source), bfs, 6000);
    assertThat(graph).singleElement().satisfies(e -> assertThat(e.text()).contains("niveles"));
    assertThat(new MaterialExcerpts().select(List.of(source), avl, 10)).isEmpty();
    assertThat(MaterialExcerpts.chunks("a".repeat(2500))).hasSize(3);
  }
}

package com.vista.pdg.quiz.material;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeVisitor;
import org.springframework.stereotype.Component;

/**
 * Páginas web: el contenido principal ({@code article}, {@code main} o el cuerpo), sin navegación,
 * scripts ni estilos. Las fórmulas KaTeX se conservan en su fuente TeX en vez de duplicarse.
 */
@Component
class HtmlTextExtractor implements TextExtractor {
  private static final Pattern BLOCK =
      Pattern.compile("p|div|section|article|li|ul|ol|pre|table|tr|blockquote|dd|dt|figure|br");
  private static final Pattern HEADING = Pattern.compile("h[1-6]");

  @Override
  public boolean supports(MaterialFormat format) {
    return format == MaterialFormat.HTML;
  }

  @Override
  public ExtractedText extract(byte[] bytes, String name) {
    Document document = Jsoup.parse(TextCleanup.utf8(bytes));
    String text = TextCleanup.tidy(text(content(document)));
    if (text.isBlank()) throw new MaterialException(name + " no tiene texto legible.");
    String title = document.title().isBlank() ? name : document.title().strip();
    return new ExtractedText(title, text, 1, MaterialFormat.HTML);
  }

  /** Enlaces absolutos de la página, para recorrer un sitio. */
  Set<URI> links(byte[] bytes, URI base) {
    Document document = Jsoup.parse(TextCleanup.utf8(bytes), base.toString());
    Set<URI> links = new LinkedHashSet<>();
    for (Element anchor : document.select("a[href]")) {
      String href = anchor.absUrl("href");
      if (href.isBlank()) continue;
      try {
        URI uri = URI.create(href.replace(" ", "%20"));
        links.add(new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), null, null));
      } catch (Exception e) {
        // Enlace mal formado: se omite.
      }
    }
    return links;
  }

  private static Element content(Document document) {
    document
        .select(
            "script, style, noscript, template, svg, nav, header, footer, aside, form, button,"
                + " .katex-html, [role=navigation]")
        .remove();
    for (Element katex : document.select(".katex")) {
      Element tex = katex.selectFirst("annotation[encoding=application/x-tex]");
      katex.replaceWith(new TextNode(tex == null ? katex.text() : " $" + tex.text() + "$ "));
    }
    Element main = document.selectFirst("article");
    if (main == null) main = document.selectFirst("main, [role=main]");
    return main == null ? document.body() : main;
  }

  private static String text(Element root) {
    StringBuilder out = new StringBuilder();
    root.traverse(
        new NodeVisitor() {
          @Override
          public void head(Node node, int depth) {
            if (node instanceof TextNode text) {
              out.append(text.getWholeText().replaceAll("\\s+", " "));
            } else if (node instanceof Element element) {
              String tag = element.normalName();
              if (HEADING.matcher(tag).matches()) out.append("\n\n## ");
              else if (BLOCK.matcher(tag).matches()) out.append('\n');
            }
          }

          @Override
          public void tail(Node node, int depth) {
            if (node instanceof Element element) {
              String tag = element.normalName();
              if (HEADING.matcher(tag).matches() || BLOCK.matcher(tag).matches()) out.append('\n');
            }
          }
        });
    return out.toString();
  }
}

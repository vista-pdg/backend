package com.vista.pdg.quiz.material;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Convierte un enlace o un archivo en texto (HU-36 · CA-2). Elige el lector por formato y, con
 * «Recorrer el sitio», sigue los enlaces de la misma sección con un tope de páginas y de tiempo que
 * cabe en el presupuesto de 55 s del proxy.
 */
@Service
public class MaterialReader {
  public static final long MAX_BYTES = 10L * 1024 * 1024;
  public static final int MAX_CHARS = 400_000;
  static final int MAX_PAGES = 30;
  private static final Pattern GITHUB_BLOB =
      Pattern.compile("^/([^/]+)/([^/]+)/blob/([^/]+)/(.+)$");
  private static final Pattern ASSET =
      Pattern.compile(
          ".*\\.(css|js|mjs|png|jpe?g|gif|svg|ico|webp|woff2?|ttf|zip|gz|mp4|mp3|json|xml)$");

  private final MaterialFetcher fetcher;
  private final List<TextExtractor> extractors;
  private final HtmlTextExtractor html;
  private final Clock clock;
  private final Duration crawlBudget;

  public MaterialReader(
      MaterialFetcher fetcher,
      List<TextExtractor> extractors,
      HtmlTextExtractor html,
      Clock clock,
      @Value("${quiz.material.crawl-budget-ms:40000}") long crawlBudgetMs) {
    this.fetcher = fetcher;
    this.extractors = extractors;
    this.html = html;
    this.clock = clock;
    this.crawlBudget = Duration.ofMillis(crawlBudgetMs);
  }

  /** Valida el esquema y convierte los enlaces {@code blob} de GitHub en su archivo crudo. */
  public static URI normalize(String raw) {
    String text = raw == null ? "" : raw.strip();
    if (!text.toLowerCase(Locale.ROOT).matches("^https?://.+")) {
      throw new MaterialException("Usa un enlace que empiece por http:// o https://.");
    }
    URI uri;
    try {
      uri = URI.create(text.replace(" ", "%20"));
    } catch (IllegalArgumentException e) {
      throw new MaterialException("El enlace no es válido.", e);
    }
    if (uri.getHost() == null) throw new MaterialException("El enlace no tiene un sitio válido.");
    if ("github.com".equalsIgnoreCase(uri.getHost()) && uri.getRawPath() != null) {
      Matcher blob = GITHUB_BLOB.matcher(uri.getRawPath());
      if (blob.matches()) {
        return URI.create(
            "https://raw.githubusercontent.com/"
                + blob.group(1)
                + "/"
                + blob.group(2)
                + "/"
                + blob.group(3)
                + "/"
                + blob.group(4));
      }
    }
    return uri;
  }

  public ExtractedText readLink(String raw, boolean crawl) {
    URI start = normalize(raw);
    MaterialFetcher.Fetched first = fetcher.fetch(start, MAX_BYTES);
    String name = nameOf(first.uri());
    MaterialFormat format = detect(name, first.contentType(), first.body());
    if (format != MaterialFormat.HTML) return extractor(format).extract(first.body(), name);
    ExtractedText page = html.extract(first.body(), name);
    return crawl ? crawl(first, page) : page;
  }

  public ExtractedText readFile(String filename, byte[] bytes) {
    String name = filename == null || filename.isBlank() ? "archivo" : filename.strip();
    MaterialFormat format =
        MaterialFormat.byName(name)
            .filter(f -> f != MaterialFormat.HTML)
            .orElseThrow(
                () ->
                    new MaterialException(
                        name + ": formato no admitido. Usa PDF, Markdown, TXT, DOCX o .ipynb."));
    if (bytes.length > MAX_BYTES) {
      throw new MaterialException(name + " supera el máximo de 10 MB.");
    }
    return extractor(format).extract(bytes, name);
  }

  private ExtractedText crawl(MaterialFetcher.Fetched first, ExtractedText firstPage) {
    URI origin = first.uri();
    String path = origin.getPath() == null || origin.getPath().isEmpty() ? "/" : origin.getPath();
    String prefix = path.substring(0, path.lastIndexOf('/') + 1);
    Instant deadline = clock.instant().plus(crawlBudget);
    StringBuilder text = new StringBuilder(section(firstPage));
    Set<String> seen = new HashSet<>(Set.of(key(origin)));
    Deque<URI> queue = new ArrayDeque<>();
    enqueue(queue, seen, html.links(first.body(), origin), origin, prefix);
    int pages = 1;
    while (!queue.isEmpty()
        && pages < MAX_PAGES
        && text.length() < MAX_CHARS
        && clock.instant().isBefore(deadline)) {
      URI next = queue.poll();
      try {
        MaterialFetcher.Fetched fetched = fetcher.fetch(next, MAX_BYTES);
        if (detect(nameOf(next), fetched.contentType(), fetched.body()) != MaterialFormat.HTML)
          continue;
        text.append("\n\n").append(section(html.extract(fetched.body(), nameOf(next))));
        enqueue(queue, seen, html.links(fetched.body(), fetched.uri()), origin, prefix);
        pages++;
      } catch (MaterialException e) {
        // Una página caída no invalida el resto del sitio.
      }
    }
    return new ExtractedText(firstPage.title(), text.toString(), pages, MaterialFormat.SITE);
  }

  private static void enqueue(
      Deque<URI> queue, Set<String> seen, Set<URI> links, URI origin, String prefix) {
    for (URI link : links) {
      if (link.getHost() == null
          || !link.getHost().equalsIgnoreCase(origin.getHost())
          || link.getPort() != origin.getPort()
          || !String.valueOf(link.getScheme()).equalsIgnoreCase(origin.getScheme())) continue;
      String path = link.getPath() == null ? "" : link.getPath();
      if (!path.startsWith(prefix) || ASSET.matcher(path.toLowerCase(Locale.ROOT)).matches())
        continue;
      if (seen.add(key(link))) queue.add(link);
    }
  }

  private static String key(URI uri) {
    String path = uri.getPath() == null || uri.getPath().isEmpty() ? "/" : uri.getPath();
    return uri.getHost().toLowerCase(Locale.ROOT) + path.replaceAll("/+$", "");
  }

  private static String section(ExtractedText page) {
    return "# " + page.title() + "\n\n" + page.text();
  }

  private TextExtractor extractor(MaterialFormat format) {
    return extractors.stream()
        .filter(e -> e.supports(format))
        .findFirst()
        .orElseThrow(() -> new MaterialException("Formato no admitido."));
  }

  static MaterialFormat detect(String name, String contentType, byte[] body) {
    Optional<MaterialFormat> byName = MaterialFormat.byName(name);
    if (byName.isPresent()) return byName.get();
    return MaterialFormat.byContentType(contentType)
        .or(() -> MaterialFormat.bySignature(body))
        .orElseThrow(
            () ->
                new MaterialException(
                    "El enlace no es una página web, un PDF, un Markdown, un DOCX ni un"
                        + " cuaderno."));
  }

  static String nameOf(URI uri) {
    String path = uri.getPath() == null ? "" : uri.getPath();
    String last = path.replaceAll("/+$", "");
    last = last.substring(last.lastIndexOf('/') + 1);
    return last.isBlank() ? uri.getHost() : last;
  }
}

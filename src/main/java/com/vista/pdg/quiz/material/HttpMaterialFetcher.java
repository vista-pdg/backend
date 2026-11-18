package com.vista.pdg.quiz.material;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Descarga por HTTP con redirecciones manuales: cada salto pasa por {@link PublicAddressGuard}. El
 * cuerpo se lee con tope, sin confiar en {@code Content-Length}.
 */
@Component
@Profile("!e2e")
public class HttpMaterialFetcher implements MaterialFetcher {
  static final int MAX_REDIRECTS = 3;
  private static final String ACCEPT =
      "text/html,application/xhtml+xml,application/pdf,text/markdown,text/plain,"
          + "application/x-ipynb+json,application/json;q=0.9,"
          + "application/vnd.openxmlformats-officedocument.wordprocessingml.document;q=0.9,"
          + "*/*;q=0.5";

  private final HttpClient client;
  private final boolean allowPrivate;
  private final Duration timeout;

  public HttpMaterialFetcher(
      @Value("${quiz.material.allow-private-hosts:false}") boolean allowPrivate,
      @Value("${quiz.material.request-timeout-ms:15000}") long timeoutMs) {
    this.allowPrivate = allowPrivate;
    this.timeout = Duration.ofMillis(timeoutMs);
    this.client =
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
  }

  @Override
  public Fetched fetch(URI uri, long maxBytes) {
    URI current = uri;
    for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
      PublicAddressGuard.check(current, allowPrivate);
      HttpResponse<InputStream> response = send(current);
      int status = response.statusCode();
      if (status >= 300 && status < 400) {
        String location = response.headers().firstValue("Location").orElse(null);
        close(response);
        if (location == null) break;
        current = current.resolve(location.replace(" ", "%20"));
        continue;
      }
      if (status == 404 || status == 410) {
        close(response);
        throw new MaterialException(
            current.getHost() + " respondió " + status + ". Revisa que el enlace sea público.");
      }
      if (status == 401 || status == 403) {
        close(response);
        throw new MaterialException(
            current.getHost() + " pidió iniciar sesión. Usa un enlace público.");
      }
      if (status < 200 || status >= 300) {
        close(response);
        throw new MaterialException(current.getHost() + " respondió " + status + ".");
      }
      byte[] body = read(response, maxBytes);
      return new Fetched(current, response.headers().firstValue("Content-Type").orElse(null), body);
    }
    throw new MaterialException("El enlace redirige demasiadas veces.");
  }

  private HttpResponse<InputStream> send(URI uri) {
    HttpRequest request =
        HttpRequest.newBuilder(uri)
            .timeout(timeout)
            .header("User-Agent", "VISTA-material/1.0")
            .header("Accept", ACCEPT)
            .GET()
            .build();
    try {
      return client.send(request, HttpResponse.BodyHandlers.ofInputStream());
    } catch (HttpTimeoutException e) {
      throw new MaterialException("El enlace no respondió a tiempo.", e);
    } catch (IOException e) {
      throw new MaterialException("No se pudo abrir el enlace.", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new MaterialException("Se canceló la lectura del enlace.", e);
    }
  }

  private static byte[] read(HttpResponse<InputStream> response, long maxBytes) {
    try (InputStream in = response.body()) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] buffer = new byte[16384];
      long total = 0;
      int read;
      while ((read = in.read(buffer)) != -1) {
        total += read;
        if (total > maxBytes) {
          throw new MaterialException("El contenido supera " + (maxBytes / (1024 * 1024)) + " MB.");
        }
        out.write(buffer, 0, read);
      }
      return out.toByteArray();
    } catch (IOException e) {
      throw new MaterialException("Se cortó la descarga del enlace.", e);
    }
  }

  private static void close(HttpResponse<InputStream> response) {
    try {
      response.body().close();
    } catch (IOException ignored) {
      // Nada que liberar.
    }
  }
}

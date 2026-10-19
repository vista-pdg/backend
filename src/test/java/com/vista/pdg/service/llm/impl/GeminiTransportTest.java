package com.vista.pdg.service.llm.impl;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.sun.net.httpserver.HttpServer;
import com.vista.pdg.config.GeminiTransportProperties;
import com.vista.pdg.exception.LlmUnavailableException;
import com.vista.pdg.service.PipelineFixtures;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Exercises the real SDK against a local HTTP server, without provider calls or credentials. */
class GeminiTransportTest {
  private HttpServer server;
  private final ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor();
  private Client client;
  private final AtomicInteger calls = new AtomicInteger();
  private static final String CONTRACT =
      "{\"type\":\"tree\",\"subtype\":\"bst\",\"operations\":[{\"op\":\"insert\",\"values\":[2,1,3]}]}";

  interface Handler {
    void run() throws InterruptedException;
  }

  private GeminiLlmAdapter adapter(int status, Handler handler, GeminiTransportProperties limits)
      throws Exception {
    byte[] body =
        new ObjectMapper()
            .writeValueAsBytes(
                status == 200
                    ? Map.of(
                        "candidates",
                        List.of(
                            Map.of("content", Map.of("parts", List.of(Map.of("text", CONTRACT))))))
                    : Map.of(
                        "error",
                        Map.of("code", status, "message", "unavailable", "status", "UNAVAILABLE")));
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.setExecutor(threads);
    server.createContext(
        "/",
        exchange -> {
          calls.incrementAndGet();
          try {
            exchange.getRequestBody().readAllBytes();
            handler.run();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          } finally {
            exchange.close();
          }
        });
    server.start();
    client =
        Client.builder()
            .apiKey("local-test-placeholder")
            .httpOptions(
                GeminiLlmAdapter.httpOptions(limits.requestTimeoutMs()).toBuilder()
                    .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                    .build())
            .clientOptions(GeminiLlmAdapter.clientOptions(limits))
            .build();
    return new GeminiLlmAdapter("local-model", client, PipelineFixtures.contractBuilder(), limits) {
      @Override
      protected void sleepBackoff(int attempt) {}
    };
  }

  @AfterEach
  void close() {
    if (server != null) server.stop(0);
    if (client != null) client.close();
    threads.shutdownNow();
  }

  @Test
  void synchronousCallsOverlapBeyondTwoButRespectLimit() throws Exception {
    AtomicInteger active = new AtomicInteger();
    AtomicInteger peak = new AtomicInteger();
    CountDownLatch firstThree = new CountDownLatch(3);
    var adapter =
        adapter(
            200,
            () -> {
              int current = active.incrementAndGet();
              peak.accumulateAndGet(current, Math::max);
              try {
                firstThree.countDown();
                if (!firstThree.await(4, TimeUnit.SECONDS))
                  throw new AssertionError("calls serialized");
                Thread.sleep(50);
              } finally {
                active.decrementAndGet();
              }
            },
            new GeminiTransportProperties(3, 5000, 5000, 10000));
    var futures = new ArrayList<Future<?>>();
    for (int i = 0; i < 8; i++) futures.add(threads.submit(() -> adapter.generate("árbol")));
    for (var result : futures) assertThat(result.get(8, TimeUnit.SECONDS)).isNotNull();
    assertThat(calls).hasValue(8);
    assertThat(peak).hasValue(3);
  }

  @Test
  void sdkDoesNotMultiplyApplicationRetryBudget() throws Exception {
    var adapter = adapter(503, () -> {}, new GeminiTransportProperties(3, 2000, 500, 10000));
    assertThatThrownBy(() -> adapter.generate("árbol")).isInstanceOf(LlmUnavailableException.class);
    assertThat(calls).hasValue(3);
  }

  @Test
  void busyQueueFailsPromptlyAndReleasesPermit() throws Exception {
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    var adapter =
        adapter(
            200,
            () -> {
              entered.countDown();
              release.await(3, TimeUnit.SECONDS);
            },
            new GeminiTransportProperties(1, 5000, 100, 10000));
    var first = threads.submit(() -> adapter.generate("árbol"));
    assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
    long start = System.nanoTime();
    assertThatThrownBy(() -> adapter.generate("otro árbol"))
        .isInstanceOf(LlmUnavailableException.class)
        .satisfies(e -> assertThat(((LlmUnavailableException) e).isRetryable()).isFalse());
    assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(1));
    release.countDown();
    assertThat(first.get(3, TimeUnit.SECONDS)).isNotNull();
    assertThat(adapter.generate("árbol final")).isNotNull();
    assertThat(calls).hasValue(2);
  }

  @Test
  void totalBudgetBoundsTimeoutAcrossRetries() throws Exception {
    var adapter =
        adapter(200, () -> Thread.sleep(2000), new GeminiTransportProperties(2, 5000, 100, 300));
    long start = System.nanoTime();
    assertThatThrownBy(() -> adapter.generate("árbol")).isInstanceOf(LlmUnavailableException.class);
    assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
    assertThat(calls.get()).isBetween(1, 3);
  }
}

package com.example.pricesync.api;

import com.example.pricesync.config.ConfigManager;
import com.example.pricesync.util.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Sends the built JSON payload to the backend's POST /api/prices.
 * Handles retry with exponential backoff + a bounded failure queue,
 * per the spec's ERROR HANDLING section.
 *
 * Uses the JDK's built-in java.net.http.HttpClient (available since Java 11;
 * this mod requires Java 25) instead of a third-party HTTP library like
 * OkHttp. Third-party libraries added via Gradle's `implementation` compile
 * fine but are NOT bundled into the final mod jar — Fabric Loader then
 * fails at runtime with NoClassDefFoundError since the class simply isn't
 * in the jar. Bundling them properly requires Loom's `include` mechanism,
 * which doesn't handle transitive dependencies (OkHttp itself pulls in
 * Okio + Kotlin stdlib) without extra tooling like the Shadow plugin. Using
 * the JDK's own HTTP client sidesteps all of that — zero extra dependencies,
 * zero bundling concerns.
 *
 * TODO: swap the in-memory failedQueue for a persisted one (file/db) so
 * queued sends survive a game restart.
 */
public class ApiClient {

    private static final int MAX_RETRIES = 3;
    private static final long BASE_BACKOFF_MS = 1000; // 1s, 2s, 4s (attempt 0/1/2)
    private static final int MAX_QUEUE_SIZE = 200; // ~200 changed-price batches worth of backlog

    private final ConfigManager configManager;
    private final HttpClient http;
    private final Deque<String> failedQueue = new ArrayDeque<>();
    private final ScheduledExecutorService retryExecutor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "price-sync-retry");
                t.setDaemon(true);
                return t;
            });

    public ApiClient(ConfigManager configManager) {
        this.configManager = configManager;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                // Force HTTP/1.1. By default java.net.http.HttpClient prefers
                // HTTP/2 and, for plaintext (http://) URLs, sends an
                // `Upgrade: h2c` header on the first request hoping the
                // server upgrades the connection. Node's built-in `http`
                // module only ever speaks HTTP/1.1 and doesn't understand
                // that upgrade — normally harmless (most servers just ignore
                // the Upgrade header), but on some Windows setups something
                // in the network path (commonly antivirus/security software
                // doing HTTP inspection) mishandles it and returns a bogus
                // 405 instead of passing the request through. Confirmed via
                // real debugging (Aug 2026): curl/PowerShell to the exact
                // same endpoint worked fine while the mod's Java client got
                // 405 on every single attempt — the h2c upgrade attempt was
                // the one concrete difference between them.
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    /** Fire-and-forget async send with retry. Also flushes any previously failed payloads first. */
    public void sendPricesAsync(String jsonPayload) {
        flushQueue();
        send(jsonPayload, 0);
    }

    private void send(String jsonPayload, int attempt) {
        String apiUrl = configManager.get().apiUrl;
        String apiKey = configManager.get().apiKey;

        if (apiUrl == null || apiUrl.isBlank()) {
            Logger.warn("apiUrl not configured, skipping send.");
            return;
        }

        String url = apiUrl.endsWith("/api/prices") ? apiUrl : apiUrl + "/api/prices";

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();
        } catch (IllegalArgumentException e) {
            Logger.error("Invalid apiUrl \"" + url + "\", skipping send.", e);
            return;
        }

        http.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .whenComplete((response, error) -> {
                    if (error != null) {
                        Logger.error("Price sync request failed (attempt " + attempt + ")", error);
                        retryOrQueue(jsonPayload, attempt);
                    } else if (response.statusCode() / 100 != 2) {
                        Logger.warn("Price sync got HTTP " + response.statusCode());
                        retryOrQueue(jsonPayload, attempt);
                    } else {
                        Logger.debug("Price sync sent successfully.");
                    }
                });
    }

    /**
     * Retries with exponential backoff (1s, 2s, 4s) instead of hammering the
     * backend immediately — important if the backend is down or rate-limiting,
     * not just a one-off blip. After MAX_RETRIES, the payload goes into the
     * (size-capped) failed queue to retry on the next successful send.
     */
    private void retryOrQueue(String jsonPayload, int attempt) {
        if (attempt < MAX_RETRIES) {
            long delayMs = BASE_BACKOFF_MS * (1L << attempt); // 1s, 2s, 4s
            Logger.debug("Retrying in " + delayMs + "ms (attempt " + (attempt + 1) + "/" + MAX_RETRIES + ")");
            retryExecutor.schedule(() -> send(jsonPayload, attempt + 1), delayMs, TimeUnit.MILLISECONDS);
        } else {
            Logger.warn("Max retries reached, queuing payload for later.");
            enqueueFailed(jsonPayload);
        }
    }

    private synchronized void enqueueFailed(String jsonPayload) {
        if (failedQueue.size() >= MAX_QUEUE_SIZE) {
            String dropped = failedQueue.pollFirst(); // drop oldest, keep freshest data
            Logger.warn("Failed-payload queue full (" + MAX_QUEUE_SIZE + "), dropping oldest entry: "
                    + (dropped != null ? dropped.substring(0, Math.min(80, dropped.length())) + "..." : "?"));
        }
        failedQueue.addLast(jsonPayload);
    }

    private synchronized void flushQueue() {
        if (failedQueue.isEmpty()) return;
        Logger.info("Flushing " + failedQueue.size() + " queued payload(s) from earlier failures.");
        String queued;
        while ((queued = failedQueue.pollFirst()) != null) {
            send(queued, 0);
        }
    }

    /** Number of payloads currently waiting to be retried. Exposed for the /pricesync status command. */
    public synchronized int getQueuedCount() {
        return failedQueue.size();
    }
}

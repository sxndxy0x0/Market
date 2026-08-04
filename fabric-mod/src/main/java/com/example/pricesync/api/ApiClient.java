package com.example.pricesync.api;

import com.example.pricesync.config.ConfigManager;
import com.example.pricesync.util.Logger;
import okhttp3.*;

import java.io.IOException;
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
 * TODO: swap the in-memory failedQueue for a persisted one (file/db) so
 * queued sends survive a game restart.
 */
public class ApiClient {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final int MAX_RETRIES = 3;
    private static final long BASE_BACKOFF_MS = 1000; // 1s, 2s, 4s (attempt 0/1/2)
    private static final int MAX_QUEUE_SIZE = 200; // ~200 changed-price batches worth of backlog

    private final ConfigManager configManager;
    private final OkHttpClient http;
    private final Deque<String> failedQueue = new ArrayDeque<>();
    private final ScheduledExecutorService retryExecutor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "price-sync-retry");
                t.setDaemon(true);
                return t;
            });

    public ApiClient(ConfigManager configManager) {
        this.configManager = configManager;
        this.http = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
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

        Request request = new Request.Builder()
                .url(apiUrl.endsWith("/api/prices") ? apiUrl : apiUrl + "/api/prices")
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(jsonPayload, JSON))
                .build();

        http.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Logger.error("Price sync request failed (attempt " + attempt + ")", e);
                retryOrQueue(jsonPayload, attempt);
            }

            @Override
            public void onResponse(Call call, Response response) {
                if (!response.isSuccessful()) {
                    Logger.warn("Price sync got HTTP " + response.code());
                    response.close();
                    retryOrQueue(jsonPayload, attempt);
                } else {
                    Logger.debug("Price sync sent successfully.");
                    response.close();
                }
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

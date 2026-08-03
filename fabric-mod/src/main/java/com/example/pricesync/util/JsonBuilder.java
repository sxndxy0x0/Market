package com.example.pricesync.util;

import com.example.pricesync.config.ConfigManager;
import com.google.gson.Gson;

import java.util.List;

/** Builds the JSON payload sent to POST /api/prices. */
public class JsonBuilder {

    private final ConfigManager configManager;
    private final Gson gson = new Gson();

    public JsonBuilder(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public String build(List<PriceEntry> entries) {
        Payload payload = new Payload();
        payload.server = configManager.get().serverName;
        payload.timestamp = System.currentTimeMillis() / 1000;
        payload.prices = entries;
        return gson.toJson(payload);
    }

    /** Matches the "JSON FORMAT" section of the project spec. */
    private static class Payload {
        String server;
        long timestamp;
        List<PriceEntry> prices;
    }
}

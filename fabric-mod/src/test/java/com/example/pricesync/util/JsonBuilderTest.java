package com.example.pricesync.util;

import com.example.pricesync.config.ConfigManager;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the JSON payload JsonBuilder produces matches what the backend's
 * pricesController expects (server, timestamp, prices[{id,name,buy,sell,stackPrice}]).
 * Uses a plain `new ConfigManager()` without calling load() — no filesystem
 * access needed, config just keeps its in-memory defaults.
 */
class JsonBuilderTest {

    @Test
    void producesExpectedTopLevelShape() {
        ConfigManager config = new ConfigManager();
        config.get().serverName = "SIAM SMP+";
        JsonBuilder builder = new JsonBuilder(config);

        PriceEntry entry = new PriceEntry();
        entry.id = "spawner";
        entry.name = "spawner";
        entry.buy = -1;
        entry.sell = 1069.02;
        entry.stackPrice = 68417.28;

        String json = builder.build(List.of(entry));
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();

        assertEquals("SIAM SMP+", root.get("server").getAsString());
        assertTrue(root.has("timestamp"));
        assertTrue(root.get("timestamp").getAsLong() > 0);

        JsonObject firstPrice = root.getAsJsonArray("prices").get(0).getAsJsonObject();
        assertEquals("spawner", firstPrice.get("id").getAsString());
        assertEquals(-1, firstPrice.get("buy").getAsDouble());
        assertEquals(1069.02, firstPrice.get("sell").getAsDouble(), 0.0001);
        assertEquals(68417.28, firstPrice.get("stackPrice").getAsDouble(), 0.0001);
    }

    @Test
    void emptyEntryListStillProducesValidJson() {
        JsonBuilder builder = new JsonBuilder(new ConfigManager());
        String json = builder.build(List.of());

        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        assertEquals(0, root.getAsJsonArray("prices").size());
    }
}

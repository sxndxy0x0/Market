package com.example.pricesync.cache;

import com.example.pricesync.util.Logger;
import com.example.pricesync.util.PriceEntry;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps the last-known price per item id, persisted to disk so it
 * survives restarts. Used to decide whether a sync should be sent.
 *
 * TODO: consider capping cache file size / pruning items no longer seen.
 */
public class CacheManager {

    private static final Path DEFAULT_CACHE_PATH = Path.of("config", "price-sync", "cache.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<HashMap<String, PriceEntry>>() {}.getType();

    private final Path cachePath;
    private Map<String, PriceEntry> cache = new HashMap<>();

    public CacheManager() {
        this(DEFAULT_CACHE_PATH);
    }

    /** Package-visible/testing constructor — lets tests point at a temp file instead of `config/`. */
    CacheManager(Path cachePath) {
        this.cachePath = cachePath;
        load();
    }

    private void load() {
        try {
            if (Files.exists(cachePath)) {
                try (var reader = Files.newBufferedReader(cachePath)) {
                    Map<String, PriceEntry> loaded = GSON.fromJson(reader, MAP_TYPE);
                    if (loaded != null) cache = loaded;
                }
            }
        } catch (IOException e) {
            Logger.error("Failed to load price cache", e);
        }
    }

    private void persist() {
        try {
            Files.createDirectories(cachePath.getParent());
            try (var writer = Files.newBufferedWriter(cachePath)) {
                GSON.toJson(cache, MAP_TYPE, writer);
            }
        } catch (IOException e) {
            Logger.error("Failed to persist price cache", e);
        }
    }

    /**
     * Compares freshly-parsed entries to the cache.
     * @return only the entries that are new or changed.
     */
    public List<PriceEntry> diff(List<PriceEntry> freshEntries) {
        List<PriceEntry> changed = new ArrayList<>();
        for (PriceEntry entry : freshEntries) {
            PriceEntry cached = cache.get(entry.id);
            if (cached == null || !cached.pricesEqual(entry)) {
                changed.add(entry);
            }
        }
        return changed;
    }

    /** Call after a successful API send to move fresh entries into the cache. */
    public void update(List<PriceEntry> entries) {
        for (PriceEntry entry : entries) {
            cache.put(entry.id, entry);
        }
        persist();
    }

    /** @return number of distinct items currently cached (across all pages/categories seen so far). */
    public int size() {
        return cache.size();
    }
}

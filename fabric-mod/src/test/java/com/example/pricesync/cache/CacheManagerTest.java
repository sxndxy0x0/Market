package com.example.pricesync.cache;

import com.example.pricesync.util.PriceEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests CacheManager's diff/update logic — the core of the spec's
 * "only send when prices changed" requirement. Uses a temp file
 * (via the package-private Path constructor) instead of touching
 * the real config/price-sync/cache.json.
 */
class CacheManagerTest {

    private PriceEntry entry(String id, double sell) {
        PriceEntry e = new PriceEntry();
        e.id = id;
        e.name = id;
        e.buy = -1;
        e.sell = sell;
        e.stackPrice = -1;
        return e;
    }

    @Test
    void newItemsAreAlwaysReportedAsChanged(@TempDir Path tempDir) {
        CacheManager cache = new CacheManager(tempDir.resolve("cache.json"));

        List<PriceEntry> fresh = List.of(entry("diamond", 100), entry("emerald", 50));
        List<PriceEntry> changed = cache.diff(fresh);

        assertEquals(2, changed.size());
    }

    @Test
    void unchangedPricesAreNotReportedAfterUpdate(@TempDir Path tempDir) {
        CacheManager cache = new CacheManager(tempDir.resolve("cache.json"));

        List<PriceEntry> first = List.of(entry("diamond", 100));
        cache.update(cache.diff(first)); // simulate a successful sync

        // Same price again -> should NOT show up as changed.
        List<PriceEntry> second = List.of(entry("diamond", 100));
        assertTrue(cache.diff(second).isEmpty());
    }

    @Test
    void changedPriceIsReportedAgain(@TempDir Path tempDir) {
        CacheManager cache = new CacheManager(tempDir.resolve("cache.json"));

        cache.update(cache.diff(List.of(entry("diamond", 100))));

        List<PriceEntry> priceWentUp = List.of(entry("diamond", 150));
        List<PriceEntry> changed = cache.diff(priceWentUp);

        assertEquals(1, changed.size());
        assertEquals(150, changed.get(0).sell);
    }

    @Test
    void stackPriceOnlyChangeIsStillReported(@TempDir Path tempDir) {
        // Regression test for the pricesEqual() bug found earlier — stackPrice
        // changes must still trigger a sync even if sell/buy stay identical.
        CacheManager cache = new CacheManager(tempDir.resolve("cache.json"));

        PriceEntry first = entry("spawner", 1069.02);
        first.stackPrice = 68417.28;
        cache.update(cache.diff(List.of(first)));

        PriceEntry stackPriceChanged = entry("spawner", 1069.02); // same per-unit price
        stackPriceChanged.stackPrice = 70000.00; // but stack price moved

        assertEquals(1, cache.diff(List.of(stackPriceChanged)).size());
    }

    @Test
    void cachePersistsAcrossInstances(@TempDir Path tempDir) {
        Path path = tempDir.resolve("cache.json");

        CacheManager first = new CacheManager(path);
        first.update(first.diff(List.of(entry("diamond", 100))));

        // Simulate a restart: new instance, same file.
        CacheManager second = new CacheManager(path);
        assertEquals(1, second.size());
        assertTrue(second.diff(List.of(entry("diamond", 100))).isEmpty());
    }
}

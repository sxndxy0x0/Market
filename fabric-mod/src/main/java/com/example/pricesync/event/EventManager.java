package com.example.pricesync.event;

import com.example.pricesync.api.ApiClient;
import com.example.pricesync.cache.CacheManager;
import com.example.pricesync.config.ConfigManager;
import com.example.pricesync.gui.GuiReader;
import com.example.pricesync.parser.GuiParser;
import com.example.pricesync.parser.PriceParser;
import com.example.pricesync.scheduler.Scheduler;
import com.example.pricesync.util.JsonBuilder;
import com.example.pricesync.util.Logger;
import com.example.pricesync.util.PriceEntry;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Wires Fabric lifecycle/network events to the read -> parse -> compare -> send pipeline,
 * matching the EVENT FLOW section of the project spec.
 *
 * GUI-open detection is handled two ways:
 *  1. HandledScreenMixin -> ScreenOpenCallback fires once when the GUI first opens.
 *  2. A tick-based poll (see registerAll) re-checks the title every POLL_INTERVAL_TICKS
 *     while a matching screen stays open. This is needed because clicking to a
 *     different page/category does NOT re-open the screen (no new init() call) —
 *     the server just updates slot contents in place, so #1 alone would only ever
 *     capture whatever page was showing the moment the GUI was first opened.
 *
 * runNow() is also available for the manual keybind (updateMode: refresh_button).
 */
public class EventManager {

    /** How often (in client ticks, 20/sec) to re-check the open GUI for page/category changes. */
    private static final int POLL_INTERVAL_TICKS = 20; // ~once per second

    private final ConfigManager configManager;
    private final GuiReader guiReader;
    private final GuiParser guiParser;
    private final PriceParser priceParser;
    private final JsonBuilder jsonBuilder;
    private final CacheManager cacheManager;
    private final ApiClient apiClient;
    private final Scheduler scheduler;

    private int tickCounter = 0;
    private long lastSyncEpochMs = 0;
    private int lastSyncedCount = 0;

    public EventManager(
            ConfigManager configManager,
            GuiReader guiReader,
            GuiParser guiParser,
            PriceParser priceParser,
            JsonBuilder jsonBuilder,
            CacheManager cacheManager,
            ApiClient apiClient,
            Scheduler scheduler
    ) {
        this.configManager = configManager;
        this.guiReader = guiReader;
        this.guiParser = guiParser;
        this.priceParser = priceParser;
        this.jsonBuilder = jsonBuilder;
        this.cacheManager = cacheManager;
        this.apiClient = apiClient;
        this.scheduler = scheduler;
    }

    public void registerAll() {
        // Player joined server -> start scheduler if in automatic mode.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            Logger.info("Joined server, starting scheduler if configured.");
            scheduler.start(this::safeRunSyncPipeline);
            // In "manual" mode nothing runs here — the open/poll hooks below
            // fire the pipeline whenever the player opens/browses the /worth GUI themselves.
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            scheduler.stop();
        });

        // Fired by HandledScreenMixin whenever ANY container GUI opens.
        // We only care about it if the title starts with the configured /worth
        // GUI title. NOTE: this server's real title is "WORTH (1/43)" — it has
        // a page number suffix that changes per page, so this MUST be a prefix
        // match, not an exact one, or every page after the first gets ignored.
        ScreenOpenCallback.EVENT.register((screen, title) -> {
            if (titleMatches(title)) {
                Logger.debug("Matched GUI title \"" + title + "\", running sync pipeline.");
                safeRunSyncPipeline();
            } else {
                Logger.debug("Ignoring screen with title \"" + title + "\" (expected prefix \""
                        + configManager.get().guiTitle + "\")");
            }
        });

        // Polls the CURRENTLY open screen's title every POLL_INTERVAL_TICKS.
        // Catches page/category changes that don't fire ScreenOpenCallback
        // (since the screen isn't re-created when you click to another page —
        // only its slot contents change). The cache diff in runSyncPipeline()
        // makes this cheap: nothing gets sent unless prices actually changed.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            tickCounter++;
            if (tickCounter < POLL_INTERVAL_TICKS) {
                return;
            }
            tickCounter = 0;

            String currentTitle = guiReader.getCurrentScreenTitle();
            if (currentTitle != null && titleMatches(currentTitle)) {
                safeRunSyncPipeline();
            }
        });
    }

    private boolean titleMatches(String title) {
        String expected = configManager.get().guiTitle;
        if (expected == null || expected.isBlank()) {
            return false;
        }
        return title.trim().toLowerCase().startsWith(expected.toLowerCase());
    }

    /** Call this manually (keybind/command) to force an immediate sync of whatever's open right now. */
    public void runNow() {
        safeRunSyncPipeline();
    }

    /**
     * Per spec's ERROR HANDLING section ("Never crash Minecraft"): this runs
     * on the client render/tick thread (from a mixin callback or tick event),
     * so any uncaught exception here would crash or freeze the game. Every
     * entry point into the pipeline goes through this wrapper instead of
     * calling runSyncPipeline() directly.
     */
    private void safeRunSyncPipeline() {
        try {
            runSyncPipeline();
        } catch (Exception e) {
            Logger.error("Sync pipeline failed (swallowed to avoid crashing the game)", e);
        }
    }

    private void runSyncPipeline() {
        if (!guiReader.isScreenOpen()) {
            Logger.debug("No GUI open, skipping sync pipeline.");
            return;
        }

        List<ItemStack> rawStacks = guiReader.readOpenScreenSlots();

        List<PriceEntry> parsedEntries = new ArrayList<>();
        for (ItemStack stack : rawStacks) {
            GuiParser.ParsedItem parsedItem = guiParser.parse(stack);
            Optional<PriceEntry> entry = priceParser.parse(parsedItem);
            entry.ifPresent(parsedEntries::add);
        }

        if (parsedEntries.isEmpty()) {
            Logger.debug("No priced items parsed from GUI.");
            return;
        }

        List<PriceEntry> changed = cacheManager.diff(parsedEntries);
        if (changed.isEmpty()) {
            Logger.debug("No price changes detected, skipping API send.");
            return;
        }

        String json = jsonBuilder.build(changed);
        apiClient.sendPricesAsync(json);
        cacheManager.update(changed);

        lastSyncEpochMs = System.currentTimeMillis();
        lastSyncedCount = changed.size();

        Logger.info("Synced " + changed.size() + " changed price(s).");
    }

    /** @return epoch millis of the last successful sync, or 0 if none yet this session. */
    public long getLastSyncEpochMs() {
        return lastSyncEpochMs;
    }

    /** @return number of items sent in the last sync. */
    public int getLastSyncedCount() {
        return lastSyncedCount;
    }
}

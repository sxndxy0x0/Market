package com.example.pricesync;

import com.example.pricesync.api.ApiClient;
import com.example.pricesync.cache.CacheManager;
import com.example.pricesync.config.ConfigManager;
import com.example.pricesync.event.EventManager;
import com.example.pricesync.event.CommandManager;
import com.example.pricesync.event.KeybindManager;
import com.example.pricesync.gui.GuiReader;
import com.example.pricesync.parser.GuiParser;
import com.example.pricesync.parser.PriceParser;
import com.example.pricesync.scheduler.Scheduler;
import com.example.pricesync.util.JsonBuilder;
import com.example.pricesync.util.Logger;
import net.fabricmc.api.ClientModInitializer;

/**
 * Entry point. Wires all modules together.
 * TODO: replace field init with a small DI container if this grows.
 */
public class PriceSyncMod implements ClientModInitializer {

    public static final String MOD_ID = "price_sync";

    private static PriceSyncMod instance;

    private ConfigManager configManager;
    private CacheManager cacheManager;
    private ApiClient apiClient;
    private GuiReader guiReader;
    private GuiParser guiParser;
    private PriceParser priceParser;
    private JsonBuilder jsonBuilder;
    private Scheduler scheduler;
    private EventManager eventManager;

    @Override
    public void onInitializeClient() {
        instance = this;

        Logger.info("Initializing Price Sync mod...");

        configManager = new ConfigManager();
        configManager.load(); // also runs validate() internally, incl. Logger.setDebugEnabled(...)

        cacheManager = new CacheManager();
        apiClient = new ApiClient(configManager);
        guiReader = new GuiReader(configManager);
        guiParser = new GuiParser();
        priceParser = new PriceParser();
        jsonBuilder = new JsonBuilder(configManager);

        scheduler = new Scheduler(configManager);
        eventManager = new EventManager(
                configManager,
                guiReader,
                guiParser,
                priceParser,
                jsonBuilder,
                cacheManager,
                apiClient,
                scheduler
        );

        eventManager.registerAll();

        // For updateMode="refresh_button": the keybind always exists (unbound
        // by default), it's just up to the player to actually set it and use
        // it — EventManager.runNow() runs the same pipeline as automatic
        // triggers, so this works regardless of updateMode.
        KeybindManager.register(eventManager::runNow);

        // /pricesync status | /pricesync sync — quick in-game diagnostics.
        CommandManager.register(configManager, eventManager, cacheManager, apiClient);

        Logger.info("Price Sync mod initialized.");
    }

    public static PriceSyncMod getInstance() {
        return instance;
    }
}

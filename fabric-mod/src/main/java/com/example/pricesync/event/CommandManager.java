package com.example.pricesync.event;

import com.example.pricesync.api.ApiClient;
import com.example.pricesync.cache.CacheManager;
import com.example.pricesync.config.ConfigManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.network.chat.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Registers `/pricesync status` and `/pricesync sync` — a quick way to check
 * whether the mod is working and force a sync without needing to open Controls
 * to find the (default-unbound) refresh keybind.
 */
public final class CommandManager {

    private CommandManager() {}

    public static void register(ConfigManager configManager, EventManager eventManager,
                                  CacheManager cacheManager, ApiClient apiClient) {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("pricesync")
                    .then(ClientCommandManager.literal("status").executes(ctx -> {
                        ctx.getSource().sendFeedback(buildStatusMessage(configManager, eventManager, cacheManager, apiClient));
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("sync").executes(ctx -> {
                        eventManager.runNow();
                        ctx.getSource().sendFeedback(Component.literal(
                                "[PriceSync] Manual sync triggered (only works if the /worth GUI is currently open)."));
                        return 1;
                    }))
                    // bare "/pricesync" behaves like "/pricesync status"
                    .executes(ctx -> {
                        ctx.getSource().sendFeedback(buildStatusMessage(configManager, eventManager, cacheManager, apiClient));
                        return 1;
                    })
            );
        });
    }

    private static Component buildStatusMessage(ConfigManager configManager, EventManager eventManager,
                                                  CacheManager cacheManager, ApiClient apiClient) {
        var config = configManager.get();
        long lastSync = eventManager.getLastSyncEpochMs();

        String lastSyncText = lastSync == 0
                ? "never (this session)"
                : Duration.between(Instant.ofEpochMilli(lastSync), Instant.now()).getSeconds() + "s ago ("
                    + eventManager.getLastSyncedCount() + " item(s))";

        return Component.literal(
                "[PriceSync] mode=" + config.updateMode
                        + " | guiTitle=\"" + config.guiTitle + "\""
                        + " | cached items=" + cacheManager.size()
                        + " | queued failures=" + apiClient.getQueuedCount()
                        + " | last sync=" + lastSyncText
        );
    }
}

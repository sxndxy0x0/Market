package com.example.pricesync.config;

import com.example.pricesync.util.Logger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * Loads/saves config.json (see project spec's CONFIG section).
 * validate() ensures a malformed config.json (bad values, wrong types edited
 * by hand, etc.) can never crash the mod — every bad value gets clamped/reset
 * to a safe default and logged as a warning instead of propagating further.
 */
public class ConfigManager {

    private static final Path CONFIG_PATH = Path.of("config", "price-sync", "config.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<String> VALID_UPDATE_MODES = Set.of("manual", "automatic", "refresh_button");

    private ModConfig config = new ModConfig();

    public void load() {
        try {
            if (!Files.exists(CONFIG_PATH)) {
                Logger.info("No config found, creating default at " + CONFIG_PATH);
                save();
                return;
            }
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                ModConfig loaded = GSON.fromJson(reader, ModConfig.class);
                if (loaded != null) {
                    this.config = loaded;
                }
            }
        } catch (Exception e) {
            // Catches IOException AND Gson's JsonSyntaxException (malformed JSON) —
            // either way, fall back to defaults rather than let the mod fail to load.
            Logger.error("Failed to load config, using defaults", e);
            this.config = new ModConfig();
        }

        validate();
    }

    /**
     * Clamps/resets any out-of-range or malformed values to safe defaults.
     * Never throws. Called automatically after load(); safe to call again
     * manually (e.g. after a live config reload command, if one gets added).
     */
    public void validate() {
        ModConfig defaults = new ModConfig();

        if (config.updateMode == null || !VALID_UPDATE_MODES.contains(config.updateMode)) {
            Logger.warn("Invalid updateMode \"" + config.updateMode + "\", falling back to \""
                    + defaults.updateMode + "\". Valid values: " + VALID_UPDATE_MODES);
            config.updateMode = defaults.updateMode;
        }

        if (config.updateInterval <= 0) {
            Logger.warn("updateInterval must be > 0 (got " + config.updateInterval + "), falling back to "
                    + defaults.updateInterval + "s.");
            config.updateInterval = defaults.updateInterval;
        }

        if (config.containerSlotCount < 0) {
            Logger.warn("containerSlotCount can't be negative (got " + config.containerSlotCount
                    + "), falling back to " + defaults.containerSlotCount + ".");
            config.containerSlotCount = defaults.containerSlotCount;
        }

        if (config.guiTitle == null || config.guiTitle.isBlank()) {
            Logger.warn("guiTitle is blank — the mod will never match any GUI and won't sync anything. "
                    + "Falling back to \"" + defaults.guiTitle + "\", but you should set this to your "
                    + "server's actual /worth GUI title.");
            config.guiTitle = defaults.guiTitle;
        }

        if (config.serverName == null || config.serverName.isBlank()) {
            Logger.warn("serverName is blank — synced prices won't be attributable to a server on the backend.");
            // not fatal, don't reset — just warn.
        }

        if (config.apiUrl == null || config.apiUrl.isBlank()) {
            Logger.warn("apiUrl is blank — ApiClient will skip sending until this is set.");
            // ApiClient already handles blank apiUrl gracefully at send time; just warn here.
        } else if (!isValidHttpUrl(config.apiUrl)) {
            Logger.warn("apiUrl \"" + config.apiUrl + "\" doesn't look like a valid http(s) URL. "
                    + "Sync requests will likely fail until this is fixed.");
        }

        Logger.setDebugEnabled(config.debug);
    }

    private boolean isValidHttpUrl(String url) {
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && uri.getHost() != null;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            Logger.error("Failed to save config", e);
        }
    }

    public ModConfig get() {
        return config;
    }

    /** Plain data holder matching the config.json schema from the spec. */
    public static class ModConfig {
        public String serverName = "";
        public String apiUrl = "";
        public String apiKey = "";
        // Prefix of the /worth GUI's plain-text title, used to tell it apart
        // from other container screens the player might open (their own
        // inventory, shops, etc.). This server's real title is "WORTH (1/43)"
        // (page number suffix changes per page) — matched as a PREFIX, so
        // "WORTH" here is enough; don't include the "(1/43)" part.
        public String guiTitle = "WORTH";
        // Number of leading slots in the menu that belong to the /worth container
        // itself (before the player's own inventory slots start). E.g. a
        // generic_9x6 chest GUI = 54. Set to 0 to read ALL slots (only safe if
        // the server's GUI has no player inventory tacked on, which is unusual).
        public int containerSlotCount = 54;
        public String updateMode = "manual"; // manual | automatic | refresh_button
        public long updateInterval = 86400;  // seconds
        public boolean enableDiscord = true;
        public boolean enableHistory = true;
        public boolean debug = false;
    }
}

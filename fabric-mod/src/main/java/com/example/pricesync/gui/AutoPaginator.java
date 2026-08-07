package com.example.pricesync.gui;

import com.example.pricesync.config.ConfigManager;
import com.example.pricesync.util.Logger;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.ClickType;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Opt-in auto-pagination: clicks the /worth GUI's "next page" slot itself so
 * the player doesn't have to browse every page by hand to get full coverage.
 *
 * Disabled by default (config.autoPaginate = false) — confirm this doesn't
 * violate your server's rules before enabling. It IS a form of automated
 * clicking, even though it only browses a menu and never buys/sells anything.
 *
 * Uses MultiPlayerGameMode.handleInventoryMouseClick(...) — the same method
 * the game itself calls when you click a slot — with ClickType.PICKUP to
 * simulate a normal left-click on the configured `nextPageSlot`.
 */
public class AutoPaginator {

    // Matches a page indicator like "(1/43)" anywhere in the title.
    private static final Pattern PAGE_PATTERN = Pattern.compile("\\((\\d+)\\s*/\\s*(\\d+)\\)");

    // Safety cap: never click more than this many times per GUI-open session,
    // even if page-number parsing somehow never reaches the last page (e.g.
    // title format changes). Prevents a runaway click loop.
    private static final int MAX_CLICKS_PER_SESSION = 300;

    private final ConfigManager configManager;
    private int lastAdvancedFromPage = -1;
    private int clicksThisSession = 0;

    public AutoPaginator(ConfigManager configManager) {
        this.configManager = configManager;
    }

    /** Call this whenever a NEW matching GUI session opens, to reset pagination state. */
    public void resetSession() {
        lastAdvancedFromPage = -1;
        clicksThisSession = 0;
    }

    /**
     * Call on each tick poll while the /worth GUI is open (after syncing the
     * current page). Clicks "next page" once per detected page, then waits
     * for the next poll to see the new page before clicking again.
     */
    public void maybeAdvance(String currentTitle) {
        if (!configManager.get().autoPaginate) {
            return;
        }

        Matcher m = PAGE_PATTERN.matcher(currentTitle);
        if (!m.find()) {
            Logger.debug("Auto-paginate: couldn't parse page number from title \"" + currentTitle + "\"");
            return;
        }

        int current = Integer.parseInt(m.group(1));
        int total = Integer.parseInt(m.group(2));

        if (current >= total) {
            Logger.debug("Auto-paginate: reached last page (" + current + "/" + total + "), stopping.");
            return;
        }

        if (current == lastAdvancedFromPage) {
            // Already clicked to advance past this page; waiting for the
            // server to actually move us to the next one before clicking again.
            return;
        }

        if (clicksThisSession >= MAX_CLICKS_PER_SESSION) {
            Logger.warn("Auto-paginate: hit safety cap of " + MAX_CLICKS_PER_SESSION
                    + " clicks this session, stopping to avoid a runaway loop.");
            return;
        }

        if (clickNextPageSlot()) {
            lastAdvancedFromPage = current;
            clicksThisSession++;
            Logger.debug("Auto-paginate: advanced from page " + current + "/" + total);
        }
    }

    private boolean clickNextPageSlot() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.player.containerMenu == null || client.gameMode == null) {
            return false;
        }

        int slot = configManager.get().nextPageSlot;
        int containerId = client.player.containerMenu.containerId;

        client.gameMode.handleInventoryMouseClick(containerId, slot, 0, ClickType.PICKUP, client.player);
        return true;
    }
}

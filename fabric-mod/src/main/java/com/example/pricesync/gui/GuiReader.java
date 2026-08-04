package com.example.pricesync.gui;

import com.example.pricesync.config.ConfigManager;
import com.example.pricesync.util.Logger;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads raw ItemStacks out of the currently open container GUI
 * (expected to be the server's /worth menu).
 *
 * IMPORTANT: tracks the currently-open screen itself via Fabric API's
 * ScreenEvents, rather than reading Minecraft's internal current-screen
 * state directly. That internal name/location has changed across recent
 * Minecraft versions (was a `screen` field, then briefly thought to be
 * `currentScreen`, then `gui.getScreen()` — none of which turned out
 * correct for 26.2 after actually testing against a real build). ScreenEvents
 * is Fabric's own stable, actively-maintained abstraction over screen
 * lifecycle and isn't affected by that kind of internal renaming.
 */
public class GuiReader {

    private final ConfigManager configManager;
    private volatile Screen currentScreen;

    public GuiReader(ConfigManager configManager) {
        this.configManager = configManager;

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            currentScreen = screen;
            ScreenEvents.remove(screen).register(closed -> {
                if (currentScreen == closed) {
                    currentScreen = null;
                }
            });
        });
    }

    /**
     * @return raw stacks for every non-empty slot in the currently open menu,
     *         excluding the player's own inventory slots (see config's
     *         `containerSlotCount`), or an empty list if no screen is open.
     */
    public List<ItemStack> readOpenScreenSlots() {
        List<ItemStack> stacks = new ArrayList<>();

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return stacks;
        }

        AbstractContainerMenu menu = client.player.containerMenu;
        if (menu == null) {
            Logger.debug("No menu open.");
            return stacks;
        }

        int limit = configManager.get().containerSlotCount;
        List<Slot> slots = menu.slots;
        int slotsToRead = (limit > 0) ? Math.min(limit, slots.size()) : slots.size();

        for (int i = 0; i < slotsToRead; i++) {
            ItemStack stack = slots.get(i).getItem();
            if (!stack.isEmpty()) {
                stacks.add(stack);
            }
        }

        Logger.debug("Read " + stacks.size() + " non-empty slots from GUI (of "
                + slotsToRead + "/" + slots.size() + " scanned).");
        return stacks;
    }

    /** @return true if a container screen is currently open (tracked via ScreenEvents). */
    public boolean isScreenOpen() {
        return currentScreen != null;
    }

    /**
     * @return the plain-text title of the currently open screen, or null if
     * no screen is open or it's not a container screen. Used to detect page/
     * category changes: EventManager polls this on a tick timer to re-check
     * which page/category is showing right now (the title includes a page
     * number, e.g. "WORTH (2/43)", which doesn't trigger a new screen-open event).
     */
    public String getCurrentScreenTitle() {
        if (currentScreen instanceof AbstractContainerScreen<?> containerScreen) {
            return containerScreen.getTitle().getString();
        }
        return null;
    }
}

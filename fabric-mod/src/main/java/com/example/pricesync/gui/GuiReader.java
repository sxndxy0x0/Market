package com.example.pricesync.gui;

import com.example.pricesync.config.ConfigManager;
import com.example.pricesync.util.Logger;
import net.minecraft.client.Minecraft;
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
 * Uses Mojang's official mappings (Minecraft 26.2+ is unobfuscated; Yarn
 * names like MinecraftClient/ScreenHandler/ItemStack's old package no
 * longer apply here).
 *
 * TODO: confirm which menu class the target server uses
 * (vanilla generic_9x6 chest vs a custom menu) and, if needed,
 * detect it by title text before reading slots.
 */
public class GuiReader {

    private final ConfigManager configManager;

    public GuiReader(ConfigManager configManager) {
        this.configManager = configManager;
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

    /**
     * @return true if a container screen is currently open.
     * NOTE: containerMenu is never actually null (it defaults to the player's
     * own inventory menu when nothing else is open) — client.screen != null
     * is the real signal here. Left both checks in for clarity/future-proofing,
     * but don't rely on containerMenu alone to mean "a GUI is open".
     */
    public boolean isScreenOpen() {
        Minecraft client = Minecraft.getInstance();
        return client.player != null && client.player.containerMenu != null
                && client.screen != null;
    }

    /**
     * @return the plain-text title of the currently open screen, or null if
     * no screen is open or it's not a container screen. Used to detect page/
     * category changes: the mixin only fires once on GUI open, so EventManager
     * polls this on a tick timer to re-check which page/category is showing
     * right now (the title includes a page number, e.g. "WORTH (2/43)").
     */
    public String getCurrentScreenTitle() {
        Minecraft client = Minecraft.getInstance();
        if (client.screen instanceof AbstractContainerScreen<?> containerScreen) {
            return containerScreen.getTitle().getString();
        }
        return null;
    }
}

package com.example.pricesync.parser;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns raw ItemStacks from GuiReader into plain text lines
 * (display name + lore) that PriceParser can then read prices from.
 *
 * Uses Mojang's official mappings (26.2+): DataComponents.LORE / ItemLore,
 * not Yarn's DataComponentTypes.LORE / LoreComponent.
 */
public class GuiParser {

    public ParsedItem parse(ItemStack stack) {
        String displayName = stack.getHoverName().getString();

        List<String> loreLines = new ArrayList<>();
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore != null) {
            for (var line : lore.lines()) {
                loreLines.add(line.getString());
            }
        }

        return new ParsedItem(displayName, loreLines);
    }

    /** Plain-text view of an item: name + lore lines, ready for price extraction. */
    public record ParsedItem(String displayName, List<String> loreLines) {}
}

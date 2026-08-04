package com.example.pricesync.parser;

import com.example.pricesync.util.PriceEntry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests PriceParser against real lore captured from SiamCraft.net's /worth
 * GUI (see screenshots in the Aug 2026 conversation). No Minecraft classes
 * involved — GuiParser.ParsedItem is a plain record, so these run with
 * plain `./gradlew test`, no game client needed.
 */
class PriceParserTest {

    private final PriceParser parser = new PriceParser();

    @Test
    void parsesRealSpawnerLore() {
        var item = new GuiParser.ParsedItem("spawner", List.of(
                "ราคาต่อชิ้น: 🪙 1,069.02",
                "ราคาต่อสแตค: 🪙 68,417.28"
        ));

        Optional<PriceEntry> result = parser.parse(item);

        assertTrue(result.isPresent());
        PriceEntry entry = result.get();
        assertEquals("spawner", entry.id);
        assertEquals(1069.02, entry.sell, 0.0001);
        assertEquals(68417.28, entry.stackPrice, 0.0001);
        assertEquals(-1, entry.buy); // this server has no separate buy price
    }

    @Test
    void ignoresCategoryButtonsWithNoPriceLore() {
        // e.g. the purple-block / sword / apple category selector column
        var item = new GuiParser.ParsedItem("Blocks", List.of());

        assertTrue(parser.parse(item).isEmpty());
    }

    @Test
    void ignoresItemsWithUnrelatedLoreOnly() {
        var item = new GuiParser.ParsedItem("Diamond Sword", List.of(
                "Sharpness V",
                "Unbreaking III"
        ));

        assertTrue(parser.parse(item).isEmpty());
    }

    @Test
    void handlesMissingStackPriceGracefully() {
        // in case some item only ever shows per-unit price
        var item = new GuiParser.ParsedItem("Cobblestone", List.of(
                "ราคาต่อชิ้น: 🪙 1.00"
        ));

        Optional<PriceEntry> result = parser.parse(item);

        assertTrue(result.isPresent());
        assertEquals(1.00, result.get().sell, 0.0001);
        assertEquals(-1, result.get().stackPrice);
    }

    @Test
    void slugifiesDisplayNameIntoId() {
        var item = new GuiParser.ParsedItem("Enchanted Golden Apple", List.of(
                "ราคาต่อชิ้น: 🪙 5,000.00"
        ));

        Optional<PriceEntry> result = parser.parse(item);

        assertTrue(result.isPresent());
        assertEquals("enchanted_golden_apple", result.get().id);
    }

    @Test
    void handlesLargeNumbersWithMultipleCommas() {
        var item = new GuiParser.ParsedItem("Netherite Block", List.of(
                "ราคาต่อชิ้น: 🪙 1,234,567.89"
        ));

        Optional<PriceEntry> result = parser.parse(item);

        assertTrue(result.isPresent());
        assertEquals(1234567.89, result.get().sell, 0.0001);
    }
}

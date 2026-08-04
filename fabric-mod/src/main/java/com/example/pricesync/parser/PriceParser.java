package com.example.pricesync.parser;

import com.example.pricesync.util.PriceEntry;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts prices out of a ParsedItem's lore lines.
 *
 * Matches this server's real lore format (confirmed via screenshot, Aug 2026):
 *   "ราคาต่อชิ้น: <coin-icon> 1,069.02"   (price per unit)
 *   "ราคาต่อสแตค: <coin-icon> 68,417.28"  (price per full stack)
 *
 * This server's /worth GUI only shows ONE price (what it pays you per item),
 * not separate buy/sell prices — so we map it to PriceEntry.sell and leave
 * buy at -1 (not applicable). Prices are decimals, not whole numbers.
 *
 * The "[^0-9]*" gap before the digits skips whatever coin icon/symbol glyph
 * sits between the colon and the number — works regardless of what that
 * glyph actually is.
 */
public class PriceParser {

    private static final Pattern PER_UNIT_PATTERN =
            Pattern.compile("ราคาต่อชิ้น:?[^0-9]*([0-9,]+(?:\\.[0-9]+)?)");
    private static final Pattern PER_STACK_PATTERN =
            Pattern.compile("ราคาต่อสแตค:?[^0-9]*([0-9,]+(?:\\.[0-9]+)?)");

    public Optional<PriceEntry> parse(GuiParser.ParsedItem item) {
        Double perUnit = extract(PER_UNIT_PATTERN, item.loreLines());
        Double perStack = extract(PER_STACK_PATTERN, item.loreLines());

        if (perUnit == null && perStack == null) {
            return Optional.empty();
        }

        PriceEntry entry = new PriceEntry();
        entry.id = toId(item.displayName());
        entry.name = item.displayName();
        entry.sell = perUnit != null ? perUnit : -1;
        entry.stackPrice = perStack != null ? perStack : -1;
        // entry.buy stays -1: this server's /worth GUI doesn't show a buy price.

        return Optional.of(entry);
    }

    private Double extract(Pattern pattern, List<String> lines) {
        for (String line : lines) {
            Matcher m = pattern.matcher(line);
            if (m.find()) {
                String raw = m.group(1).replace(",", ""); // keep the decimal point!
                try {
                    return Double.parseDouble(raw);
                } catch (NumberFormatException ignored) {
                    // fall through
                }
            }
        }
        return null;
    }

    /** Slugify a display name into a stable id, e.g. "Diamond" -> "diamond". */
    private String toId(String displayName) {
        return displayName
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }
}

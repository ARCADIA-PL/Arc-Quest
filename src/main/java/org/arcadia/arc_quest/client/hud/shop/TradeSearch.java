package org.arcadia.arc_quest.client.hud.shop;

import net.minecraft.locale.Language;
import org.arcadia.arc_quest.trade.api.TradeCategory;
import org.arcadia.arc_quest.trade.api.TradeEntry;
import org.arcadia.arc_quest.trade.offer.ItemTradeOffer;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntPredicate;
import java.util.regex.Pattern;

/** Client presentation only: authority visibility and original entry order always take precedence. */
final class TradeSearch {
    private static final Pattern FORMATTING = Pattern.compile("§[0-9a-fk-orx]", Pattern.CASE_INSENSITIVE);
    private static final Pattern WHITESPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);
    private final Map<TradeEntry, List<String>> names = new IdentityHashMap<>();
    private Language language;
    private List<String> tokens = List.of();

    void setQuery(String query) {
        String normalized = normalize(query).strip();
        tokens = normalized.isEmpty() ? List.of() : List.of(WHITESPACE.split(normalized));
    }

    List<TradeEntry> filter(List<TradeEntry> entries, IntPredicate visible, TradeCategory category) {
        if (language != Language.getInstance()) {
            names.clear();
            language = Language.getInstance();
        }
        List<TradeEntry> result = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            if (!visible.test(i)) continue;
            TradeEntry entry = entries.get(i);
            if (category != null && !category.equals(entry.getCategory())) continue;
            if (tokens.isEmpty() || matches(names.computeIfAbsent(entry, TradeSearch::searchableNames), tokens)) {
                result.add(entry);
            }
        }
        return result;
    }

    private static List<String> searchableNames(TradeEntry entry) {
        List<String> result = new ArrayList<>();
        result.add(normalize(entry.getDisplayName().getString()));
        result.add(normalize(entry.getEntryId()));
        for (var reward : entry.getRewards()) {
            result.add(normalize(reward.describe().getString()));
            if (reward instanceof ItemTradeOffer item) {
                for (var stack : item.getDisplayStacks()) result.add(normalize(stack.getHoverName().getString()));
            }
        }
        return result;
    }

    static boolean matches(List<String> names, List<String> tokens) {
        return tokens.stream().allMatch(token -> names.stream().anyMatch(name -> fuzzyMatches(name, token)));
    }

    private static boolean fuzzyMatches(String name, String token) {
        if (name.contains(token)) return true;
        // Ordered character matching also accepts e.g. 神刀 -> 神龙刀, without regex input.
        int offset = 0;
        for (int point : token.codePoints().toArray()) {
            int found = name.indexOf(point, offset);
            if (found < 0) return false;
            offset = found + Character.charCount(point);
        }
        return true;
    }

    static String normalize(String text) {
        return FORMATTING.matcher(Normalizer.normalize(text, Normalizer.Form.NFKC))
                .replaceAll("").toLowerCase(Locale.ROOT);
    }
}

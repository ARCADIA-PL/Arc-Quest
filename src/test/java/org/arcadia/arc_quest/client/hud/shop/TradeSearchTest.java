package org.arcadia.arc_quest.client.hud.shop;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.trade.api.ITradeOffer;
import org.arcadia.arc_quest.trade.api.TradeCategory;
import org.arcadia.arc_quest.trade.api.TradeEntry;
import org.arcadia.arc_quest.trade.builder.TradeEntryBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TradeSearchTest {
    private final TradeSearch search = new TradeSearch();
    private static final TradeCategory WEAPONS = TradeCategory.of("weapons", "武器");
    private static final TradeCategory ARMOR = TradeCategory.of("armor", "盔甲");

    @Test void supportsChineseOrderedAbbreviationsCaseWidthAndMultipleTokens() {
        var dragon = entry("dragon", "§b神龙刀", "Dragon Sword", WEAPONS);
        var diamond = entry("diamond", "Diamond Sword", "Diamond Sword", WEAPONS);
        var entries = List.of(dragon, diamond);
        for (String query : List.of("神刀", "神龙", "ＳＷＯＲＤ　dragon", "DRGN swrd")) {
            search.setQuery(query);
            assertEquals(List.of(dragon), search.filter(entries, i -> true, null), query);
        }
        search.setQuery("sword diamond");
        assertEquals(List.of(diamond), search.filter(entries, i -> true, null));
        search.setQuery("刀神");
        assertTrue(search.filter(entries, i -> true, null).isEmpty());
    }

    @Test void intersectsCurrentCategoryWithAuthorityVisibilityWithoutReadingHiddenNames() {
        var sword = entry("sword", "铁剑", "Iron Sword", WEAPONS);
        var helmet = entry("helmet", "铁头盔", "Iron Helmet", ARMOR);
        ITradeOffer hidden = new ITradeOffer() {
            public boolean canAfford(ServerPlayer player) { return false; }
            public void execute(ServerPlayer player) { fail("Search must never execute an offer"); }
            public Component describe() { throw new AssertionError("Hidden offer was inspected"); }
            public String getType() { return "hidden"; }
        };
        var secret = TradeEntryBuilder.create("secret").displayName("铁剑").reward(hidden).build();
        search.setQuery("铁");
        var entries = List.of(sword, helmet, secret);
        assertEquals(List.of(sword), search.filter(entries, i -> i != 2, WEAPONS));
        assertEquals(List.of(helmet), search.filter(entries, i -> i != 2, ARMOR));
        assertEquals(List.of(sword, helmet), search.filter(entries, i -> i != 2, null));
    }

    @Test void returnsOriginalObjectsInOriginalOrderAndDoesNotTreatInputAsRegex() {
        var first = entry("first", "神龙刀", "Sword", WEAPONS);
        var second = entry("second", "神龙刀", "Sword", WEAPONS);
        var entries = List.of(first, second);
        search.setQuery("神刀");
        var results = search.filter(entries, i -> true, null);
        assertSame(first, results.get(0));
        assertSame(second, results.get(1));
        search.setQuery("[.*]");
        assertTrue(search.filter(entries, i -> true, null).isEmpty());
        search.setQuery("   ");
        assertEquals(entries, search.filter(entries, i -> true, null));
        search.setQuery("second");
        assertEquals(List.of(second), search.filter(entries, i -> true, null));
    }

    @Test void newDefinitionWithReusedIdCannotReuseOldSearchText() {
        var oldEntry = entry("same", "Old Sword", "Old Sword", WEAPONS);
        var newEntry = entry("same", "New Shield", "New Shield", ARMOR);
        search.setQuery("sword");
        assertEquals(List.of(oldEntry), search.filter(List.of(oldEntry), i -> true, null));
        assertTrue(search.filter(List.of(newEntry), i -> true, null).isEmpty());
        search.setQuery("shield");
        assertEquals(List.of(newEntry), search.filter(List.of(newEntry), i -> true, null));
    }

    private static TradeEntry entry(String id, String name, String rewardName, TradeCategory category) {
        ITradeOffer reward = new ITradeOffer() {
            public boolean canAfford(ServerPlayer player) { return true; }
            public void execute(ServerPlayer player) { fail("Search must never execute an offer"); }
            public Component describe() { return Component.literal(rewardName); }
            public String getType() { return "test"; }
        };
        return TradeEntryBuilder.create(id).displayName(name).category(category).reward(reward).build();
    }
}

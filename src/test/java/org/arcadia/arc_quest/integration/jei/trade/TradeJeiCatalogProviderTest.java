package org.arcadia.arc_quest.integration.jei.trade;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.dialogue.api.CooldownType;
import org.arcadia.arc_quest.integration.jei.api.JeiCatalogEntry;
import org.arcadia.arc_quest.quest.api.ICondition;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.trade.api.ITradeOffer;
import org.arcadia.arc_quest.trade.api.TradeEntry;
import org.arcadia.arc_quest.trade.api.TradeShopDefinition;
import org.arcadia.arc_quest.trade.api.TradeText;
import org.arcadia.arc_quest.trade.offer.CompositeTradeOffer;
import org.arcadia.arc_quest.trade.registry.TradeRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.*;

class TradeJeiCatalogProviderTest {
    @BeforeEach @AfterEach void resetRegistry() { TradeRegistry.clearAll(); }

    @Test void visibilityIsDistinctFromPurchaseQualification() {
        TradeRegistry.registerDatapack(shop(null, entry("secret", ICondition.flagSet("reveal"), null, null),
                entry("locked", null, ICondition.flagSet("permission"), null)));
        var rows = collect(data());
        assertEquals(1, rows.size());
        assertEquals("locked", rows.get(0).navigationDetail());
        assertTrue(hasNote(rows.get(0), "arc_quest.jei.qualification_unmet"));
    }

    @Test void lockedOrBrokenShopAndBrokenEntryFailClosed() {
        ICondition broken = (player, quests, flags, variables) -> { throw new IllegalStateException("Test failure"); };
        TradeRegistry.registerDatapack(shop(ICondition.flagSet("open"), entry("hidden", null, null, null)));
        assertTrue(collect(data()).isEmpty());
        TradeRegistry.registerDatapack(shop(broken, entry("hidden", null, null, null)));
        assertTrue(collect(data()).isEmpty());
        TradeRegistry.registerDatapack(shop(null, entry("broken", broken, null, null), entry("good", null, null, null)));
        assertEquals("good", collect(data()).get(0).navigationDetail());
    }

    @Test void dueResetAndCompositePreviewNeverSpendGrantOrMutateStore() {
        var data = data();
        data.getTradeDataStore().incrementPurchase("test:shop", "limited");
        data.getTradeDataStore().incrementPurchase("test:shop", "limited");
        TradeRegistry.registerDatapack(shop(null, entry("limited", null, null, player -> true)));
        var row = collect(data).get(0);
        assertEquals(2, data.getTradeDataStore().getPurchaseCount("test:shop", "limited"));
        assertTrue(hasNote(row, "arc_quest.jei.reset_due"));
        assertFalse(hasNote(row, "arc_quest.jei.state_unavailable"));
        assertTrue(hasNote(row, "arc_quest.jei.cost_detail"));
        assertEquals(2L, row.notes().stream().filter(note -> note.getContents() instanceof TranslatableContents content
                && content.getKey().equals("arc_quest.jei.reward_detail")).count());
        assertTrue(row.outputs().isEmpty(), "Arbitrary descriptions are not item outputs");
    }

    @Test void freshCollectionReflectsRegistryReplacementWithoutStaleRows() {
        TradeRegistry.registerDatapack(shop(null, entry("old", null, null, null)));
        assertEquals("old", collect(data()).get(0).navigationDetail());
        TradeRegistry.replaceDatapackSnapshot(Map.of("test:shop", shop(null, entry("new", null, null, null))));
        var next = collect(data());
        assertEquals(1, next.size());
        assertEquals("new", next.get(0).navigationDetail());
        TradeRegistry.replaceDatapackSnapshot(Map.of());
        assertTrue(collect(data()).isEmpty());
    }

    private static ArrayList<JeiCatalogEntry> collect(ArcQuestPlayer data) {
        var rows = new ArrayList<JeiCatalogEntry>();
        new TradeJeiCatalogProvider().collect(null, data, rows::add);
        return rows;
    }
    private static boolean hasNote(JeiCatalogEntry row, String key) {
        return row.notes().stream().anyMatch(note -> note.getContents() instanceof TranslatableContents contents && contents.getKey().equals(key));
    }
    private static ArcQuestPlayer data() { return new ArcQuestPlayer(UUID.randomUUID()); }
    private static TradeShopDefinition shop(ICondition open, TradeEntry... entries) {
        var byId = new LinkedHashMap<String, TradeEntry>();
        for (var entry : entries) byId.put(entry.getEntryId(), entry);
        return new TradeShopDefinition("test:shop", TradeText.literal("Shop"), null, List.of(), byId,
                open, false, -1, null, null);
    }
    private static TradeEntry entry(String id, ICondition visible, ICondition purchase, Predicate<ServerPlayer> reset) {
        return new TradeEntry(id, TradeText.literal(id), null, List.of(offer("cost")),
                List.of(new CompositeTradeOffer(List.of(offer("reward one"), offer("reward two")))),
                null, visible, purchase, CooldownType.NONE, 0, 0, 2, null, null, 0, -1, reset,
                null, null, null, null, null);
    }
    private static ITradeOffer offer(String name) {
        return new ITradeOffer() {
            public boolean canAfford(ServerPlayer player) { throw new AssertionError("Preview does not invoke payment gates"); }
            public void execute(ServerPlayer player) { throw new AssertionError("Preview cannot execute offers"); }
            public Component describe() { return Component.literal(name); }
            public String getType() { return "test"; }
        };
    }
}

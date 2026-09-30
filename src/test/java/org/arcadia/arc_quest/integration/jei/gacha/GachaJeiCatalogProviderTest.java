package org.arcadia.arc_quest.integration.jei.gacha;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.dialogue.api.CooldownType;
import org.arcadia.arc_quest.integration.jei.api.JeiCatalogEntry;
import org.arcadia.arc_quest.quest.api.ICondition;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.trade.api.ITradeOffer;
import org.arcadia.arc_quest.trade.api.TradeText;
import org.arcadia.arc_quest.trade.gacha.api.GachaItem;
import org.arcadia.arc_quest.trade.gacha.api.GachaPool;
import org.arcadia.arc_quest.trade.gacha.api.GachaShopDefinition;
import org.arcadia.arc_quest.trade.gacha.registry.GachaRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class GachaJeiCatalogProviderTest {
    @BeforeEach @AfterEach void resetRegistry() { GachaRegistry.clearAll(); }

    @Test void hiddenPoolsAndOutcomesNeverAppear() {
        GachaRegistry.registerDatapack(shop(ICondition.flagSet("open"), null, List.of(item("public", true))));
        assertTrue(collect(data()).isEmpty());
        GachaRegistry.registerDatapack(shop(null, null, List.of(item("secret", false), item("public", true))));
        var rows = collect(data());
        assertEquals(1, rows.size());
        assertEquals("public", rows.get(0).navigationDetail());
    }
    @Test void brokenWeightCalculationOmitsEntirePoolRatherThanInventingOdds() {
        GachaItem bad = new GachaItem("bad", null, offer(), 10, GachaItem.Rarity.COMMON, true) {
            @Override public int getEffectiveWeight(ServerPlayer player, ArcQuestPlayer data) { throw new IllegalStateException("Test failure"); }
        };
        GachaRegistry.registerDatapack(shop(null, null, List.of(item("good", true), bad)));
        assertTrue(collect(data()).isEmpty());
    }
    @Test void nonItemRewardHasNoInventedDecorativeOutputAndPreviewDoesNotResetDraws() {
        var data = data();
        data.incrementGachaDrawCount("test:gacha");
        data.incrementGachaDrawCount("test:gacha");
        data.setGachaPityCounter("test:gacha", 7);
        GachaRegistry.registerDatapack(shop(null, ICondition.always(), List.of(item("custom", true))));
        var row = collect(data).get(0);
        assertTrue(row.outputs().isEmpty());
        assertTrue(hasNote(row, "arc_quest.jei.gacha_offer_once"));
        assertTrue(hasNote(row, "arc_quest.jei.reset_due"));
        assertEquals(2, data.getGachaDrawCount("test:gacha"));
        assertEquals(7, data.getGachaPityCounter("test:gacha"));
    }
    @Test void probabilityPresentationDoesNotRoundPossibleRareOutcomesToZero() {
        assertNotEquals("0", GachaJeiCatalogProvider.percent(100D / (1000L * Integer.MAX_VALUE)));
        assertEquals("0", GachaJeiCatalogProvider.percent(0D));
        assertEquals("100", GachaJeiCatalogProvider.percent(100D));
    }
    private static ArrayList<JeiCatalogEntry> collect(ArcQuestPlayer data) {
        var rows = new ArrayList<JeiCatalogEntry>();
        new GachaJeiCatalogProvider().collect(null, data, rows::add);
        return rows;
    }
    private static boolean hasNote(JeiCatalogEntry row, String key) {
        return row.notes().stream().anyMatch(note -> note.getContents() instanceof TranslatableContents contents && contents.getKey().equals(key));
    }
    private static ArcQuestPlayer data() { return new ArcQuestPlayer(UUID.randomUUID()); }
    private static GachaItem item(String id, boolean visible) {
        return new GachaItem(id, null, offer(), 10, GachaItem.Rarity.COMMON, true, 3, 9,
                (player, quests, flags, variables) -> visible, null, -1, null, 0) {
            @Override public int calculateActualCount() { throw new AssertionError("Preview cannot roll"); }
        };
    }
    private static ITradeOffer offer() {
        return new ITradeOffer() {
            public boolean canAfford(ServerPlayer player) { throw new AssertionError("Preview does not run payment gates"); }
            public void execute(ServerPlayer player) { throw new AssertionError("Preview cannot grant or spend"); }
            public Component describe() { return Component.literal("Custom effect"); }
            public String getType() { return "test"; }
        };
    }
    @SuppressWarnings("deprecation")
    private static GachaShopDefinition shop(ICondition open, ICondition reset, List<GachaItem> items) {
        return new GachaShopDefinition("test:gacha", TradeText.literal("Pool"), null, List.of(),
                new LinkedHashMap<>(), open, false, -1, null, null, new GachaPool(items),
                List.of(offer()), CooldownType.NONE, 0, 0, null, 2, reset, true, true,
                null, null, null, null, null);
    }
}

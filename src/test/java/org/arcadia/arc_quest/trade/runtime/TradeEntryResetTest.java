package org.arcadia.arc_quest.trade.runtime;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.dialogue.api.CooldownType;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.trade.api.ITradeOffer;
import org.arcadia.arc_quest.trade.api.TradeEntry;
import org.arcadia.arc_quest.trade.api.TradeText;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class TradeEntryResetTest {
    @Test
    void expiredLimitedPurchaseResetsWithoutCustomPredicate() {
        ArcQuestPlayer data = purchased(2);
        data.getTradeDataStore().recordCooldown("shop", "entry", 1, 0, 0);
        assertTrue(TradeEntryStateResolver.resetIfNeeded(null, data, "shop", entry(CooldownType.SECONDS, 2, null)));
        assertEquals(0, data.getTradeDataStore().getPurchaseCount("shop", "entry"));
        assertFalse(data.getTradeDataStore().getCooldown("shop", "entry").exists());
    }

    @Test
    void unexpiredCooldownPreservesPurchaseCountAndTimestamp() {
        ArcQuestPlayer data = purchased(2);
        long now = System.currentTimeMillis();
        data.getTradeDataStore().recordCooldown("shop", "entry", now, 12, 8);
        assertFalse(TradeEntryStateResolver.resetIfNeeded(null, data, "shop", entry(CooldownType.SECONDS, 2, null)));
        assertEquals(2, data.getTradeDataStore().getPurchaseCount("shop", "entry"));
        assertEquals(now, data.getTradeDataStore().getCooldown("shop", "entry").realTime());
    }

    @Test
    void expiredCooldownDoesNotResetLimitBeforeItWasReached() {
        ArcQuestPlayer data = purchased(1);
        data.getTradeDataStore().recordCooldown("shop", "entry", 1, 0, 0);
        assertFalse(TradeEntryStateResolver.resetIfNeeded(null, data, "shop", entry(CooldownType.SECONDS, 2, null)));
        assertEquals(1, data.getTradeDataStore().getPurchaseCount("shop", "entry"));
    }

    @Test
    void unlimitedEntryStillClearsExpiredCooldown() {
        ArcQuestPlayer data = purchased(3);
        data.getTradeDataStore().recordCooldown("shop", "entry", 1, 0, 0);
        assertTrue(TradeEntryStateResolver.resetIfNeeded(null, data, "shop", entry(CooldownType.SECONDS, -1, null)));
        assertFalse(data.getTradeDataStore().getCooldown("shop", "entry").exists());
    }

    @Test
    void customResetAppliesWithoutCooldownAndStopsWhenCounterIsEmpty() {
        ArcQuestPlayer data = purchased(1);
        int[] calls = {0};
        TradeEntry entry = entry(CooldownType.NONE, 2, player -> { calls[0]++; return true; });
        assertTrue(TradeEntryStateResolver.resetIfNeeded(null, data, "shop", entry));
        assertFalse(TradeEntryStateResolver.resetIfNeeded(null, data, "shop", entry));
        assertEquals(1, calls[0]);
    }

    private static ArcQuestPlayer purchased(int count) {
        ArcQuestPlayer data = new ArcQuestPlayer(UUID.randomUUID());
        for (int i = 0; i < count; i++) data.getTradeDataStore().incrementPurchase("shop", "entry");
        return data;
    }

    private static TradeEntry entry(CooldownType cooldown, int limit, Predicate<ServerPlayer> reset) {
        ITradeOffer reward = new ITradeOffer() {
            public boolean canAfford(ServerPlayer player) { return true; }
            public void execute(ServerPlayer player) { fail("Reset cannot grant rewards"); }
            public Component describe() { return Component.empty(); }
            public String getType() { return "test"; }
        };
        return new TradeEntry("entry", TradeText.literal("entry"), null, List.of(), List.of(reward),
                null, null, null, cooldown, 60, 0, limit, null, null, 0, -1, reset,
                null, null, null, null, null);
    }
}

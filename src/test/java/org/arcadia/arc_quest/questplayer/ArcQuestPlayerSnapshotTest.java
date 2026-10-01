package org.arcadia.arc_quest.questplayer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerData;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ArcQuestPlayerSnapshotTest {
    @Test
    void loadingLegacyDataDoesNotModifyTheCallersNbt() {
        CompoundTag legacy = new CompoundTag();
        legacy.putString("TrackedQuestId", "arc_quest:legacy");
        CompoundTag before = legacy.copy();
        ArcQuestPlayer player = player();
        player.deserializeNBT(legacy);
        assertEquals(before, legacy);
        assertEquals(ArcQuestPlayer.getCurrentDataVersion(), player.serializeNBT().getInt("_ArcQuestVer"));
    }

    @Test
    void invalidVersionsPreserveDataDirtyStateAndStoreIdentity() {
        ArcQuestPlayer target = populated();
        CompoundTag before = target.serializeNBT();
        var dialogue = target.getDialogueProgress();
        for (int version : new int[]{-1, ArcQuestPlayer.getCurrentDataVersion() + 1}) {
            CompoundTag invalid = new CompoundTag();
            invalid.putInt("_ArcQuestVer", version);
            assertThrows(RuntimeException.class, () -> target.deserializeNBT(invalid));
            assertEquals(before, target.serializeNBT());
            assertTrue(target.isDirty());
            assertSame(dialogue, target.getDialogueProgress());
        }
        CompoundTag wrongType = new CompoundTag();
        wrongType.putString("_ArcQuestVer", "4");
        assertThrows(IllegalArgumentException.class, () -> target.deserializeNBT(wrongType));
        assertEquals(before, target.serializeNBT());
    }

    @Test
    void loadingEmptyCurrentDataClearsAllStoresAndDirtyFlags() {
        ArcQuestPlayer target = populated();
        var dialogue = target.getDialogueProgress();
        var trade = target.getTradeDataStore();
        var gacha = target.getGachaDataStore();
        var markers = target.getAllMarkers();
        CompoundTag empty = new CompoundTag();
        empty.putInt("_ArcQuestVer", ArcQuestPlayer.getCurrentDataVersion());
        target.deserializeNBT(empty);
        assertEquals(player().serializeNBT(), target.serializeNBT());
        assertFalse(target.isDirty());
        assertTrue(markers.isEmpty());
        assertSame(dialogue, target.getDialogueProgress());
        assertSame(trade, target.getTradeDataStore());
        assertSame(gacha, target.getGachaDataStore());
    }

    @Test
    void markerSnapshotIndexAndPublicViewAreBothReplaced() {
        ArcQuestPlayer target = populated();
        var view = target.getAllMarkers();
        ArcQuestPlayer source = player();
        source.upsertMarker(QuestMarkerData.location("new", 1, 2, 3, "new"));
        target.copyFrom(source);
        assertEquals(source.serializeNBT(), target.serializeNBT());
        assertEquals(source.getAllMarkers().keySet(), view.keySet());
        target.removeMarker("new");
        assertTrue(target.serializeNBT().getList("Markers", 10).isEmpty());
    }

    @Test
    void copiedMutableStoreCollectionsAreIndependent() {
        ArcQuestPlayer source = populated();
        ArcQuestPlayer target = player();
        target.copyFrom(source);
        CompoundTag before = source.serializeNBT();
        target.getTradeDataStore().incrementPurchase("shop", "entry");
        target.getGachaDataStore().clearDrawHistory("shop");
        target.getDialogueProgress().clear();
        assertEquals(before, source.serializeNBT());
        assertNotEquals(before, target.serializeNBT());
    }

    private static ArcQuestPlayer populated() {
        ArcQuestPlayer player = player();
        player.getDialogueProgress().recordNodeVisit("arc_quest", "node", 1, 2, 3);
        player.getTradeDataStore().incrementPurchase("shop", "entry");
        player.incrementGachaDrawCount("shop");
        player.addGachaDrawHistory("shop", "minecraft:stone", "common", 1, false, 100);
        player.unlockGuide(java.util.Objects.requireNonNull(ResourceLocation.tryParse("arc_quest:test")));
        player.upsertMarker(QuestMarkerData.location("old", 0, 0, 0, "old"));
        player.consumeOneShotMarker("consumed");
        return player;
    }

    private static ArcQuestPlayer player() { return new ArcQuestPlayer(UUID.randomUUID()); }
}

package org.arcadia.arc_quest.quest.logic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import org.arcadia.arc_quest.questplayer.capability.ArcQuestPlayerCapability;
import org.arcadia.arc_quest.testsupport.MinecraftRegistryTestBootstrap;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CollectionRewardDeliveryTest {
    @BeforeAll static void setup() { MinecraftRegistryTestBootstrap.initialize(); }
    @Test void fullInventoryIsRetainedAndPartialCapacityCannotAuthorizeWholeChunkDelivery() {
        List<ItemStack> slots = new ArrayList<>(); for (int i = 0; i < 36; i++) slots.add(new ItemStack(Items.STONE, 64));
        assertFalse(CollectionRewardDelivery.fits(slots, new ItemStack(Items.COAL, 5)));
        slots.set(0, new ItemStack(Items.COAL, 62));
        assertFalse(CollectionRewardDelivery.fits(slots, new ItemStack(Items.COAL, 5)));
        slots.set(1, new ItemStack(Items.COAL, 61));
        assertTrue(CollectionRewardDelivery.fits(slots, new ItemStack(Items.COAL, 5)));
        assertEquals(62, slots.get(0).getCount(), "Capacity checking cannot insert or discard the pending reward");
    }
    @Test void resetGenerationRevokesPermanentDeliveryIdentityButOtherRunPaymentRemainsIndependent() {
        var entry = ResourceLocation.parse("example:entry");
        assertNotEquals(CollectionRewardDelivery.identity(entry, 1, "q", "", "p", "b", "reward"), CollectionRewardDelivery.identity(entry, 2, "q", "", "p", "b", "reward"));
        assertEquals(CollectionRewardDelivery.identity(entry, 1, "q", "run", "p", "b", "reward"), CollectionRewardDelivery.identity(entry, 2, "q", "run", "p", "b", "reward"));
    }
    @Test void vanillaDeliveryTokensSurvivePlayerCloneButProgressSnapshotCannotForgeThem() {
        UUID token = UUID.randomUUID(); var capability = new ArcQuestPlayerCapability(); capability.recordDeliveredCollection(token);
        var restored = new ArcQuestPlayerCapability(); restored.deserializeNBT(capability.serializeNBT()); assertTrue(restored.hasDeliveredCollection(token));
        var imported = new ArcQuestPlayerCapability(); imported.replaceSnapshot(capability.serializeNBT()); assertFalse(imported.hasDeliveredCollection(token));
        imported.copyDeliveryReceiptsFrom(restored); assertTrue(imported.hasDeliveredCollection(token));
        CompoundTag malformed = restored.serializeNBT(); malformed.getCompound("DeliveredCollectionReceipts").putBoolean("invalid", true);
        assertThrows(IllegalArgumentException.class, () -> restored.deserializeNBT(malformed)); assertTrue(restored.hasDeliveredCollection(token));
    }
}

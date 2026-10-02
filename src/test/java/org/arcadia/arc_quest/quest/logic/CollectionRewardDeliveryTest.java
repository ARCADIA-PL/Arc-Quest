package org.arcadia.arc_quest.quest.logic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import org.arcadia.arc_quest.questplayer.attachment.ArcQuestPlayerAttachment;
import org.arcadia.arc_quest.questplayer.attachment.ArcQuestPlayerAttachmentSerializer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.quest.data.CollectionRewardEntitlement;
import org.arcadia.arc_quest.quest.data.CollectionRuntimeData;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.api.CollectionEntryRewardDefinition;
import org.arcadia.arc_quest.quest.api.CollectionEntryRewardTrigger;
import org.arcadia.arc_quest.quest.api.EntryRewardGrantMode;
import org.arcadia.arc_quest.quest.reward.ItemReward;
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
        UUID token = UUID.randomUUID(); var capability = ArcQuestPlayerAttachment.empty(); capability.recordDeliveredCollection(token);
        var serializer = new ArcQuestPlayerAttachmentSerializer();
        var restored = serializer.read(null, serializer.write(capability, null), null); assertTrue(restored.hasDeliveredCollection(token));
        CompoundTag progressImport = capability.snapshot();
        progressImport.put(ArcQuestPlayerAttachment.COLLECTION_RECEIPTS_KEY, capability.serializeCollectionDeliveryReceipts());
        var imported = ArcQuestPlayerAttachment.empty(); imported.replaceSnapshot(progressImport); assertFalse(imported.hasDeliveredCollection(token));
        assertFalse(imported.snapshot().contains(ArcQuestPlayerAttachment.COLLECTION_RECEIPTS_KEY));
        imported.copyDeliveryReceiptsFrom(restored); assertTrue(imported.hasDeliveredCollection(token));
        CompoundTag malformed = serializer.write(restored, null); malformed.getCompound("DeliveredCollectionReceipts").putBoolean("invalid", true);
        UUID untouchedDraw = UUID.randomUUID(); restored.recordDeliveredDraw(untouchedDraw);
        assertThrows(IllegalArgumentException.class, () -> restored.readDeliveryReceipts(malformed)); assertTrue(restored.hasDeliveredCollection(token));
        assertTrue(restored.serializeDeliveryReceipts().getBoolean(untouchedDraw.toString()), "A malformed collection container cannot partially replace draw receipts");
    }

    @Test void boundedDeliveryReceiptsCanRejectNewItemsBeforeInsertionAndOrphansAreReclaimed() {
        var capability = ArcQuestPlayerAttachment.empty();
        UUID unresolved = UUID.randomUUID(); capability.recordDeliveredCollection(unresolved);
        for (int index = 1; index < 8192; index++) capability.recordDeliveredCollection(UUID.randomUUID());
        assertFalse(capability.canRecordDeliveredCollection(UUID.randomUUID()));
        assertTrue(capability.canRecordDeliveredCollection(unresolved));
        capability.retainCollectionDeliveryReceipts(Set.of(unresolved));
        assertTrue(capability.hasDeliveredCollection(unresolved));
        assertEquals(1, capability.serializeCollectionDeliveryReceipts().size());
        assertTrue(capability.canRecordDeliveredCollection(UUID.randomUUID()));
        capability.retainCollectionDeliveryReceipts(Set.of());
        assertTrue(capability.serializeCollectionDeliveryReceipts().isEmpty());
    }

    @Test void collectionReceiptOnlyAttachmentSurvivesProgressClearAndPlayerSerialization() {
        var attachment = ArcQuestPlayerAttachment.empty();
        var serializer = new ArcQuestPlayerAttachmentSerializer();
        UUID token = UUID.randomUUID(); attachment.recordDeliveredCollection(token);
        attachment.clear();
        assertTrue(attachment.isEmpty());
        CompoundTag encoded = serializer.write(attachment, null);
        assertNotNull(encoded, "Collection receipts must keep an otherwise empty attachment serializable");
        var loaded = serializer.read(null, encoded, null);
        assertTrue(loaded.hasDeliveredCollection(token));
        loaded.acknowledgeDeliveredCollection(token);
        assertNull(serializer.write(loaded, null));
    }

    @Test void onlyMarkedOriginalAuthorizationCanRestoreMissingRunReceipt() {
        var data = new ArcQuestPlayer(UUID.randomUUID());
        var run = new QuestRuntimeData("example:authorized_run", "survey", 1, 0, 0, 0);
        run.setFrozenDefinition("1".repeat(64), "test-v1");
        run.setCollectionData(new CollectionRuntimeData());
        run.getCollectionData().initializeSheet("survey", List.of("binding"), 1, Set.of());
        data.addActiveQuest(run);
        var entry = ResourceLocation.parse("example:authorized_entry");
        var reward = new CollectionEntryRewardDefinition("payment", CollectionEntryRewardTrigger.BINDING_COMPLETE,
                EntryRewardGrantMode.AUTO, List.of(new ItemReward(Items.DIAMOND, 70)));
        CompoundTag grant = new CompoundTag();
        grant.putString("EntryId", entry.toString()); grant.putLong("Generation", 0);
        grant.putString("QuestId", run.getQuestId()); grant.putString("RunId", run.getCollectionData().getRunId());
        grant.putString("PhaseId", "survey"); grant.putString("BindingId", "binding"); grant.putString("RewardId", "payment");
        grant.put("Entitlement", CollectionRewardEntitlement.capture(reward, run.getQuestId(), run.getFrozenDefinitionHash(), "survey", "binding", entry));
        assertFalse(CollectionRewardDelivery.restoreAuthorizedClaim(data, grant), "Old journals cannot infer missing claim receipts");
        assertFalse(run.getCollectionData().isEntryRewardClaimed("survey", "binding", "payment"));
        grant.putInt("AuthorizationFormat", 1); grant.putBoolean("ClaimAuthorized", true);
        assertTrue(CollectionRewardDelivery.restoreAuthorizedClaim(data, grant));
        assertTrue(run.getCollectionData().isEntryRewardClaimed("survey", "binding", "payment"));
        assertTrue(run.getCollectionData().isEntryRewardDeliveryPending("survey", "binding", "payment"));
        assertEquals(0, run.getObjectiveProgress("survey", 0), "Authorization restores an owed receipt, not arbitrary investigation progress");
        data.resetQuest(run.getQuestId());
        assertFalse(CollectionRewardDelivery.restoreAuthorizedClaim(data, grant), "Reset run identities revoke even explicitly marked authorizations");
    }

    @Test void markedAuthorizationCannotRestoreAResetPermanentGeneration() {
        var data = new ArcQuestPlayer(UUID.randomUUID());
        var entry = ResourceLocation.parse("example:reset_authorized_entry");
        var reward = new CollectionEntryRewardDefinition("first_record", CollectionEntryRewardTrigger.DISCOVERED,
                EntryRewardGrantMode.AUTO, List.of(new ItemReward(Items.COAL, 1)));
        CompoundTag grant = new CompoundTag();
        grant.putInt("AuthorizationFormat", 1); grant.putBoolean("ClaimAuthorized", true);
        grant.putString("EntryId", entry.toString()); grant.putLong("Generation", 0); grant.putString("RewardId", "first_record");
        grant.put("Entitlement", CollectionRewardEntitlement.capture(reward, "", "", "", "", entry));
        data.getCollectionRecords().resetQuest("", Set.of(entry));
        assertFalse(CollectionRewardDelivery.restoreAuthorizedClaim(data, grant));
        assertFalse(data.getCollectionRecords().isRewardClaimed(entry, "first_record"));
    }
}

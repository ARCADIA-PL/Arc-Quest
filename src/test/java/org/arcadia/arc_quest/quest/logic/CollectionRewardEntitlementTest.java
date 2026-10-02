package org.arcadia.arc_quest.quest.logic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.builder.*;
import org.arcadia.arc_quest.quest.data.*;
import org.arcadia.arc_quest.quest.reward.*;
import org.arcadia.arc_quest.testsupport.MinecraftRegistryTestBootstrap;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CollectionRewardEntitlementTest {
    static final ResourceLocation ENTRY = ResourceLocation.parse("example:entitlement");
    @BeforeAll static void setup() { MinecraftRegistryTestBootstrap.initialize(); }

    @Test void serverSnapshotsPreserveRealCommandAndAmountsWhilePacketsContainOnlyWhitelistedItemPresentation() {
        var original = new CollectionEntryRewardDefinition("first", CollectionEntryRewardTrigger.DISCOVERED, List.of(
                new ItemReward(Items.COAL, 5), new CommandReward("give {player} minecraft:diamond 2"), FlagReward.set("private")));
        CompoundTag snapshot = CollectionRewardEntitlement.capture(original, "example:quest", "", "", "", ENTRY);
        var restored = CollectionRewardEntitlement.restore(snapshot, null);
        assertEquals(5, ((ItemReward) restored.rewards().get(0)).getCount());
        assertEquals("give {player} minecraft:diamond 2", ((CommandReward) restored.rewards().get(1)).getCommandTemplate());
        CompoundTag payload = snapshot.getList("Payload", 10).getCompound(0); payload.putString("Command", "secret");
        var safe = CollectionRewardEntitlement.presentation(snapshot);
        assertFalse(safe.toString().contains("secret")); assertFalse(safe.toString().contains("diamond"));
        assertFalse(safe.contains("DefinitionHash")); assertFalse(safe.toString().contains("private"));
        assertEquals(1, CollectionRewardEntitlement.presentationDefinition(safe).rewards().size());
    }

    @Test void removedAndChangedPermanentRewardsKeepTheirOriginalEntitlementAcrossReloadAndResetRevokesIt() {
        var records = new CollectionRecordState(); records.discover(ENTRY); records.unlockReward(ENTRY, "removed");
        var original = new CollectionEntryRewardDefinition("removed", CollectionEntryRewardTrigger.DISCOVERED, List.of(new ItemReward(Items.COAL, 5)));
        records.snapshotReward(ENTRY, "removed", CollectionRewardEntitlement.capture(original, "", "", "", "", ENTRY));
        var replacement = new CollectionEntryRewardDefinition("removed", CollectionEntryRewardTrigger.DISCOVERED, List.of(new ItemReward(Items.COAL, 99)));
        assertFalse(records.snapshotReward(ENTRY, "removed", CollectionRewardEntitlement.capture(replacement, "", "", "", "", ENTRY)));
        var reloaded = new CollectionRecordState(); reloaded.readSnapshot(records.serializeNBT());
        var entry = CollectionEntryBuilder.create(ENTRY).category("field").build();
        var phase = PhaseBuilder.create("survey").collectionSheet(CollectionSheetBuilder.create().binding(EntryRequirementBuilder.create("entry", ENTRY).discovered())).build();
        var quest = QuestBuilder.create("example:entitlement_quest").mode(QuestMode.COLLECTION)
                .collectionConfig(CollectionQuestConfigBuilder.create().category("field", "Field").entry(entry).build()).phase(phase).build();
        var rows = CollectionEntryRewardService.project(quest, phase, null, reloaded, phase.getCollectionSheet().getBinding("entry"));
        assertEquals(1, rows.size()); assertTrue(rows.get(0).canClaim());
        assertEquals(5, ((ItemReward) rows.get(0).definition().rewards().get(0)).getCount());
        reloaded.resetQuest(quest.getId().toString(), Set.of(ENTRY));
        assertTrue(reloaded.getRecord(ENTRY).getRewardEntitlementIds().isEmpty());
    }

    @Test void owedRunRetainsOriginalPhaseBindingAndAmountEvenAfterLiveNodesAreRenamed() {
        var old = new QuestRuntimeData("example:owed_renamed", "old_phase", 1, 0, 0, 0);
        var run = old.getOrCreateCollectionData(); run.initializeSheet("old_phase", List.of("old_binding"), 1, Set.of());
        run.markBindingComplete("old_phase", "old_binding"); run.unlockEntryReward("old_phase", "old_binding", "old_pay");
        run.snapshotEntryReward("old_phase", "old_binding", "old_pay", CollectionRewardEntitlement.capture(
                new CollectionEntryRewardDefinition("old_pay", CollectionEntryRewardTrigger.BINDING_COMPLETE, List.of(new ItemReward(Items.EMERALD, 2))),
                old.getQuestId(), "", "old_phase", "old_binding", ENTRY));
        var archives = new CollectionQuestArchives(); archives.capture(old);
        var entry = CollectionEntryBuilder.create(ENTRY).category("field").build();
        var phase = PhaseBuilder.create("new_phase").collectionSheet(CollectionSheetBuilder.create().binding(EntryRequirementBuilder.create("new_binding", ENTRY).discovered())).build();
        var quest = QuestBuilder.create(old.getQuestId()).mode(QuestMode.COLLECTION)
                .collectionConfig(CollectionQuestConfigBuilder.create().category("field", "Field").entry(entry).build()).phase(phase).build();
        var rows = CollectionEntryRewardService.pendingPriorRewards(quest, phase, phase.getCollectionSheet().getBinding("new_binding"), null, new CollectionRecordState(), archives);
        assertEquals(1, rows.size()); assertEquals("old_phase", rows.get(0).sourcePhaseId()); assertEquals("old_binding", rows.get(0).sourceBindingId());
        assertEquals(run.getRunId(), rows.get(0).sourceRunId()); assertEquals(2, ((ItemReward) rows.get(0).definition().rewards().get(0)).getCount());
    }
}

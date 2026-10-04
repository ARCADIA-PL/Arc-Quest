package org.arcadia.arc_quest.data.sync;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import org.arcadia.arc_quest.guide.api.GuideMediaDefinition;
import org.arcadia.arc_quest.guide.api.GuideMediaType;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.builder.*;
import org.arcadia.arc_quest.quest.data.CollectionRecordState;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.data.CollectionRewardEntitlement;
import org.arcadia.arc_quest.quest.reward.CommandReward;
import org.arcadia.arc_quest.quest.reward.FlagReward;
import org.arcadia.arc_quest.quest.reward.ItemReward;
import org.arcadia.arc_quest.quest.logic.profile.collection.CollectionProgressProjector;
import org.arcadia.arc_quest.quest.spec.QuestSpec;
import org.arcadia.arc_quest.quest.spec.compile.QuestSpecCompiler;
import org.arcadia.arc_quest.quest.spec.io.QuestSpecJsonReader;
import org.arcadia.arc_quest.quest.spec.io.QuestSpecJsonWriter;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.testsupport.MinecraftRegistryTestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CollectionContentDisclosureTest {
    private static final ResourceLocation ENTRY = ResourceLocation.parse("example:specimen");
    private static final ResourceLocation UNRELATED = ResourceLocation.parse("example:private_record");
    private static final ResourceLocation TARGET = ResourceLocation.parse("minecraft:nether_star");
    @BeforeAll static void setup() { MinecraftRegistryTestBootstrap.initialize(); }

    @Test void hiddenIdentityAndLockedImagesAreAbsentFromActualEncodedDocuments() throws Exception {
        var quest = quest(entry(true));
        var records = new CollectionRecordState();
        var projected = project(source(quest), quest, records, true);
        String wire = DatapackContentCodec.decode(DatapackContentCodec.encode(projected)).documents(DatapackContentModule.QUEST).get(0);
        for (String secret : List.of("Secret specimen", TARGET.toString(), "Private introduction", "Secret research", "private_image", "minecraft:diamond")) {
            assertFalse(wire.contains(secret), secret);
        }
        var compiled = QuestSpecCompiler.compileClientPresentation(QuestSpecJsonReader.read(wire), Map.of());
        assertTrue(compiled.hasCollectionSheets());
        assertEquals(5, compiled.getCollectionConfig().getEntry(ENTRY).getResearchObjectives().get(0).getRequiredCount());
        assertEquals(0, QuestSpecJsonReader.read(wire).phases.get(0).collectionSheet.requiredCount);
    }

    @Test void anonymousEntriesKeepAuthorOrderingThroughWireEncodingWithoutRevealingTheirIdentity() throws Exception {
        var spec = CollectionDefinitionSpecExporter.quest(quest(entry(true)), null);
        spec.collectionConfig.categories.get(0).sortOrder = -30;
        spec.collectionConfig.entries.get(0).sortOrder = -20;
        spec.phases.get(0).collectionSheet.bindings.get(0).sortOrder = -10;
        var serverDefinition = new QuestSpecCompiler().compile(spec);
        var projected = project(source(serverDefinition), serverDefinition, new CollectionRecordState(), true);
        String wire = DatapackContentCodec.decode(DatapackContentCodec.encode(projected))
                .documents(DatapackContentModule.QUEST).get(0);
        var presentation = QuestSpecCompiler.compileClientPresentation(QuestSpecJsonReader.read(wire), Map.of());
        assertEquals(-30, presentation.getCollectionConfig().getCategories().get(0).getSortOrder());
        assertEquals(-20, presentation.getCollectionConfig().getEntry(ENTRY).getSortOrder());
        assertEquals(-10, presentation.getPhase("survey").getCollectionSheet().getBindings().get(0).getSortOrder());
        for (String secret : List.of("Secret specimen", TARGET.toString(), "Private introduction", "private_image"))
            assertFalse(wire.contains(secret), secret);
        assertEquals(Set.of("survey"), serverDefinition.getPhaseIds());
    }

    @Test void discoveringRevealsIdentityButKeepsResearchImagesPrivateForOtherPlayers() {
        var quest = quest(entry(true)); var discovered = new CollectionRecordState(); discovered.discover(ENTRY);
        String first = json(project(source(quest), quest, discovered, true));
        assertTrue(first.contains("Secret specimen")); assertTrue(first.contains("Private introduction"));
        assertFalse(first.contains("private_image")); assertFalse(first.contains("Secret research"));
        assertFalse(json(project(source(quest), quest, new CollectionRecordState(), true)).contains("Secret specimen"));
        assertFalse(json(project(source(quest), quest, discovered, false)).contains("Secret specimen"));
        discovered.increment(ENTRY, CollectionProgressProjector.researchKey("study"), 5, 5);
        String unlocked = json(project(source(quest), quest, discovered, true));
        assertTrue(unlocked.contains("private_image")); assertTrue(unlocked.contains("Secret research"));
    }

    @Test void publicDefaultEntriesStillRequireAKnownTaskAndDiscoveryToRevealContent() {
        var quest = quest(entry(false)); var records = new CollectionRecordState();
        String known = json(project(source(quest), quest, records, true));
        assertTrue(known.contains("Secret specimen")); assertFalse(known.contains("Private introduction"));
        assertFalse(json(project(source(quest), quest, records, false)).contains("Secret specimen"));
    }

    @Test void partialCountsAndSeenStatusKeepTheSameAuthorizationGrantAndPresentation() {
        var entry = entry(true); var quest = quest(entry); var records = new CollectionRecordState(); records.discover(ENTRY);
        var grant = DatapackContentSyncService.DisclosureGrant.of(entry, records);
        var before = project(source(quest), quest, records, true);
        records.increment(ENTRY, CollectionProgressProjector.researchKey("study"), 1, 5);
        records.markSeen(ENTRY, "intro");
        assertEquals(grant, DatapackContentSyncService.DisclosureGrant.of(entry, records));
        assertEquals(before, project(source(quest), quest, records, true));
        records.increment(ENTRY, CollectionProgressProjector.researchKey("study"), 4, 5);
        assertNotEquals(grant, DatapackContentSyncService.DisclosureGrant.of(entry, records));
    }

    @Test void sharedEntriesAreInlinedWithoutAnUnfilteredRegistryFallback() {
        var quest = quest(entry(true)); var spec = CollectionDefinitionSpecExporter.quest(quest, null);
        spec.collectionConfig.entries.clear(); spec.collectionConfig.entryIds.add(ENTRY.toString());
        var source = new DatapackContentSnapshot(9, Map.of(DatapackContentModule.QUEST, List.of(QuestSpecJsonWriter.write(spec))));
        var projected = QuestSpecJsonReader.read(json(project(source, quest, new CollectionRecordState(), true)));
        assertTrue(projected.collectionConfig.entryIds.isEmpty());
        assertEquals(1, projected.collectionConfig.entries.size());
        assertFalse(QuestSpecJsonWriter.write(projected).contains("Secret specimen"));
    }

    @Test void javaQuestSupplementUsesTheAuthorizedChannelAndPreservesAllSheetSemantics() {
        var quest = quest(entry(true));
        var projected = CollectionContentDisclosure.project(DatapackContentSnapshot.empty(5), new CollectionRecordState(),
                ignored -> true, ignored -> quest, null, List.of(quest));
        assertEquals(1, projected.documents(DatapackContentModule.QUEST).size());
        QuestSpec spec = QuestSpecJsonReader.read(json(projected));
        assertEquals(0, spec.phases.get(0).collectionSheet.requiredCount);
        assertEquals(1, QuestSpecCompiler.compileClientPresentation(spec, Map.of()).getPhase("survey").getCollectionSheet().getRequiredCount());
    }

    @Test void recordDeltaDropsUnknownEntriesAndUnauthorizedSeenBlocksWithoutMutatingServerFacts() {
        var quest = quest(entry(true)); var player = player(quest); var records = player.getCollectionRecords();
        records.discover(ENTRY); records.discover(UNRELATED); records.markLegacyMigrated("private/server/key");
        records.increment(ENTRY, CollectionProgressProjector.researchKey("study"), 1, 5);
        records.increment(ENTRY, "private:obsolete_step", 1, 1);
        records.markSeen(ENTRY, "intro"); records.markSeen(ENTRY, "research"); records.markSeen(ENTRY, "obsolete_secret");
        CompoundTag original = records.serializeNBT(), copy = original.copy();
        original.putString("LegacyRewardReceipts", "private reward receipts"); copy = original.copy();
        CompoundTag filtered = CollectionContentDisclosure.sanitizeRecordSnapshot(player, original, ignored -> quest);
        assertEquals(copy, original);
        assertFalse(filtered.contains("MigratedLegacyEntries"), "server migration bookkeeping must stay private");
        assertFalse(filtered.contains("LegacyRewardReceipts"), "server reward receipts must stay private");
        assertEquals(records.getRevision(), filtered.getLong("Revision"));
        assertEquals(Set.of(ENTRY.toString()), filtered.getCompound("Entries").getAllKeys());
        assertEquals(Set.of(CollectionProgressProjector.researchKey("study")), filtered.getCompound("Entries").getCompound(ENTRY.toString()).getCompound("Steps").getAllKeys());
        assertEquals(1, filtered.getCompound("Entries").getCompound(ENTRY.toString()).getList("Seen", Tag.TAG_STRING).size());
        assertEquals("intro", filtered.getCompound("Entries").getCompound(ENTRY.toString()).getList("Seen", Tag.TAG_STRING).getString(0));
        assertTrue(records.isDiscovered(UNRELATED)); assertTrue(records.getRecord(ENTRY).isSeen("research"));
    }

    @Test void hiddenUnknownRecordsKeepRevisionAndBecomeAvailableAfterAcceptAndDiscovery() {
        var quest = quest(entry(true)); var player = new ArcQuestPlayer(UUID.randomUUID()); player.getCollectionRecords().discover(ENTRY);
        var first = CollectionContentDisclosure.sanitizeRecordSnapshot(player, player.getCollectionRecords().serializeNBT(), ignored -> quest);
        assertTrue(first.getCompound("Entries").isEmpty());
        player.addActiveQuest(runtime(quest));
        assertTrue(CollectionContentDisclosure.sanitizeRecordSnapshot(player, player.getCollectionRecords().serializeNBT(), ignored -> quest)
                .getCompound("Entries").contains(ENTRY.toString()));
    }

    @Test void activeAndArchivedSnapshotsClipGlobalDiscoveryBaselinesAndPreserveServerState() {
        var quest = quest(entry(true)); var player = player(quest); var run = player.getActiveQuest(quest.getId().toString());
        run.getOrCreateCollectionData().initializeSheet("survey", List.of("specimen"), 1, Set.of(ENTRY.toString(), UNRELATED.toString()));
        player.getCollectionRecords().discover(ENTRY); player.getCollectionRecords().discover(UNRELATED);
        CompoundTag before = player.serializeNBT();
        CompoundTag active = CollectionContentDisclosure.sanitizePlayerSnapshot(player, ignored -> quest);
        assertEquals(before, player.serializeNBT());
        assertEquals(Set.of(ENTRY.toString()), baseline(active.getList("ActiveQuests", Tag.TAG_COMPOUND).getCompound(0)));
        player.markCompleted(quest.getId().toString());
        CompoundTag archive = CollectionContentDisclosure.sanitizePlayerSnapshot(player, ignored -> quest);
        assertEquals(Set.of(ENTRY.toString()), baseline(archive.getList("CollectionQuestArchives", Tag.TAG_COMPOUND).getCompound(0)));
        assertEquals(2, baseline(player.getCollectionArchives().get(quest.getId().toString()).serializeNBT()).size());
    }

    @Test void explicitlyPublicClueSurvivesEncodedPlaceholderWithoutDisclosingIdentityOrAssets() throws Exception {
        var entry = CollectionEntryBuilder.create(ENTRY).category("mobs").displayName("Hidden spider identity").entity(net.minecraft.world.entity.EntityType.SPIDER)
                .publicClue("Survey the night and defeat a climbing creature.")
                .visibility(VisibilityMode.HIDDEN_BY_DEFAULT, HiddenPresentationMode.PLACEHOLDER)
                .discover(ObjectiveBuilder.kill(net.minecraft.world.entity.EntityType.SPIDER, 1).id("first"))
                .text("secret", "Confidential archive").build();
        var quest = quest(entry);
        String wire = DatapackContentCodec.decode(DatapackContentCodec.encode(project(source(quest), quest, new CollectionRecordState(), true)))
                .documents(DatapackContentModule.QUEST).get(0);
        assertTrue(wire.contains("Survey the night"));
        for (String secret : List.of("Hidden spider identity", "minecraft:spider", "Confidential archive", "textures/entity")) assertFalse(wire.contains(secret), secret);
        var presentation = QuestSpecCompiler.compileClientPresentation(QuestSpecJsonReader.read(wire), Map.of());
        var runtime = runtime(presentation);
        var row = CollectionProgressProjector.project(presentation, presentation.getPhase("survey"), runtime, new CollectionRecordState()).binding("specimen");
        assertTrue(row.hasPublicClue()); assertFalse(row.revealed()); assertTrue(row.requirements().isEmpty());
        assertTrue(org.arcadia.arc_quest.client.quest.tracking.CollectionTrackingFocusSelector.actionable(row));
        assertFalse(json(project(source(quest), quest, new CollectionRecordState(), false)).contains("Survey the night"));
        var fullyHidden = quest(CollectionEntryBuilder.create(ENTRY).category("mobs").publicClue("PRIVATE CLUE")
                .visibility(VisibilityMode.HIDDEN_BY_DEFAULT, HiddenPresentationMode.FULLY_HIDDEN).build());
        assertFalse(json(project(source(fullyHidden), fullyHidden, new CollectionRecordState(), true)).contains("PRIVATE CLUE"));
    }

    @Test void selectedRunReplacesTheWholeLiveDefinitionAndUnavailableRunOnlyOmitsItsOwnQuest() {
        var old = quest(CollectionEntryBuilder.create(ENTRY).category("mobs").displayName("Version one archive").build());
        var live = quest(CollectionEntryBuilder.create(ENTRY).category("mobs").displayName("Version two archive").build());
        var otherSpec = CollectionDefinitionSpecExporter.quest(live, null); otherSpec.id = "example:other";
        var other = QuestSpecCompiler.compileClientPresentation(otherSpec, Map.of());
        var mixed = new DatapackContentSnapshot(4, Map.of(DatapackContentModule.QUEST, List.of(json(source(live)), QuestSpecJsonWriter.write(otherSpec))));
        var projected = CollectionContentDisclosure.project(mixed, new CollectionRecordState(), ignored -> true,
                id -> id.equals(old.getId().toString()) ? old : other, null, List.of(), Map.of(old.getId().toString(), old));
        String oldWire = projected.documents(DatapackContentModule.QUEST).stream().filter(value -> value.contains(old.getId().toString())).findFirst().orElseThrow();
        assertTrue(oldWire.contains("Version one archive")); assertFalse(oldWire.contains("Version two archive"));
        var missing = new java.util.LinkedHashMap<String, QuestDefinition>(); missing.put(old.getId().toString(), null);
        var filtered = CollectionContentDisclosure.project(mixed, new CollectionRecordState(), ignored -> true, ignored -> other, null, List.of(), missing);
        assertEquals(1, filtered.documents(DatapackContentModule.QUEST).size());
        assertEquals("example:other", QuestSpecJsonReader.read(json(filtered)).id);
    }

    @Test void oldPermanentAndRunRewardsKeepOnlyEarnedPresentationAfterIdsAndBindingsWereRemoved() {
        var quest = quest(entry(false)); var player = player(quest); player.getCollectionRecords().discover(ENTRY);
        var permanent = new CollectionEntryRewardDefinition("old_first", CollectionEntryRewardTrigger.DISCOVERED, EntryRewardGrantMode.MANUAL,
                List.of(new ItemReward(Items.IRON_NUGGET, 2), new CommandReward("say private-executable"), FlagReward.set("private-flag")));
        var runReward = new CollectionEntryRewardDefinition("old_run", CollectionEntryRewardTrigger.BINDING_COMPLETE, EntryRewardGrantMode.MANUAL, permanent.rewards());
        CompoundTag recordRoot = player.getCollectionRecords().serializeNBT(), record = recordRoot.getCompound("Entries").getCompound(ENTRY.toString());
        ListTag unlocked = new ListTag(); unlocked.add(StringTag.valueOf("old_first")); record.put("UnlockedRewards", unlocked);
        CompoundTag entitlements = new CompoundTag(); entitlements.put("old_first", CollectionRewardEntitlement.capture(permanent, quest.getId().toString(), "private-hash", "old_phase", "old_binding", ENTRY));
        var unearned = new CollectionEntryRewardDefinition("unearned", CollectionEntryRewardTrigger.DISCOVERED, EntryRewardGrantMode.MANUAL, List.of(new ItemReward(Items.DIAMOND, 64)));
        entitlements.put("unearned", CollectionRewardEntitlement.capture(unearned, quest.getId().toString(), "private-hash", "", "", ENTRY));
        var foreign = new CollectionEntryRewardDefinition("foreign", CollectionEntryRewardTrigger.DISCOVERED, EntryRewardGrantMode.MANUAL, List.of(new ItemReward(Items.NETHER_STAR, 1)));
        unlocked.add(StringTag.valueOf("foreign"));
        entitlements.put("foreign", CollectionRewardEntitlement.capture(foreign, quest.getId().toString(), "private-hash", "", "", UNRELATED));
        record.put("RewardEntitlements", entitlements);
        CompoundTag safeRecords = CollectionContentDisclosure.sanitizeRecordSnapshot(player, recordRoot, ignored -> quest);
        CompoundTag safeRecord = safeRecords.getCompound("Entries").getCompound(ENTRY.toString());
        assertEquals("old_first", safeRecord.getList("UnlockedRewards", Tag.TAG_STRING).getString(0));
        assertEquals(Set.of("old_first"), safeRecord.getCompound("RewardEntitlements").getAllKeys());
        assertEquals(1, safeRecord.getList("UnlockedRewards", Tag.TAG_STRING).size());
        assertEquals(2, CollectionRewardEntitlement.presentationDefinition(safeRecord.getCompound("RewardEntitlements").getCompound("old_first")).rewards()
                .stream().filter(ItemReward.class::isInstance).map(ItemReward.class::cast).findFirst().orElseThrow().getCount());
        CompoundTag runtime = runtime(quest).serializeNBT(), collection = new CompoundTag(), sheets = new CompoundTag(), phases = new CompoundTag();
        CompoundTag oldPhase = new CompoundTag(), bindings = new CompoundTag(), rewards = new CompoundTag(), receipt = new CompoundTag();
        receipt.putBoolean("Unlocked", true); receipt.put("Entitlement", CollectionRewardEntitlement.capture(runReward, quest.getId().toString(), "private-hash", "old_phase", "old_binding", ENTRY));
        rewards.put("old_run", receipt); bindings.put("old_binding", rewards); oldPhase.put("EntryRewards", bindings); phases.put("old_phase", oldPhase);
        sheets.put("Phases", phases); collection.put("Sheets", sheets); runtime.put("CollectionData", collection);
        CollectionContentDisclosure.sanitizeRuntimeSnapshot(runtime, quest, player.getCollectionRecords());
        CompoundTag safeReceipt = runtime.getCompound("CollectionData").getCompound("Sheets").getCompound("Phases").getCompound("old_phase")
                .getCompound("EntryRewards").getCompound("old_binding").getCompound("old_run");
        assertTrue(safeReceipt.getBoolean("Unlocked")); assertFalse(safeReceipt.contains("Entitlement"));
        assertEquals("old_run", safeReceipt.getCompound("EntitlementPresentation").getString("RewardId"));
        for (String secret : List.of("private-executable", "private-flag", "private-hash", "Command", "DefinitionHash")) {
            assertFalse(safeRecords.toString().contains(secret), secret); assertFalse(runtime.toString().contains(secret), secret);
        }
        CollectionContentDisclosure.sanitizeRuntimeSnapshot(runtime, quest(entry(true)), new CollectionRecordState());
        assertTrue(runtime.getCompound("CollectionData").getCompound("Sheets").getCompound("Phases").getCompound("old_phase").getCompound("EntryRewards").isEmpty());
    }

    @Test void frozenTagCandidatesStayPrivateUntilTheirEntryIsRevealed() {
        ResourceLocation tagId = ResourceLocation.parse("example:hidden_samples");
        var hidden = quest(CollectionEntryBuilder.create(ENTRY).category("mobs").itemTag(tagId)
                .visibility(VisibilityMode.HIDDEN_BY_DEFAULT, HiddenPresentationMode.PLACEHOLDER).build());
        CompoundTag runtime = runtime(hidden).serializeNBT(), frozen = new CompoundTag();
        ListTag members = new ListTag(); members.add(StringTag.valueOf("minecraft:nether_star")); frozen.put(tagId.toString(), members);
        runtime.put(QuestRuntimeData.FROZEN_ITEM_TAGS_KEY, frozen); runtime.putString(QuestRuntimeData.FROZEN_DEFINITION_HASH_KEY, "private hash");
        runtime.putBoolean(QuestRuntimeData.FROZEN_ITEM_TAGS_COMPLETE_KEY, true);
        CompoundTag revealed = runtime.copy();
        CollectionContentDisclosure.sanitizeRuntimeSnapshot(runtime, hidden, new CollectionRecordState());
        assertFalse(runtime.contains(QuestRuntimeData.FROZEN_ITEM_TAGS_KEY)); assertFalse(runtime.contains(QuestRuntimeData.FROZEN_DEFINITION_HASH_KEY));
        assertFalse(runtime.contains(QuestRuntimeData.FROZEN_ITEM_TAGS_COMPLETE_KEY));
        var records = new CollectionRecordState(); records.discover(ENTRY);
        CollectionContentDisclosure.sanitizeRuntimeSnapshot(revealed, hidden, records);
        assertEquals(1, revealed.getCompound(QuestRuntimeData.FROZEN_ITEM_TAGS_KEY).getList(tagId.toString(), Tag.TAG_STRING).size());
    }

    @Test void authorizedFrozenCandidatesReachEntryIconsAndRunObjectivesWithoutHiddenCandidates() throws Exception {
        ResourceLocation tagId = ResourceLocation.parse("example:changed_tag");
        var entry = CollectionEntryBuilder.create(ENTRY).category("materials").itemTag(tagId)
                .visibility(VisibilityMode.HIDDEN_BY_DEFAULT, HiddenPresentationMode.PLACEHOLDER).build();
        var quest = QuestBuilder.create("example:tag_run").mode(QuestMode.COLLECTION)
                .collectionConfig(CollectionQuestConfigBuilder.create().category("materials", "Materials").entry(entry).build())
                .phase(PhaseBuilder.create("survey").objective(ObjectiveBuilder.offerTag(tagId, 2).id("sample"))
                        .collectionSheet(CollectionSheetBuilder.create().binding(EntryRequirementBuilder.create("specimen", ENTRY).objective("sample")))).build();
        var runtime = runtime(quest); runtime.freezeItemTag(tagId, List.of(ResourceLocation.parse("minecraft:nether_star")));
        var records = new CollectionRecordState();
        var hidden = CollectionContentDisclosure.project(source(quest), records, ignored -> true, ignored -> quest, null, List.of(),
                Map.of(quest.getId().toString(), quest), Map.of(quest.getId().toString(), runtime));
        String hiddenWire = DatapackContentCodec.decode(DatapackContentCodec.encode(hidden)).documents(DatapackContentModule.QUEST).get(0);
        assertFalse(hiddenWire.contains("minecraft:nether_star")); assertFalse(hiddenWire.contains(ObjectiveItemResolver.FROZEN_TAG_MEMBERS));
        records.discover(ENTRY);
        var visible = CollectionContentDisclosure.project(source(quest), records, ignored -> true, ignored -> quest, null, List.of(),
                Map.of(quest.getId().toString(), quest), Map.of(quest.getId().toString(), runtime));
        var presentation = QuestSpecCompiler.compileClientPresentation(QuestSpecJsonReader.read(json(visible)), Map.of());
        assertEquals(List.of(ResourceLocation.parse("minecraft:nether_star")), presentation.getCollectionConfig().getEntry(ENTRY).getPresentationItemTagMembers());
        assertEquals(List.of(ResourceLocation.parse("minecraft:nether_star")), ObjectiveItemResolver.targetIds(presentation.getPhase("survey").getObjectives().get(0)));
        var emptyRuntime = runtime(quest); emptyRuntime.freezeItemTag(tagId, List.of());
        var empty = CollectionContentDisclosure.project(source(quest), records, ignored -> true, ignored -> quest, null, List.of(),
                Map.of(quest.getId().toString(), quest), Map.of(quest.getId().toString(), emptyRuntime));
        var emptyPresentation = QuestSpecCompiler.compileClientPresentation(QuestSpecJsonReader.read(json(empty)), Map.of());
        assertEquals(List.of(), emptyPresentation.getCollectionConfig().getEntry(ENTRY).getPresentationItemTagMembers());
        assertTrue(ObjectiveItemResolver.candidates(emptyPresentation.getPhase("survey").getObjectives().get(0)).isEmpty());
    }

    @Test void sharedRecordAuthorizationKeepsTheUnionOfItsKnownDefinitionVersions() {
        var first = quest(entry(true));
        var secondSpec = CollectionDefinitionSpecExporter.quest(quest(CollectionEntryBuilder.create(ENTRY).category("mobs")
                .research(ObjectiveBuilder.custom(TARGET, 3).id("new_step")).build()), null);
        secondSpec.id = "example:second";
        var second = QuestSpecCompiler.compileClientPresentation(secondSpec, Map.of());
        var player = player(first); player.addActiveQuest(runtime(second)); player.getCollectionRecords().discover(ENTRY);
        player.getCollectionRecords().increment(ENTRY, CollectionProgressProjector.researchKey("study"), 1, 5);
        player.getCollectionRecords().increment(ENTRY, CollectionProgressProjector.researchKey("new_step"), 1, 3);
        CompoundTag filtered = CollectionContentDisclosure.sanitizeRecordSnapshot(player, player.getCollectionRecords().serializeNBT(),
                id -> id.equals(first.getId().toString()) ? first : second);
        assertEquals(Set.of(CollectionProgressProjector.researchKey("study"), CollectionProgressProjector.researchKey("new_step")),
                filtered.getCompound("Entries").getCompound(ENTRY.toString()).getCompound("Steps").getAllKeys());
    }

    private static Set<String> baseline(CompoundTag runtime) {
        var ids = runtime.getCompound("CollectionData").getCompound("Sheets").getCompound("Phases").getCompound("survey").getList("DiscoveryBaseline", Tag.TAG_STRING);
        var result = new java.util.HashSet<String>(); for (int i = 0; i < ids.size(); i++) result.add(ids.getString(i)); return result;
    }
    private static String json(DatapackContentSnapshot source) { return source.documents(DatapackContentModule.QUEST).get(0); }
    private static DatapackContentSnapshot source(QuestDefinition quest) {
        return new DatapackContentSnapshot(3, Map.of(DatapackContentModule.QUEST, List.of(QuestSpecJsonWriter.write(CollectionDefinitionSpecExporter.quest(quest, null)))));
    }
    private static DatapackContentSnapshot project(DatapackContentSnapshot source, QuestDefinition quest, CollectionRecordState records, boolean known) {
        return CollectionContentDisclosure.project(source, records, ignored -> known, ignored -> quest, null);
    }
    private static CollectionEntryDefinition entry(boolean hidden) {
        return CollectionEntryBuilder.create(ENTRY).category("mobs").displayName("Secret specimen").description("Secret description")
                .subject(CollectionSubjectKind.ITEM, TARGET).iconItem(TARGET).relatedItem(ResourceLocation.parse("minecraft:diamond"))
                .visibility(hidden ? VisibilityMode.HIDDEN_BY_DEFAULT : VisibilityMode.VISIBLE_BY_DEFAULT, HiddenPresentationMode.PLACEHOLDER)
                .discover(ObjectiveBuilder.custom(TARGET, 1).id("discover"))
                .research(ObjectiveBuilder.custom(TARGET, 5).id("study").display("Secret study requirement"))
                .text("intro", "Private introduction")
                .content(new CollectionContentBlock("research", QuestText.literal("Secret research"),
                        new GuideMediaDefinition(GuideMediaType.IMAGE, ResourceLocation.parse("example:textures/private_image.png"), null, 120, 60, false, false),
                        QuestText.literal("Private caption"), CollectionMediaFit.CONTAIN, true, CollectionContentReveal.RESEARCH_STEP, "study"))
                .build();
    }
    private static QuestDefinition quest(CollectionEntryDefinition entry) {
        return QuestBuilder.create("example:survey").category(QuestCategory.ADVENTURE).mode(QuestMode.COLLECTION)
                .collectionConfig(CollectionQuestConfigBuilder.create().category("mobs", "Mobs").entry(entry).build())
                .phase(PhaseBuilder.create("survey").objective(ObjectiveBuilder.custom(TARGET, 2).id("action"))
                        .collectionSheet(CollectionSheetBuilder.create().binding(EntryRequirementBuilder.create("specimen", ENTRY).objective("action").discovered())))
                .build();
    }
    private static QuestRuntimeData runtime(QuestDefinition quest) { return new QuestRuntimeData(quest.getId().toString(), "survey", 1, 0, 0, 0); }
    private static ArcQuestPlayer player(QuestDefinition quest) {
        var player = new ArcQuestPlayer(UUID.randomUUID()); player.addActiveQuest(runtime(quest)); return player;
    }
}

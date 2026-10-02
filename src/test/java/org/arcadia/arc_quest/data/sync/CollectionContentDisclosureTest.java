package org.arcadia.arc_quest.data.sync;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.guide.api.GuideMediaDefinition;
import org.arcadia.arc_quest.guide.api.GuideMediaType;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.builder.*;
import org.arcadia.arc_quest.quest.data.CollectionRecordState;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
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

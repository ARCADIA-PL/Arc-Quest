package org.arcadia.arc_quest.quest.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.builder.*;
import org.arcadia.arc_quest.quest.spec.QuestSpec;
import org.arcadia.arc_quest.quest.spec.io.QuestSpecJsonReader;
import org.arcadia.arc_quest.quest.spec.io.QuestSpecJsonWriter;
import org.arcadia.arc_quest.testsupport.MinecraftRegistryTestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CollectionRunDefinitionStoreTest {
    private static final ResourceLocation QUEST = ResourceLocation.parse("example:frozen_authoring");
    @BeforeAll static void initialize() { MinecraftRegistryTestBootstrap.initialize(); }

    @Test void fullOriginalRulesAndOwedRewardPayloadSurviveRestartAndLiveChanges() {
        var authoring = legacyAuthoring();
        var store = new CollectionRunDefinitionStore();
        String oldHash = store.freezeAuthoring(authoring);
        authoring.collectionConfig.entries.get(0).researchObjectives.get(0).requiredCount = 99;
        authoring.collectionConfig.entries.get(0).rewards.get(0).rewards.get(0).command = "say changed";
        String newHash = store.freezeAuthoring(authoring);
        assertNotEquals(oldHash, newHash);
        var restarted = CollectionRunDefinitionStore.load(store.save(new CompoundTag()));
        var oldQuest = restarted.resolve(oldHash, QUEST);
        var entry = oldQuest.getCollectionConfig().getEntries().get(0);
        assertFalse(entry.isUnifiedGameplay());
        assertEquals(5, entry.getResearchObjectives().get(0).getRequiredCount());
        assertEquals("Command(say original server reward)", entry.getRewards().get(0).rewards().get(0).describe());
        assertNotNull(oldQuest.getPhase("survey").getEnterCondition());
        assertNotNull(oldQuest.getPhase("survey").getTransitions().get(0).getCondition());
        assertEquals(List.of("accepted_flag"), oldQuest.getFlagsToSetOnAccept());
        assertEquals(List.of("survey_done"), oldQuest.getPhase("survey").getFlagsToSetOnComplete());
        assertEquals(99, restarted.resolve(newHash, QUEST).getCollectionConfig().getEntries().get(0).getResearchObjectives().get(0).getRequiredCount());
    }

    @Test void identicalAuthoringIsDeduplicatedAcrossPlayersAndReturnedDefinitionIsCached() {
        var store = new CollectionRunDefinitionStore();
        String first = store.freezeAuthoring(legacyAuthoring());
        String second = store.freezeAuthoring(QuestSpecJsonReader.read(QuestSpecJsonWriter.write(legacyAuthoring())));
        assertEquals(first, second); assertEquals(1, store.snapshotCount());
        assertSame(store.resolve(first, QUEST), store.resolve(second, QUEST));
    }

    @Test void sharedEntryAuthoringIsInlinedWithoutLosingConditionsOrRewards() {
        var source = legacyAuthoring();
        var shared = source.collectionConfig.entries.remove(0);
        source.collectionConfig.entryIds.add(shared.entryId);
        var store = new CollectionRunDefinitionStore();
        assertThrows(CollectionRunDefinitionStore.UnsupportedSnapshotException.class, () -> store.freezeAuthoring(source));
        String hash = store.freezeAuthoring(source, id -> id.equals(shared.entryId) ? shared : null);
        shared.researchObjectives.get(0).requiredCount = 99;
        var restored = CollectionRunDefinitionStore.load(store.save(new CompoundTag())).resolve(hash, QUEST);
        var entry = restored.getCollectionConfig().getEntries().get(0);
        assertEquals(5, entry.getResearchObjectives().get(0).getRequiredCount());
        assertEquals(1, entry.getRecordConditions().size());
        assertEquals("Command(say original server reward)", entry.getRewards().get(0).rewards().get(0).describe());
    }

    @Test void unknownConditionsAndTamperedSnapshotsFailClosedWithoutLiveFallback() {
        var unknown = legacyAuthoring(); unknown.phases.get(0).enterCondition.condition = "example:missing_condition";
        var store = new CollectionRunDefinitionStore();
        assertThrows(CollectionRunDefinitionStore.UnsupportedSnapshotException.class, () -> store.freezeAuthoring(unknown));
        String hash = store.freezeAuthoring(legacyAuthoring());
        assertThrows(CollectionRunDefinitionStore.UnsupportedSnapshotException.class, () -> store.resolve("missing", QUEST));
        assertThrows(IllegalArgumentException.class, () -> store.resolve(hash, ResourceLocation.parse("example:other")));
        CompoundTag tampered = store.save(new CompoundTag());
        tampered.getCompound("Snapshots").getCompound(hash).putString("AuthoringJson", "{}");
        assertThrows(IllegalArgumentException.class, () -> CollectionRunDefinitionStore.load(tampered));
    }

    @Test void explicitVersionedFactoriesPreserveCallbacksAndOriginalVersionAfterRestart() {
        ResourceLocation id = ResourceLocation.parse("example:frozen_code_callback_test");
        ICondition originalCondition = (player, completed, flags, variables) -> false;
        QuestDefinition versionOne = codeQuest(id, originalCondition, 5);
        QuestDefinition versionTwo = codeQuest(id, (player, completed, flags, variables) -> true, 9);
        CollectionRunDefinitionStore.registerCodeDefinitionFactory(id, "v1", () -> versionOne, false);
        CollectionRunDefinitionStore.registerCodeDefinitionFactory(id, "v2", () -> versionTwo, true);
        var store = new CollectionRunDefinitionStore();
        String oldHash = store.freezeLegacyCode(id, "v1");
        String newHash = store.freezeRegistered(versionTwo);
        assertNotEquals(oldHash, newHash);
        var restarted = CollectionRunDefinitionStore.load(store.save(new CompoundTag()));
        var oldQuest = restarted.resolve(oldHash, id);
        assertSame(originalCondition, oldQuest.getPhase("survey").getEnterCondition());
        assertEquals(5, oldQuest.getPhase("survey").getObjectives().get(0).getRequiredCount());
        assertEquals(9, restarted.resolve(newHash, id).getPhase("survey").getObjectives().get(0).getRequiredCount());
        assertTrue(CollectionRunDefinitionStore.capability(versionTwo).supported());
        assertEquals("v2", CollectionRunDefinitionStore.capability(versionTwo).version());
    }

    @Test void unregisteredCodeCallbacksAndUnknownOldVersionsAreDiagnosed() {
        var id = ResourceLocation.parse("example:frozen_missing_callback_test");
        var quest = codeQuest(id, (player, completed, flags, variables) -> false, 5);
        var store = new CollectionRunDefinitionStore();
        assertFalse(CollectionRunDefinitionStore.capability(quest).supported());
        assertThrows(CollectionRunDefinitionStore.UnsupportedSnapshotException.class, () -> store.freezeRegistered(quest));
        assertThrows(CollectionRunDefinitionStore.UnsupportedSnapshotException.class, () -> store.freezeLegacyCode(id, "unknown-v1"));
    }

    private static QuestDefinition codeQuest(ResourceLocation id, ICondition condition, int target) {
        var entry = CollectionEntryBuilder.create("example:code_frozen_entry").category("field").build();
        return QuestBuilder.create(id).mode(QuestMode.COLLECTION)
                .collectionConfig(CollectionQuestConfigBuilder.create().category("field", "Field").entry(entry).build())
                .phase(PhaseBuilder.create("survey").enterWhen(condition)
                        .objective(ObjectiveBuilder.custom(ResourceLocation.parse("example:action"), target).id("action"))
                        .collectionSheet(CollectionSheetBuilder.create().binding(EntryRequirementBuilder.create("investigate", entry.getEntryId()).objective("action"))))
                .build();
    }

    private static QuestSpec legacyAuthoring() {
        return QuestSpecJsonReader.read("""
            {"id":"example:frozen_authoring","mode":"COLLECTION","initialPhaseId":"survey",
             "flagsToSetOnAccept":["accepted_flag"],
             "collectionConfig":{"categories":[{"categoryId":"mobs"}],"entries":[{
               "entryId":"example:frozen_zombie","categoryId":"mobs","gameplayVersion":1,
               "recordConditions":[{"condition":"arc_quest:has_flag","flag":"allowed"}],
               "researchObjectives":[{"id":"study","type":"arc_quest:custom","targetId":"example:study","requiredCount":5}],
               "rewards":[{"rewardId":"original","trigger":"RESEARCH_COMPLETE","rewards":[{"type":"command","command":"say original server reward"}]}]
             }]},"phases":[{"phaseId":"survey","objectives":[],
               "enterCondition":{"condition":"arc_quest:has_flag","flag":"ready"},
               "flagsToSetOnComplete":["survey_done"],
               "transitions":[{"targetPhaseId":"handoff","condition":{"condition":"arc_quest:has_flag","flag":"handoff_allowed"}}],
               "collectionSheet":{"bindings":[{"bindingId":"legacy","entryId":"example:frozen_zombie","recordRequirements":[{"type":"RESEARCH_STEP","stepId":"study"}]}]}
             },{"phaseId":"handoff","objectives":[{"id":"finish","type":"arc_quest:custom","targetId":"example:finish","requiredCount":1}]}]}
            """);
    }
}

package org.arcadia.arc_quest.questplayer.migration;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerData;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.restore.ArcQuestPlayerMigrationImporter;
import org.arcadia.arc_quest.questplayer.restore.ArcQuestPlayerMigrationValidator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlayerMigrationRoundTripTest {
    private final ArcQuestPlayerMigrationParser parser = new ArcQuestPlayerMigrationParser();
    private final ArcQuestPlayerMigrationSerializer serializer = new ArcQuestPlayerMigrationSerializer();
    private final ArcQuestPlayerMigrationImporter importer = new ArcQuestPlayerMigrationImporter();

    @Test
    void allPlayerStateSurvivesExportSerializeParseAndImport() {
        ArcQuestPlayer source = populated();
        CompoundTag original = source.serializeNBT();
        ArcQuestPlayerMigrationBundle bundle = bundle(ArcQuestPlayerMigrationSections.fromPlayerData(original));
        ArcQuestPlayerMigrationBundle parsed = parser.parse(serializer.serialize(bundle));
        ArcQuestPlayer target = new ArcQuestPlayer(UUID.randomUUID());
        importer.apply(target, parsed, ArcQuestPlayerMigrationMode.REPLACE_ALL, parsed.getMeta().getExportedSections());
        assertEquals(original, target.serializeNBT());
        assertEquals(original, source.serializeNBT());
        assertFalse(target.isDirty());
    }

    @Test
    void fieldMapCoversEveryCurrentPlayerFieldExceptTheVersion() {
        CompoundTag source = populated().serializeNBT();
        ArcQuestPlayerMigrationSections sections = ArcQuestPlayerMigrationSections.fromPlayerData(source);
        CompoundTag restored = sections.applyTo(new CompoundTag(), sections.getAvailableSections());
        source.remove("_ArcQuestVer");
        assertEquals(source, restored);
    }

    @Test
    void selectingOneSectionLeavesEveryOtherSectionUntouched() {
        CompoundTag source = populated().serializeNBT();
        ArcQuestPlayerMigrationSections exported = ArcQuestPlayerMigrationSections.fromPlayerData(source);
        CompoundTag target = new ArcQuestPlayer(UUID.randomUUID()).serializeNBT();
        ArcQuestPlayerMigrationSections baseline = ArcQuestPlayerMigrationSections.fromPlayerData(target);
        for (String selected : exported.getAvailableSections()) {
            CompoundTag merged = exported.applyTo(target, Set.of(selected));
            ArcQuestPlayerMigrationSections actual = ArcQuestPlayerMigrationSections.fromPlayerData(merged);
            for (String inspected : exported.getAvailableSections()) {
                ArcQuestPlayerMigrationSections expected = inspected.equals(selected) ? exported : baseline;
                assertEquals(expected.applyTo(new CompoundTag(), Set.of(inspected)),
                        actual.applyTo(new CompoundTag(), Set.of(inspected)), selected + " / " + inspected);
            }
        }
    }

    @Test
    void legacySixSectionSnapshotsPreserveGuideButClearStaleQuestAndMarkerFields() {
        ArcQuestPlayer target = populated();
        CompoundTag before = target.serializeNBT();
        ArcQuestPlayerMigrationSections legacy = new ArcQuestPlayerMigrationSections(null, null, null, null, null, null);
        ArcQuestPlayerMigrationBundle parsed = parser.parse(serializer.serialize(bundle(legacy)));
        importer.apply(target, parsed, ArcQuestPlayerMigrationMode.REPLACE_ALL, Set.of("questState", "markers"));
        assertNull(target.getTrackedQuestId());
        assertFalse(target.isPhaseStoryRead("arc_quest:test", "phase"));
        assertFalse(target.isOneShotMarkerConsumed("consumed"));
        assertEquals(before.get("GuideProgress"), target.serializeNBT().get("GuideProgress"));
        assertEquals(before.get("UnlockedGuides"), target.serializeNBT().get("UnlockedGuides"));
        assertFalse(parsed.getSections().getAvailableSections().contains("guide"));
    }

    @Test
    void sectionGettersAndMergedOutputCannotMutateTheSnapshot() {
        ArcQuestPlayerMigrationSections sections = ArcQuestPlayerMigrationSections.fromPlayerData(populated().serializeNBT());
        CompoundTag before = sections.toTag();
        sections.getGuide().remove("GuideProgress");
        CompoundTag output = sections.applyTo(new CompoundTag(), Set.of("guide"));
        output.getCompound("GuideProgress").putInt("arc_quest:test", 999);
        assertEquals(before, sections.toTag());
    }

    @Test
    void malformedEnvelopeAndAdvertisedMissingSectionAreRejected() {
        CompoundTag encoded = serializer.serialize(bundle(ArcQuestPlayerMigrationSections.fromPlayerData(populated().serializeNBT())));
        CompoundTag missing = encoded.copy();
        missing.getCompound("Sections").remove("Guide");
        assertThrows(IllegalArgumentException.class, () -> parser.parse(missing));
        CompoundTag wrongType = encoded.copy();
        wrongType.getCompound("Sections").putString("QuestState", "invalid");
        assertThrows(IllegalArgumentException.class, () -> parser.parse(wrongType));
        CompoundTag wrongUuid = encoded.copy();
        wrongUuid.getCompound("Meta").putString("SourcePlayerUuid", "invalid");
        assertThrows(IllegalArgumentException.class, () -> parser.parse(wrongUuid));
        assertThrows(IllegalArgumentException.class, () -> parser.parse(new CompoundTag()));
    }

    @Test
    void malformedSelectedFieldsFailWithoutChangingTheTarget() {
        ArcQuestPlayer target = populated();
        CompoundTag before = target.serializeNBT();
        CompoundTag flags = new CompoundTag();
        ListTag wrongElements = new ListTag();
        wrongElements.add(IntTag.valueOf(4));
        flags.put("Flags", wrongElements);
        var invalid = bundle(new ArcQuestPlayerMigrationSections(flags, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> importer.apply(target, invalid,
                ArcQuestPlayerMigrationMode.REPLACE_ALL, Set.of("flagsVars")));
        assertEquals(before, target.serializeNBT());
        assertTrue(new ArcQuestPlayerMigrationValidator().validate(null, invalid, Set.of("flagsVars")).isBlocking());
    }

    @Test
    void unknownUnexportedAndFutureInputsAreBlocked() {
        var sections = new ArcQuestPlayerMigrationSections(null, null, null, null, null, null);
        var valid = bundle(sections);
        var validator = new ArcQuestPlayerMigrationValidator();
        assertTrue(validator.validate(null, valid, Set.of("guide")).isBlocking());
        assertTrue(validator.validate(null, valid, Set.of("unknown")).isBlocking());
        assertTrue(validator.validate(null, valid, Set.of()).isBlocking());
        var future = new ArcQuestPlayerMigrationBundle(valid.getFormat(), 2, valid.getMeta(), sections);
        assertTrue(validator.validate(null, future, Set.of("flagsVars")).isBlocking());
        ArcQuestPlayer target = populated();
        CompoundTag before = target.serializeNBT();
        assertThrows(IllegalArgumentException.class, () -> importer.apply(target, future,
                ArcQuestPlayerMigrationMode.REPLACE_ALL, Set.of("flagsVars")));
        assertEquals(before, target.serializeNBT());
    }

    @Test
    void invalidNestedStoresAndGuideIdsCannotSilentlyEraseProgress() {
        ArcQuestPlayer target = populated();
        CompoundTag before = target.serializeNBT();
        CompoundTag dialogue = new CompoundTag();
        dialogue.putString("Nodes", "invalid");
        CompoundTag guide = new CompoundTag();
        ListTag ids = new ListTag();
        ids.add(net.minecraft.nbt.StringTag.valueOf("INVALID ID"));
        guide.put("UnlockedGuides", ids);
        for (String section : Set.of("dialogue", "guide")) {
            var invalid = bundle(new ArcQuestPlayerMigrationSections(null, null, dialogue, null, null, null, guide));
            assertThrows(IllegalArgumentException.class, () -> importer.apply(target, invalid,
                    ArcQuestPlayerMigrationMode.REPLACE_ALL, Set.of(section)));
            assertEquals(before, target.serializeNBT());
        }
    }

    private static ArcQuestPlayerMigrationBundle bundle(ArcQuestPlayerMigrationSections sections) {
        return new ArcQuestPlayerMigrationBundle(ArcQuestPlayerMigrationBundle.FORMAT, 1,
                new ArcQuestPlayerMigrationMeta(UUID.randomUUID(), "player", 100,
                        ArcQuestPlayer.getCurrentDataVersion(), "test", "world", sections.getAvailableSections()), sections);
    }

    private static ArcQuestPlayer populated() {
        ArcQuestPlayer data = new ArcQuestPlayer(UUID.randomUUID());
        data.addActiveQuest(new QuestRuntimeData("arc_quest:test", "phase", 1, 2, 3, 4));
        data.getActiveQuest("arc_quest:test").setObjectiveProgress("phase", 0, 3);
        data.markPhaseStoryRead("arc_quest:test", "phase");
        data.setTrackedQuestId("arc_quest:test");
        data.setTrackedPhaseId("phase");
        ResourceLocation guide = java.util.Objects.requireNonNull(ResourceLocation.tryParse("arc_quest:test"));
        data.unlockGuide(guide);
        data.markGuideSeen(guide);
        data.setGuideProgress(guide, 3);
        data.upsertMarker(QuestMarkerData.location("marker", 2, 3, 4, "test"));
        data.consumeOneShotMarker("consumed");
        data.getDialogueProgress().recordNodeVisit("arc_quest", "test", 1, 2, 3);
        data.getTradeDataStore().incrementPurchase("shop", "entry");
        data.getTradeDataStore().recordCooldown("shop", "entry", 10, 20, 30);
        data.incrementGachaDrawCount("shop");
        data.setGachaPityCounter("shop", 5);
        data.addGachaDrawHistory("shop", "minecraft:stone", "common", 1, false, 100);
        return data;
    }
}

package org.arcadia.arc_quest.collection;

import com.mojang.authlib.GameProfile;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.data.sync.*;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.builder.*;
import org.arcadia.arc_quest.quest.data.CollectionRunDefinitionStore;
import org.arcadia.arc_quest.quest.logic.CollectionRecordService;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.quest.spec.*;
import org.arcadia.arc_quest.quest.spec.compile.QuestSpecCompiler;
import org.arcadia.arc_quest.quest.spec.io.*;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import org.arcadia.arc_quest.questplayer.PlayerSessionEpochManager;

import java.util.*;

@GameTestHolder(Arc_Quest.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CollectionAtlasGameTests {
    private static final String BATCH = "arc_quest.collection.atlas";

    private static CollectionSubjectSpec subject(String quality) {
        String nbt = "{quality:" + quality + "}";
        return new CollectionSubjectSpec(
                ResourceLocation.parse("arc_quest:item_stack"),
                ResourceLocation.parse("minecraft:diamond"),
                1,
                nbt,
                new StackMatchSpec(StackMatchSpec.Mode.NBT_SUBSET, nbt),
                Map.of());
    }

    private static CollectionEntryDefinition entry() {
        return CollectionEntryBuilder.create("arc_quest:gametest/atlas_specimen")
                .category("gems")
                .item(Items.DIAMOND)
                .outcome("rare", "Rare")
                .outcome("epic", "Epic")
                .observation(new CollectionObservationRule("rare", 1, subject("rare"), "rare"))
                .observation(new CollectionObservationRule("epic", 1, subject("epic"), "epic"))
                .presentation(
                        new CollectionEntryPresentation(
                                null,
                                List.of(
                                        new CollectionVariantDefinition(
                                                "rare",
                                                QuestText.literal("Rare"),
                                                subject("rare"),
                                                "rare",
                                                CollectionContentReveal.ALWAYS),
                                        new CollectionVariantDefinition(
                                                "epic",
                                                QuestText.literal("Epic"),
                                                subject("epic"),
                                                "epic",
                                                CollectionContentReveal.ALWAYS)),
                                "rare",
                                List.of()))
                .build();
    }

    private static QuestDefinition quest(CollectionEntryDefinition entry) {
        return QuestBuilder.create("arc_quest:gametest/atlas_observation")
                .category(QuestCategory.ADVENTURE)
                .mode(QuestMode.COLLECTION)
                .collectionConfig(
                        CollectionQuestConfigBuilder.create()
                                .category("gems", "Gems")
                                .entry(entry)
                                .build())
                .phase(
                        PhaseBuilder.create("catalogue")
                                .collectionSheet(
                                        CollectionSheetBuilder.create()
                                                .binding(
                                                        EntryRequirementBuilder.create(
                                                                        "specimen",
                                                                        entry.getEntryId())
                                                                .discovered())))
                .build();
    }

    @GameTest(template = "jei_empty", batch = BATCH, timeoutTicks = 100)
    public static void realInventoryQualityIsPlayerScopedAndResetNeedsFreshAcquisition(
            GameTestHelper helper) {
        var entry = entry();
        var quest = quest(entry);
        var previous = QuestRegistry.getDatapackSnapshot();
        var first =
                new ServerPlayer(
                        helper.getLevel().getServer(),
                        helper.getLevel(),
                        new GameProfile(UUID.randomUUID(), "AtlasFirst"));
        var second =
                new ServerPlayer(
                        helper.getLevel().getServer(),
                        helper.getLevel(),
                        new GameProfile(UUID.randomUUID(), "AtlasSecond"));
        try {
            var next = new LinkedHashMap<>(previous);
            next.put(quest.getId(), quest);
            QuestRegistry.replaceDatapackSnapshot(next);
            var a = ArcQuestPlayerManager.getOrCreate(first);
            var b = ArcQuestPlayerManager.getOrCreate(second);
            first.getInventory().setItem(0, subject("rare").resolveStack());
            second.getInventory().setItem(0, subject("epic").resolveStack());
            CollectionRecordService.discoverInventory(
                    first, Map.of(ResourceLocation.parse("minecraft:diamond"), 1));
            CollectionRecordService.discoverInventory(
                    second, Map.of(ResourceLocation.parse("minecraft:diamond"), 1));
            helper.assertTrue(
                    a.getCollectionRecords().hasOutcome(entry.getEntryId(), "rare")
                            && !a.getCollectionRecords().hasOutcome(entry.getEntryId(), "epic"),
                    "Rare acquisition contaminated another quality");
            helper.assertTrue(
                    b.getCollectionRecords().hasOutcome(entry.getEntryId(), "epic")
                            && !b.getCollectionRecords().hasOutcome(entry.getEntryId(), "rare"),
                    "Player knowledge crossed player boundary");
            helper.assertTrue(
                    a.getAllActiveQuests().isEmpty() && b.getAllActiveQuests().isEmpty(),
                    "Inventory observer fabricated an accepted binding");
            a.getCollectionRecords()
                    .resetQuest(quest.getId().toString(), Set.of(entry.getEntryId()));
            CollectionRecordService.discoverInventory(
                    first, Map.of(ResourceLocation.parse("minecraft:diamond"), 1));
            helper.assertTrue(
                    !a.getCollectionRecords().isDiscovered(entry.getEntryId()),
                    "Stale inventory refilled explicit reset");
            first.getInventory().setItem(0, ItemStack.EMPTY);
            CollectionRecordService.discoverInventory(first, Map.of());
            first.getInventory().setItem(0, subject("rare").resolveStack());
            CollectionRecordService.discoverInventory(
                    first, Map.of(ResourceLocation.parse("minecraft:diamond"), 1));
            helper.assertTrue(
                    a.getCollectionRecords().hasOutcome(entry.getEntryId(), "rare"),
                    "Actual fresh acquisition failed to restore knowledge");
        } finally {
            QuestRegistry.replaceDatapackSnapshot(previous);
            for (var player : List.of(first, second)) {
                ArcQuestPlayerManager.unload(player.getUUID());
                PlayerSessionEpochManager.endSession(player.getUUID());
                player.invalidateCaps();
            }
        }
        helper.succeed();
    }

    @GameTest(template = "jei_empty", batch = BATCH, timeoutTicks = 100)
    public static void savedAuthoringPinsObservationRulesAndOldGridSurvivesPresentationExport(
            GameTestHelper helper) {
        var original = quest(entry());
        var spec = CollectionAuthoringExporter.quest(original);
        spec.collectionConfig.collectionPresentationMode = "GRID";
        var store = new CollectionRunDefinitionStore();
        var hash = store.freezeAuthoring(spec);
        spec.collectionConfig.entries.get(0).observationRules.clear();
        var restored =
                CollectionRunDefinitionStore.load(store.save(new net.minecraft.nbt.CompoundTag()))
                        .resolve(hash, original.getId());
        helper.assertTrue(
                restored.getCollectionConfig()
                                .getEntry(entry().getEntryId())
                                .getObservationRules()
                                .size()
                        == 2,
                "Stored authoring lost frozen server observation rules");
        helper.assertTrue(
                restored.getCollectionConfig().getJournalMode() == CollectionPresentationMode.GRID,
                "Old GRID mode changed during schema migration");
        var presentation = CollectionDefinitionSpecExporter.quest(restored, null);
        helper.assertTrue(
                presentation.collectionConfig.entries.get(0).observationRules.isEmpty(),
                "Executable observation rules leaked into presentation export");
        var client =
                QuestSpecCompiler.compileClientPresentation(
                        QuestSpecJsonReader.read(QuestSpecJsonWriter.write(presentation)),
                        Map.of());
        helper.assertTrue(
                client.getCollectionConfig().getJournalMode() == CollectionPresentationMode.GRID,
                "GRID presentation failed to compile");
        helper.succeed();
    }
}

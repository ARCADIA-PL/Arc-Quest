package org.arcadia.arc_quest.integration.jei;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.integration.jei.api.JeiDisplayAdapters;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.builder.ObjectiveBuilder;
import org.arcadia.arc_quest.quest.builder.PhaseBuilder;
import org.arcadia.arc_quest.quest.builder.QuestBuilder;
import org.arcadia.arc_quest.quest.data.CollectionRuntimeData;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.logic.ObjectiveRequiredCounts;
import org.arcadia.arc_quest.quest.logic.QuestProgressHandler;
import org.arcadia.arc_quest.quest.logic.profile.collection.CollectionObjectiveDispatcher;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.quest.service.QuestOfferService;
import org.arcadia.arc_quest.quest.tracking.ObjectiveKey;
import org.arcadia.arc_quest.quest.tracking.ObjectiveTypeIndex;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import org.arcadia.arc_quest.questplayer.PlayerSessionEpochManager;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/** Loaded tags and real player-dependent count rules; never sends through a fake login connection. */
@GameTestHolder(Arc_Quest.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("deprecation")
public final class ArcQuestObjectiveConsistencyGameTests {
    private static final String BATCH = "arc_quest.jei";
    private static final String TEMPLATE = "jei_empty";
    private ArcQuestObjectiveConsistencyGameTests() {}

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void loadedTagCandidatesMatchEveryIndexedItemObjective(GameTestHelper helper) {
        var phase = PhaseBuilder.create("logs");
        for (ObjectiveType type : List.of(ObjectiveType.COLLECT, ObjectiveType.CRAFT, ObjectiveType.OFFER, ObjectiveType.DELIVER)) {
            phase.objective(new ObjectiveEntry(type, ResourceLocation.parse("minecraft:oak_log"), 2,
                    QuestText.literal("Logs"), false, false, Map.of("target_tag", "minecraft:logs"), List.of(), null));
        }
        var definition = QuestBuilder.create("arc_quest:gametest_tag_index").phase(phase).build();
        var index = ObjectiveTypeIndex.build(Map.of(definition.getId(), definition));
        for (var objective : definition.getPhase("logs").getObjectives()) {
            var candidates = ObjectiveItemResolver.candidates(objective);
            helper.assertTrue(candidates.stream().anyMatch(stack -> stack.is(Items.OAK_LOG)), "Loaded oak tag member missing");
            helper.assertTrue(candidates.stream().anyMatch(stack -> stack.is(Items.BIRCH_LOG)), "Loaded birch tag member missing");
            var ids = ObjectiveItemResolver.targetIds(objective);
            helper.assertTrue(ids.equals(ids.stream().sorted(java.util.Comparator.comparing(ResourceLocation::toString)).toList()),
                    "Tag candidate order is not stable");
            for (var candidate : candidates) {
                candidate.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Named log"));
                helper.assertTrue(ObjectiveItemResolver.matches(objective, candidate), "A shown tag member would not count");
                var itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(candidate.getItem());
                var refs = index.find(objective.getType(), itemId);
                helper.assertTrue(refs != null && refs.size() == 1, "Tag member missing from event index");
            }
            helper.assertTrue(index.find(objective.getType(), ResourceLocation.parse("minecraft:stone")) == null,
                    "Nonmember falsely matched the tag");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void effectiveCountsAgreeAcrossProgressionRuntimeAndJei(GameTestHelper helper) {
        withPlayer(helper, (player, data) -> {
            player.experienceLevel = 9;
            data.setVariable("objective_count_test", 2);
            AtomicInteger modifiers = new AtomicInteger();
            var level = ObjectiveBuilder.collect(Items.APPLE, 1).countModifier(context -> {
                helper.assertTrue(context == player, "Count modifier lost authoritative player");
                modifiers.incrementAndGet();
                return context.experienceLevel + 2;
            }).extra("count_mode", "level_scale").extra("count_per_level", "4").build();
            var variable = ObjectiveBuilder.collect(Items.APPLE, 1).countModifier(context -> 15)
                    .extra("count_mode", "variable").extra("count_var", "objective_count_test")
                    .extra("count_per_var", "5").build();
            var fixed = ObjectiveBuilder.collect(Items.APPLE, 1).extra("count_mode", "fixed")
                    .extra("count_base", "65537").build();
            var definition = QuestBuilder.create("arc_quest:gametest_effective_counts")
                    .phase(PhaseBuilder.create("counts").objective(level).objective(variable).objective(fixed)).build();
            var previous = QuestRegistry.getDatapackSnapshot();
            try {
                install(previous, definition);
                var runtime = new QuestRuntimeData(definition.getId().toString(), "counts", 3, 0L, 0L, 0L);
                data.addActiveQuest(runtime);
                helper.assertTrue(ObjectiveRequiredCounts.refresh(player, data, runtime), "First count projection was missing");
                helper.assertTrue(modifiers.get() == 1, "Count projection evaluated a modifier more than once");
                int[] expected = {47, 25, 65537};
                var objectives = definition.getPhase("counts").getObjectives();
                for (int i = 0; i < objectives.size(); i++) {
                    helper.assertTrue(runtime.getRequiredCount("counts", i, 1) == expected[i], "Runtime count_mode mismatch");
                    helper.assertTrue(QuestProgressHandler.resolveRequiredCount(player, objectives.get(i), data) == expected[i],
                            "Progression count_mode mismatch");
                    helper.assertTrue(JeiDisplayAdapters.objective(objectives.get(i), player).ingredients().get(0).amount() == expected[i],
                            "JEI did not use the full count_mode rule");
                    helper.assertTrue(runtime.getObjectiveProgress("counts", i) == 0, "Projection changed business progress");
                }
                helper.assertTrue(!ObjectiveRequiredCounts.refresh(player, data, runtime), "Unchanged counts should not resync");
                data.setVariable("objective_count_test", 7);
                player.experienceLevel = 10;
                helper.assertTrue(ObjectiveRequiredCounts.refresh(player, data, runtime), "Level/variable change was not projected");
                helper.assertTrue(runtime.getRequiredCount("counts", 0, 1) == 52, "Updated level count is wrong");
                helper.assertTrue(runtime.getRequiredCount("counts", 1, 1) == 50, "Updated variable count is wrong");
                var buf = new FriendlyByteBuf(Unpooled.buffer());
                try {
                    runtime.writeToNetwork(buf);
                    var clientRuntime = QuestRuntimeData.readFromNetwork(buf);
                    helper.assertTrue(clientRuntime.getRequiredCount("counts", 0, 1) == 52
                            && clientRuntime.getRequiredCount("counts", 1, 1) == 50
                            && clientRuntime.getRequiredCount("counts", 2, 1) == 65537, "Native client snapshot lost effective counts");
                } finally { buf.release(); }
                helper.assertTrue(runtime.isPhaseActive("counts") && runtime.getCompletedPhaseIds().isEmpty(),
                        "Read-only threshold refresh advanced the quest");
            } finally { QuestRegistry.replaceDatapackSnapshot(previous); }
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void collectionTagsMatchWithoutChangingEntryCountingModes(GameTestHelper helper) {
        withPlayer(helper, (player, data) -> {
            var category = new CollectionCategoryDefinition("logs", QuestText.literal("Logs"), null, 0, List.of(), List.of(), List.of());
            var builder = QuestBuilder.create("arc_quest:gametest_collection_tags").mode(QuestMode.COLLECTION)
                    .collectionConfig(new CollectionQuestConfig(List.of(category), List.of(), List.of(), null, null, true, false, true));
            for (CountingMode mode : CountingMode.values()) {
                builder.phase(PhaseBuilder.create(mode.name()).objective(ObjectiveBuilder.collectTag(ResourceLocation.parse("minecraft:logs"), 2))
                        .collectionEntryConfig(new CollectionEntryConfig("logs", null, null, List.of(), mode, 9,
                                true, false, 20, null, List.of(), 0, true)));
            }
            var definition = builder.build();
            var previous = QuestRegistry.getDatapackSnapshot();
            try {
                install(previous, definition);
                var runtime = new QuestRuntimeData(definition.getId().toString(), CountingMode.BINARY.name(), 1, 0L, 0L, 0L);
                var collection = new CollectionRuntimeData();
                for (String phaseId : definition.getPhaseIds()) {
                    runtime.activatePhase(phaseId, 1);
                    collection.markVisible(phaseId);
                }
                runtime.setCollectionData(collection);
                data.addActiveQuest(runtime);
                for (String item : List.of("minecraft:oak_log", "minecraft:birch_log")) {
                    var bindings = CollectionObjectiveDispatcher.findBindings(data, new ObjectiveKey(ObjectiveType.COLLECT, ResourceLocation.parse(item)));
                    helper.assertTrue(bindings.size() == CountingMode.values().length, "Collection dispatcher lost a tagged entry");
                    helper.assertTrue(bindings.stream().map(binding -> binding.getCountingMode()).collect(Collectors.toSet())
                            .equals(Set.of(CountingMode.values())), "Collection counting modes were changed");
                    helper.assertTrue(bindings.stream().allMatch(binding -> binding.isRepeatableProgress() && !binding.isRepeatableCompletion()),
                            "Collection repeatability was changed");
                }
                helper.assertTrue(CollectionObjectiveDispatcher.findBindings(data, new ObjectiveKey(ObjectiveType.CRAFT, ResourceLocation.parse("minecraft:oak_log"))).isEmpty(),
                        "Collection dispatcher mixed event types");
                helper.assertTrue(CollectionObjectiveDispatcher.findBindings(data, new ObjectiveKey(ObjectiveType.COLLECT, ResourceLocation.parse("minecraft:stone"))).isEmpty(),
                        "Collection dispatcher matched a nonmember");
                helper.assertTrue(definition.getPhase(CountingMode.BINARY.name()).getCollectionEntryConfig().getCompletionTarget() == 9,
                        "Collection entry threshold was replaced by individual objective count");
            } finally { QuestRegistry.replaceDatapackSnapshot(previous); }
        });
    }

    private static void install(Map<ResourceLocation, QuestDefinition> previous, QuestDefinition definition) {
        var next = new LinkedHashMap<>(previous);
        next.put(definition.getId(), definition);
        QuestRegistry.replaceDatapackSnapshot(next);
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void offerableInventoryUsesTheSameLoadedTagAndNeverFallsBack(GameTestHelper helper) {
        withPlayer(helper, (player, data) -> {
            var namedOak = new ItemStack(Items.OAK_LOG, 13);
            namedOak.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Named objective log"));
            player.getInventory().setItem(0, namedOak);
            player.getInventory().setItem(1, new ItemStack(Items.BIRCH_LOG, 3));
            player.getInventory().setItem(2, new ItemStack(Items.STONE, 5));
            var inventoryBefore = player.getInventory().save(new net.minecraft.nbt.ListTag());
            for (ObjectiveType type : List.of(ObjectiveType.OFFER, ObjectiveType.DELIVER)) {
                var objective = new ObjectiveEntry(type, ResourceLocation.parse("minecraft:oak_log"), 1,
                        QuestText.literal("Logs"), false, false, Map.of("target_tag", "minecraft:logs"), List.of(), null);
                helper.assertTrue(QuestOfferService.countOfferable(player, objective) == 16,
                        "Offerable inventory disagrees with visible tag candidates or rejects named variants");
                for (String invalid : List.of("", "Invalid Tag!", "arc_quest:missing_test_tag")) {
                    var missing = new ObjectiveEntry(type, ResourceLocation.parse("minecraft:oak_log"), 1,
                            QuestText.literal("Missing logs"), false, false, Map.of("target_tag", invalid), List.of(), null);
                    helper.assertTrue(QuestOfferService.countOfferable(player, missing) == 0, "Invalid tag fell back to oak item");
                }
            }
            helper.assertTrue(inventoryBefore.equals(player.getInventory().save(new net.minecraft.nbt.ListTag())),
                    "Offerable inventory preview consumed items");
        });
    }

    private static void withPlayer(GameTestHelper helper, BiConsumer<ServerPlayer, ArcQuestPlayer> test) {
        var level = helper.getLevel();
        var player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "ArcQCountTest"), net.minecraft.server.level.ClientInformation.createDefault());
        try { test.accept(player, ArcQuestPlayerManager.getOrCreate(player)); }
        finally {
            ArcQuestPlayerManager.unload(player.getUUID());
            PlayerSessionEpochManager.endSession(player.getUUID());
        }
        helper.succeed();
    }
}

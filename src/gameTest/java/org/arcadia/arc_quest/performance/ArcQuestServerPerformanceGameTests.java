package org.arcadia.arc_quest.performance;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.integration.jei.api.JeiCatalogEntry;
import org.arcadia.arc_quest.integration.jei.network.JeiCatalogCodec;
import org.arcadia.arc_quest.integration.jei.quest.JeiQuestHistory;
import org.arcadia.arc_quest.integration.jei.quest.QuestJeiCatalogProvider;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.builder.ObjectiveBuilder;
import org.arcadia.arc_quest.quest.builder.PhaseBuilder;
import org.arcadia.arc_quest.quest.builder.QuestBuilder;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.logic.ObjectiveRequiredCounts;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import org.arcadia.arc_quest.questplayer.PlayerSessionEpochManager;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bounded workload probes on the real server-side projection implementations.
 * Players have separate real ArcQ sessions but are not in PlayerList and have no
 * connections. These probes do not measure ServerTick scheduling, packet sending,
 * disk throughput or real multiplayer capacity. Wall-clock timings are diagnostic,
 * never hardware-dependent pass/fail thresholds. All registry changes are restored.
 */
@GameTestHolder(Arc_Quest.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("deprecation")
public final class ArcQuestServerPerformanceGameTests {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TEMPLATE = "jei_empty";
    private static final String BATCH = "arc_quest.performance";
    private static final String PHASE = "workload";
    private static final int REPEATS = 3;

    private ArcQuestServerPerformanceGameTests() {}

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void boundedThresholdRefreshKeepsPlayersIsolated(GameTestHelper helper) {
        for (Scale scale : List.of(new Scale(1, 4, 4), new Scale(4, 16, 8))) {
            withWorkload(helper, scale, false, workload -> {
                long started = System.nanoTime();
                for (int p = 0; p < workload.players().size(); p++) {
                    ObjectiveRequiredCounts.refreshAll(workload.players().get(p), workload.data().get(p));
                }
                long initialNanos = System.nanoTime() - started;
                int initialCalls = workload.countCalls().get();
                helper.assertTrue(initialCalls == scale.players() * scale.quests() * scale.objectives(),
                        "Initial projection evaluated a dynamic objective more than once or skipped it");

                started = System.nanoTime();
                for (int pass = 0; pass < REPEATS; pass++) {
                    for (int p = 0; p < workload.players().size(); p++) {
                        ServerPlayer player = workload.players().get(p);
                        ArcQuestPlayer data = workload.data().get(p);
                        for (QuestRuntimeData runtime : data.getAllActiveQuests().values()) {
                            helper.assertTrue(!ObjectiveRequiredCounts.refresh(player, data, runtime),
                                    "Unchanged workload incorrectly requested another quest-state sync");
                        }
                    }
                }
                long repeatedNanos = System.nanoTime() - started;
                int repeatCalls = workload.countCalls().get() - initialCalls;
                helper.assertTrue(repeatCalls <= REPEATS * initialCalls,
                        "Repeated projection amplified dynamic-count evaluation beyond one call per objective");

                for (int p = 0; p < workload.players().size(); p++) {
                    for (QuestRuntimeData runtime : workload.data().get(p).getAllActiveQuests().values()) {
                        for (int objective = 0; objective < scale.objectives(); objective++) {
                            helper.assertTrue(runtime.getRequiredCount(PHASE, objective, -1) == 101 + p,
                                    "A player's threshold leaked into another player's runtime");
                            helper.assertTrue(runtime.getObjectiveProgress(PHASE, objective) == 0,
                                    "Read-only threshold projection changed business progress");
                        }
                    }
                }
                LOGGER.info("[ArcQuestServerPerformance] case=thresholds players={} questsPerPlayer={} objectivesPerQuest={} repeatedPasses={} initialCallbacks={} repeatCallbacks={} initialNanos={} repeatedNanos={} mode=synthetic_server_objects_no_network",
                        scale.players(), scale.quests(), scale.objectives(), REPEATS, initialCalls, repeatCalls,
                        initialNanos, repeatedNanos);
            });
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void boundedQuestCatalogRebuildStaysStableAndPlayerSpecific(GameTestHelper helper) {
        for (Scale scale : List.of(new Scale(1, 4, 4), new Scale(4, 16, 8))) {
            withWorkload(helper, scale, true, workload -> {
                var provider = new QuestJeiCatalogProvider();
                List<byte[]> baselines = new ArrayList<>();
                List<net.minecraft.nbt.CompoundTag> businessState = new ArrayList<>();
                long bytesPerPass = 0;
                long projectionNanos = 0;
                long encodingNanos = 0;
                for (int p = 0; p < workload.players().size(); p++) {
                    ServerPlayer player = workload.players().get(p);
                    ArcQuestPlayer data = workload.data().get(p);
                    businessState.add(data.serializeNBT());
                    var rows = new ArrayList<JeiCatalogEntry>();
                    long started = System.nanoTime();
                    provider.collect(player, data, rows::add);
                    projectionNanos += System.nanoTime() - started;
                    helper.assertTrue(rows.size() == scale.quests() * scale.objectives(),
                            "Quest catalog lost workload entries");
                    final int expectedAmount = 101 + p;
                    helper.assertTrue(rows.stream().allMatch(row -> row.inputs().size() == 1
                                    && row.inputs().get(0).amount() == expectedAmount),
                            "Player-dependent quantities were lost in catalog projection");
                    helper.assertTrue(rows.stream().anyMatch(row -> row.inputs().get(0).alternatives().size() > 1),
                            "Loaded tag alternatives were not exercised by this workload");
                    started = System.nanoTime();
                    byte[] encoded = JeiCatalogCodec.encode(rows);
                    encodingNanos += System.nanoTime() - started;
                    helper.assertTrue(JeiCatalogCodec.decode(encoded).size() == rows.size(),
                            "Real catalog codec lost workload entries");
                    baselines.add(encoded);
                    bytesPerPass += encoded.length;
                }
                int initialCallbacks = workload.countCalls().get();
                long repeatProjectionNanos = 0;
                long repeatEncodingNanos = 0;
                for (int pass = 0; pass < REPEATS; pass++) {
                    for (int p = 0; p < workload.players().size(); p++) {
                        var rows = new ArrayList<JeiCatalogEntry>();
                        long started = System.nanoTime();
                        provider.collect(workload.players().get(p), workload.data().get(p), rows::add);
                        repeatProjectionNanos += System.nanoTime() - started;
                        started = System.nanoTime();
                        byte[] encoded = JeiCatalogCodec.encode(rows);
                        repeatEncodingNanos += System.nanoTime() - started;
                        helper.assertTrue(Arrays.equals(encoded, baselines.get(p)),
                                "Unchanged quest catalog generated a different full snapshot");
                    }
                }
                for (int p = 0; p < workload.players().size(); p++) {
                    helper.assertTrue(businessState.get(p).equals(workload.data().get(p).serializeNBT()),
                            "JEI projection mutated a player's business state");
                    if (p > 0) helper.assertTrue(!Arrays.equals(baselines.get(p - 1), baselines.get(p)),
                            "Different authorized player quantities shared the same catalog");
                }
                LOGGER.info("[ArcQuestServerPerformance] case=quest_catalog players={} questsPerPlayer={} objectivesPerQuest={} repeatedPasses={} initialCallbacks={} repeatCallbacks={} bytesPerFullPass={} projectionNanos={} encodingNanos={} repeatProjectionNanos={} repeatEncodingNanos={} mode=synthetic_server_objects_no_network",
                        scale.players(), scale.quests(), scale.objectives(), REPEATS, initialCallbacks,
                        workload.countCalls().get() - initialCallbacks, bytesPerPass, projectionNanos, encodingNanos,
                        repeatProjectionNanos, repeatEncodingNanos);
            });
        }
        helper.succeed();
    }

    private static void withWorkload(GameTestHelper helper, Scale scale, boolean useTags,
                                     java.util.function.Consumer<Workload> test) {
        var previous = QuestRegistry.getDatapackSnapshot();
        var installed = new LinkedHashMap<>(previous);
        var definitions = new ArrayList<QuestDefinition>();
        var players = new ArrayList<ServerPlayer>();
        var states = new ArrayList<ArcQuestPlayer>();
        AtomicInteger calls = new AtomicInteger();
        try {
            for (int quest = 0; quest < scale.quests(); quest++) {
                var phase = PhaseBuilder.create(PHASE);
                for (int objective = 0; objective < scale.objectives(); objective++) {
                    var builder = useTags && objective % 2 == 0
                            ? ObjectiveBuilder.collectTag(ResourceLocation.parse("minecraft:logs"), 1)
                            : ObjectiveBuilder.collect(Items.APPLE, 1);
                    phase.objective(builder.countModifier(player -> {
                        calls.incrementAndGet();
                        return 100 + player.experienceLevel;
                    }));
                }
                QuestDefinition definition = QuestBuilder.create("arc_quest:gametest_server_perf_" + quest)
                        .phase(phase).build();
                definitions.add(definition);
                installed.put(definition.getId(), definition);
            }
            QuestRegistry.replaceDatapackSnapshot(installed);
            for (int p = 0; p < scale.players(); p++) {
                var level = helper.getLevel();
                var player = new ServerPlayer(level.getServer(), level,
                        new GameProfile(UUID.randomUUID(), "ArcQPerf" + p));
                players.add(player);
                player.experienceLevel = p + 1;
                ArcQuestPlayer data = ArcQuestPlayerManager.getOrCreate(player);
                states.add(data);
                for (QuestDefinition definition : definitions) {
                    data.addActiveQuest(new QuestRuntimeData(definition.getId().toString(), PHASE,
                            scale.objectives(), 0L, 0L, 0L));
                }
            }
            test.accept(new Workload(players, states, calls));
        } finally {
            try {
                for (ServerPlayer player : players) {
                    try {
                        if (useTags) JeiQuestHistory.get(player).retain(player, Set.of());
                    } finally {
                        ArcQuestPlayerManager.unload(player.getUUID());
                        PlayerSessionEpochManager.endSession(player.getUUID());
                        player.invalidateCaps();
                    }
                }
            } finally {
                QuestRegistry.replaceDatapackSnapshot(previous);
            }
        }
    }

    private record Scale(int players, int quests, int objectives) {}
    private record Workload(List<ServerPlayer> players, List<ArcQuestPlayer> data, AtomicInteger countCalls) {}
}

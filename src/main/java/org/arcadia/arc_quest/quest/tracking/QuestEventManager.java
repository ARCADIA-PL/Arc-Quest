package org.arcadia.arc_quest.quest.tracking;
import org.arcadia.arc_quest.util.log.ArcQuestLog;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.logic.QuestProgressHandler;
import org.arcadia.arc_quest.quest.logic.CollectionRecordService;
import org.arcadia.arc_quest.quest.logic.QuestEventSettlement;
import org.arcadia.arc_quest.quest.logic.CollectionRunAccess;
import org.arcadia.arc_quest.quest.logic.profile.collection.CollectionObjectiveDispatcher;
import org.arcadia.arc_quest.quest.logic.profile.collection.CollectionObjectiveBinding;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.questmarker.runtime.QuestMarkerRuntimeManager;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;

import java.util.*;

@EventBusSubscriber(modid = Arc_Quest.MOD_ID)
public final class QuestEventManager {
    private static final Object2ObjectOpenHashMap<String, int[]> reachLocationIndexCache = new Object2ObjectOpenHashMap<>();

    /**
     * 玩家背包物品快照，用于通过差异检测新获得的物品。
     */
    private static final Map<UUID, Map<ResourceLocation, Integer>> inventorySnapshots = new HashMap<>();

    private QuestEventManager() {
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getSource().getEntity() == null) return;
        Entity source = event.getSource().getEntity();
        if (!(source instanceof ServerPlayer player)) return;

        Entity killed = event.getEntity();
        if (killed instanceof Player) return;

        ResourceLocation entityTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(killed.getType());
        if (entityTypeId == null) return;

        processMatch(player, ObjectiveType.KILL, entityTypeId, 1);
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onItemPickup(ItemEntityPickupEvent.Post event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (QuestEventSettlement.isGrantingReward(player)) return;

        ItemStack original = event.getOriginalStack();
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(original.getItem());
        if (itemId == null) return;

        // This post-pickup event reports the amount actually acquired, including partial pickup.
        int count = original.getCount() - event.getCurrentStack().getCount();
        if (count <= 0) return;
        processMatch(player, ObjectiveType.COLLECT, itemId, count);

        // 同步快照，避免 tick diff 重复计数
        Map<ResourceLocation, Integer> snap = inventorySnapshots.get(player.getUUID());
        if (snap != null) {
            snap.merge(itemId, count, Integer::sum);
        }
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Map<ResourceLocation, Integer> snapshot = takeInventorySnapshot(player);
            inventorySnapshots.put(player.getUUID(), snapshot);
            // Capability/session loading can run later in the same login event.
            player.server.execute(() -> CollectionRecordService.discoverInventory(player, snapshot));
        }
    }

    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        if (event.getUpdateCause() != TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        server.execute(() -> {
            CollectionRecordService.rebuildIndex();
            reachLocationIndexCache.clear();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                ArcQuestPlayer data = ArcQuestPlayerManager.get(player);
                if (data != null) QuestProgressHandler.rebuildTrackingIndex(player, data);
            }
        });
    }

    // ═══════════════════════════════════════════════════════
    //  快照工具方法
    // ═══════════════════════════════════════════════════════

    /**
     * 对玩家背包及鼠标持有物品（不含装备栏）生成物品计数快照。
     */
    private static Map<ResourceLocation, Integer> takeInventorySnapshot(ServerPlayer player) {
        Map<ResourceLocation, Integer> snapshot = new HashMap<>();
        for (ItemStack stack : player.getInventory().items) {
            if (stack.isEmpty()) continue;
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (id != null) {
                snapshot.merge(id, stack.getCount(), Integer::sum);
            }
        }
        // Craft output can stay on the cursor across ticks before being put into the inventory.
        ItemStack carried = player.containerMenu.getCarried();
        if (!carried.isEmpty()) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(carried.getItem());
            if (id != null) snapshot.merge(id, carried.getCount(), Integer::sum);
        }
        return snapshot;
    }

    /** Used by reward transactions to exclude their own newly granted stacks from legacy acquisition diffs. */
    public static Map<ResourceLocation, Integer> inventorySnapshot(ServerPlayer player) {
        return takeInventorySnapshot(player);
    }

    public static void excludeRewardAcquisitions(ServerPlayer player, Map<ResourceLocation, Integer> before) {
        Map<ResourceLocation, Integer> baseline = inventorySnapshots.get(player.getUUID());
        if (baseline == null) return;
        Map<ResourceLocation, Integer> after = takeInventorySnapshot(player);
        for (var entry : after.entrySet()) {
            int granted = entry.getValue() - before.getOrDefault(entry.getKey(), 0);
            if (granted > 0) baseline.merge(entry.getKey(), granted, Integer::sum);
        }
    }

    /**
     * 对比前后快照，对数量增加的所有物品触发 COLLECT。
     */
    private static void diffAndTriggerCollect(ServerPlayer player,
                                               Map<ResourceLocation, Integer> before,
                                               Map<ResourceLocation, Integer> after) {
        List<QuestEventPlan.Signal> signals = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Integer> entry : after.entrySet()) {
            ResourceLocation itemId = entry.getKey();
            int afterCount = entry.getValue();
            int beforeCount = before.getOrDefault(itemId, 0);
            int delta = afterCount - beforeCount;
            if (delta > 0) {
                signals.add(new QuestEventPlan.Signal(ObjectiveType.COLLECT, itemId, delta, false));
            }
        }
        processSignals(player, signals);
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (QuestEventSettlement.isGrantingReward(player)) return;

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(event.getCrafting().getItem());
        if (itemId == null) return;

        int count = event.getCrafting().getCount();
        processSignals(player, List.of(
                new QuestEventPlan.Signal(ObjectiveType.CRAFT, itemId, count, true),
                new QuestEventPlan.Signal(ObjectiveType.COLLECT, itemId, count, true)));
        // The same crafted stack can be seen again by the inventory diff on the next tick.
        Map<ResourceLocation, Integer> snapshot = inventorySnapshots.get(player.getUUID());
        if (snapshot != null) snapshot.merge(itemId, count, Integer::sum);
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onRightClickEntity(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Entity target = event.getTarget();
        if (target == null) return;

        ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
        if (typeId != null) processMatch(player, ObjectiveType.INTERACT, typeId, 1);
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var block = player.level().getBlockState(event.getPos()).getBlock();
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(block);
        if (blockId != null) processMatch(player, ObjectiveType.INTERACT, blockId, 1);
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if ((player.tickCount % 20) != 0) return;

        // ── COLLECT 背包 diff 检测 ──
        // 每 20 tick 对比背包快照，增量即视为"获得"，包括从容器、合成、钓鱼等所有来源
        {
            Map<ResourceLocation, Integer> before = inventorySnapshots.get(player.getUUID());
            Map<ResourceLocation, Integer> after = takeInventorySnapshot(player);
            // A completion reward can amend this baseline while COLLECT settles; install it first.
            inventorySnapshots.put(player.getUUID(), after);
            if (before != null) {
                diffAndTriggerCollect(player, before, after);
            }
        }

        ArcQuestPlayer data = ArcQuestPlayerManager.get(player);
        if (data == null) return;
        refreshPossessionObjectives(player);
        ArcQuestNetwork.syncRequiredCounts(player, data);
        var locationRecipients = freezeLocationRecipients(player, data);
        var locationRecords = CollectionRecordService.freezeLocationRules(player);
        try (var scope = QuestEventSettlement.begin(player)) {
            CollectionRecordService.applyFrozen(player, locationRecords, 1);
            for (var recipient : locationRecipients) recipient.apply(player, data);
        }
    }

    /** One position sample cannot fall through into a phase unlocked by that same sample. */
    private static List<LocationRecipient> freezeLocationRecipients(ServerPlayer player, ArcQuestPlayer data) {
        List<LocationRecipient> recipients = new ArrayList<>();
        for (QuestRuntimeData qdata : List.copyOf(data.getAllActiveQuests().values())) {
            if (qdata.getState() != QuestState.ACTIVE) continue;

            var def = CollectionRunAccess.resolve(player.server, qdata);
            if (def == null) continue;

            for (String phaseId : List.copyOf(qdata.getActivePhaseIds())) {
                var phase = def.getPhase(phaseId);
                if (phase == null) continue;

                String cacheKey = def.getId() + ":" + qdata.getFrozenDefinitionHash() + ":" + phaseId;
                int[] reachIndices = reachLocationIndexCache.get(cacheKey);
                if (reachIndices == null) {
                    reachIndices = computeReachLocationIndices(phase);
                    reachLocationIndexCache.put(cacheKey, reachIndices);
                }
                if (reachIndices.length == 0) continue;

                for (int idx : reachIndices) {
                    var obj = phase.getObjectives().get(idx);
                    int required = QuestProgressHandler.resolveRequiredCount(player, obj, data);
                    if (qdata.getObjectiveProgress(phaseId, idx) >= required) continue;

                    Double x = parseDouble(obj.getExtra("x"));
                    Double y = parseDouble(obj.getExtra("y"));
                    Double z = parseDouble(obj.getExtra("z"));
                    int radius = obj.getExtraInt("radius", 4);
                    if (x == null || y == null || z == null) continue;

                    String dim = firstNonEmpty(obj.getExtra("dimension"), obj.getExtra("dim"), obj.getExtra("world"));
                    if (!dim.isEmpty() && !player.level().dimension().location().toString().equals(dim)) continue;

                    double dx = player.getX() - x;
                    double dy = player.getY() - y;
                    double dz = player.getZ() - z;
                    if ((dx * dx + dy * dy + dz * dz) <= (double) radius * radius)
                        recipients.add(new LocationRecipient(qdata, def, phaseId, idx, required));
                }
            }
        }
        return List.copyOf(recipients);
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ObjectiveTracker.INSTANCE.unregisterPlayer(player.getUUID());
            ArcQuestNetwork.clearPlayerMarkerState(player.getUUID());
            QuestMarkerRuntimeManager.clearPlayer(player.getUUID());
            inventorySnapshots.remove(player.getUUID());
            ArcQuestLog.debug(ArcQuestLog.Category.QUEST_PROGRESS, "Cleared tracking and marker state for: {}", player.getGameProfile().getName());
        }
    }

    private static int[] computeReachLocationIndices(PhaseDefinition phase) {
        var objs = phase.getObjectives();
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < objs.size(); i++) {
            if (ObjectiveType.REACH_LOCATION.equals(objs.get(i).getType())) {
                indices.add(i);
            }
        }
        return indices.stream().mapToInt(Integer::intValue).toArray();
    }

    private static void processMatch(ServerPlayer player,
                                     ObjectiveType type,
                                     ResourceLocation targetId,
                                     int amount) {
        processSignals(player, List.of(new QuestEventPlan.Signal(type, targetId, amount, false)));
    }

    private static void processSignals(ServerPlayer player, List<QuestEventPlan.Signal> signals) {
        if (signals.isEmpty() || QuestEventSettlement.isGrantingReward(player)) return;
        ArcQuestPlayer data = ArcQuestPlayerManager.get(player);
        if (data == null) return;
        // Freeze ALL signals first. Crafting must not advance the phase before its COLLECT signal is delivered.
        var recipients = QuestEventPlan.freeze(data, signals,
                runtime -> CollectionRunAccess.resolve(player.server, runtime));
        var permanent = signals.stream().map(signal -> CollectionRecordService.freezeRules(
                player, signal.type(), signal.target(), signal.crafted())).toList();
        List<LegacyRecipient> legacy = new ArrayList<>();
        for (var signal : signals) {
            if (signal.amount() <= 0) continue;
            var key = new ObjectiveKey(signal.type(), signal.target());
            for (var binding : CollectionObjectiveDispatcher.findBindings(data, key)) {
                if (signal.type().equals(ObjectiveType.COLLECT)
                        && !CollectMode.from(binding.getObjectiveEntry()).acceptsAcquisition(signal.crafted())) continue;
                legacy.add(new LegacyRecipient(data.getActiveQuest(binding.getQuestId()), binding, signal));
            }
        }
        try (var scope = QuestEventSettlement.begin(player)) {
            for (int i = 0; i < signals.size(); i++) {
                CollectionRecordService.applyFrozen(player, permanent.get(i), signals.get(i).amount());
            }
            for (var recipient : legacy) {
                var current = ArcQuestPlayerManager.get(player);
                if (current != null) recipient.apply(player, current);
            }
            for (var recipient : recipients) {
                var current = ArcQuestPlayerManager.get(player);
                if (current == null || !recipient.stillEligible(current)) continue;
                var ref = recipient.reference();
                QuestProgressHandler.incrementObjective(player,
                        ref.questId().toString(), ref.phaseId(), ref.objIndex(), recipient.amount());
            }
        }
    }

    /** Current holdings are an absolute preparation check, including declines before completion. */
    public static void refreshPossessionObjectives(ServerPlayer player) {
        var data = ArcQuestPlayerManager.get(player);
        if (data == null) return;
        var snapshot = takeInventorySnapshot(player);
        try (var scope = QuestEventSettlement.begin(player)) {
            for (var runtime : List.copyOf(data.getAllActiveQuests().values())) {
                if (runtime.getState() != QuestState.ACTIVE) continue;
                var definition = CollectionRunAccess.resolve(player.server, runtime);
                if (definition == null || definition.isCollectionQuest() && !definition.hasCollectionSheets()) continue;
                for (String phaseId : runtime.getActivePhaseIds()) {
                    var phase = definition.getPhase(phaseId);
                    if (phase == null) continue;
                    for (int i = 0; i < phase.getObjectives().size(); i++) {
                        var objective = phase.getObjectives().get(i);
                        if (!objective.getType().equals(ObjectiveType.COLLECT)
                                || CollectMode.from(objective) != CollectMode.POSSESSION) continue;
                        int held = possessionCount(objective, snapshot, runtime);
                        QuestProgressHandler.setPossessionProgress(player, runtime.getQuestId(), phaseId, i, held);
                    }
                }
            }
        }
    }

    static int possessionCount(ObjectiveEntry objective, Map<ResourceLocation, Integer> snapshot) {
        return possessionCount(objective, snapshot, null);
    }

    static int possessionCount(ObjectiveEntry objective, Map<ResourceLocation, Integer> snapshot, QuestRuntimeData runtime) {
        long total = 0;
        for (var entry : snapshot.entrySet()) {
            if (ObjectiveItemResolver.matches(objective, entry.getKey(), runtime)) total += Math.max(0, entry.getValue());
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    private record LegacyRecipient(QuestRuntimeData run, CollectionObjectiveBinding binding,
                                   QuestEventPlan.Signal signal) {
        void apply(ServerPlayer player, ArcQuestPlayer data) {
            if (run == null || data.getActiveQuest(binding.getQuestId()) != run || run.getState() != QuestState.ACTIVE) return;
            var collection = run.getCollectionData();
            if (collection == null || !collection.isVisible(binding.getPhaseId())) return;
            if (run.isPhaseCompleted(binding.getPhaseId())
                    && !binding.isRepeatableProgress() && !binding.isRepeatableCompletion()) return;
            switch (binding.getCountingMode()) {
                case BINARY -> QuestProgressHandler.incrementCollectionEntry(
                        player, binding.getQuestId(), binding.getPhaseId(), 1);
                case ACCUMULATE -> QuestProgressHandler.incrementCollectionEntry(
                        player, binding.getQuestId(), binding.getPhaseId(), signal.amount());
                case UNIQUE_SET -> QuestProgressHandler.addCollectionUniqueKey(player, binding.getQuestId(),
                        binding.getPhaseId(), collectionUniqueKey(signal.type(), signal.target()));
            }
        }
    }

    private record LocationRecipient(QuestRuntimeData run, QuestDefinition definition, String phaseId,
                                     int objectiveIndex, int required) {
        void apply(ServerPlayer player, ArcQuestPlayer data) {
            if (data.getActiveQuest(run.getQuestId()) != run || run.getState() != QuestState.ACTIVE
                    || !run.isPhaseActive(phaseId)) return;
            var phase = definition.getPhase(phaseId);
            var objective = phase.getObjectives().get(objectiveIndex);
            if (definition.isCollectionQuest() && !definition.hasCollectionSheets()) {
                var config = phase.getCollectionEntryConfig();
                if (config != null && config.getCountingMode() == CountingMode.UNIQUE_SET)
                    QuestProgressHandler.addCollectionUniqueKey(player, run.getQuestId(), phaseId,
                            collectionUniqueKey(ObjectiveType.REACH_LOCATION, objective.getTargetId()));
                else QuestProgressHandler.incrementCollectionEntry(player, run.getQuestId(), phaseId, 1);
            } else {
                var reference = new ObjectiveTypeIndex.ObjectiveRef(definition.getId(), phaseId, objectiveIndex);
                if (!QuestEventPlan.objectiveFinalized(run, reference, definition))
                    QuestProgressHandler.incrementObjective(player, run.getQuestId(), phaseId, objectiveIndex, 1, required);
            }
        }
    }

    public static void notifyTalk(ServerPlayer player, ResourceLocation npcId) {
        Objects.requireNonNull(player);
        Objects.requireNonNull(npcId);
        processMatch(player, ObjectiveType.TALK, npcId, 1);
    }

    public static void notifyExplore(ServerPlayer player, ResourceLocation locationId) {
        Objects.requireNonNull(player);
        Objects.requireNonNull(locationId);
        processMatch(player, ObjectiveType.REACH_LOCATION, locationId, 1);
    }

    public static void notifyInteract(ServerPlayer player, ResourceLocation targetId) {
        Objects.requireNonNull(player);
        Objects.requireNonNull(targetId);
        processMatch(player, ObjectiveType.INTERACT, targetId, 1);
    }

    public static void notifyCustom(ServerPlayer player, ResourceLocation customId, int amount) {
        Objects.requireNonNull(player);
        Objects.requireNonNull(customId);
        if (amount <= 0) return;
        processMatch(player, ObjectiveType.CUSTOM, customId, amount);
    }

    public static void notifyCustom(ServerPlayer player, ResourceLocation customId) {
        notifyCustom(player, customId, 1);
    }

    public static void broadcastExplore(Set<UUID> players,
                                        ResourceLocation locationId,
                                        MinecraftServer server) {
        Objects.requireNonNull(players);
        Objects.requireNonNull(locationId);
        for (UUID uuid : players) {
            ServerPlayer sp = server.getPlayerList().getPlayer(uuid);
            if (sp != null) notifyExplore(sp, locationId);
        }
    }

    private static String collectionUniqueKey(ObjectiveType type, ResourceLocation targetId) {
        return type.getId() + ":" + targetId;
    }

    private static String firstNonEmpty(String... candidates) {
        for (String s : candidates) {
            if (s != null && !s.isEmpty()) return s;
        }
        return "";
    }

    private static Double parseDouble(String v) {
        if (v == null || v.isEmpty()) return null;
        try {
            return Double.parseDouble(v);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}


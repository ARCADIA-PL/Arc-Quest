package org.arcadia.arc_quest.quest.tracking;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.core.registries.BuiltInRegistries;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.logic.QuestProgressHandler;
import org.arcadia.arc_quest.quest.logic.profile.collection.CollectionObjectiveDispatcher;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.slf4j.Logger;

import java.util.*;

@EventBusSubscriber(modid = Arc_Quest.MOD_ID)
public final class QuestEventManager {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Object2ObjectOpenHashMap<String, int[]> reachLocationIndexCache = new Object2ObjectOpenHashMap<>();

    /**
     * Player inventory snapshot used to detect newly acquired items by diff.
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
    public static void onItemPickup(ItemEntityPickupEvent.Pre event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(event.getItemEntity().getItem().getItem());
        if (itemId == null) return;

        int count = event.getItemEntity().getItem().getCount();
        processMatch(player, ObjectiveType.COLLECT, itemId, count);

        Map<ResourceLocation, Integer> snapshot = inventorySnapshots.get(player.getUUID());
        if (snapshot != null) {
            snapshot.merge(itemId, count, Integer::sum);
        }
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            inventorySnapshots.put(player.getUUID(), takeInventorySnapshot(player));
        }
    }

    private static Map<ResourceLocation, Integer> takeInventorySnapshot(ServerPlayer player) {
        Map<ResourceLocation, Integer> snapshot = new HashMap<>();
        for (ItemStack stack : player.getInventory().items) {
            if (stack.isEmpty()) continue;
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (id != null) {
                snapshot.merge(id, stack.getCount(), Integer::sum);
            }
        }
        return snapshot;
    }

    private static void diffAndTriggerCollect(ServerPlayer player,
                                              Map<ResourceLocation, Integer> before,
                                              Map<ResourceLocation, Integer> after) {
        for (Map.Entry<ResourceLocation, Integer> entry : after.entrySet()) {
            ResourceLocation itemId = entry.getKey();
            int delta = entry.getValue() - before.getOrDefault(itemId, 0);
            if (delta > 0) {
                processMatch(player, ObjectiveType.COLLECT, itemId, delta);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(event.getCrafting().getItem());
        if (itemId == null) return;

        int count = event.getCrafting().getCount();
        processMatch(player, ObjectiveType.CRAFT, itemId, count);
        processMatch(player, ObjectiveType.COLLECT, itemId, count);
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

        Map<ResourceLocation, Integer> before = inventorySnapshots.get(player.getUUID());
        Map<ResourceLocation, Integer> after = takeInventorySnapshot(player);
        if (before != null) {
            diffAndTriggerCollect(player, before, after);
        }
        inventorySnapshots.put(player.getUUID(), after);

        ArcQuestPlayer data = ArcQuestPlayerManager.get(player);
        if (data == null) return;
        for (QuestRuntimeData qdata : data.getAllActiveQuests().values()) {
            if (qdata.getState() != QuestState.ACTIVE) continue;

            var def = QuestRegistry.get(ResourceLocation.parse(qdata.getQuestId()));
            if (def == null) continue;

            for (String phaseId : qdata.getActivePhaseIds()) {
                var phase = def.getPhase(phaseId);
                if (phase == null) continue;

                String cacheKey = def.getId() + ":" + phaseId;
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
                    if ((dx * dx + dy * dy + dz * dz) <= (radius * radius)) {
                        if (def.isCollectionQuest()) {
                            var entryConfig = phase.getCollectionEntryConfig();
                            if (entryConfig != null && entryConfig.getCountingMode() == CountingMode.UNIQUE_SET) {
                                QuestProgressHandler.addCollectionUniqueKey(player, qdata.getQuestId(), phaseId, collectionUniqueKey(ObjectiveType.REACH_LOCATION, obj.getTargetId()));
                            } else {
                                QuestProgressHandler.incrementCollectionEntry(player, qdata.getQuestId(), phaseId, 1);
                            }
                        } else {
                            QuestProgressHandler.incrementObjective(player, qdata.getQuestId(), phaseId, idx, 1);
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ObjectiveTracker.INSTANCE.unregisterPlayer(player.getUUID());
            ArcQuestNetwork.clearPlayerMarkerState(player.getUUID());
            inventorySnapshots.remove(player.getUUID());
            LOGGER.debug("[QuestEvent] Cleared tracking and marker state for: {}", player.getGameProfile().getName());
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
        if (amount <= 0) return;

        ObjectiveKey key = new ObjectiveKey(type, targetId);
        CollectionObjectiveDispatcher.dispatch(player, key, amount, collectionUniqueKey(type, targetId));

        ArcQuestPlayer data = ArcQuestPlayerManager.get(player);
        if (data == null) return;

        ObjectiveTypeIndex index = QuestRegistry.getObjectiveIndex();
        List<ObjectiveTypeIndex.ObjectiveRef> refs = index.find(type, targetId);
        if (refs == null) return;

        for (ObjectiveTypeIndex.ObjectiveRef ref : refs) {
            QuestRuntimeData qdata = data.getActiveQuest(ref.questId().toString());
            if (qdata == null || qdata.getState() != QuestState.ACTIVE) continue;
            if (!qdata.isPhaseActive(ref.phaseId())) continue;

            QuestDefinition def = QuestRegistry.get(ref.questId());
            if (def == null || def.isCollectionQuest()) continue;

            int required = def.getPhase(ref.phaseId()).getObjectives().get(ref.objIndex()).getRequiredCount();
            int current = qdata.getObjectiveProgress(ref.phaseId(), ref.objIndex());
            if (current >= required) continue;

            int add = Math.min(amount, required - current);
            QuestProgressHandler.incrementObjective(player,
                    ref.questId().toString(), ref.phaseId(), ref.objIndex(), add);
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


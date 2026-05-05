package org.arcadia.arc_quest.quest.tracking;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.quest.api.CountingMode;
import org.arcadia.arc_quest.quest.api.ObjectiveType;
import org.arcadia.arc_quest.quest.api.QuestState;
import org.arcadia.arc_quest.quest.capability.IQuestCapability;
import org.arcadia.arc_quest.quest.capability.QuestCapabilityProvider;
import org.arcadia.arc_quest.quest.capability.QuestRuntimeData;
import org.arcadia.arc_quest.quest.logic.QuestProgressHandler;
import org.arcadia.arc_quest.quest.logic.profile.collection.CollectionObjectiveDispatcher;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.slf4j.Logger;

import java.util.*;

@Mod.EventBusSubscriber(modid = Arc_Quest.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class QuestEventManager {

    private static final Logger LOGGER = LogUtils.getLogger();

    private QuestEventManager() {
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getSource().getEntity() == null) return;
        Entity source = event.getSource().getEntity();
        if (!(source instanceof ServerPlayer player)) return;

        Entity killed = event.getEntity();
        if (killed instanceof Player) return;

        ResourceLocation entityTypeId = ForgeRegistries.ENTITY_TYPES.getKey(killed.getType());
        if (entityTypeId == null) return;

        processMatch(player, ObjectiveType.KILL, entityTypeId, 1);
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onItemPickup(EntityItemPickupEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(event.getItem().getItem().getItem());
        if (itemId == null) return;

        int count = event.getItem().getItem().getCount();
        processMatch(player, ObjectiveType.COLLECT, itemId, count);
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(event.getCrafting().getItem());
        if (itemId == null) return;

        int count = event.getCrafting().getCount();
        processMatch(player, ObjectiveType.CRAFT, itemId, count);
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onRightClickEntity(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Entity target = event.getTarget();
        if (target == null) return;

        ResourceLocation typeId = ForgeRegistries.ENTITY_TYPES.getKey(target.getType());
        if (typeId != null) processMatch(player, ObjectiveType.INTERACT, typeId, 1);
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var block = player.level().getBlockState(event.getPos()).getBlock();
        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(block);
        if (blockId != null) processMatch(player, ObjectiveType.INTERACT, blockId, 1);
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        if ((player.tickCount % 20) != 0) return;

        IQuestCapability cap = QuestCapabilityProvider.getOrNull(player);
        for (QuestRuntimeData data : cap.getAllActiveQuests().values()) {
            if (data.getState() != QuestState.ACTIVE) continue;

            var def = QuestRegistry.get(ResourceLocation.parse(data.getQuestId()));
            if (def == null) continue;

            for (String phaseId : data.getActivePhaseIds()) {
                var phase = def.getPhase(phaseId);
                if (phase == null) continue;

                var objs = phase.getObjectives();
                for (int i = 0; i < objs.size(); i++) {
                    var obj = objs.get(i);
                    if (obj.getType() != ObjectiveType.REACH_LOCATION) continue;
                    int required = QuestProgressHandler.resolveRequiredCount(player, obj, cap);
                    if (data.getObjectiveProgress(phaseId, i) >= required) continue;

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
                                QuestProgressHandler.addCollectionUniqueKey(player, data.getQuestId(), phaseId, collectionUniqueKey(ObjectiveType.REACH_LOCATION, obj.getTargetId()));
                            } else {
                                QuestProgressHandler.incrementCollectionEntry(player, data.getQuestId(), phaseId, 1);
                            }
                        } else {
                            QuestProgressHandler.incrementObjective(player, data.getQuestId(), phaseId, i, 1);
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
            LOGGER.debug("[QuestEvent] Cleared tracking for: {}", player.getGameProfile().getName());
        }
    }

    private static void processMatch(ServerPlayer player,
                                     ObjectiveType type,
                                     ResourceLocation targetId,
                                     int amount) {
        if (amount <= 0) return;

        ObjectiveKey key = new ObjectiveKey(type, targetId);
        CollectionObjectiveDispatcher.dispatch(player, key, amount, collectionUniqueKey(type, targetId));
        Set<TrackedObjective> matches = ObjectiveTracker.INSTANCE.lookup(key);
        if (matches.isEmpty()) return;

        Map<PhaseGroupKey, List<TrackedObjective>> grouped = new HashMap<>();
        UUID playerId = player.getUUID();

        for (TrackedObjective tracked : matches) {
            if (!tracked.getPlayerId().equals(playerId)) continue;
            PhaseGroupKey groupKey = new PhaseGroupKey(tracked.getQuestId().toString(), tracked.getPhaseId());
            grouped.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(tracked);
        }

        if (grouped.isEmpty()) return;

        for (Map.Entry<PhaseGroupKey, List<TrackedObjective>> entry : grouped.entrySet()) {
            PhaseGroupKey groupKey = entry.getKey();
            List<TrackedObjective> objectives = entry.getValue();
            objectives.sort(Comparator.comparingInt(TrackedObjective::getObjectiveIndex));
            distributeAmountInPhase(player, groupKey.questId(), groupKey.phaseId(), objectives, amount);
        }
    }

    private static void distributeAmountInPhase(ServerPlayer player,
                                                String questId,
                                                String phaseId,
                                                List<TrackedObjective> objectives,
                                                int amount) {
        if (amount <= 0 || objectives.isEmpty()) return;

        IQuestCapability cap = QuestCapabilityProvider.getOrNull(player);
        if (cap == null) return;

        QuestRuntimeData data = cap.getActiveQuest(questId);
        if (data == null || !data.isPhaseActive(phaseId)) return;

        int remaining = amount;

        for (TrackedObjective tracked : objectives) {
            if (remaining <= 0) break;

            int idx = tracked.getObjectiveIndex();
            int required = Math.max(1, tracked.getRequiredCount());
            int current = data.getObjectiveProgress(phaseId, idx);
            if (current >= required) continue;

            int need = required - current;
            int add = Math.min(remaining, need);

            QuestProgressHandler.incrementObjective(player, questId, phaseId, idx, add);
            remaining -= add;
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
        return type.name().toLowerCase(Locale.ROOT) + ":" + targetId;
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

    private record PhaseGroupKey(String questId, String phaseId) {
    }
}

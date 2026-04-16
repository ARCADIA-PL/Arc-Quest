package org.com.arc_quest.quest.tracking;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.com.arc_quest.Arc_quest;
import org.com.arc_quest.quest.api.ObjectiveType;
import org.com.arc_quest.quest.logic.QuestProgressHandler;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 桥接层：Forge 游戏事件 → ObjectiveTracker O(1) 查找 → QuestProgressHandler 进度推进。
 * <p>
 * <b>Phase 2 已激活</b>：所有事件处理器现在通过 {@link QuestProgressHandler#incrementObjective}
 * 实际修改 Capability 数据并触发网络同步。
 */
@Mod.EventBusSubscriber(modid = Arc_quest.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class QuestEventManager {

    private static final Logger LOGGER = LogUtils.getLogger();

    private QuestEventManager() {
    }

    // ═══════════════════════════════════════════════════════
    //  击杀检测
    // ═══════════════════════════════════════════════════════

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

    // ═══════════════════════════════════════════════════════
    //  物品拾取检测
    // ═══════════════════════════════════════════════════════

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onItemPickup(EntityItemPickupEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(
                event.getItem().getItem().getItem());
        if (itemId == null) return;

        int count = event.getItem().getItem().getCount();
        processMatch(player, ObjectiveType.COLLECT, itemId, count);
    }

    // ═══════════════════════════════════════════════════════
    //  合成检测
    // ═══════════════════════════════════════════════════════

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(
                event.getCrafting().getItem());
        if (itemId == null) return;

        int count = event.getCrafting().getCount();
        processMatch(player, ObjectiveType.CRAFT, itemId, count);
    }

    // ═══════════════════════════════════════════════════════
    //  玩家断开连接 — 清理追踪数据
    // ═══════════════════════════════════════════════════════

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ObjectiveTracker.INSTANCE.unregisterPlayer(player.getUUID());
            LOGGER.debug("[QuestEvent] Cleared tracking for: {}",
                    player.getGameProfile().getName());
        }
    }

    // ═══════════════════════════════════════════════════════
    // 核心：统一匹配与推进
    // ═══════════════════════════════════════════════════════

    private static void processMatch(ServerPlayer player,
                                     ObjectiveType type,
                                     ResourceLocation targetId,
                                     int amount) {
        ObjectiveKey key = new ObjectiveKey(type, targetId);
        Set<TrackedObjective> matches = ObjectiveTracker.INSTANCE.lookup(key);

        if (matches.isEmpty()) return;

        for (TrackedObjective tracked : matches) {
            if (!tracked.getPlayerId().equals(player.getUUID())) continue;

            // ★ Phase 2 激活：实际推进进度
            QuestProgressHandler.incrementObjective(
                    player,
                    tracked.getQuestId().toString(),
                    tracked.getPhaseId(),
                    tracked.getObjectiveIndex(),
                    amount
            );
        }
    }

    // ═══════════════════════════════════════════════════════
    //  公开 API — 手动触发
    // ═══════════════════════════════════════════════════════

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
}
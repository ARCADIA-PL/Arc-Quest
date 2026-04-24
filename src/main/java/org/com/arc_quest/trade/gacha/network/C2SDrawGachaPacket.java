package org.com.arc_quest.trade.gacha.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import org.com.arc_quest.Arc_quest;
import org.com.arc_quest.api.event.GachaEvents;
import org.com.arc_quest.client.util.ClientCooldownHelper;
import org.com.arc_quest.quest.capability.IQuestCapability;
import org.com.arc_quest.quest.capability.QuestCapabilityProvider;
import org.com.arc_quest.quest.network.ArcQuestNetwork;
import org.com.arc_quest.trade.api.CostShortfallLine;
import org.com.arc_quest.trade.api.ITradeOffer;
import org.com.arc_quest.trade.gacha.api.GachaItem;
import org.com.arc_quest.trade.gacha.api.GachaShopDefinition;
import org.com.arc_quest.trade.gacha.registry.GachaRegistry;
import org.com.arc_quest.trade.gacha.runtime.GachaEntryStateResolver;
import org.com.arc_quest.trade.gacha.runtime.GachaSession;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Supplier;

/**
 * 客户端请求执行抽奖。
 */
public class C2SDrawGachaPacket {
    
    private final String shopId;
    
    public C2SDrawGachaPacket(String shopId) {
        this.shopId = shopId;
    }
    
    public static void encode(C2SDrawGachaPacket pkt, FriendlyByteBuf buf) {
        buf.writeUtf(pkt.shopId);
    }
    
    public static C2SDrawGachaPacket decode(FriendlyByteBuf buf) {
        return new C2SDrawGachaPacket(buf.readUtf());
    }

    public static void handle(C2SDrawGachaPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            IQuestCapability cap = QuestCapabilityProvider.getOrNull(player);
            if (cap == null) return;

            GachaShopDefinition gachaShop = GachaRegistry.get(pkt.shopId);
            if (gachaShop == null) {
                Arc_quest.LOGGER.warn("[Gacha] Shop not found: {}", pkt.shopId);
                return;
            }

            DrawResolution resolution;
            synchronized (cap) {
                resolution = resolveDrawUnderLock(player, cap, gachaShop, pkt.shopId);
            }

            if (!resolution.succeeded()) {
                GachaEvents.DrawFailedEvent.FailReason reason = GachaEvents.DrawFailedEvent.FailReason.valueOf(resolution.failedReasonName());
                var failedEvent = new GachaEvents.DrawFailedEvent(player, pkt.shopId, cap, reason);
                MinecraftForge.EVENT_BUS.post(failedEvent);

                ArcQuestNetwork.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> player),
                        new S2CDrawFailedPacket(pkt.shopId, reason.name(),
                                resolution.shortfallLines() != null ? resolution.shortfallLines() : java.util.List.of())
                );
                return;
            }

            if (resolution.earlyTrigger()) {
                var earlyTriggerEvent = new GachaEvents.PityEarlyTriggerEvent(
                        player,
                        pkt.shopId,
                        resolution.item(),
                        resolution.newPityCounter(),
                        resolution.pityThreshold(),
                        true
                );
                MinecraftForge.EVENT_BUS.post(earlyTriggerEvent);
            }

            ArcQuestNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new S2CDrawResultPacket(
                            pkt.shopId,
                            resolution.item().getItemId(),
                            resolution.item().getRarity().getName(),
                            resolution.actualCount(),
                            resolution.pityTriggered(),
                            resolution.newPityCounter(),
                            resolution.canDraw(),
                            resolution.remainingDraws(),
                            resolution.lastDrawRealTime(),
                            resolution.lastDrawGameTime(),
                            resolution.lastDrawDayTime()
                    )
            );

            Arc_quest.LOGGER.info("[Gacha] Player {} drew {} x{} from {}",
                    player.getName().getString(),
                    resolution.item().getItemId(),
                    resolution.actualCount(),
                    pkt.shopId
            );
        });
        ctx.get().setPacketHandled(true);
    }

    private static DrawResolution resolveDrawUnderLock(ServerPlayer player, IQuestCapability cap,
                                                       GachaShopDefinition gachaShop, String shopId) {
        GachaSession session = new GachaSession(player, gachaShop, cap);

        session.checkAndResetDraws();

        if (!session.canDraw()) {
            GachaEvents.DrawFailedEvent.FailReason reason = mapFailReason(session.getFailReason());
            java.util.List<org.com.arc_quest.trade.api.CostShortfallLine> shortfallLines = java.util.List.of();

            ITradeOffer drawCost = gachaShop.getDrawCost();
            if (reason == GachaEvents.DrawFailedEvent.FailReason.CANNOT_AFFORD && drawCost != null) {
                shortfallLines = drawCost.buildShortfallLines(player);
            }

            return new DrawResolution(
                    false,
                    reason.name(),
                    shortfallLines,
                    null,
                    0,
                    false,
                    0,
                    false,
                    -1,
                    0,
                    -1,
                    -1,
                    false,
                    0
            );
        }

        int pityCounter = cap.getGachaPityCounter(shopId);

        var preEvent = new GachaEvents.PreDrawEvent(player, shopId, cap, pityCounter);
        MinecraftForge.EVENT_BUS.post(preEvent);
        if (preEvent.isCancelled()) {
            return new DrawResolution(
                    false,
                    GachaEvents.DrawFailedEvent.FailReason.UNKNOWN.name(),
                    java.util.List.of(),
                    null,
                    0,
                    false,
                    pityCounter,
                    true,
                    -1,
                    0,
                    -1,
                    -1,
                    false,
                    0
            );
        }

        pityCounter = preEvent.getPityCounter();

        ITradeOffer drawCost = gachaShop.getDrawCost();
        if (drawCost != null) {
            if (!drawCost.canAfford(player)) {
                return new DrawResolution(
                        false,
                        GachaEvents.DrawFailedEvent.FailReason.CANNOT_AFFORD.name(),
                        drawCost.buildShortfallLines(player),
                        null,
                        0,
                        false,
                        pityCounter,
                        true,
                        -1,
                        0,
                        -1,
                        -1,
                        false,
                        0
                );
            }
            drawCost.execute(player);
        }

        var drawingEvent = new GachaEvents.DrawingEvent(player, shopId, cap, pityCounter);
        MinecraftForge.EVENT_BUS.post(drawingEvent);

        var drawResult = gachaShop.performDraw(player, cap, pityCounter);
        if (drawResult.item() == null) {
            return new DrawResolution(
                    false,
                    GachaEvents.DrawFailedEvent.FailReason.UNKNOWN.name(),
                    java.util.List.of(),
                    null,
                    0,
                    false,
                    pityCounter,
                    true,
                    -1,
                    0,
                    -1,
                    -1,
                    false,
                    0
            );
        }

        int actualCount = drawResult.item().calculateActualCount();

        int newPityCounter;
        boolean isEarlyTrigger = false;
        int pityThreshold = 0;

        if (drawResult.pityTriggered()) {
            newPityCounter = 0;
        } else if (gachaShop.shouldResetPityOnEarlyTrigger()) {
            var pityConfig = gachaShop.getPityConfig();
            if (pityConfig != null) {
                pityThreshold = pityConfig.getPityThreshold();
                String drawnRarity = drawResult.item().getRarity().getName();
                String targetRarity = pityConfig.getGuaranteedRarity() != null
                        ? pityConfig.getGuaranteedRarity().getName() : null;

                if (targetRarity != null && drawnRarity.equals(targetRarity)) {
                    isEarlyTrigger = true;
                    newPityCounter = 0;
                } else {
                    newPityCounter = pityCounter + 1;
                }
            } else {
                newPityCounter = pityCounter + 1;
            }
        } else {
            newPityCounter = pityCounter + 1;
        }

        boolean stored = PendingDrawManager.storePendingDraw(
                player,
                shopId,
                drawResult.item(),
                actualCount,
                drawResult.pityTriggered(),
                newPityCounter
        );

        if (!stored) {
            return new DrawResolution(
                    false,
                    GachaEvents.DrawFailedEvent.FailReason.UNKNOWN.name(),
                    java.util.List.of(),
                    null,
                    0,
                    false,
                    pityCounter,
                    true,
                    -1,
                    0,
                    -1,
                    -1,
                    false,
                    pityThreshold
            );
        }

        if (GachaEntryStateResolver.shouldRecordCooldown(cap, shopId, gachaShop)) {
            GachaEntryStateResolver.recordCooldown(player, cap, shopId, gachaShop);
        }

        session.incrementDrawCount();
        cap.setGachaPityCounter(shopId, newPityCounter);

        var postEvent = new GachaEvents.PostDrawEvent(
                player,
                shopId,
                drawResult.item(),
                drawResult.pityTriggered(),
                newPityCounter,
                cap
        );
        MinecraftForge.EVENT_BUS.post(postEvent);

        var refreshedCap = QuestCapabilityProvider.getOrNull(player);
        boolean canDraw = true;
        int remainingDraws = -1;
        long lastDrawRealTime = 0;
        long lastDrawGameTime = -1;
        long lastDrawDayTime = -1;

        if (refreshedCap != null) {
            if (gachaShop.hasLimit() && gachaShop.getMaxDraws() > 0) {
                int totalDraws = gachaShop.getTotalDraws(refreshedCap);
                remainingDraws = Math.max(0, gachaShop.getMaxDraws() - totalDraws);
                if (remainingDraws <= 0) {
                    canDraw = false;
                }
            }

            if (gachaShop.hasCooldown()) {
                var cooldownEntry = refreshedCap.getGachaDataStore().getDrawCooldown(gachaShop.getShopId());
                lastDrawRealTime = cooldownEntry.realTime();
                lastDrawGameTime = cooldownEntry.gameTime();
                lastDrawDayTime = cooldownEntry.dayTime();

                if (canDraw) {
                    boolean onCooldown = ClientCooldownHelper.isOnCooldown(
                            lastDrawRealTime, lastDrawGameTime, lastDrawDayTime,
                            gachaShop.getCooldownType().ordinal(),
                            gachaShop.getCooldownValue(),
                            gachaShop.getResetTimeTicks()
                    );
                    if (onCooldown) {
                        canDraw = false;
                    }
                }
            }
        }

        return new DrawResolution(
                true,
                null,
                java.util.List.of(),
                drawResult.item(),
                actualCount,
                drawResult.pityTriggered(),
                newPityCounter,
                canDraw,
                remainingDraws,
                lastDrawRealTime,
                lastDrawGameTime,
                lastDrawDayTime,
                isEarlyTrigger,
                pityThreshold
        );
    }
    
    /**
     * 将 GachaSession 的失败原因映射到事件的失败原因。
     */
    private static GachaEvents.DrawFailedEvent.FailReason mapFailReason(GachaSession.DrawFailReason sessionReason) {
        return switch (sessionReason) {
            case NOT_VISIBLE -> GachaEvents.DrawFailedEvent.FailReason.NOT_VISIBLE;
            case MAX_DRAWS_REACHED -> GachaEvents.DrawFailedEvent.FailReason.MAX_DRAWS_REACHED;
            case ON_COOLDOWN -> GachaEvents.DrawFailedEvent.FailReason.ON_COOLDOWN;
            case CONDITION_NOT_MET -> GachaEvents.DrawFailedEvent.FailReason.CONDITION_NOT_MET;
            case CANNOT_AFFORD -> GachaEvents.DrawFailedEvent.FailReason.CANNOT_AFFORD;  // 【新增】
            default -> GachaEvents.DrawFailedEvent.FailReason.UNKNOWN;
        };
    }

    private record DrawResolution(
            boolean succeeded,
            @Nullable String failedReasonName,
            @Nullable List<CostShortfallLine> shortfallLines,
            @Nullable GachaItem item,
            int actualCount,
            boolean pityTriggered,
            int newPityCounter,
            boolean canDraw,
            int remainingDraws,
            long lastDrawRealTime,
            long lastDrawGameTime,
            long lastDrawDayTime,
            boolean earlyTrigger,
            int pityThreshold
    ) {}
}

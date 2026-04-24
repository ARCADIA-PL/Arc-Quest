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
import org.com.arc_quest.quest.network.ArcQuestNetwork;
import org.com.arc_quest.trade.api.CostShortfallLine;
import org.com.arc_quest.trade.api.ITradeOffer;
import org.com.arc_quest.trade.gacha.api.GachaItem;
import org.com.arc_quest.trade.gacha.api.GachaShopDefinition;
import org.com.arc_quest.trade.gacha.runtime.GachaEntryStateResolver;
import org.com.arc_quest.trade.gacha.runtime.GachaSession;
import org.com.arc_quest.trade.network.RejectCodeDictionary;

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
            ServerPlayer player = GachaRequestValidator.requirePlayer(ctx.get().getSender(), "draw", pkt.shopId);
            if (player == null) return;

            IQuestCapability cap = GachaRequestValidator.requireCapability(player, "draw", pkt.shopId);
            if (cap == null) return;

            GachaShopDefinition gachaShop = GachaRequestValidator.requireShop(pkt.shopId, player, "draw");
            if (gachaShop == null) return;

            // 0) PreDrawEvent（锁外，允许改 pityCounter）
            int basePityCounter = cap.getGachaPityCounter(pkt.shopId);
            GachaEvents.PreDrawEvent preEvent = new GachaEvents.PreDrawEvent(player, pkt.shopId, cap, basePityCounter);
            MinecraftForge.EVENT_BUS.post(preEvent);
            if (preEvent.isCancelled()) {
                GachaRequestValidator.reject(
                        RejectCodeDictionary.Code.PRE_DRAW_CANCELLED,
                        "draw",
                        player,
                        pkt.shopId,
                        "pre draw event cancelled"
                );
                var reason = GachaEvents.DrawFailedEvent.FailReason.UNKNOWN;
                MinecraftForge.EVENT_BUS.post(new GachaEvents.DrawFailedEvent(player, pkt.shopId, cap, reason));

                ArcQuestNetwork.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> player),
                        new S2CDrawFailedPacket(
                                pkt.shopId,
                                reason.name(),
                                GachaRequestValidator.toErrorKey(RejectCodeDictionary.Code.PRE_DRAW_CANCELLED),
                                List.of()
                        )
                );
                return;
            }
            int pityCounter = preEvent.getPityCounter();

            // 1) 扣费预检查 + 扣费执行（锁外）
            ITradeOffer drawCost = gachaShop.getDrawCost();
            if (drawCost != null && !drawCost.canAfford(player)) {
                GachaRequestValidator.reject(
                        RejectCodeDictionary.Code.CANNOT_AFFORD,
                        "draw",
                        player,
                        pkt.shopId,
                        "draw cost cannot afford"
                );
                var reason = GachaEvents.DrawFailedEvent.FailReason.CANNOT_AFFORD;
                MinecraftForge.EVENT_BUS.post(new GachaEvents.DrawFailedEvent(player, pkt.shopId, cap, reason));

                ArcQuestNetwork.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> player),
                        new S2CDrawFailedPacket(
                                pkt.shopId,
                                reason.name(),
                                GachaRequestValidator.toErrorKey(RejectCodeDictionary.Code.CANNOT_AFFORD),
                                drawCost.buildShortfallLines(player)
                        )
                );
                return;
            }
            if (drawCost != null) {
                drawCost.execute(player);
            }

            // 2) 锁内：只做状态推进 + 产出权威快照（不发包/不日志/不派发事件）
            DrawResolution resolution;
            synchronized (cap) {
                resolution = resolveDrawStateUnderLock(player, cap, gachaShop, pkt.shopId, pityCounter);
            }

            // 3) 锁外：事件派发、发包、日志（彻底移出锁）
            if (!resolution.succeeded()) {
                GachaEvents.DrawFailedEvent.FailReason reason =
                        GachaEvents.DrawFailedEvent.FailReason.valueOf(resolution.failedReasonName());
                GachaRequestValidator.reject(
                        GachaRequestValidator.fromDrawFailedReasonName(resolution.failedReasonName()),
                        "draw",
                        player,
                        pkt.shopId,
                        "resolution failed"
                );

                MinecraftForge.EVENT_BUS.post(new GachaEvents.DrawFailedEvent(player, pkt.shopId, cap, reason));

                ArcQuestNetwork.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> player),
                        new S2CDrawFailedPacket(
                                pkt.shopId,
                                reason.name(),
                                GachaRequestValidator.toErrorKey(
                                        GachaRequestValidator.fromDrawFailedReasonName(resolution.failedReasonName())
                                ),
                                resolution.shortfallLines() != null ? resolution.shortfallLines() : List.of()
                        )
                );
                return;
            }

            MinecraftForge.EVENT_BUS.post(new GachaEvents.DrawingEvent(
                    player,
                    pkt.shopId,
                    cap,
                    resolution.preEventPityCounter()
            ));

            if (resolution.earlyTrigger()) {
                MinecraftForge.EVENT_BUS.post(new GachaEvents.PityEarlyTriggerEvent(
                        player,
                        pkt.shopId,
                        resolution.item(),
                        resolution.preEventPityCounter(),
                        resolution.pityThreshold(),
                        true
                ));
            }

            MinecraftForge.EVENT_BUS.post(new GachaEvents.PostDrawEvent(
                    player,
                    pkt.shopId,
                    resolution.item(),
                    resolution.pityTriggered(),
                    resolution.newPityCounter(),
                    cap
            ));

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

    private static DrawResolution resolveDrawStateUnderLock(ServerPlayer player, IQuestCapability cap,
                                                            GachaShopDefinition gachaShop, String shopId,
                                                            int pityCounter) {
        GachaSession session = new GachaSession(player, gachaShop, cap);

        session.checkAndResetDraws();

        if (!session.canDraw()) {
            GachaEvents.DrawFailedEvent.FailReason reason = mapFailReason(session.getFailReason());

            // 尽量在锁内计算 shortfall（但这里不再调用 canAfford/execute，避免与锁外扣费冲突）
            List<CostShortfallLine> shortfallLines = List.of();
            if (reason == GachaEvents.DrawFailedEvent.FailReason.CANNOT_AFFORD) {
                ITradeOffer drawCost = gachaShop.getDrawCost();
                if (drawCost != null) {
                    shortfallLines = drawCost.buildShortfallLines(player);
                }
            }

            return new DrawResolution(
                    false,
                    reason.name(),
                    shortfallLines,
                    null,
                    0,
                    false,
                    pityCounter,
                    pityCounter,
                    false,
                    -1,
                    0,
                    -1,
                    -1,
                    false,
                    0
            );
        }

        var drawResult = gachaShop.performDraw(player, cap, pityCounter);
        if (drawResult.item() == null) {
            GachaRequestValidator.reject(
                    RejectCodeDictionary.Code.DRAW_RESULT_EMPTY,
                    "draw",
                    player,
                    shopId,
                    "draw result item is null"
            );
            return new DrawResolution(
                    false,
                    GachaEvents.DrawFailedEvent.FailReason.UNKNOWN.name(),
                    List.of(),
                    null,
                    0,
                    false,
                    pityCounter,
                    pityCounter,
                    false,
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
                        ? pityConfig.getGuaranteedRarity().getName()
                        : null;

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
            GachaRequestValidator.reject(
                    RejectCodeDictionary.Code.PENDING_STORE_FAILED,
                    "draw",
                    player,
                    shopId,
                    "pending draw store failed"
            );
            return new DrawResolution(
                    false,
                    GachaEvents.DrawFailedEvent.FailReason.UNKNOWN.name(),
                    List.of(),
                    null,
                    0,
                    false,
                    pityCounter,
                    pityCounter,
                    false,
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

        boolean canDraw = true;
        int remainingDraws = -1;
        long lastDrawRealTime = 0;
        long lastDrawGameTime = -1;
        long lastDrawDayTime = -1;

        if (gachaShop.hasLimit() && gachaShop.getMaxDraws() > 0) {
            int totalDraws = gachaShop.getTotalDraws(cap);
            remainingDraws = Math.max(0, gachaShop.getMaxDraws() - totalDraws);
            if (remainingDraws <= 0) {
                canDraw = false;
            }
        }

        if (gachaShop.hasCooldown()) {
            var cooldownEntry = cap.getGachaDataStore().getDrawCooldown(gachaShop.getShopId());
            lastDrawRealTime = cooldownEntry.realTime();
            lastDrawGameTime = cooldownEntry.gameTime();
            lastDrawDayTime = cooldownEntry.dayTime();

            if (canDraw) {
                boolean onCooldown = ClientCooldownHelper.isOnCooldown(
                        lastDrawRealTime,
                        lastDrawGameTime,
                        lastDrawDayTime,
                        gachaShop.getCooldownType().ordinal(),
                        gachaShop.getCooldownValue(),
                        gachaShop.getResetTimeTicks()
                );
                if (onCooldown) {
                    canDraw = false;
                }
            }
        }

        return new DrawResolution(
                true,
                null,
                List.of(),
                drawResult.item(),
                actualCount,
                drawResult.pityTriggered(),
                pityCounter,
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
            case CANNOT_AFFORD -> GachaEvents.DrawFailedEvent.FailReason.CANNOT_AFFORD;
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
            int preEventPityCounter,
            int newPityCounter,
            boolean canDraw,
            int remainingDraws,
            long lastDrawRealTime,
            long lastDrawGameTime,
            long lastDrawDayTime,
            boolean earlyTrigger,
            int pityThreshold
    ) {
    }
}
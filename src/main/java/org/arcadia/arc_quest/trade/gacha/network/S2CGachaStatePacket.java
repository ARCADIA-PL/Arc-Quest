package org.arcadia.arc_quest.trade.gacha.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.network.NetworkEvent;
import org.arcadia.arc_quest.api.event.gacha.GachaEvents;
import org.arcadia.arc_quest.client.hud.gacha.GachaScreen;
import org.arcadia.arc_quest.quest.capability.IQuestCapability;
import org.arcadia.arc_quest.quest.capability.QuestCapabilityProvider;
import org.arcadia.arc_quest.trade.api.CostShortfallLine;
import org.arcadia.arc_quest.trade.gacha.registry.GachaRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * 服务端 -> 客户端：抽奖状态包（合并 OPEN + SYNC）
 */
public class S2CGachaStatePacket {

    private final Mode mode;
    private final String shopId;
    private final int pityCounter;
    private final int totalDraws;
    private final boolean canDraw;
    private final int remainingDraws;
    private final long lastDrawRealTime;
    private final long lastDrawGameTime;
    private final long lastDrawDayTime;
    private final int cooldownType;
    private final long cooldownValue;
    private final int resetTimeTicks;
    private final List<CostShortfallLine> shortfallLines;
    private final List<IQuestCapability.GachaDrawRecord> drawHistory;
    private S2CGachaStatePacket(Mode mode,
                                String shopId,
                                int pityCounter,
                                int totalDraws,
                                boolean canDraw,
                                int remainingDraws,
                                long lastDrawRealTime,
                                long lastDrawGameTime,
                                long lastDrawDayTime,
                                int cooldownType,
                                long cooldownValue,
                                int resetTimeTicks,
                                List<CostShortfallLine> shortfallLines,
                                List<IQuestCapability.GachaDrawRecord> drawHistory) {
        this.mode = mode;
        this.shopId = shopId;
        this.pityCounter = pityCounter;
        this.totalDraws = totalDraws;
        this.canDraw = canDraw;
        this.remainingDraws = remainingDraws;
        this.lastDrawRealTime = lastDrawRealTime;
        this.lastDrawGameTime = lastDrawGameTime;
        this.lastDrawDayTime = lastDrawDayTime;
        this.cooldownType = cooldownType;
        this.cooldownValue = cooldownValue;
        this.resetTimeTicks = resetTimeTicks;
        this.shortfallLines = shortfallLines != null ? List.copyOf(shortfallLines) : List.of();
        this.drawHistory = drawHistory != null ? List.copyOf(drawHistory) : Collections.emptyList();
    }

    public static S2CGachaStatePacket open(String shopId,
                                           int pityCounter,
                                           int totalDraws,
                                           boolean canDraw,
                                           int remainingDraws,
                                           long lastDrawRealTime,
                                           long lastDrawGameTime,
                                           long lastDrawDayTime,
                                           int cooldownType,
                                           long cooldownValue,
                                           int resetTimeTicks,
                                           List<CostShortfallLine> shortfallLines,
                                           List<IQuestCapability.GachaDrawRecord> drawHistory) {
        return new S2CGachaStatePacket(
                Mode.OPEN, shopId, pityCounter, totalDraws, canDraw, remainingDraws,
                lastDrawRealTime, lastDrawGameTime, lastDrawDayTime,
                cooldownType, cooldownValue, resetTimeTicks,
                shortfallLines, drawHistory
        );
    }

    public static S2CGachaStatePacket sync(String shopId,
                                           int pityCounter,
                                           int totalDraws,
                                           boolean canDraw,
                                           int remainingDraws,
                                           long lastDrawRealTime,
                                           long lastDrawGameTime,
                                           long lastDrawDayTime,
                                           int cooldownType,
                                           long cooldownValue,
                                           int resetTimeTicks,
                                           List<CostShortfallLine> shortfallLines) {
        return new S2CGachaStatePacket(
                Mode.SYNC, shopId, pityCounter, totalDraws, canDraw, remainingDraws,
                lastDrawRealTime, lastDrawGameTime, lastDrawDayTime,
                cooldownType, cooldownValue, resetTimeTicks,
                shortfallLines, List.of()
        );
    }

    public static void encode(S2CGachaStatePacket pkt, FriendlyByteBuf buf) {
        buf.writeEnum(pkt.mode);
        buf.writeUtf(pkt.shopId);
        buf.writeInt(pkt.pityCounter);
        buf.writeInt(pkt.totalDraws);
        buf.writeBoolean(pkt.canDraw);
        buf.writeInt(pkt.remainingDraws);

        buf.writeLong(pkt.lastDrawRealTime);
        buf.writeLong(pkt.lastDrawGameTime);
        buf.writeLong(pkt.lastDrawDayTime);
        buf.writeInt(pkt.cooldownType);
        buf.writeLong(pkt.cooldownValue);
        buf.writeInt(pkt.resetTimeTicks);

        buf.writeVarInt(pkt.shortfallLines.size());
        for (CostShortfallLine line : pkt.shortfallLines) {
            buf.writeComponent(line.label());
            buf.writeVarInt(line.required());
            buf.writeVarInt(line.owned());
            buf.writeVarInt(line.missing());
        }

        buf.writeInt(pkt.drawHistory.size());
        for (IQuestCapability.GachaDrawRecord record : pkt.drawHistory) {
            buf.writeUtf(record.itemId());
            buf.writeUtf(record.rarityName());
            buf.writeInt(record.actualCount());
            buf.writeBoolean(record.pityTriggered());
            buf.writeLong(record.drawTime());
        }
    }

    public static S2CGachaStatePacket decode(FriendlyByteBuf buf) {
        Mode mode = buf.readEnum(Mode.class);
        String shopId = buf.readUtf();
        int pityCounter = buf.readInt();
        int totalDraws = buf.readInt();
        boolean canDraw = buf.readBoolean();
        int remainingDraws = buf.readInt();

        long lastDrawRealTime = buf.readLong();
        long lastDrawGameTime = buf.readLong();
        long lastDrawDayTime = buf.readLong();
        int cooldownType = buf.readInt();
        long cooldownValue = buf.readLong();
        int resetTimeTicks = buf.readInt();

        int shortfallCount = buf.readVarInt();
        List<CostShortfallLine> shortfalls = new ArrayList<>(shortfallCount);
        for (int i = 0; i < shortfallCount; i++) {
            shortfalls.add(new CostShortfallLine(
                    buf.readComponent(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt()
            ));
        }

        int historySize = buf.readInt();
        List<IQuestCapability.GachaDrawRecord> history = new ArrayList<>(historySize);
        for (int i = 0; i < historySize; i++) {
            history.add(new IQuestCapability.GachaDrawRecord(
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readInt(),
                    buf.readBoolean(),
                    buf.readLong()
            ));
        }

        return new S2CGachaStatePacket(
                mode,
                shopId,
                pityCounter,
                totalDraws,
                canDraw,
                remainingDraws,
                lastDrawRealTime,
                lastDrawGameTime,
                lastDrawDayTime,
                cooldownType,
                cooldownValue,
                resetTimeTicks,
                shortfalls,
                history
        );
    }

    public static void handle(S2CGachaStatePacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            if (GachaRegistry.get(pkt.shopId) == null) return;

            // OPEN 才派发 opened 事件
            if (pkt.mode == Mode.OPEN) {
                var cap = QuestCapabilityProvider.getOrNull(mc.player);
                if (cap != null) {
                    MinecraftForge.EVENT_BUS.post(new GachaEvents.OpenedEvent(null, pkt.shopId, cap));
                }
            }

            if (!pkt.drawHistory.isEmpty()) {
                List<ClientGachaCache.DrawRecord> historyRecords = new ArrayList<>();
                for (var record : pkt.drawHistory) {
                    historyRecords.add(new ClientGachaCache.DrawRecord(
                            record.itemId(),
                            record.rarityName(),
                            record.actualCount(),
                            record.pityTriggered(),
                            record.drawTime()
                    ));
                }

                ClientGachaCache.INSTANCE.updateSessionWithHistory(
                        pkt.shopId,
                        pkt.pityCounter,
                        pkt.totalDraws,
                        pkt.canDraw,
                        pkt.remainingDraws,
                        pkt.lastDrawRealTime,
                        pkt.lastDrawGameTime,
                        pkt.lastDrawDayTime,
                        pkt.cooldownType,
                        pkt.cooldownValue,
                        pkt.resetTimeTicks,
                        historyRecords
                );
            } else {
                ClientGachaCache.INSTANCE.updateSession(
                        pkt.shopId,
                        pkt.pityCounter,
                        pkt.totalDraws,
                        pkt.canDraw,
                        pkt.remainingDraws,
                        pkt.lastDrawRealTime,
                        pkt.lastDrawGameTime,
                        pkt.lastDrawDayTime,
                        pkt.cooldownType,
                        pkt.cooldownValue,
                        pkt.resetTimeTicks
                );
            }

            if (!pkt.shortfallLines.isEmpty()) {
                ClientGachaCache.INSTANCE.recordDrawFailure(
                        pkt.shopId,
                        GachaEvents.DrawFailedEvent.FailReason.CANNOT_AFFORD.name(),
                        pkt.shortfallLines
                );
            } else if (pkt.canDraw) {
                ClientGachaCache.INSTANCE.clearFeedback(pkt.shopId);
            }

            if (mc.screen instanceof GachaScreen gachaScreen && gachaScreen.getShopId().equals(pkt.shopId)) {
                gachaScreen.getPreviewPanel().updateDataSnapshot();
                return;
            }

            if (pkt.mode == Mode.OPEN) {
                mc.setScreen(new GachaScreen(pkt.shopId));
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public enum Mode {
        OPEN,
        SYNC
    }
}
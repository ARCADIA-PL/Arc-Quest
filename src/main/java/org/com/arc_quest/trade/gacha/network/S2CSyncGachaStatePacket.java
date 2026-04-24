package org.com.arc_quest.trade.gacha.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.com.arc_quest.client.gui.gacha.GachaScreen;
import org.com.arc_quest.trade.api.CostShortfallLine;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 服务端 -> 客户端：仅同步抽奖界面的权威状态，不负责打开界面。
 */
public class S2CSyncGachaStatePacket {

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

    public S2CSyncGachaStatePacket(String shopId,
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
    }

    public static void encode(S2CSyncGachaStatePacket pkt, FriendlyByteBuf buf) {
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
    }

    public static S2CSyncGachaStatePacket decode(FriendlyByteBuf buf) {
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
        List<CostShortfallLine> shortfallLines = new ArrayList<>(shortfallCount);
        for (int i = 0; i < shortfallCount; i++) {
            shortfallLines.add(new CostShortfallLine(
                    buf.readComponent(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt()
            ));
        }

        return new S2CSyncGachaStatePacket(
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
                shortfallLines
        );
    }

    public static void handle(S2CSyncGachaStatePacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

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

            if (!pkt.shortfallLines.isEmpty()) {
                ClientGachaCache.INSTANCE.recordDrawFailure(
                        pkt.shopId,
                        "CANNOT_AFFORD",
                        pkt.shortfallLines
                );
            }

            if (mc.screen instanceof GachaScreen gachaScreen && gachaScreen.getShopId().equals(pkt.shopId)) {
                gachaScreen.getPreviewPanel().updateDataSnapshot();
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
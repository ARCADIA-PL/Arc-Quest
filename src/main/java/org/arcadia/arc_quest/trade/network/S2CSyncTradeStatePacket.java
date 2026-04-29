package org.arcadia.arc_quest.trade.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.arcadia.arc_quest.client.hud.shop.SimpleTradePanel;
import org.arcadia.arc_quest.client.hud.shop.TradeScreen;

import java.util.function.Supplier;

/**
 * 服务端 -> 客户端：仅同步交易权威状态，不负责打开界面。
 */
public class S2CSyncTradeStatePacket {

    private final String shopId;
    private final int[] purchaseCounts;
    private final int[] maxPurchases;
    private final long[] lastPurchaseTimes;
    private final long[] purchaseGameTimes;
    private final long[] purchaseDayTimes;
    private final int[] cooldownTypes;
    private final long[] cooldownValues;
    private final int[] resetTimeTicks;
    private final boolean[] visibility;
    private final boolean[] canBuyConditions;

    public S2CSyncTradeStatePacket(String shopId,
                                   int[] purchaseCounts,
                                   int[] maxPurchases,
                                   long[] lastPurchaseTimes,
                                   long[] purchaseGameTimes,
                                   long[] purchaseDayTimes,
                                   int[] cooldownTypes,
                                   long[] cooldownValues,
                                   int[] resetTimeTicks,
                                   boolean[] visibility,
                                   boolean[] canBuyConditions) {
        this.shopId = shopId;
        this.purchaseCounts = purchaseCounts;
        this.maxPurchases = maxPurchases;
        this.lastPurchaseTimes = lastPurchaseTimes;
        this.purchaseGameTimes = purchaseGameTimes;
        this.purchaseDayTimes = purchaseDayTimes;
        this.cooldownTypes = cooldownTypes;
        this.cooldownValues = cooldownValues;
        this.resetTimeTicks = resetTimeTicks;
        this.visibility = visibility;
        this.canBuyConditions = canBuyConditions;
    }

    public static void encode(S2CSyncTradeStatePacket pkt, FriendlyByteBuf buf) {
        buf.writeUtf(pkt.shopId);
        int count = pkt.purchaseCounts != null ? pkt.purchaseCounts.length : 0;
        buf.writeVarInt(count);

        for (int i = 0; i < count; i++) {
            buf.writeVarInt(pkt.purchaseCounts[i]);
            buf.writeVarInt(pkt.maxPurchases[i]);
            buf.writeLong(pkt.lastPurchaseTimes[i]);
            buf.writeLong(pkt.purchaseGameTimes[i]);
            buf.writeLong(pkt.purchaseDayTimes[i]);
            buf.writeVarInt(pkt.cooldownTypes[i]);
            buf.writeLong(pkt.cooldownValues[i]);
            buf.writeVarInt(pkt.resetTimeTicks[i]);
            buf.writeBoolean(pkt.visibility[i]);
            buf.writeBoolean(pkt.canBuyConditions[i]);
        }
    }

    public static S2CSyncTradeStatePacket decode(FriendlyByteBuf buf) {
        String shopId = buf.readUtf();
        int count = buf.readVarInt();

        int[] purchases = new int[count];
        int[] maxPurch = new int[count];
        long[] lastTimes = new long[count];
        long[] purchaseGTs = new long[count];
        long[] purchaseDTs = new long[count];
        int[] cdTypes = new int[count];
        long[] cdValues = new long[count];
        int[] resetTicks = new int[count];
        boolean[] vis = new boolean[count];
        boolean[] canBuy = new boolean[count];

        for (int i = 0; i < count; i++) {
            purchases[i] = buf.readVarInt();
            maxPurch[i] = buf.readVarInt();
            lastTimes[i] = buf.readLong();
            purchaseGTs[i] = buf.readLong();
            purchaseDTs[i] = buf.readLong();
            cdTypes[i] = buf.readVarInt();
            cdValues[i] = buf.readLong();
            resetTicks[i] = buf.readVarInt();
            vis[i] = buf.readBoolean();
            canBuy[i] = buf.readBoolean();
        }

        return new S2CSyncTradeStatePacket(
                shopId, purchases, maxPurch, lastTimes, purchaseGTs, purchaseDTs,
                cdTypes, cdValues, resetTicks, vis, canBuy
        );
    }

    public static void handle(S2CSyncTradeStatePacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();

            ClientTradeCache.INSTANCE.updateSession(
                    pkt.shopId,
                    pkt.purchaseCounts,
                    pkt.maxPurchases,
                    pkt.lastPurchaseTimes,
                    pkt.purchaseGameTimes,
                    pkt.purchaseDayTimes,
                    pkt.cooldownTypes,
                    pkt.cooldownValues,
                    pkt.resetTimeTicks,
                    pkt.visibility,
                    pkt.canBuyConditions
            );

            if (mc.screen instanceof TradeScreen ts && ts.getShopId().equals(pkt.shopId)) {
                ts.refreshData();
            } else if (mc.screen instanceof SimpleTradePanel sp && sp.getShopId().equals(pkt.shopId)) {
                sp.refreshData();
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
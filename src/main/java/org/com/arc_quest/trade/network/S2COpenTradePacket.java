package org.com.arc_quest.trade.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.com.arc_quest.client.gui.SimpleTradePanel;
import org.com.arc_quest.client.gui.TradeScreen;

import java.util.function.Supplier;

/**
 * 服务端→客户端：打开交易窗口 / 交易结果反馈。
 */
public class S2COpenTradePacket {

    public enum Mode {
        OPEN_FULL,
        OPEN_SIMPLE,
        TRADE_SUCCESS,
        TRADE_FAIL,
        CLOSE
    }

    private final Mode mode;
    private final String shopId;
    private final String errorKey;

    /** 每个交易项的购买次数 */
    private final int[] purchaseCounts;
    /** 每个交易项的最大购买次数（-1=无限） */
    private final int[] maxPurchases;
    /** 每个交易项的最后购买时间戳（毫秒） */
    private final long[] lastPurchaseTimes;
    /**  每个交易项购买时的 gameTime */
    private final long[] purchaseGameTimes;
    /**  每个交易项购买时的 dayTime */
    private final long[] purchaseDayTimes;
    /** 每个交易项的冷却类型（ordinal） */
    private final int[] cooldownTypes;
    /** 每个交易项的冷却值 */
    private final long[] cooldownValues;
    /** 每个交易项的重置刻（仅 GAME_TICK 有效） */
    private final int[] resetTimeTicks;
    /** 每个交易项是否可见 */
    private final boolean[] visibility;
    /** 每个交易项是否满足购买资格条件（用于 HUD 显示） */
    private final boolean[] canBuyConditions;

    /**
     * 打开交易窗口
     */
    public S2COpenTradePacket(Mode mode, String shopId,
                              int[] purchaseCounts, int[] maxPurchases,
                              long[] lastPurchaseTimes,
                              long[] purchaseGameTimes, long[] purchaseDayTimes,
                              int[] cooldownTypes, long[] cooldownValues,
                              int[] resetTimeTicks, boolean[] visibility,
                              boolean[] canBuyConditions) {
        this.mode = mode;
        this.shopId = shopId;
        this.errorKey = null;
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

    /**
     * 交易结果反馈
     */
    public S2COpenTradePacket(Mode mode, String shopId, String errorKey) {
        this.mode = mode;
        this.shopId = shopId;
        this.errorKey = errorKey;
        this.purchaseCounts = null;
        this.maxPurchases = null;
        this.lastPurchaseTimes = null;
        this.purchaseGameTimes = null;
        this.purchaseDayTimes = null;
        this.cooldownTypes = null;
        this.cooldownValues = null;
        this.resetTimeTicks = null;
        this.visibility = null;
        this.canBuyConditions = null;
    }

    /**
     * 关闭交易窗口
     */
    public static S2COpenTradePacket close() {
        return new S2COpenTradePacket(Mode.CLOSE, "", null);
    }

    public static S2COpenTradePacket openFull(String shopId,
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
        return new S2COpenTradePacket(Mode.OPEN_FULL, shopId,
                purchaseCounts, maxPurchases, lastPurchaseTimes, purchaseGameTimes, purchaseDayTimes,
                cooldownTypes, cooldownValues, resetTimeTicks, visibility, canBuyConditions);
    }

    public static S2COpenTradePacket openSimple(String shopId,
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
        return new S2COpenTradePacket(Mode.OPEN_SIMPLE, shopId,
                purchaseCounts, maxPurchases, lastPurchaseTimes, purchaseGameTimes, purchaseDayTimes,
                cooldownTypes, cooldownValues, resetTimeTicks, visibility, canBuyConditions);
    }

    public static S2COpenTradePacket tradeSuccess(String shopId) {
        return new S2COpenTradePacket(Mode.TRADE_SUCCESS, shopId, null);
    }

    public static S2COpenTradePacket tradeFail(String shopId, String errorKey) {
        return new S2COpenTradePacket(Mode.TRADE_FAIL, shopId, errorKey);
    }

    

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(mode);
        buf.writeUtf(shopId);

        if (mode == Mode.OPEN_FULL || mode == Mode.OPEN_SIMPLE) {
            int count = purchaseCounts != null ? purchaseCounts.length : 0;
            buf.writeVarInt(count);
            for (int i = 0; i < count; i++) {
                buf.writeVarInt(purchaseCounts[i]);
                buf.writeVarInt(maxPurchases[i]);
                buf.writeLong(lastPurchaseTimes[i]);
                buf.writeLong(purchaseGameTimes[i]);  
                buf.writeLong(purchaseDayTimes[i]);   
                buf.writeVarInt(cooldownTypes[i]);
                buf.writeLong(cooldownValues[i]);
                buf.writeVarInt(resetTimeTicks[i]);
                buf.writeBoolean(visibility[i]);
                buf.writeBoolean(canBuyConditions != null && canBuyConditions[i]);
            }
        } else if (mode == Mode.TRADE_FAIL) {
            buf.writeUtf(errorKey != null ? errorKey : "");
        }
    }

    public static S2COpenTradePacket decode(FriendlyByteBuf buf) {
        Mode mode = buf.readEnum(Mode.class);
        String shopId = buf.readUtf();

        if (mode == Mode.OPEN_FULL || mode == Mode.OPEN_SIMPLE) {
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
            return new S2COpenTradePacket(mode, shopId, purchases, maxPurch, lastTimes,
                    purchaseGTs, purchaseDTs, cdTypes, cdValues, resetTicks, vis, canBuy);
        } else if (mode == Mode.TRADE_FAIL) {
            return new S2COpenTradePacket(mode, shopId, buf.readUtf());
        } else {
            return new S2COpenTradePacket(mode, shopId, (String) null);
        }
    }

    public static void handle(S2COpenTradePacket pkt,
                               Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            switch (pkt.mode) {
                case OPEN_FULL -> {
                    
                    if (mc.screen instanceof TradeScreen ts && ts.getShopId().equals(pkt.shopId)) {
                        ts.updateData(pkt.purchaseCounts, pkt.maxPurchases, pkt.lastPurchaseTimes,
                                pkt.purchaseGameTimes, pkt.purchaseDayTimes,
                                pkt.cooldownTypes, pkt.cooldownValues, pkt.resetTimeTicks, pkt.visibility, pkt.canBuyConditions);
                    } else {
                        mc.setScreen(new TradeScreen(pkt.shopId, pkt.purchaseCounts, pkt.maxPurchases,
                                pkt.lastPurchaseTimes, pkt.purchaseGameTimes, pkt.purchaseDayTimes,
                                pkt.cooldownTypes, pkt.cooldownValues,
                                pkt.resetTimeTicks, pkt.visibility, pkt.canBuyConditions));
                    }
                }
                case OPEN_SIMPLE -> {
                    
                    if (mc.screen instanceof SimpleTradePanel sp && sp.getShopId().equals(pkt.shopId)) {
                        sp.updateData(pkt.purchaseCounts, pkt.maxPurchases, pkt.lastPurchaseTimes,
                                pkt.purchaseGameTimes, pkt.purchaseDayTimes,
                                pkt.cooldownTypes, pkt.cooldownValues, pkt.resetTimeTicks, pkt.visibility, pkt.canBuyConditions);
                    } else {
                        mc.setScreen(new SimpleTradePanel(pkt.shopId, pkt.purchaseCounts, pkt.maxPurchases,
                                pkt.lastPurchaseTimes, pkt.purchaseGameTimes, pkt.purchaseDayTimes,
                                pkt.cooldownTypes, pkt.cooldownValues,
                                pkt.resetTimeTicks, pkt.visibility, pkt.canBuyConditions));
                    }
                }
                case TRADE_SUCCESS -> {
                    if (mc.screen instanceof TradeScreen ts) {
                        ts.onTradeSuccess();
                    } else if (mc.screen instanceof SimpleTradePanel sp) {
                        sp.onTradeSuccess();
                    }
                }
                case TRADE_FAIL -> {
                    if (mc.screen instanceof TradeScreen ts) {
                        ts.onTradeFail(pkt.errorKey);
                    } else if (mc.screen instanceof SimpleTradePanel sp) {
                        sp.onTradeFail(pkt.errorKey);
                    }
                }
                case CLOSE -> {
                    if (mc.screen instanceof TradeScreen || mc.screen instanceof SimpleTradePanel) {
                        mc.setScreen(null);
                    }
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }

    
    public Mode getMode() { return mode; }
    public String getShopId() { return shopId; }
}

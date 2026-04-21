package org.com.arc_quest.trade.network;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.com.arc_quest.client.gui.AbstractTradeScreen;
import org.com.arc_quest.client.gui.DialogueScreen;
import org.com.arc_quest.client.gui.SimpleTradePanel;
import org.com.arc_quest.client.gui.TradeScreen;
import org.slf4j.Logger;

import java.util.function.Supplier;

/**
 * 服务端→客户端：打开交易窗口 / 交易结果反馈。
 */
public class S2COpenTradePacket {

    private static final Logger LOGGER = LogUtils.getLogger();

    public enum Mode {
        OPEN_FULL,
        OPEN_SIMPLE,
        TRADE_SUCCESS,
        TRADE_FAIL,
        CLOSE
    }

    public enum FailReason {
        GENERIC,          // 通用错误
        COOLDOWN,         // 冷却中
        LIMIT_REACHED,    // 限购已满
        CONDITION_FAIL,   // 条件不满足
        CANNOT_AFFORD,    // 余额不足
        NOT_VISIBLE       // 不可见
    }

    private final Mode mode;
    private final String shopId;
    private final String entryId;
    private final FailReason failReason;
    private final String errorKey;

    /** 各项数据数组（由于网络协议不变，保留接收逻辑，但 UI 不再直接消费它们） */
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

    public S2COpenTradePacket(Mode mode, String shopId,
                              int[] purchaseCounts, int[] maxPurchases,
                              long[] lastPurchaseTimes,
                              long[] purchaseGameTimes, long[] purchaseDayTimes,
                              int[] cooldownTypes, long[] cooldownValues,
                              int[] resetTimeTicks, boolean[] visibility,
                              boolean[] canBuyConditions) {
        this.mode = mode;
        this.shopId = shopId;
        this.entryId = null;
        this.failReason = null;
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

    public S2COpenTradePacket(Mode mode, String shopId, String entryId, FailReason failReason, String errorKey) {
        this.mode = mode;
        this.shopId = shopId;
        this.entryId = entryId;
        this.failReason = failReason;
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

    public static S2COpenTradePacket close() {
        return new S2COpenTradePacket(Mode.CLOSE, "", null, null, null);
    }

    public static S2COpenTradePacket openFull(String shopId,
                                              int[] purchaseCounts, int[] maxPurchases,
                                              long[] lastPurchaseTimes, long[] purchaseGameTimes, long[] purchaseDayTimes,
                                              int[] cooldownTypes, long[] cooldownValues,
                                              int[] resetTimeTicks, boolean[] visibility, boolean[] canBuyConditions) {
        return new S2COpenTradePacket(Mode.OPEN_FULL, shopId,
                purchaseCounts, maxPurchases, lastPurchaseTimes, purchaseGameTimes, purchaseDayTimes,
                cooldownTypes, cooldownValues, resetTimeTicks, visibility, canBuyConditions);
    }

    public static S2COpenTradePacket openSimple(String shopId,
                                                int[] purchaseCounts, int[] maxPurchases,
                                                long[] lastPurchaseTimes, long[] purchaseGameTimes, long[] purchaseDayTimes,
                                                int[] cooldownTypes, long[] cooldownValues,
                                                int[] resetTimeTicks, boolean[] visibility, boolean[] canBuyConditions) {
        return new S2COpenTradePacket(Mode.OPEN_SIMPLE, shopId,
                purchaseCounts, maxPurchases, lastPurchaseTimes, purchaseGameTimes, purchaseDayTimes,
                cooldownTypes, cooldownValues, resetTimeTicks, visibility, canBuyConditions);
    }

    public static S2COpenTradePacket tradeSuccess(String shopId, String entryId) {
        return new S2COpenTradePacket(Mode.TRADE_SUCCESS, shopId, entryId, null, null);
    }

    public static S2COpenTradePacket tradeFail(String shopId, String entryId, FailReason reason, String errorKey) {
        return new S2COpenTradePacket(Mode.TRADE_FAIL, shopId, entryId, reason, errorKey);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(mode);
        buf.writeUtf(shopId);

        if (mode == Mode.OPEN_FULL || mode == Mode.OPEN_SIMPLE) {
            int count = purchaseCounts != null ? purchaseCounts.length : 0;
            buf.writeVarInt(count);
            for (int i = 0; i < count; i++) {
                buf.writeVarInt(purchaseCounts[i]);
                if (maxPurchases != null) {
                    buf.writeVarInt(maxPurchases[i]);
                }
                if (lastPurchaseTimes != null) {
                    buf.writeLong(lastPurchaseTimes[i]);
                }
                if (purchaseGameTimes != null) {
                    buf.writeLong(purchaseGameTimes[i]);
                }
                if (purchaseDayTimes != null) {
                    buf.writeLong(purchaseDayTimes[i]);
                }
                if (cooldownTypes != null) {
                    buf.writeVarInt(cooldownTypes[i]);
                }
                if (cooldownValues != null) {
                    buf.writeLong(cooldownValues[i]);
                }
                if (resetTimeTicks != null) {
                    buf.writeVarInt(resetTimeTicks[i]);
                }
                if (visibility != null) {
                    buf.writeBoolean(visibility[i]);
                }
                buf.writeBoolean(canBuyConditions != null && canBuyConditions[i]);
            }
        } else if (mode == Mode.TRADE_FAIL || mode == Mode.TRADE_SUCCESS) {
            buf.writeUtf(entryId != null ? entryId : "");
            if (mode == Mode.TRADE_FAIL) {
                buf.writeEnum(failReason != null ? failReason : FailReason.GENERIC);
            }
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
            return new S2COpenTradePacket(mode, shopId, buf.readUtf(), buf.readEnum(FailReason.class), buf.readUtf());
        } else if (mode == Mode.TRADE_SUCCESS) {
            return new S2COpenTradePacket(mode, shopId, buf.readUtf(), null, buf.readUtf());
        } else {
            return new S2COpenTradePacket(mode, shopId, null, null, null);
        }
    }

    public static void handle(S2COpenTradePacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            switch (pkt.mode) {
                case OPEN_FULL -> {
                    ClientTradeCache.INSTANCE.updateSession(pkt.shopId, pkt.purchaseCounts, pkt.maxPurchases,
                            pkt.lastPurchaseTimes, pkt.purchaseGameTimes, pkt.purchaseDayTimes,
                            pkt.cooldownTypes, pkt.cooldownValues, pkt.resetTimeTicks, pkt.visibility, pkt.canBuyConditions);

                    if (mc.screen instanceof DialogueScreen) {
                        TradeScreen.setParentScreen(mc.screen);
                    }

                    if (mc.screen instanceof TradeScreen ts && ts.getShopId().equals(pkt.shopId)) {
                        LOGGER.info("[Trade-Packet] Updating existing TradeScreen for shop={}", pkt.shopId);
                        ts.refreshData();
                    } else {
                        LOGGER.warn("[Trade-Packet] Creating new TradeScreen for shop={}", pkt.shopId);
                        mc.setScreen(new TradeScreen(pkt.shopId));
                    }
                }

                case OPEN_SIMPLE -> {
                    ClientTradeCache.INSTANCE.updateSession(pkt.shopId, pkt.purchaseCounts, pkt.maxPurchases,
                            pkt.lastPurchaseTimes, pkt.purchaseGameTimes, pkt.purchaseDayTimes,
                            pkt.cooldownTypes, pkt.cooldownValues, pkt.resetTimeTicks, pkt.visibility, pkt.canBuyConditions);

                    if (mc.screen instanceof DialogueScreen) {
                        SimpleTradePanel.setParentScreen(mc.screen);
                    }

                    if (mc.screen instanceof SimpleTradePanel sp && sp.getShopId().equals(pkt.shopId)) {
                        sp.refreshData();
                    } else {
                        mc.setScreen(new SimpleTradePanel(pkt.shopId));
                    }
                }

                case TRADE_SUCCESS -> {
                    ClientTradeCache.INSTANCE.handlePurchaseResult(pkt.shopId, pkt.entryId, true, null);
                    if (mc.screen instanceof AbstractTradeScreen ts) {
                        ts.onTradeSuccess();
                    }
                }

                case TRADE_FAIL -> {
                    ClientTradeCache.INSTANCE.handlePurchaseResult(pkt.shopId, pkt.entryId, false, pkt.failReason);
                    if (mc.screen instanceof AbstractTradeScreen ts) {
                        ts.onTradeFail(pkt.errorKey);
                    }
                }

                case CLOSE -> {
                    if (mc.screen instanceof AbstractTradeScreen) {
                        TradeScreen.setParentScreen(null);
                        SimpleTradePanel.setParentScreen(null);
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
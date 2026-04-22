package org.com.arc_quest.trade.gacha.network;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.com.arc_quest.client.gui.gacha.GachaScreen;
import org.com.arc_quest.trade.gacha.api.GachaShopDefinition;
import org.com.arc_quest.trade.gacha.registry.GachaRegistry;
import org.slf4j.Logger;

import java.util.function.Supplier;

/**
 * 服务端发送抽奖结果给客户端。
 * <p>
 * 【状态安全设计】
 * 1. 同步底层缓存数据。
 * 2. 显式校验当前界面，直接向 GachaScreen 下达状态转移指令，保证动画与网络回包的 100% 强绑定。
 */
public class S2CDrawResultPacket {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final String shopId;
    private final String drawnItemId;
    private final String rarityName;
    private final int actualCount;
    private final boolean pityTriggered;
    private final int newPityCounter;

    private final boolean canDraw;
    private final int remainingDraws;

    private final long lastDrawRealTime;
    private final long lastDrawGameTime;
    private final long lastDrawDayTime;

    public S2CDrawResultPacket(String shopId, String drawnItemId, String rarityName,
                               int actualCount, boolean pityTriggered, int newPityCounter,
                               boolean canDraw, int remainingDraws,
                               long lastDrawRealTime, long lastDrawGameTime, long lastDrawDayTime) {
        this.shopId = shopId;
        this.drawnItemId = drawnItemId;
        this.rarityName = rarityName;
        this.actualCount = actualCount;
        this.pityTriggered = pityTriggered;
        this.newPityCounter = newPityCounter;
        this.canDraw = canDraw;
        this.remainingDraws = remainingDraws;
        this.lastDrawRealTime = lastDrawRealTime;
        this.lastDrawGameTime = lastDrawGameTime;
        this.lastDrawDayTime = lastDrawDayTime;
    }

    public static void encode(S2CDrawResultPacket pkt, FriendlyByteBuf buf) {
        buf.writeUtf(pkt.shopId);
        buf.writeUtf(pkt.drawnItemId);
        buf.writeUtf(pkt.rarityName);
        buf.writeInt(pkt.actualCount);
        buf.writeBoolean(pkt.pityTriggered);
        buf.writeInt(pkt.newPityCounter);
        buf.writeBoolean(pkt.canDraw);
        buf.writeInt(pkt.remainingDraws);
        buf.writeLong(pkt.lastDrawRealTime);
        buf.writeLong(pkt.lastDrawGameTime);
        buf.writeLong(pkt.lastDrawDayTime);
    }

    public static S2CDrawResultPacket decode(FriendlyByteBuf buf) {
        return new S2CDrawResultPacket(
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readInt(),
                buf.readBoolean(),
                buf.readInt(),
                buf.readBoolean(),
                buf.readInt(),
                buf.readLong(),
                buf.readLong(),
                buf.readLong()
        );
    }

    public static void handle(S2CDrawResultPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            ClientGachaCache.INSTANCE.recordDrawResult(
                    pkt.shopId,
                    pkt.drawnItemId,
                    pkt.rarityName,
                    pkt.actualCount,
                    pkt.pityTriggered,
                    pkt.newPityCounter
            );

            GachaShopDefinition gachaShop = GachaRegistry.get(pkt.shopId);
            int cooldownType = gachaShop != null ? gachaShop.getCooldownType().ordinal() : 0;
            long cooldownValue = gachaShop != null ? gachaShop.getCooldownValue() : 0;
            int resetTimeTicks = gachaShop != null ? gachaShop.getResetTimeTicks() : 0;

            ClientGachaCache.INSTANCE.updateSession(
                    pkt.shopId,
                    pkt.newPityCounter,
                    ClientGachaCache.INSTANCE.getTotalDraws(pkt.shopId) + 1,
                    pkt.canDraw,
                    pkt.lastDrawRealTime,
                    pkt.lastDrawGameTime,
                    pkt.lastDrawDayTime,
                    cooldownType,
                    cooldownValue,
                    resetTimeTicks
            );

            if (mc.screen instanceof GachaScreen gachaScreen) {
                if (gachaScreen.getShopId().equals(pkt.shopId)) {
                    var result = ClientGachaCache.INSTANCE.getLastDrawResult(pkt.shopId);
                    if (result != null) {
                        gachaScreen.triggerRollingAnimation(result);
                    } else {
                        LOGGER.warn("[Gacha] Draw result not found in cache for shop {}", pkt.shopId);
                    }
                }
            } else {
                LOGGER.debug("[Gacha] Screen changed before draw result arrived");
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
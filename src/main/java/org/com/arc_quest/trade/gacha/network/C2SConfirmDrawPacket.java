package org.com.arc_quest.trade.gacha.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.com.arc_quest.Arc_quest;
import org.com.arc_quest.quest.capability.IQuestCapability;
import org.com.arc_quest.quest.capability.QuestCapabilityProvider;
import org.com.arc_quest.trade.gacha.api.GachaShopDefinition;
import org.com.arc_quest.trade.gacha.registry.GachaRegistry;
import org.com.arc_quest.trade.gacha.runtime.GachaScreenOpener;

import java.util.function.Supplier;

/**
 * 客户端确认抽奖结果并请求发放奖励。
 * <p>
 * 在抽奖动画第二阶段结束时由客户端发送，
 * 服务端收到后从 PendingDrawManager 中取出暂存的结果并发放奖励。
 */
public class C2SConfirmDrawPacket {

    final String shopId;

    public C2SConfirmDrawPacket(String shopId) {
        this.shopId = shopId;
    }

    public static void encode(C2SConfirmDrawPacket pkt, FriendlyByteBuf buf) {
        buf.writeUtf(pkt.shopId);
    }

    public static C2SConfirmDrawPacket decode(FriendlyByteBuf buf) {
        return new C2SConfirmDrawPacket(buf.readUtf());
    }

    public static void handle(C2SConfirmDrawPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            boolean success = PendingDrawManager.confirmAndGrant(player, pkt.shopId);

            if (!success) {
                Arc_quest.LOGGER.warn("[Gacha] Player {} tried to confirm draw for shop '{}' but no matching pending data found or it expired",
                        player.getName().getString(), pkt.shopId);
                return;
            }

            Arc_quest.LOGGER.info("[Gacha] Confirmed and granted draw reward for player {} in shop {}",
                    player.getName().getString(), pkt.shopId);

            GachaShopDefinition shop = GachaRegistry.get(pkt.shopId);
            IQuestCapability cap = QuestCapabilityProvider.getOrNull(player);
            if (shop != null && cap != null) {
                GachaScreenOpener.openGachaScreen(player, shop, cap);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}

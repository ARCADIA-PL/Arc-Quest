package org.com.arc_quest.trade.gacha.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.com.arc_quest.Arc_quest;
import org.com.arc_quest.quest.capability.IQuestCapability;
import org.com.arc_quest.quest.network.SyncObservability;
import org.com.arc_quest.trade.gacha.api.GachaShopDefinition;
import org.com.arc_quest.trade.gacha.runtime.GachaScreenOpener;
import org.com.arc_quest.trade.network.RejectCodeDictionary;

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
            ServerPlayer player = GachaRequestValidator.requirePlayer(ctx.get().getSender(), "confirm_draw", pkt.shopId);
            if (player == null) return;

            SyncObservability.trace("gacha", pkt.shopId, player.getName().getString(),
                    SyncObservability.Stage.ACTION, "confirm_draw");

            boolean success = PendingDrawManager.confirmAndGrant(player, pkt.shopId);

            if (!success) {
                GachaRequestValidator.reject(
                        RejectCodeDictionary.Code.PENDING_CONFIRM_MISSING,
                        "confirm_draw",
                        player,
                        pkt.shopId,
                        "no pending draw to confirm"
                );
                SyncObservability.trace("gacha", pkt.shopId, player.getName().getString(),
                        SyncObservability.Stage.RESULT, "confirm_draw_failed");
                return;
            }

            Arc_quest.LOGGER.info("[Gacha] Confirmed and granted draw reward for player {} in shop {}",
                    player.getName().getString(), pkt.shopId);
            SyncObservability.trace("gacha", pkt.shopId, player.getName().getString(),
                    SyncObservability.Stage.RESULT, "confirm_draw_success");

            GachaShopDefinition shop = GachaRequestValidator.requireShop(pkt.shopId, player, "confirm_draw");
            if (shop == null) return;

            IQuestCapability cap = GachaRequestValidator.requireCapability(player, "confirm_draw", pkt.shopId);
            if (cap == null) return;

            GachaScreenOpener.syncGachaState(player, shop, cap);
        });
        ctx.get().setPacketHandled(true);
    }
}
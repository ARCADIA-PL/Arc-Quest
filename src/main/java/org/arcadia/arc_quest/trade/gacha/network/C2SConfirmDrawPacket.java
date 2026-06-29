package org.arcadia.arc_quest.trade.gacha.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.quest.network.SyncObservability;
import org.arcadia.arc_quest.trade.gacha.api.GachaShopDefinition;
import org.arcadia.arc_quest.trade.gacha.runtime.GachaScreenOpener;
import org.arcadia.arc_quest.trade.network.RejectCodeDictionary;

/**
 * 客户端确认抽奖结果并请求发放奖励。
 * <p>
 * 在抽奖动画第二阶段结束时由客户端发送，
 * 服务端收到后从 PendingDrawManager 中取出暂存的结果并发放奖励。
 */
public class C2SConfirmDrawPacket implements CustomPacketPayload {

    public static final Type<C2SConfirmDrawPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "confirm_draw"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SConfirmDrawPacket> STREAM_CODEC =
            StreamCodec.ofMember(C2SConfirmDrawPacket::encode, C2SConfirmDrawPacket::decode);

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

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SConfirmDrawPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sender)) {
                return;
            }
            ServerPlayer player = GachaRequestValidator.requirePlayer(sender, "confirm_draw", pkt.shopId);
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

            Arc_Quest.LOGGER.info("[Gacha] Confirmed and granted draw reward for player {} in shop {}",
                    player.getName().getString(), pkt.shopId);
            SyncObservability.trace("gacha", pkt.shopId, player.getName().getString(),
                    SyncObservability.Stage.RESULT, "confirm_draw_success");

            GachaShopDefinition shop = GachaRequestValidator.requireShop(pkt.shopId, player, "confirm_draw");
            if (shop == null) return;

            ArcQuestPlayer data = GachaRequestValidator.requireData(player, "confirm_draw", pkt.shopId);
            if (data == null) return;

            GachaScreenOpener.syncGachaState(player, shop, data);
        });
    }
}
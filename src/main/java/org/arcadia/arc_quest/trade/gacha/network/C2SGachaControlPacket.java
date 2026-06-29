package org.arcadia.arc_quest.trade.gacha.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.api.event.gacha.GachaEvents;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.quest.network.SyncObservability;
import org.arcadia.arc_quest.trade.gacha.api.GachaShopDefinition;
import org.arcadia.arc_quest.trade.gacha.runtime.GachaScreenOpener;

/**
 * 客户端 -> 服务端：抽奖界面控制请求
 * OPEN: 打开界面
 * SYNC: 同步权威状态（不打开界面）
 */
public class C2SGachaControlPacket implements CustomPacketPayload {

    public static final Type<C2SGachaControlPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "gacha_control"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SGachaControlPacket> STREAM_CODEC =
            StreamCodec.ofMember(C2SGachaControlPacket::encode, C2SGachaControlPacket::decode);

    private final Action action;
    private final String shopId;

    public C2SGachaControlPacket(Action action, String shopId) {
        this.action = action;
        this.shopId = shopId;
    }

    public static C2SGachaControlPacket open(String shopId) {
        return new C2SGachaControlPacket(Action.OPEN, shopId);
    }

    public static C2SGachaControlPacket sync(String shopId) {
        return new C2SGachaControlPacket(Action.SYNC, shopId);
    }

    public static C2SGachaControlPacket close(String shopId) {
        return new C2SGachaControlPacket(Action.CLOSE, shopId);
    }

    public static void encode(C2SGachaControlPacket pkt, FriendlyByteBuf buf) {
        buf.writeEnum(pkt.action);
        buf.writeUtf(pkt.shopId);
    }

    public static C2SGachaControlPacket decode(FriendlyByteBuf buf) {
        Action action = buf.readEnum(Action.class);
        String shopId = buf.readUtf();
        return new C2SGachaControlPacket(action, shopId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SGachaControlPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            ServerPlayer sender = GachaRequestValidator.requirePlayer(player, "gacha_control", pkt.shopId);
            if (sender == null) return;

            if (pkt.action == Action.SYNC) {
                SyncObservability.recordRequest("gacha", pkt.shopId, sender.getName().getString());
                SyncObservability.trace("gacha", pkt.shopId, sender.getName().getString(), SyncObservability.Stage.SYNC_REQUEST, "manual_sync");
            }

            if (pkt.action == Action.CLOSE) {
                GachaScreenOpener.closeActiveShopContext(sender);
                NeoForge.EVENT_BUS.post(new GachaEvents.ClosedEvent(sender, pkt.shopId));
                return;
            }

            GachaShopDefinition shop = GachaRequestValidator.requireShop(pkt.shopId, sender, "gacha_control");
            if (shop == null) return;

            ArcQuestPlayer data = GachaRequestValidator.requireData(sender, "gacha_control", pkt.shopId);
            if (data == null) return;

            switch (pkt.action) {
                case OPEN -> GachaScreenOpener.openGachaScreen(sender, shop, data);
                case SYNC -> GachaScreenOpener.syncGachaState(sender, shop, data);
                case CLOSE -> {
                }
            }
        });
    }

    public enum Action {
        OPEN,
        SYNC,
        CLOSE
    }
}
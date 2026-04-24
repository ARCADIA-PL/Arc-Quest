package org.com.arc_quest.trade.gacha.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.com.arc_quest.quest.capability.IQuestCapability;
import org.com.arc_quest.quest.network.SyncObservability;
import org.com.arc_quest.trade.gacha.api.GachaShopDefinition;
import org.com.arc_quest.trade.gacha.runtime.GachaScreenOpener;

import java.util.function.Supplier;

/**
 * 客户端 -> 服务端：抽奖界面控制请求
 * OPEN: 打开界面
 * SYNC: 同步权威状态（不打开界面）
 */
public class C2SGachaControlPacket {

    public enum Action {
        OPEN,
        SYNC
    }

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

    public static void encode(C2SGachaControlPacket pkt, FriendlyByteBuf buf) {
        buf.writeEnum(pkt.action);
        buf.writeUtf(pkt.shopId);
    }

    public static C2SGachaControlPacket decode(FriendlyByteBuf buf) {
        Action action = buf.readEnum(Action.class);
        String shopId = buf.readUtf();
        return new C2SGachaControlPacket(action, shopId);
    }

    public static void handle(C2SGachaControlPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = GachaRequestValidator.requirePlayer(ctx.get().getSender(), "gacha_control", pkt.shopId);
            if (sender == null) return;

            if (pkt.action == Action.SYNC) {
                SyncObservability.recordRequest("gacha", pkt.shopId, sender.getName().getString());
            }

            GachaShopDefinition shop = GachaRequestValidator.requireShop(pkt.shopId, sender, "gacha_control");
            if (shop == null) return;

            IQuestCapability cap = GachaRequestValidator.requireCapability(sender, "gacha_control", pkt.shopId);
            if (cap == null) return;

            switch (pkt.action) {
                case OPEN -> GachaScreenOpener.openGachaScreen(sender, shop, cap);
                case SYNC -> GachaScreenOpener.syncGachaState(sender, shop, cap);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
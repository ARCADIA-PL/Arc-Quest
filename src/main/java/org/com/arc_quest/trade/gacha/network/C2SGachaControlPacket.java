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
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            GachaShopDefinition shop = GachaRegistry.get(pkt.shopId);
            if (shop == null) {
                Arc_quest.LOGGER.warn("[Gacha] Unknown shop '{}' requested by {}", pkt.shopId, player.getName().getString());
                return;
            }

            IQuestCapability cap = QuestCapabilityProvider.getOrNull(player);
            if (cap == null) {
                Arc_quest.LOGGER.warn("[Gacha] Player {} has no quest capability", player.getName().getString());
                return;
            }

            switch (pkt.action) {
                case OPEN -> GachaScreenOpener.openGachaScreen(player, shop, cap);
                case SYNC -> GachaScreenOpener.syncGachaState(player, shop, cap);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
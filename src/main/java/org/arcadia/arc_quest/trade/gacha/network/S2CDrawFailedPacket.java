package org.arcadia.arc_quest.trade.gacha.network;

import net.minecraft.network.chat.ComponentSerialization;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.client.hud.gacha.GachaScreen;
import org.arcadia.arc_quest.trade.api.CostShortfallLine;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端发送抽奖失败通知给客户端（冷却/限购/条件不满足）。
 */
public class S2CDrawFailedPacket implements CustomPacketPayload {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final Type<S2CDrawFailedPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "draw_failed"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CDrawFailedPacket> STREAM_CODEC =
            StreamCodec.ofMember(S2CDrawFailedPacket::encode, S2CDrawFailedPacket::decode);

    private final String shopId;
    private final String failReason;
    private final String errorKey;
    private final List<CostShortfallLine> shortfallLines;

    public S2CDrawFailedPacket(String shopId, String failReason) {
        this(shopId, failReason, "", List.of());
    }

    public S2CDrawFailedPacket(String shopId, String failReason, List<CostShortfallLine> shortfallLines) {
        this(shopId, failReason, "", shortfallLines);
    }

    public S2CDrawFailedPacket(String shopId,
                               String failReason,
                               String errorKey,
                               List<CostShortfallLine> shortfallLines) {
        this.shopId = shopId;
        this.failReason = failReason;
        this.errorKey = errorKey != null ? errorKey : "";
        this.shortfallLines = shortfallLines != null ? List.copyOf(shortfallLines) : List.of();
    }

    public static void encode(S2CDrawFailedPacket pkt, FriendlyByteBuf buf) {
        buf.writeUtf(pkt.shopId);
        buf.writeUtf(pkt.failReason);
        buf.writeUtf(pkt.errorKey);
        buf.writeVarInt(pkt.shortfallLines.size());
        for (CostShortfallLine line : pkt.shortfallLines) {
            ComponentSerialization.STREAM_CODEC.encode((RegistryFriendlyByteBuf) buf, line.label());
            buf.writeVarInt(line.required());
            buf.writeVarInt(line.owned());
            buf.writeVarInt(line.missing());
        }
    }

    public static S2CDrawFailedPacket decode(FriendlyByteBuf buf) {
        String shopId = buf.readUtf();
        String failReason = buf.readUtf();
        String errorKey = buf.readUtf();
        int shortfallCount = buf.readVarInt();
        List<CostShortfallLine> shortfalls = new ArrayList<>(shortfallCount);
        for (int i = 0; i < shortfallCount; i++) {
            shortfalls.add(new CostShortfallLine(
                    ComponentSerialization.STREAM_CODEC.decode((RegistryFriendlyByteBuf) buf),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt()
            ));
        }
        return new S2CDrawFailedPacket(shopId, failReason, errorKey, shortfalls);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(S2CDrawFailedPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            LOGGER.info("[Gacha-Failed] Player {} draw failed for shop {}: reason={}, errorKey={}",
                    mc.player.getName().getString(), pkt.shopId, pkt.failReason, pkt.errorKey);

            String clientFailReason = pkt.errorKey != null && !pkt.errorKey.isEmpty()
                    ? pkt.errorKey
                    : pkt.failReason;
            ClientGachaCache.INSTANCE.recordDrawFailure(pkt.shopId, clientFailReason, pkt.shortfallLines);

            if (mc.screen instanceof GachaScreen gachaScreen) {
                if (gachaScreen.getShopId().equals(pkt.shopId)) {
                    gachaScreen.onDrawFailedAndReturnToPreview();
                }
            }
        });
    }
}

package org.arcadia.arc_quest.guide.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.arcadia.arc_quest.Arc_Quest;

import java.util.ArrayList;
import java.util.List;

public final class S2CSyncGuideStatePacket implements CustomPacketPayload {

    public static final Type<S2CSyncGuideStatePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "sync_guide_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CSyncGuideStatePacket> STREAM_CODEC =
            StreamCodec.ofMember(S2CSyncGuideStatePacket::encode, S2CSyncGuideStatePacket::decode);

    private final List<ResourceLocation> unlockedGuides;
    private final List<ResourceLocation> seenGuides;

    public S2CSyncGuideStatePacket(List<ResourceLocation> unlockedGuides, List<ResourceLocation> seenGuides) {
        this.unlockedGuides = List.copyOf(unlockedGuides);
        this.seenGuides = List.copyOf(seenGuides);
    }

    public static void encode(S2CSyncGuideStatePacket pkt, FriendlyByteBuf buf) {
        buf.writeVarInt(pkt.unlockedGuides.size());
        for (ResourceLocation id : pkt.unlockedGuides) {
            buf.writeResourceLocation(id);
        }
        buf.writeVarInt(pkt.seenGuides.size());
        for (ResourceLocation id : pkt.seenGuides) {
            buf.writeResourceLocation(id);
        }
    }

    public static S2CSyncGuideStatePacket decode(FriendlyByteBuf buf) {
        int unlockedSize = buf.readVarInt();
        List<ResourceLocation> unlocked = new ArrayList<>(unlockedSize);
        for (int i = 0; i < unlockedSize; i++) {
            unlocked.add(buf.readResourceLocation());
        }

        int seenSize = buf.readVarInt();
        List<ResourceLocation> seen = new ArrayList<>(seenSize);
        for (int i = 0; i < seenSize; i++) {
            seen.add(buf.readResourceLocation());
        }
        return new S2CSyncGuideStatePacket(unlocked, seen);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(S2CSyncGuideStatePacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientGuideCache.INSTANCE.applySync(pkt.unlockedGuides, pkt.seenGuides));
    }
}

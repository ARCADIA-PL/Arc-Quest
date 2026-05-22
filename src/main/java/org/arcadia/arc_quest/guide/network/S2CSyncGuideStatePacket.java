package org.arcadia.arc_quest.guide.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import org.arcadia.arc_quest.client.hud.guide.ClientGuideCache;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class S2CSyncGuideStatePacket {

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

    public static void handle(S2CSyncGuideStatePacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientGuideCache.INSTANCE.applySync(pkt.unlockedGuides, pkt.seenGuides));
        ctx.get().setPacketHandled(true);
    }
}

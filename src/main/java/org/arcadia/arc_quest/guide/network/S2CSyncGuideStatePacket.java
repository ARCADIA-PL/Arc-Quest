package org.arcadia.arc_quest.guide.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class S2CSyncGuideStatePacket {

    private final List<ResourceLocation> unlockedGuides;
    private final List<ResourceLocation> seenGuides;
    private final Map<ResourceLocation, Integer> guideProgress;

    public S2CSyncGuideStatePacket(List<ResourceLocation> unlockedGuides, List<ResourceLocation> seenGuides) {
        this(unlockedGuides, seenGuides, Map.of());
    }

    public S2CSyncGuideStatePacket(List<ResourceLocation> unlockedGuides, List<ResourceLocation> seenGuides,
                                   Map<ResourceLocation, Integer> guideProgress) {
        this.unlockedGuides = List.copyOf(unlockedGuides);
        this.seenGuides = List.copyOf(seenGuides);
        this.guideProgress = Map.copyOf(guideProgress);
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
        buf.writeVarInt(pkt.guideProgress.size());
        pkt.guideProgress.forEach((id, page) -> {
            buf.writeResourceLocation(id);
            buf.writeVarInt(Math.max(0, page));
        });
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
        int progressSize = buf.readVarInt();
        Map<ResourceLocation, Integer> progress = new LinkedHashMap<>();
        for (int i = 0; i < progressSize; i++) {
            progress.put(buf.readResourceLocation(), Math.max(0, buf.readVarInt()));
        }
        return new S2CSyncGuideStatePacket(unlocked, seen, progress);
    }

    public static void handle(S2CSyncGuideStatePacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientGuideCache.INSTANCE.applySync(
                pkt.unlockedGuides, pkt.seenGuides, pkt.guideProgress));
        ctx.get().setPacketHandled(true);
    }
}

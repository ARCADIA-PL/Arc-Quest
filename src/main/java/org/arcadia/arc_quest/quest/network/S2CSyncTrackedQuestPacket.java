package org.arcadia.arc_quest.quest.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public final class S2CSyncTrackedQuestPacket {

    @Nullable
    private final String questId;

    public S2CSyncTrackedQuestPacket(@Nullable String questId) {
        this.questId = questId;
    }

    public static void encode(S2CSyncTrackedQuestPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.questId != null);
        if (packet.questId != null) buffer.writeUtf(packet.questId, 256);
    }

    public static S2CSyncTrackedQuestPacket decode(FriendlyByteBuf buffer) {
        return new S2CSyncTrackedQuestPacket(buffer.readBoolean() ? buffer.readUtf(256) : null);
    }

    public static void handle(S2CSyncTrackedQuestPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> ClientQuestCache.INSTANCE.applyTrackedQuestSync(packet.questId));
        context.setPacketHandled(true);
    }
}

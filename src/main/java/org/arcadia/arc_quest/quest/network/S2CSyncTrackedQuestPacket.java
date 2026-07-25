package org.arcadia.arc_quest.quest.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.arcadia.arc_quest.Arc_Quest;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public final class S2CSyncTrackedQuestPacket implements CustomPacketPayload {

    public static final Type<S2CSyncTrackedQuestPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "sync_tracked_quest"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CSyncTrackedQuestPacket> STREAM_CODEC =
            StreamCodec.ofMember(S2CSyncTrackedQuestPacket::encode, S2CSyncTrackedQuestPacket::decode);

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

    public static void handle(S2CSyncTrackedQuestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientQuestCache.INSTANCE.applyTrackedQuestSync(packet.questId));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

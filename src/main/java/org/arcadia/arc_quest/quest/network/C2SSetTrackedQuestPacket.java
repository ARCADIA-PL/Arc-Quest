package org.arcadia.arc_quest.quest.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.arcadia.arc_quest.quest.service.TrackedQuestService;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Supplier;

public final class C2SSetTrackedQuestPacket {

    @Nullable
    private final String questId;

    public C2SSetTrackedQuestPacket(@Nullable String questId) {
        this.questId = questId;
    }

    public static void encode(C2SSetTrackedQuestPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.questId != null);
        if (packet.questId != null) buffer.writeUtf(packet.questId, 256);
    }

    public static C2SSetTrackedQuestPacket decode(FriendlyByteBuf buffer) {
        return new C2SSetTrackedQuestPacket(buffer.readBoolean() ? buffer.readUtf(256) : null);
    }

    public static void handle(C2SSetTrackedQuestPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                boolean changed = TrackedQuestService.setTrackedQuest(player, packet.questId);
                if (!changed) {
                    ArcQuestPlayer data = ArcQuestPlayerManager.getOrCreate(player);
                    ArcQuestNetwork.syncTrackedQuest(player, data);
                }
            }
        });
        context.setPacketHandled(true);
    }

    public static void clearPlayer(UUID playerId) {
    }

    public static void clear() {
    }
}

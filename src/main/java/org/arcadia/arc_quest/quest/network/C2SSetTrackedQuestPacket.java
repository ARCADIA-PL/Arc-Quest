package org.arcadia.arc_quest.quest.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.arcadia.arc_quest.quest.service.TrackedQuestService;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class C2SSetTrackedQuestPacket {

    private static final int UPDATE_COOLDOWN_TICKS = 2;
    private static final ConcurrentHashMap<UUID, Integer> LAST_UPDATE_TICK = new ConcurrentHashMap<>();

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
            if (player != null && acquireUpdateSlot(player)) {
                TrackedQuestService.setTrackedQuest(player, packet.questId);
            }
        });
        context.setPacketHandled(true);
    }

    private static boolean acquireUpdateSlot(ServerPlayer player) {
        int currentTick = player.getServer().getTickCount();
        Integer previousTick = LAST_UPDATE_TICK.get(player.getUUID());
        if (previousTick != null && currentTick - previousTick < UPDATE_COOLDOWN_TICKS) return false;
        LAST_UPDATE_TICK.put(player.getUUID(), currentTick);
        return true;
    }

    public static void clearPlayer(UUID playerId) {
        LAST_UPDATE_TICK.remove(playerId);
    }

    public static void clear() {
        LAST_UPDATE_TICK.clear();
    }
}

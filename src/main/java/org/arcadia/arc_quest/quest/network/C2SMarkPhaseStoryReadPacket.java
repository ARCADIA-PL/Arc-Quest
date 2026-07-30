package org.arcadia.arc_quest.quest.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.arcadia.arc_quest.quest.api.PhaseDefinition;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class C2SMarkPhaseStoryReadPacket {

    private static final int MAX_ID_LENGTH = 256;
    private static final int UPDATE_COOLDOWN_TICKS = 2;
    private static final ConcurrentHashMap<UUID, Integer> LAST_UPDATE_TICK = new ConcurrentHashMap<>();

    private final String questId;
    private final String phaseId;

    public C2SMarkPhaseStoryReadPacket(String questId, String phaseId) {
        this.questId = questId;
        this.phaseId = phaseId;
    }

    public static void encode(C2SMarkPhaseStoryReadPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.questId, MAX_ID_LENGTH);
        buffer.writeUtf(packet.phaseId, MAX_ID_LENGTH);
    }

    public static C2SMarkPhaseStoryReadPacket decode(FriendlyByteBuf buffer) {
        return new C2SMarkPhaseStoryReadPacket(
                buffer.readUtf(MAX_ID_LENGTH),
                buffer.readUtf(MAX_ID_LENGTH)
        );
    }

    public static void handle(C2SMarkPhaseStoryReadPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !acquireUpdateSlot(player)) return;
            if (packet.questId.isBlank() || packet.phaseId.isBlank()) return;

            QuestDefinition quest = QuestRegistry.get(packet.questId);
            if (quest == null) return;
            PhaseDefinition phase = quest.getPhase(packet.phaseId);
            if (phase == null || !phase.hasStory()) return;

            ArcQuestPlayer data = ArcQuestPlayerManager.getOrCreate(player);
            QuestRuntimeData runtime = data.getActiveQuest(packet.questId);
            boolean reached = runtime != null
                    && (runtime.isPhaseActive(packet.phaseId) || runtime.isPhaseCompleted(packet.phaseId));
            if (!reached && !data.isQuestCompleted(packet.questId) && !data.isQuestFailed(packet.questId)) return;

            if (data.markPhaseStoryRead(packet.questId, packet.phaseId)) {
                QuestSyncCoordinator.persistAndSyncIfChanged(player, data);
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

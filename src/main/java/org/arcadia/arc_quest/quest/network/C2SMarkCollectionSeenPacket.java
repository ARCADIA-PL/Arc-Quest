package org.arcadia.arc_quest.quest.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import org.arcadia.arc_quest.quest.logic.profile.collection.CollectionProgressProjector;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import java.util.Set;
import java.util.function.Supplier;

/** Marks only the currently authorized entry and content; does not discover or grant anything. */
public record C2SMarkCollectionSeenPacket(String questId, String phaseId, String bindingId) {
    public static void encode(C2SMarkCollectionSeenPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.questId, 256); buffer.writeUtf(packet.phaseId, 256); buffer.writeUtf(packet.bindingId, 256);
    }
    public static C2SMarkCollectionSeenPacket decode(FriendlyByteBuf buffer) {
        return new C2SMarkCollectionSeenPacket(buffer.readUtf(256), buffer.readUtf(256), buffer.readUtf(256));
    }
    public static void handle(C2SMarkCollectionSeenPacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            var player = context.get().getSender();
            if (player == null) return;
            var data = ArcQuestPlayerManager.get(player);
            var quest = QuestRegistry.getServerDefinition(ResourceLocation.tryParse(packet.questId));
            var phase = quest == null ? null : quest.getPhase(packet.phaseId);
            if (data == null || phase == null || !phase.hasCollectionSheet()) return;
            var runtime = data.getActiveQuest(packet.questId);
            if (runtime == null) runtime = data.getCollectionArchives().get(packet.questId);
            if (runtime == null) return;
            var row = CollectionProgressProjector.project(quest, phase, runtime, data.getCollectionRecords()).binding(packet.bindingId);
            if (row == null || !row.revealed() || !row.discovered()) return;
            boolean changed = data.getCollectionRecords().markSeen(row.entryId(), "entry");
            for (var block : row.content()) changed |= data.getCollectionRecords().markSeen(row.entryId(), block.blockId());
            if (changed) {
                ArcQuestNetwork.syncCollectionRecords(player, Set.of(row.entryId()));
                QuestSyncCoordinator.persistSnapshot(player, data);
            }
        });
        context.get().setPacketHandled(true);
    }
}

package org.arcadia.arc_quest.quest.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.quest.logic.profile.collection.CollectionProgressProjector;
import org.arcadia.arc_quest.quest.logic.CollectionRunAccess;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import java.util.Set;
import java.util.List;
import java.util.LinkedHashSet;

/** Marks only the currently authorized entry and content; does not discover or grant anything. */
public record C2SMarkCollectionSeenPacket(String questId, String phaseId, List<String> bindingIds) implements CustomPacketPayload {
    public static final Type<C2SMarkCollectionSeenPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "mark_collection_seen"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SMarkCollectionSeenPacket> STREAM_CODEC =
            StreamCodec.ofMember(C2SMarkCollectionSeenPacket::encode, C2SMarkCollectionSeenPacket::decode);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public C2SMarkCollectionSeenPacket {
        bindingIds = List.copyOf(bindingIds);
        if (bindingIds.size() > 256) throw new IllegalArgumentException("Too many collection read acknowledgements");
    }
    public C2SMarkCollectionSeenPacket(String questId, String phaseId, String bindingId) {
        this(questId, phaseId, List.of(bindingId));
    }
    public static void encode(C2SMarkCollectionSeenPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.questId, 256); buffer.writeUtf(packet.phaseId, 256);
        buffer.writeCollection(packet.bindingIds, (buf, id) -> buf.writeUtf(id, 256));
    }
    public static C2SMarkCollectionSeenPacket decode(FriendlyByteBuf buffer) {
        String questId = buffer.readUtf(256), phaseId = buffer.readUtf(256);
        int count = buffer.readVarInt();
        if (count < 0 || count > 256) throw new io.netty.handler.codec.DecoderException("Too many collection read acknowledgements");
        List<String> bindings = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) bindings.add(buffer.readUtf(256));
        return new C2SMarkCollectionSeenPacket(questId, phaseId, bindings);
    }
    public static void handle(C2SMarkCollectionSeenPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> { if (context.player() instanceof ServerPlayer player) packet.apply(player); });
    }

    public void apply(ServerPlayer player) {
        if (player == null) return;
        var data = ArcQuestPlayerManager.get(player);
        if (data == null) return;
        var runtime = data.getActiveQuest(questId);
        if (runtime == null) runtime = data.getCollectionArchives().get(questId);
        if (runtime == null) return;
        var quest = CollectionRunAccess.resolve(player.server, runtime);
        var phase = quest == null ? null : quest.getPhase(phaseId);
        if (phase == null || !phase.hasCollectionSheet()
                || !(runtime.isPhaseActive(phaseId) || runtime.isPhaseCompleted(phaseId)
                || runtime.isPhasePendingManualAdvance(phaseId))) return;
        var sheet = CollectionProgressProjector.project(quest, phase, runtime, data.getCollectionRecords());
        Set<ResourceLocation> changedIds = new LinkedHashSet<>();
        for (String bindingId : bindingIds) {
            var row = sheet.binding(bindingId);
            if (row == null || !row.visible() || !row.revealed()) continue;
            boolean changed = data.getCollectionRecords().markSeen(row.entryId(), "entry");
            for (var block : row.content()) changed |= data.getCollectionRecords().markSeen(row.entryId(), block.blockId());
            if (changed) changedIds.add(row.entryId());
        }
        if (!changedIds.isEmpty()) {
            ArcQuestNetwork.syncCollectionRecords(player, changedIds);
            QuestSyncCoordinator.persistSnapshot(player, data);
        }
    }
}

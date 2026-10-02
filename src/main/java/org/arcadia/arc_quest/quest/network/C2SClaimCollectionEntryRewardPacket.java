package org.arcadia.arc_quest.quest.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.quest.logic.CollectionEntryRewardService;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;

/** A stale modal must identify its run explicitly instead of claiming a newly accepted repeat. */
public record C2SClaimCollectionEntryRewardPacket(String questId, String runId, String phaseId, String bindingId, String rewardId) implements CustomPacketPayload {
    public static final Type<C2SClaimCollectionEntryRewardPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "claim_collection_entry_reward"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SClaimCollectionEntryRewardPacket> STREAM_CODEC =
            StreamCodec.ofMember(C2SClaimCollectionEntryRewardPacket::encode, C2SClaimCollectionEntryRewardPacket::decode);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public C2SClaimCollectionEntryRewardPacket {
        questId = questId == null ? "" : questId; runId = runId == null ? "" : runId;
        phaseId = phaseId == null ? "" : phaseId; bindingId = bindingId == null ? "" : bindingId; rewardId = rewardId == null ? "" : rewardId;
    }
    public static C2SClaimCollectionEntryRewardPacket of(String questId, String runId, String phaseId, String bindingId, String rewardId) {
        return new C2SClaimCollectionEntryRewardPacket(questId, runId, phaseId, bindingId, rewardId);
    }
    public static void encode(C2SClaimCollectionEntryRewardPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.questId, 256); buffer.writeUtf(packet.runId, 64); buffer.writeUtf(packet.phaseId, 256);
        buffer.writeUtf(packet.bindingId, 256); buffer.writeUtf(packet.rewardId, 128);
    }
    public static C2SClaimCollectionEntryRewardPacket decode(FriendlyByteBuf buffer) {
        return new C2SClaimCollectionEntryRewardPacket(buffer.readUtf(256), buffer.readUtf(64), buffer.readUtf(256), buffer.readUtf(256), buffer.readUtf(128));
    }
    public static void handle(C2SClaimCollectionEntryRewardPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            var data = ArcQuestPlayerManager.get(player);
            if (data == null) return;
            var result = CollectionEntryRewardService.claim(player, data, packet.questId, packet.runId, packet.phaseId, packet.bindingId, packet.rewardId);
            if (result == QuestRejectCodeDictionary.Code.OK) {
                // Archived runs have no active-run dirty index, so persist explicitly as well.
                QuestSyncCoordinator.persistSnapshot(player, data);
                QuestSyncCoordinator.syncFullDataAndPush(player, data);
            }
            PacketDistributor.sendToPlayer(player, new S2CQuestActionResultPacket(
                    C2SRequestQuestActionPacket.Action.CLAIM_COLLECTION_REWARD, packet.questId, result));
        });
    }
}

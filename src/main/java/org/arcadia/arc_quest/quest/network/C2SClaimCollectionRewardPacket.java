package org.arcadia.arc_quest.quest.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.logic.profile.CollectionQuestEngine;
import org.arcadia.arc_quest.quest.logic.profile.CollectionRewardClaimResult;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;

import java.util.function.Supplier;

public class C2SClaimCollectionRewardPacket {

    private final String questId;
    private final String rewardNodeId;

    public C2SClaimCollectionRewardPacket(String questId, String rewardNodeId) {
        this.questId = questId == null ? "" : questId;
        this.rewardNodeId = rewardNodeId == null ? "" : rewardNodeId;
    }

    public static C2SClaimCollectionRewardPacket of(String questId, String rewardNodeId) {
        return new C2SClaimCollectionRewardPacket(questId, rewardNodeId);
    }

    public static void encode(C2SClaimCollectionRewardPacket pkt, FriendlyByteBuf buf) {
        buf.writeUtf(pkt.questId, 256);
        buf.writeUtf(pkt.rewardNodeId, 256);
    }

    public static C2SClaimCollectionRewardPacket decode(FriendlyByteBuf buf) {
        return new C2SClaimCollectionRewardPacket(buf.readUtf(256), buf.readUtf(256));
    }

    public static void handle(C2SClaimCollectionRewardPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;

            ArcQuestPlayer data = ArcQuestPlayerManager.get(sender);
            if (data == null) return;

            QuestRuntimeData runtime = data.getActiveQuest(pkt.questId);
            if (runtime == null || !runtime.hasCollectionData()) {
                ArcQuestNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sender),
                        new S2CQuestActionResultPacket(C2SRequestQuestActionPacket.Action.CLAIM_COLLECTION_REWARD, pkt.questId, QuestRejectCodeDictionary.Code.NOT_ACTIVE));
                return;
            }

            QuestDefinition def = QuestRegistry.get(ResourceLocation.tryParse(pkt.questId));
            if (def == null || !def.isCollectionQuest()) {
                ArcQuestNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sender),
                        new S2CQuestActionResultPacket(C2SRequestQuestActionPacket.Action.CLAIM_COLLECTION_REWARD, pkt.questId, QuestRejectCodeDictionary.Code.QUEST_NOT_FOUND));
                return;
            }

            CollectionRewardClaimResult result = CollectionQuestEngine.claimRewardWithResult(sender, data, def, runtime, pkt.rewardNodeId);
            if (result.isOk()) {
                QuestSyncCoordinator.syncQuestStateAndPush(sender, runtime);
            }
            ArcQuestNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sender),
                    new S2CQuestActionResultPacket(C2SRequestQuestActionPacket.Action.CLAIM_COLLECTION_REWARD, pkt.questId, result.toRejectCode()));
        });
        ctx.get().setPacketHandled(true);
    }
}

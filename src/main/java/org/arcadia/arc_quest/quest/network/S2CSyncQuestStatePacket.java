package org.arcadia.arc_quest.quest.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.client.events.ClientHudEvents;
import org.arcadia.arc_quest.client.hud.QuestHudOverlay;
import org.arcadia.arc_quest.client.hud.quest.toast.QuestToastManager;
import org.arcadia.arc_quest.quest.api.PhaseDefinition;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.api.SplashType;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;

import java.util.Set;

public final class S2CSyncQuestStatePacket implements CustomPacketPayload {

    public static final Type<S2CSyncQuestStatePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "sync_quest_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CSyncQuestStatePacket> STREAM_CODEC =
            StreamCodec.ofMember(S2CSyncQuestStatePacket::encode, S2CSyncQuestStatePacket::decode);

    private final QuestRuntimeData data;

    public S2CSyncQuestStatePacket(QuestRuntimeData data) {
        this.data = data;
    }

    public static void encode(S2CSyncQuestStatePacket pkt, FriendlyByteBuf buf) {
        pkt.data.writeToNetwork(buf);
    }

    public static S2CSyncQuestStatePacket decode(FriendlyByteBuf buf) {
        return new S2CSyncQuestStatePacket(QuestRuntimeData.readFromNetwork(buf));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(S2CSyncQuestStatePacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ResourceLocation questRl = ResourceLocation.tryParse(pkt.data.getQuestId());
            QuestDefinition def = questRl != null ? QuestRegistry.get(questRl) : null;
            String name = def != null ? def.getDisplayName().getString() : pkt.data.getQuestId();

            boolean isNewQuest = !ClientQuestCache.INSTANCE.isQuestActive(pkt.data.getQuestId());

            ClientQuestCache.INSTANCE.updateQuest(pkt.data);

            switch (pkt.data.getState()) {
                case ACTIVE -> {
                    if (isNewQuest) {
                        QuestToastManager.show(QuestToastManager.ToastType.QUEST_ACCEPTED, name);
                        if (def != null) ClientHudEvents.handleVisualTrigger(def, SplashType.QUEST_ACQUIRED, null);
                    }

                    if (def != null && Minecraft.getInstance().player != null) {
                        Set<String> activePhases = pkt.data.getActivePhaseIds();
                        for (String phaseId : activePhases) {
                            PhaseDefinition phase = def.getPhase(phaseId);
                            if (phase == null || !phase.hasChoices()) continue;

                            int[] progress = pkt.data.getAllProgress(phaseId);
                            boolean allCompleted = true;
                            for (int i = 0; i < phase.getObjectives().size(); i++) {
                                if (i >= progress.length || progress[i] < phase.getObjectives().get(i).getRequiredCount()) {
                                    allCompleted = false;
                                    break;
                                }
                            }

                            if (allCompleted) {
                                QuestHudOverlay.INSTANCE.showBranchChoiceToast(pkt.data.getQuestId(), phaseId);
                                break;
                            }
                        }
                    }
                }
                case COMPLETED -> {
                    QuestToastManager.show(QuestToastManager.ToastType.QUEST_COMPLETED, name);
                    if (def != null) ClientHudEvents.handleVisualTrigger(def, SplashType.QUEST_COMPLETED, null);
                }
                case FAILED -> {
                }
            }
        });
    }
}
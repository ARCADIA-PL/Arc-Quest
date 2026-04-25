package org.com.arc_quest.quest.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import org.com.arc_quest.client.events.ClientQuestEvents;
import org.com.arc_quest.client.gui.QuestHudOverlay;
import org.com.arc_quest.client.gui.quest.QuestToastManager;
import org.com.arc_quest.quest.api.PhaseDefinition;
import org.com.arc_quest.quest.api.QuestDefinition;
import org.com.arc_quest.quest.api.SplashType;
import org.com.arc_quest.quest.capability.QuestRuntimeData;
import org.com.arc_quest.quest.registry.QuestRegistry;

import java.util.Set;
import java.util.function.Supplier;

public class S2CSyncQuestStatePacket {

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

    public static void handle(S2CSyncQuestStatePacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ResourceLocation questRl = ResourceLocation.tryParse(pkt.data.getQuestId());
            QuestDefinition def = questRl != null ? QuestRegistry.get(questRl) : null;
            String name = def != null ? def.getDisplayName().getString() : pkt.data.getQuestId();

            boolean isNewQuest = !ClientQuestCache.INSTANCE.isQuestActive(pkt.data.getQuestId());

            ClientQuestCache.INSTANCE.updateQuest(pkt.data);

            switch (pkt.data.getState()) {
                case ACTIVE -> {
                    if (isNewQuest) {
                        QuestToastManager.show(QuestToastManager.ToastType.QUEST_ACCEPTED, name);
                        if (def != null) ClientQuestEvents.handleVisualTrigger(def, SplashType.QUEST_ACQUIRED, null);
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
                    if (def != null) ClientQuestEvents.handleVisualTrigger(def, SplashType.QUEST_COMPLETED, null);
                }
                case FAILED -> {
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
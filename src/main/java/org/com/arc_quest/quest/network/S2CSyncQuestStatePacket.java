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

            // 在更新缓存前检查是否为新任务
            boolean isNewQuest = !ClientQuestCache.INSTANCE.isQuestActive(pkt.data.getQuestId());

            // 更新客户端缓存
            ClientQuestCache.INSTANCE.updateQuest(pkt.data);

            switch (pkt.data.getState()) {
                case ACTIVE -> {
                    if (isNewQuest) {
                        QuestToastManager.show(QuestToastManager.ToastType.QUEST_ACCEPTED, name);
                        if (def != null) ClientQuestEvents.handleVisualTrigger(def, SplashType.QUEST_ACQUIRED, null);
                    } else {
                        /*QuestToastManager.show(QuestToastManager.ToastType.PHASE_ADVANCED, name);*/
                    }

                    if (def != null && Minecraft.getInstance().player != null) {
                        PhaseDefinition currentPhase = def.getPhase(pkt.data.getCurrentPhaseId());
                        if (currentPhase != null && currentPhase.hasChoices()) {
                            int[] progress = pkt.data.getAllProgress();
                            boolean allCompleted = true;
                            for (int i = 0; i < currentPhase.getObjectives().size(); i++) {
                                if (i >= progress.length || progress[i] < currentPhase.getObjectives().get(i).getRequiredCount()) {
                                    allCompleted = false;
                                    break;
                                }
                            }
                            if (allCompleted) QuestHudOverlay.INSTANCE.showBranchChoiceToast(pkt.data.getQuestId());
                        }
                    }
                }
                case COMPLETED -> {
                    QuestToastManager.show(QuestToastManager.ToastType.QUEST_COMPLETED, name);
                    if (def != null) ClientQuestEvents.handleVisualTrigger(def, SplashType.QUEST_COMPLETED, null);
                }
                case FAILED -> {
                    QuestToastManager.show(QuestToastManager.ToastType.QUEST_FAILED, name);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
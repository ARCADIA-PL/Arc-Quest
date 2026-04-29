package org.arcadia.arc_quest.api.event.quest;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

public class QuestChoiceResolvedEvent extends Event {
    private final ServerPlayer player;
    private final ResourceLocation questId;
    private final String phaseId;
    private final int choiceIndex;
    private final String choiceId;
    private final String targetPhaseId;

    public QuestChoiceResolvedEvent(ServerPlayer player, ResourceLocation questId, String phaseId,
                                    int choiceIndex, String choiceId, String targetPhaseId) {
        this.player = player;
        this.questId = questId;
        this.phaseId = phaseId;
        this.choiceIndex = choiceIndex;
        this.choiceId = choiceId;
        this.targetPhaseId = targetPhaseId;
    }

    public ServerPlayer getPlayer() { return player; }
    public ResourceLocation getQuestId() { return questId; }
    public String getPhaseId() { return phaseId; }
    public int getChoiceIndex() { return choiceIndex; }
    public String getChoiceId() { return choiceId; }
    public String getTargetPhaseId() { return targetPhaseId; }
}

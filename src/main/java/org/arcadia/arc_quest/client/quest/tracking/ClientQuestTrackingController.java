package org.arcadia.arc_quest.client.quest.tracking;

import net.minecraft.client.Minecraft;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.jetbrains.annotations.Nullable;

public final class ClientQuestTrackingController {

    public static final ClientQuestTrackingController INSTANCE = new ClientQuestTrackingController();

    private ClientQuestTrackingController() {
    }

    public void requestTrack(@Nullable String questId) {
        if (Minecraft.getInstance().getConnection() == null) return;
        ClientQuestTrackingStore.INSTANCE.markRequestPending();
        ArcQuestNetwork.sendTrackedQuestUpdate(questId);
    }

    public void requestFocus(String questId, @Nullable String phaseId) {
        QuestTrackingPresentationState.INSTANCE.focus(questId, phaseId);
        requestTrack(questId);
    }

    @Nullable
    public String trackedQuestId() {
        return ClientQuestTrackingStore.INSTANCE.trackedQuestId();
    }

    @Nullable
    public String trackedPhaseId() {
        return QuestTrackingPresentationState.INSTANCE.phaseIdFor(trackedQuestId());
    }
}

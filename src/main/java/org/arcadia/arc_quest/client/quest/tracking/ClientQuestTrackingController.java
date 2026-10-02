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
        if (Minecraft.getInstance().getConnection() == null) return;
        ClientQuestTrackingStore.INSTANCE.markRequestPending();
        if (phaseId == null || phaseId.isBlank()) requestTrack(questId);
        else ArcQuestNetwork.sendTrackedPhaseFocusUpdate(questId, phaseId);
    }

    public void requestCollectionFocus(String questId, String phaseId, @Nullable String bindingId) {
        // The server continues to own Quest/Phase tracking; the specimen is local presentation only.
        requestFocus(questId, phaseId);
        QuestTrackingPresentationState.INSTANCE.focusCollection(questId, phaseId, bindingId);
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

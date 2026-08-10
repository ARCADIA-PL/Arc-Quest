package org.arcadia.arc_quest.client.quest.tracking;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class QuestTrackingPresentationState {

    public static final QuestTrackingPresentationState INSTANCE = new QuestTrackingPresentationState();

    private String questId;
    private String phaseId;

    private QuestTrackingPresentationState() {
    }

    public void focus(@Nullable String questId, @Nullable String phaseId) {
        this.questId = normalize(questId);
        this.phaseId = normalize(phaseId);
        if (this.questId == null) this.phaseId = null;
    }

    public void onTrackedQuestChanged(@Nullable String trackedQuestId) {
        if (!Objects.equals(questId, trackedQuestId)) clear();
    }

    @Nullable
    public String phaseIdFor(@Nullable String trackedQuestId) {
        return Objects.equals(questId, trackedQuestId) ? phaseId : null;
    }

    public void clear() {
        questId = null;
        phaseId = null;
    }

    @Nullable
    private String normalize(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

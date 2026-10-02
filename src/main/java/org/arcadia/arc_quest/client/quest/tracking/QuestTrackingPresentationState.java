package org.arcadia.arc_quest.client.quest.tracking;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class QuestTrackingPresentationState {

    public static final QuestTrackingPresentationState INSTANCE = new QuestTrackingPresentationState();

    private String questId;
    private String phaseId;
    private String collectionBindingId;

    private QuestTrackingPresentationState() {
    }

    public void focus(@Nullable String questId, @Nullable String phaseId) {
        this.questId = normalize(questId);
        this.phaseId = normalize(phaseId);
        this.collectionBindingId = null;
        if (this.questId == null) this.phaseId = null;
    }

    public void focusCollection(String questId, String phaseId, @Nullable String bindingId) {
        focus(questId, phaseId);
        collectionBindingId = normalize(bindingId);
    }

    @Nullable
    public String collectionBindingIdFor(@Nullable String trackedQuestId) {
        return Objects.equals(questId, trackedQuestId) ? collectionBindingId : null;
    }

    /** Completion/invalid references return to the same Quest's overview, preserving its real chapter. */
    public void clearCollectionFocus(@Nullable String trackedQuestId) {
        if (Objects.equals(questId, trackedQuestId)) collectionBindingId = null;
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
        collectionBindingId = null;
    }

    @Nullable
    private String normalize(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

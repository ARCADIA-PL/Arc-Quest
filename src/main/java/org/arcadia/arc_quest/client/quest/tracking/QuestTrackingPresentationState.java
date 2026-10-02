package org.arcadia.arc_quest.client.quest.tracking;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class QuestTrackingPresentationState {

    public static final QuestTrackingPresentationState INSTANCE = new QuestTrackingPresentationState();

    private String questId;
    private String phaseId;
    private String collectionBindingId;
    private String collectionRunId;

    QuestTrackingPresentationState() {
    }

    public void focus(@Nullable String questId, @Nullable String phaseId) {
        this.questId = normalize(questId);
        this.phaseId = normalize(phaseId);
        this.collectionBindingId = null;
        this.collectionRunId = null;
        if (this.questId == null) this.phaseId = null;
    }

    public void focusCollection(String questId, String phaseId, @Nullable String bindingId) {
        focus(questId, phaseId);
        collectionBindingId = normalize(bindingId);
    }

    public void focusCollection(String questId, String phaseId, String bindingId, String runId) {
        focusCollection(questId, phaseId, bindingId);
        collectionRunId = normalize(runId);
    }

    /** Phase echo packets carry no specimen ID and must preserve a matching local selection. */
    public void applyPhaseFocusSync(@Nullable String questId, @Nullable String phaseId) {
        if (Objects.equals(this.questId, normalize(questId)) && Objects.equals(this.phaseId, normalize(phaseId))) return;
        focus(questId, phaseId);
    }

    public void bindCollectionRun(String questId, String runId) {
        if (!Objects.equals(this.questId, normalize(questId))) return;
        String normalizedRun = normalize(runId);
        if (collectionRunId != null && !Objects.equals(collectionRunId, normalizedRun)) {
            collectionBindingId = null;
            phaseId = null;
        }
        collectionRunId = normalizedRun;
    }

    @Nullable
    public String collectionBindingIdFor(@Nullable String trackedQuestId) {
        return Objects.equals(questId, trackedQuestId) ? collectionBindingId : null;
    }

    /** A cleared specimen ends the focus until another actionable binding is selected. */
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
        collectionRunId = null;
    }

    @Nullable
    private String normalize(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

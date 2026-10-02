package org.arcadia.arc_quest.client.quest.tracking;

import net.minecraft.client.Minecraft;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.jetbrains.annotations.Nullable;

public final class ClientQuestTrackingController {

    public static final ClientQuestTrackingController INSTANCE = new ClientQuestTrackingController();

    private ClientQuestTrackingController() {
    }

    public void requestTrack(@Nullable String questId) {
        if (Minecraft.getInstance().getConnection() == null) return;
        QuestDefinition quest = questId == null ? null : QuestRegistry.get(questId);
        if (quest != null && quest.hasCollectionSheets()) {
            var state = QuestTrackingPresentationState.INSTANCE;
            var selected = selectCollectionFocus(questId, state.phaseIdFor(questId), state.collectionBindingIdFor(questId));
            if (selected != null) {
                requestCollectionFocus(questId, selected.phaseId(), selected.bindingId());
                return;
            }
            state.focus(questId, null);
        } else if (questId == null) QuestTrackingPresentationState.INSTANCE.clear();
        ClientQuestTrackingStore.INSTANCE.markRequestPending();
        ArcQuestNetwork.sendTrackedQuestUpdate(questId);
    }

    public void requestFocus(String questId, @Nullable String phaseId) {
        QuestDefinition quest = QuestRegistry.get(questId);
        if (quest != null && quest.hasCollectionSheets()) {
            requestCollectionFocus(questId, phaseId, null);
            return;
        }
        QuestTrackingPresentationState.INSTANCE.focus(questId, phaseId);
        sendFocus(questId, phaseId);
    }

    private void sendFocus(String questId, @Nullable String phaseId) {
        if (Minecraft.getInstance().getConnection() == null) return;
        ClientQuestTrackingStore.INSTANCE.markRequestPending();
        if (phaseId == null || phaseId.isBlank()) ArcQuestNetwork.sendTrackedQuestUpdate(questId);
        else ArcQuestNetwork.sendTrackedPhaseFocusUpdate(questId, phaseId);
    }

    public void requestCollectionFocus(String questId, @Nullable String phaseId, @Nullable String bindingId) {
        var selected = bindingId == null ? selectCollectionFocus(questId, phaseId, null)
                : CollectionTrackingFocusSelector.selectRequested(QuestRegistry.get(questId),
                    ClientQuestCache.INSTANCE.getActiveQuest(questId), phaseId, bindingId,
                    id -> ClientQuestCache.INSTANCE.getCollectionSheetProgress(questId, id));
        if (selected == null) return;
        QuestRuntimeData runtime = ClientQuestCache.INSTANCE.getActiveQuest(questId);
        if (runtime == null) return;
        QuestTrackingPresentationState.INSTANCE.focusCollection(questId, selected.phaseId(), selected.bindingId(),
                CollectionTrackingFocusSelector.runId(runtime));
        sendFocus(questId, selected.phaseId());
    }

    /** Shared by journal cards, details and requests for record or run-action requirements. */
    public boolean canTrackCollectionBinding(String questId, @Nullable String phaseId, @Nullable String bindingId) {
        return CollectionTrackingFocusSelector.canTrack(QuestRegistry.get(questId),
                ClientQuestCache.INSTANCE.getActiveQuest(questId), phaseId, bindingId,
                id -> ClientQuestCache.INSTANCE.getCollectionSheetProgress(questId, id));
    }

    @Nullable
    public CollectionTrackingFocusSelector.Focus selectCollectionFocus(String questId, @Nullable String phaseId,
                                                                        @Nullable String bindingId) {
        return CollectionTrackingFocusSelector.select(QuestRegistry.get(questId),
                ClientQuestCache.INSTANCE.getActiveQuest(questId), phaseId, bindingId,
                id -> ClientQuestCache.INSTANCE.getCollectionSheetProgress(questId, id));
    }

    /** Server auto-tracking and login choose a specimen without issuing another network request. */
    public void ensureCollectionFocus(String questId) {
        QuestDefinition quest = QuestRegistry.get(questId);
        QuestRuntimeData runtime = ClientQuestCache.INSTANCE.getActiveQuest(questId);
        if (quest == null || runtime == null || !quest.hasCollectionSheets()) return;
        var state = QuestTrackingPresentationState.INSTANCE;
        state.bindCollectionRun(questId, CollectionTrackingFocusSelector.runId(runtime));
        String phase = state.phaseIdFor(questId), binding = state.collectionBindingIdFor(questId);
        var sheet = phase == null ? null : ClientQuestCache.INSTANCE.getCollectionSheetProgress(questId, phase);
        var current = sheet == null || binding == null ? null : sheet.binding(binding);
        // A completed selection remains briefly visible until the tracker advances or dismisses it.
        if (current != null && current.visible() && (current.revealed() || current.hasPublicClue())
                && (current.complete() || canTrackCollectionBinding(questId, phase, binding))
                && (runtime.isPhaseActive(phase) || runtime.isPhasePendingManualAdvance(phase))) return;
        var next = selectCollectionFocus(questId, phase, null);
        if (next != null) state.focusCollection(questId, next.phaseId(), next.bindingId(), CollectionTrackingFocusSelector.runId(runtime));
        else state.clearCollectionFocus(questId);
    }

    @Nullable
    public String trackedQuestId() {
        return ClientQuestTrackingStore.INSTANCE.trackedQuestId();
    }

    @Nullable
    public String trackedPhaseId() {
        String questId = trackedQuestId();
        if (questId != null) ensureCollectionFocus(questId);
        return QuestTrackingPresentationState.INSTANCE.phaseIdFor(questId);
    }
}

package org.arcadia.arc_quest.quest.tracking.domain;

import org.arcadia.arc_quest.quest.tracking.api.QuestTrackingResult;
import org.arcadia.arc_quest.quest.tracking.api.QuestTrackingSnapshot;
import org.arcadia.arc_quest.quest.tracking.api.QuestTrackingState;

import java.util.List;

public final class QuestTrackingStateMachine {

    public QuestTrackingTransition transition(QuestTrackingSnapshot current,
                                              QuestTrackingAction action,
                                              List<String> activeQuestIds,
                                              List<String> orderedAutoTrackCandidates) {
        return switch (action.type()) {
            case USER_TRACK -> trackManually(current, action.questId(), activeQuestIds);
            case USER_UNTRACK -> untrackManually(current, activeQuestIds);
            case AUTO_SELECT -> selectAutomatically(current, action, activeQuestIds, orderedAutoTrackCandidates);
            case RECONCILE -> reconcile(current, activeQuestIds, orderedAutoTrackCandidates);
        };
    }

    private QuestTrackingTransition trackManually(QuestTrackingSnapshot current,
                                                  String questId,
                                                  List<String> activeQuestIds) {
        if (questId == null || questId.isBlank()) {
            return rejected(current, QuestTrackingResult.Rejection.INVALID_QUEST_ID);
        }
        if (!activeQuestIds.contains(questId)) {
            return rejected(current, QuestTrackingResult.Rejection.QUEST_NOT_ACTIVE);
        }
        return accepted(current, questId, QuestTrackingState.TRACKING_MANUAL);
    }

    private QuestTrackingTransition untrackManually(QuestTrackingSnapshot current,
                                                    List<String> activeQuestIds) {
        return accepted(current, null, activeQuestIds.isEmpty()
                ? QuestTrackingState.EMPTY
                : QuestTrackingState.UNTRACKED_MANUAL);
    }

    private QuestTrackingTransition selectAutomatically(QuestTrackingSnapshot current,
                                                        QuestTrackingAction action,
                                                        List<String> activeQuestIds,
                                                        List<String> autoTrackCandidates) {
        if (current.state() == QuestTrackingState.UNTRACKED_MANUAL
                && !action.overrideManualUntracked()) {
            return unchanged(current);
        }
        if (current.questId() != null && activeQuestIds.contains(current.questId())) {
            return unchanged(current);
        }
        String selected = action.questId() != null && autoTrackCandidates.contains(action.questId())
                ? action.questId()
                : action.fallbackToFirstCandidate() ? first(autoTrackCandidates) : null;
        if (selected == null && !action.fallbackToFirstCandidate()) return unchanged(current);
        return accepted(current, selected, selected == null
                ? QuestTrackingState.EMPTY
                : QuestTrackingState.TRACKING_AUTOMATIC);
    }

    private QuestTrackingTransition reconcile(QuestTrackingSnapshot current,
                                              List<String> activeQuestIds,
                                              List<String> autoTrackCandidates) {
        if (current.state() == QuestTrackingState.UNTRACKED_MANUAL) return unchanged(current);
        if (current.questId() != null && activeQuestIds.contains(current.questId())) return unchanged(current);
        String selected = first(autoTrackCandidates);
        return accepted(current, selected, selected == null
                ? QuestTrackingState.EMPTY
                : QuestTrackingState.TRACKING_AUTOMATIC);
    }

    private QuestTrackingTransition accepted(QuestTrackingSnapshot current,
                                             String questId,
                                             QuestTrackingState state) {
        return new QuestTrackingTransition(true,
                new QuestTrackingSnapshot(questId, state, current.revision()), null);
    }

    private QuestTrackingTransition unchanged(QuestTrackingSnapshot current) {
        return new QuestTrackingTransition(true, current, null);
    }

    private QuestTrackingTransition rejected(QuestTrackingSnapshot current,
                                             QuestTrackingResult.Rejection rejection) {
        return new QuestTrackingTransition(false, current, rejection);
    }

    private String first(List<String> activeQuestIds) {
        return activeQuestIds.isEmpty() ? null : activeQuestIds.get(0);
    }
}

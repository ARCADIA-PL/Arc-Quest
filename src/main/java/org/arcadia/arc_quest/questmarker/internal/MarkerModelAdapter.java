package org.arcadia.arc_quest.questmarker.internal;

import org.arcadia.arc_quest.questmarker.api.QuestMarkerData;
import org.arcadia.arc_quest.questmarker.internal.model.MarkerOwner;
import org.arcadia.arc_quest.questmarker.internal.model.MarkerPersistence;
import org.arcadia.arc_quest.questmarker.internal.model.MarkerPresentation;
import org.arcadia.arc_quest.questmarker.internal.model.MarkerSnapshot;
import org.arcadia.arc_quest.questmarker.internal.model.MarkerTarget;

public final class MarkerModelAdapter {

    private MarkerModelAdapter() {
    }

    public static MarkerSnapshot fromPublic(QuestMarkerData marker) {
        MarkerOwner owner = resolveOwner(marker);
        MarkerTarget target = marker.hasEntityBinding()
                ? new MarkerTarget.Entity(marker.getDimension(), marker.getWorldX(), marker.getWorldY(), marker.getWorldZ(),
                marker.getFollowEntityId(), marker.getFollowEntityUuid(), marker.getFollowEntityGuid(), marker.getAttachPoint())
                : new MarkerTarget.Position(marker.getDimension(), marker.getWorldX(), marker.getWorldY(), marker.getWorldZ());
        MarkerPresentation presentation = new MarkerPresentation(marker.getLabel(), marker.getType(),
                marker.getColorARGB(), marker.isShowDistance(), marker.isAllowOffscreenArrow(),
                marker.getPriority(), marker.getStyleHints());
        return new MarkerSnapshot(marker.getId(), owner, target, presentation, marker.getState(),
                resolvePersistence(marker));
    }

    public static QuestMarkerData toPublic(MarkerSnapshot marker) {
        MarkerTarget target = marker.target();
        MarkerPresentation presentation = marker.presentation();
        QuestMarkerData.Builder builder = new QuestMarkerData.Builder(
                marker.id(), target.x(), target.y(), target.z(), presentation.label())
                .dimension(target.dimension())
                .type(presentation.type())
                .state(marker.state())
                .color(presentation.colorArgb())
                .showDistance(presentation.showDistance())
                .allowOffscreenArrow(presentation.allowOffscreenArrow())
                .priority(presentation.priority())
                .styleHints(presentation.extensionHints())
                .persistent(marker.persistence() == MarkerPersistence.DURABLE);
        applyOwner(builder, marker.owner());
        if (target instanceof MarkerTarget.Entity entity) {
            builder.followEntity(entity.runtimeEntityId(), entity.entityUuid(), entity.persistentGuid(), entity.attachPoint());
        }
        return builder.build();
    }

    public static boolean belongsToQuest(MarkerSnapshot marker, String questId) {
        return marker != null && questId != null && questId.equals(marker.owner().questId());
    }

    public static boolean sameMovingBinding(QuestMarkerData first, QuestMarkerData second) {
        if (first == null || second == null) return false;
        MarkerSnapshot firstSnapshot = fromPublic(first);
        MarkerSnapshot secondSnapshot = fromPublic(second);
        if (!(firstSnapshot.target() instanceof MarkerTarget.Entity firstTarget)
                || !(secondSnapshot.target() instanceof MarkerTarget.Entity secondTarget)) return false;
        return firstTarget.runtimeEntityId() == secondTarget.runtimeEntityId()
                && firstTarget.entityUuid().equals(secondTarget.entityUuid())
                && firstTarget.persistentGuid().equals(secondTarget.persistentGuid())
                && firstTarget.attachPoint() == secondTarget.attachPoint()
                && firstSnapshot.owner().equals(secondSnapshot.owner())
                && firstSnapshot.presentation().equals(secondSnapshot.presentation())
                && firstSnapshot.state() == secondSnapshot.state();
    }

    public static boolean isDerivedId(String markerId) {
        return MarkerIds.isDerived(markerId);
    }

    private static MarkerOwner resolveOwner(QuestMarkerData marker) {
        if (MarkerIds.isDialogue(marker.getId())) {
            return new MarkerOwner.Dialogue(marker.getQuestId().isBlank() ? marker.getId() : marker.getQuestId());
        }
        if (marker.hasObjectiveBinding()) {
            return new MarkerOwner.Objective(marker.getQuestId(), marker.getPhaseId(), marker.getObjectiveIndex());
        }
        if (marker.hasPhaseBinding()) return new MarkerOwner.Phase(marker.getQuestId(), marker.getPhaseId());
        if (marker.hasQuestBinding()) return new MarkerOwner.Quest(marker.getQuestId());
        return new MarkerOwner.Manual(marker.getId());
    }

    private static MarkerPersistence resolvePersistence(QuestMarkerData marker) {
        if (isDerivedId(marker.getId())) {
            return MarkerIds.isTriggered(marker.getId()) || MarkerIds.isDialogue(marker.getId())
                    ? MarkerPersistence.TRANSIENT
                    : MarkerPersistence.DERIVED;
        }
        return marker.isPersistent() ? MarkerPersistence.DURABLE : MarkerPersistence.SESSION;
    }

    private static void applyOwner(QuestMarkerData.Builder builder, MarkerOwner owner) {
        if (owner instanceof MarkerOwner.Objective objective) {
            builder.bindQuest(objective.questId()).bindPhase(objective.phaseId()).bindObjective(objective.objectiveIndex());
        } else if (owner instanceof MarkerOwner.Phase phase) {
            builder.bindQuest(phase.questId()).bindPhase(phase.phaseId());
        } else if (owner instanceof MarkerOwner.Quest quest) {
            builder.bindQuest(quest.questId());
        } else if (owner instanceof MarkerOwner.Dialogue dialogue) {
            builder.bindQuest(dialogue.sourceId());
        }
    }
}

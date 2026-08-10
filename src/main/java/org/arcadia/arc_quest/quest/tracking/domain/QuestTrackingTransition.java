package org.arcadia.arc_quest.quest.tracking.domain;

import org.arcadia.arc_quest.quest.tracking.api.QuestTrackingResult;
import org.arcadia.arc_quest.quest.tracking.api.QuestTrackingSnapshot;
import org.jetbrains.annotations.Nullable;

public record QuestTrackingTransition(boolean accepted,
                                      QuestTrackingSnapshot target,
                                      @Nullable QuestTrackingResult.Rejection rejection) {
}

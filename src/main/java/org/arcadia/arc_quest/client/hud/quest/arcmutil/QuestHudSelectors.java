package org.arcadia.arc_quest.client.hud.quest.arcmutil;

import org.arcadia.arc_quest.quest.capability.QuestRuntimeData;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;

import javax.annotation.Nullable;
import java.util.Map;

public final class QuestHudSelectors {
    private QuestHudSelectors() {
    }

    public static ResolvedTrackedQuest resolveTrackedQuest(@Nullable String requestedQuestId) {
        Map<String, QuestRuntimeData> active = ClientQuestCache.INSTANCE.getAllActiveQuests();
        QuestRuntimeData data = ClientQuestCache.INSTANCE.resolveTrackedQuest(requestedQuestId);
        if (data != null) {
            return new ResolvedTrackedQuest(requestedQuestId, data, false);
        }
        if (requestedQuestId != null) {
            return new ResolvedTrackedQuest(requestedQuestId, null, true);
        }
        if (!active.isEmpty()) {
            QuestRuntimeData first = active.values().iterator().next();
            return new ResolvedTrackedQuest(null, first, false);
        }
        return new ResolvedTrackedQuest(null, null, false);
    }

    public record ResolvedTrackedQuest(@Nullable String requestedQuestId, @Nullable QuestRuntimeData data, boolean requestedInvalidated) {
    }
}

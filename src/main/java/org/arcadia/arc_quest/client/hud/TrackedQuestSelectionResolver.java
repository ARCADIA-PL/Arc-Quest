package org.arcadia.arc_quest.client.hud;

import javax.annotation.Nullable;
import java.util.Collection;

final class TrackedQuestSelectionResolver {

    private TrackedQuestSelectionResolver() {
    }

    @Nullable
    static String resolve(@Nullable String trackedQuestId,
                          Collection<String> activeQuestIds,
                          boolean fullSyncApplied) {
        if (!fullSyncApplied) return trackedQuestId;
        if (trackedQuestId == null || activeQuestIds.contains(trackedQuestId)) return trackedQuestId;
        return activeQuestIds.stream().findFirst().orElse(null);
    }
}

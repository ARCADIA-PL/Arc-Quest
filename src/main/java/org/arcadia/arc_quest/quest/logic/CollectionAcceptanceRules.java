package org.arcadia.arc_quest.quest.logic;

import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;

/** Pure acceptance checks; the timestamp is persisted independently of the abandoned run. */
public final class CollectionAcceptanceRules {
    private CollectionAcceptanceRules() { }

    public static long cooldownRemaining(QuestDefinition definition, ArcQuestPlayer data, long gameTime) {
        if (!definition.hasCollectionSheets() || !definition.isRepeatable()) return 0L;
        long cooldown = definition.getCollectionConfig().getRepeatCooldownTicks();
        long accepted = data.getCollectionAcceptedAt(definition.getId().toString());
        if (cooldown <= 0 || accepted < 0) return 0L;
        long elapsed = gameTime >= accepted ? gameTime - accepted : 0L;
        return elapsed >= cooldown ? 0L : cooldown - elapsed;
    }
}

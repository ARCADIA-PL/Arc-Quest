package org.arcadia.arc_quest.client.hud.quest.history;

import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.quest.api.PhaseDefinition;
import org.arcadia.arc_quest.quest.api.VisualAsset;

record QuestHistoryNodeData(
        String id,
        int x,
        int y,
        int depth,
        boolean completed,
        boolean active,
        boolean reached,
        Component displayName,
        PhaseDefinition phase,
        VisualAsset image) {
}

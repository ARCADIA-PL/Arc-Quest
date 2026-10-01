package org.arcadia.arc_quest.client.hud.quest.history;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.api.ObjectiveEntry;
import org.arcadia.arc_quest.quest.api.ObjectiveType;
import org.arcadia.arc_quest.quest.api.QuestText;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuestHistoryDetailPanelTest {
    @Test void aDynamicCountCanTurnBaseOneIntoANumericProgressDisplay() {
        var objective = objective(ObjectiveType.COLLECT, 1);
        assertEquals("6/40", QuestHistoryDetailPanel.objectiveProgressText(objective, 6, 40));
        assertEquals("40/40", QuestHistoryDetailPanel.objectiveProgressText(objective, 50, 40));
        assertEquals("65536/65537", QuestHistoryDetailPanel.objectiveProgressText(objective, 65536, 65537));
    }

    @Test void effectiveOneAndNonCountingTypesRemainBoolean() {
        assertEquals("", QuestHistoryDetailPanel.objectiveProgressText(objective(ObjectiveType.COLLECT, 40), 0, 1));
        assertEquals("", QuestHistoryDetailPanel.objectiveProgressText(objective(ObjectiveType.TALK, 1), 0, 40));
    }

    private static ObjectiveEntry objective(ObjectiveType type, int base) {
        return new ObjectiveEntry(type, ResourceLocation.parse("arc_quest:history_test"), base,
                QuestText.literal("Historical objective"), false, false, Map.of(), List.of(), null);
    }
}

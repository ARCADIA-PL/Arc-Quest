package org.arcadia.arc_quest.client.hud.quest.tracker;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.quest.api.CollectionEntryConfig;
import org.arcadia.arc_quest.quest.api.PhaseDefinition;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.capability.CollectionRuntimeData;
import org.arcadia.arc_quest.quest.capability.QuestRuntimeData;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;

public final class TrackerCollectionWidget {

    public int computeHeight() {
        return 34;
    }

    public void render(GuiGraphics g,
                       Font font,
                       QuestRuntimeData tracked,
                       QuestDefinition def,
                       PhaseDefinition phase,
                       int currentThemeColor,
                       int x,
                       int y,
                       float panelReveal,
                       float wipeAlpha) {
        CollectionEntryConfig entryConfig = phase != null ? phase.getCollectionEntryConfig() : null;
        CollectionRuntimeData collectionData = tracked.getCollectionData();
        if (entryConfig == null || collectionData == null) return;

        int count = ClientQuestCache.INSTANCE.getCollectionEntryCount(def.getId().toString(), phase.getPhaseId());
        int target = Math.max(1, entryConfig.getCompletionTarget());
        int completed = ClientQuestCache.INSTANCE.getCollectionCompletedEntryCount(def.getId().toString());
        int total = ClientQuestCache.INSTANCE.getCollectionTotalEntryCount(def.getId().toString());
        int alpha = (int) (255 * panelReveal * wipeAlpha);

        g.drawString(font, "Entry: " + count + "/" + target, x, y, HudAnimUtil.withAlpha(0xFFFFFF, alpha), false);
        g.drawString(font, "Quest: " + completed + "/" + total, x, y + 12, HudAnimUtil.withAlpha(currentThemeColor, alpha), false);

        String lastUpdated = collectionData.getLastUpdatedPhaseId();
        if (lastUpdated != null && !lastUpdated.isEmpty()) {
            g.drawString(font, "Last: " + lastUpdated, x, y + 24, HudAnimUtil.withAlpha(0xAAAAAA, alpha), false);
        }
    }
}

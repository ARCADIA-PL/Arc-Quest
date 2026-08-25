package org.arcadia.arc_quest.client.hud.quest.tracker;


import org.arcadia.arc_quest.client.hud.HudText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.StyledTextUtil;
import org.arcadia.arc_quest.quest.api.CollectionEntryConfig;
import org.arcadia.arc_quest.quest.api.PhaseDefinition;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.data.CollectionRuntimeData;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;

public final class TrackerCollectionWidget {

    public int computeHeight() {
        return 60;
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
        render(g, font, tracked, def, phase, currentThemeColor, x, y,
                panelReveal, wipeAlpha, TrackerConstants.PANEL_WIDTH);
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
                       float wipeAlpha,
                       int panelWidth) {
        CollectionEntryConfig entryConfig = phase != null ? phase.getCollectionEntryConfig() : null;
        CollectionRuntimeData collectionData = tracked.getCollectionData();
        if (entryConfig == null || collectionData == null) return;

        String questId = def.getId().toString();
        int count = ClientQuestCache.INSTANCE.getCollectionEntryCount(questId, phase.getPhaseId());
        int target = Math.max(1, entryConfig.getCompletionTarget());
        int completed = ClientQuestCache.INSTANCE.getCollectionCompletedEntryCount(questId);
        int total = Math.max(1, ClientQuestCache.INSTANCE.getCollectionTotalEntryCount(questId));
        int discovered = ClientQuestCache.INSTANCE.getCollectionDiscoveredEntryCount(questId);
        int claimable = ClientQuestCache.INSTANCE.getCollectionClaimableRewardCount(questId);

        int a = (int) (255 * panelReveal * wipeAlpha);
        if (a <= 5) return;

        int barW = panelWidth - TrackerConstants.ACCENT_WIDTH - TrackerConstants.PADDING * 2 - 4;

        // 1. Entry Focus (聚焦条目)
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.85f, 0.85f, 1f);
        g.drawString(font, HudText.of("tracker.entry_focus"), 0, 0, HudAnimUtil.withAlpha(currentThemeColor, a), false);
        g.drawString(font, count + " / " + target, (int) ((barW - font.width(count + " / " + target) * 0.85f) / 0.85f), 0, HudAnimUtil.withAlpha(0xFFFFFF, a), false);
        g.pose().popPose();
        y += 10;

        // Entry 进度条
        float entryRatio = Math.min(1f, (float) count / target);
        g.fill(x, y, x + barW, y + 2, HudAnimUtil.withAlpha(0xFFFFFF, (int) (0x33 * (a / 255f))));
        if (entryRatio > 0) {
            int fillW = (int) (barW * entryRatio);
            g.fill(x, y, x + fillW, y + 2, HudAnimUtil.withAlpha(currentThemeColor, (int) (0xCC * (a / 255f))));
            g.fill(x + fillW - 2, y - 1, x + fillW, y + 3, HudAnimUtil.withAlpha(0xFFFFFF, a));
        }
        y += 8;

        // 2. Global Progress (全局概览)
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.85f, 0.85f, 1f);
        g.drawString(font, HudText.of("tracker.global_log"), 0, 0, HudAnimUtil.withAlpha(0xAAAAAA, a), false);
        g.drawString(font, completed + " / " + total, (int) ((barW - font.width(completed + " / " + total) * 0.85f) / 0.85f), 0, HudAnimUtil.withAlpha(completed >= total ? 0x66FF88 : 0xDDDDDD, a), false);
        g.pose().popPose();
        y += 10;

        // Global 进度条
        float globalRatio = Math.min(1f, (float) completed / total);
        g.fill(x, y, x + barW, y + 2, HudAnimUtil.withAlpha(0xFFFFFF, (int) (0x33 * (a / 255f))));
        if (globalRatio > 0) {
            int fillW = (int) (barW * globalRatio);
            g.fill(x, y, x + fillW, y + 2, HudAnimUtil.withAlpha(0x888888, (int) (0xCC * (a / 255f))));
        }
        y += 8;

        // 3. 补充信息 (Claimable & Last Updated)
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.8f, 0.8f, 1f);
        if (claimable > 0) {
            float pulse = (float) (Math.sin(System.currentTimeMillis() / 200.0) * 0.5f + 0.5f);
            int claimCol = HudAnimUtil.lerpColor(0xFFD166, 0xFFFFFF, pulse);
            g.drawString(font, HudText.of("collection.claimable_rewards", claimable), 0, 0, HudAnimUtil.withAlpha(claimCol, a), false);
        } else {
            g.drawString(font, HudText.of("tracker.seen", discovered + " / " + total), 0, 0, HudAnimUtil.withAlpha(0x777777, a), false);
        }

        Component lastUpdated = resolveLastUpdatedName(def, collectionData);
        if (!lastUpdated.getString().isEmpty()) {
            FormattedCharSequence text = StyledTextUtil.fitSingleLine(font, HudText.of("tracker.last", lastUpdated), 130);
            g.drawString(font, text, (int) ((barW - font.width(text) * 0.8f) / 0.8f), 0, HudAnimUtil.withAlpha(0x555555, a), false);
        }
        g.pose().popPose();
    }

    private Component resolveLastUpdatedName(QuestDefinition def, CollectionRuntimeData collectionData) {
        String lastUpdated = collectionData.getLastUpdatedPhaseId();
        if (lastUpdated == null || lastUpdated.isEmpty()) return Component.empty();
        PhaseDefinition phase = def.getPhase(lastUpdated);
        if (phase == null) return Component.literal(lastUpdated);
        Component name = phase.getDisplayName();
        return name == null || name.getString().isEmpty() ? Component.literal(lastUpdated) : name;
    }
}

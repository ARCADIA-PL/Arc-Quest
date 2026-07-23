package org.arcadia.arc_quest.client.hud.quest.tracker;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.client.hud.HudRenderUtil;
import org.arcadia.arc_quest.client.hud.quest.journal.history.QuestChangeNotificationManager;

final class TrackerNewQuestIndicator {

    private static final Component LABEL = Component.translatable("arc_quest.gui.tracker.new_quest");
    private static final long FADE_IN_DURATION_MS = 250L;

    private long visibleSinceMs;
    private boolean wasVisible;

    boolean isVisible(String trackedQuestId) {
        return QuestChangeNotificationManager.INSTANCE.hasUnreadNewQuestOtherThan(trackedQuestId);
    }

    int reservedWidth(Font font, String trackedQuestId) {
        return isVisible(trackedQuestId) ? font.width(LABEL) + 16 : 0;
    }

    void render(GuiGraphics graphics, Font font, int panelX, int panelY,
                String trackedQuestId, float panelAlpha, long now) {
        if (!isVisible(trackedQuestId)) {
            wasVisible = false;
            return;
        }
        if (!wasVisible) {
            visibleSinceMs = now;
            wasVisible = true;
        }
        if (panelAlpha <= 0.01f) return;

        float fadeIn = clamp((now - visibleSinceMs) / (float) FADE_IN_DURATION_MS);
        float alpha = panelAlpha * fadeIn;

        int right = panelX + TrackerConstants.PANEL_WIDTH - TrackerConstants.PADDING;
        int textY = panelY + TrackerConstants.PADDING;
        int dotCenterX = right - 5;
        int dotCenterY = textY + 4;
        int textWidth = font.width(LABEL);
        int textX = dotCenterX - textWidth - 7;

        int textAlpha = (int) (255 * alpha);
        graphics.drawString(font, LABEL, textX, textY, (textAlpha << 24) | 0xFFE8E8, true);

        HudRenderUtil.drawBreathingRedDot(graphics, dotCenterX, dotCenterY, alpha);
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}

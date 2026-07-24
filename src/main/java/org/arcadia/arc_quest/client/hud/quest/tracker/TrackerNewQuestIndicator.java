package org.arcadia.arc_quest.client.hud.quest.tracker;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.client.events.ClientEventHandler;
import org.arcadia.arc_quest.client.hud.HudRenderUtil;
import org.arcadia.arc_quest.client.hud.quest.journal.history.QuestChangeNotificationManager;

final class TrackerNewQuestIndicator {

    private static final Component LABEL = Component.translatable("arc_quest.gui.tracker.new_quest")
            .withStyle(ChatFormatting.BOLD);
    private static final float SHORTCUT_SCALE = 0.84f;
    private static final int SHORTCUT_TOP_OFFSET = 13;
    private static final int RIGHT_INSET = 7;
    private static final int EXTRA_HEIGHT = 11;
    private static final long FADE_IN_DURATION_MS = 250L;

    private long visibleSinceMs;
    private boolean wasVisible;
    private String cachedKeyName = "";
    private Component cachedShortcut = Component.empty();

    boolean isVisible(String trackedQuestId) {
        return QuestChangeNotificationManager.INSTANCE.hasUnreadNewQuestOtherThan(trackedQuestId);
    }

    int reservedWidth(Font font, String trackedQuestId) {
        return isVisible(trackedQuestId) ? font.width(LABEL) + 16 + RIGHT_INSET : 0;
    }

    int additionalHeight(String trackedQuestId) {
        return isVisible(trackedQuestId) ? EXTRA_HEIGHT : 0;
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
        int right = panelX + TrackerConstants.PANEL_WIDTH - TrackerConstants.PADDING - RIGHT_INSET;
        int labelY = panelY + TrackerConstants.PADDING;
        int dotCenterX = right - 5;
        int dotCenterY = labelY + 4;
        int labelX = dotCenterX - font.width(LABEL) - 7;
        int textAlpha = (int) (255 * alpha);

        graphics.drawString(font, LABEL, labelX, labelY,
                (textAlpha << 24) | 0xFFF4F4F4, true);

        Component shortcut = resolveShortcut();
        float shortcutX = right - font.width(shortcut) * SHORTCUT_SCALE;
        graphics.pose().pushPose();
        graphics.pose().translate(shortcutX, labelY + SHORTCUT_TOP_OFFSET, 0);
        graphics.pose().scale(SHORTCUT_SCALE, SHORTCUT_SCALE, 1f);
        graphics.drawString(font, shortcut, 0, 0,
                (textAlpha << 24) | 0xFFE0EBF2, true);
        graphics.pose().popPose();

        HudRenderUtil.drawBreathingRedDot(graphics, dotCenterX, dotCenterY, alpha);
    }

    private Component resolveShortcut() {
        Component keyMessage = ClientEventHandler.KEY_OPEN_JOURNAL.getTranslatedKeyMessage();
        String keyName = keyMessage.getString();
        if (!keyName.equals(cachedKeyName)) {
            cachedKeyName = keyName;
            cachedShortcut = Component.translatable(
                    "arc_quest.gui.tracker.open_journal_shortcut",
                    keyMessage
            );
        }
        return cachedShortcut;
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}

package org.arcadia.arc_quest.client.hud.quest.tracker;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.client.events.ClientEventHandler;
import org.arcadia.arc_quest.client.hud.HudRenderUtil;
import org.arcadia.arc_quest.client.hud.quest.journal.history.QuestChangeNotificationManager;

final class TrackerNewQuestIndicator {

    private static final float MESSAGE_SCALE = 0.84f;
    private static final int RIGHT_INSET = 7;
    private static final int EXTRA_HEIGHT = 11;
    private static final long FADE_IN_DURATION_MS = 250L;

    private long visibleSinceMs;
    private boolean wasVisible;
    private String cachedKeyName = "";
    private Component cachedMessage = Component.empty();

    boolean isVisible(String trackedQuestId) {
        return QuestChangeNotificationManager.INSTANCE.hasUnreadNewQuestOtherThan(trackedQuestId);
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
        int messageY = panelY + TrackerConstants.PADDING
                + TrackerConstants.TITLE_HEIGHT + TrackerConstants.GAP_AFTER_TITLE;
        int left = panelX + TrackerConstants.ACCENT_WIDTH + TrackerConstants.PADDING;
        int maxTextWidth = Math.max(1, right - left - 12);
        int textAlpha = (int) (255 * alpha);

        Component message = resolveMessage();
        float messageScale = Math.min(MESSAGE_SCALE,
                maxTextWidth / (float) Math.max(1, font.width(message)));
        float messageX = right - font.width(message) * messageScale;
        graphics.pose().pushPose();
        graphics.pose().translate(messageX, messageY, 0);
        graphics.pose().scale(messageScale, messageScale, 1f);
        graphics.drawString(font, message, 0, 0,
                (textAlpha << 24) | 0xFFF4F4F4, true);
        graphics.pose().popPose();

        int dotCenterX = (int) messageX - 7;
        int dotCenterY = messageY + Math.max(3, (int) (font.lineHeight * messageScale / 2f));
        HudRenderUtil.drawBreathingRedDot(graphics, dotCenterX, dotCenterY, alpha);
    }

    private Component resolveMessage() {
        Component keyMessage = ClientEventHandler.KEY_OPEN_JOURNAL.getTranslatedKeyMessage();
        String keyName = keyMessage.getString();
        if (!keyName.equals(cachedKeyName)) {
            cachedKeyName = keyName;
            cachedMessage = Component.translatable(
                    "arc_quest.gui.tracker.new_quest",
                    keyMessage
            ).withStyle(ChatFormatting.BOLD);
        }
        return cachedMessage;
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}

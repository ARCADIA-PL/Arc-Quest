package org.arcadia.arc_quest.client.hud.quest.toast;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.locale.Language;
import net.minecraft.util.FormattedCharSequence;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.HudRenderUtil;
import org.arcadia.arc_quest.client.hud.StyledTextUtil;

/** Cached, two-line renderer shared by every task notification and pending action. */
public final class QuestNotificationToast {
    private long cachedVersion = Long.MIN_VALUE;
    private Font cachedFont;
    private Language cachedLanguage;
    private QuestToastManager.ToastType cachedType;
    private Component cachedTitle;
    private Component cachedDetail;
    private FormattedCharSequence title = Component.empty().getVisualOrderText();
    private FormattedCharSequence subtitle = Component.empty().getVisualOrderText();

    public void render(GuiGraphics graphics, Font font, QuestToastManager.DisplayToast toast,
                       int screenWidth, int screenHeight, float partialTick) {
        if (toast == null) return;
        float partial = Float.isFinite(partialTick) ? Math.max(0, Math.min(1, partialTick)) : 0;
        float alpha = QuestToastLayout.opacity(toast.elapsedMillis() + partial * 50, toast.persistent());
        if (alpha < 0.025f) return;
        QuestToastLayout.Frame frame = QuestToastLayout.resolve(screenWidth, screenHeight,
                HudRenderUtil.getUniversalUiScale(screenWidth, screenHeight));
        cacheText(font, toast);
        int accent = HudAnimUtil.withAlpha(toast.themeColor(), (int) (220 * alpha));
        int textAlpha = (int) (255 * alpha);

        graphics.pose().pushPose();
        try {
            graphics.pose().translate(frame.x() - 6 * (1 - alpha) * frame.scale(), frame.y(), 0);
            graphics.pose().scale(frame.scale(), frame.scale(), 1);
            graphics.fill(0, 0, QuestToastLayout.WIDTH, QuestToastLayout.HEIGHT,
                    HudAnimUtil.withAlpha(0x151515, (int) (0x88 * alpha)));
            graphics.fill(0, 0, 2, QuestToastLayout.HEIGHT, accent);
            graphics.fill(2, 0, QuestToastLayout.WIDTH, 1,
                    HudAnimUtil.withAlpha(0xFFFFFF, (int) (0x22 * alpha)));
            graphics.fill(2, QuestToastLayout.HEIGHT - 1, QuestToastLayout.WIDTH, QuestToastLayout.HEIGHT,
                    HudAnimUtil.withAlpha(0xFFFFFF, (int) (0x22 * alpha)));
            graphics.pose().pushPose();
            try {
                graphics.pose().translate(QuestToastLayout.TEXT_X, QuestToastLayout.SUBTITLE_Y, 0);
                graphics.pose().scale(QuestToastLayout.SUBTITLE_SCALE, QuestToastLayout.SUBTITLE_SCALE, 1);
                graphics.drawString(font, subtitle, 0, 0,
                        HudAnimUtil.withAlpha(0xBBBBBB, textAlpha), false);
            } finally {
                graphics.pose().popPose();
            }
            graphics.drawString(font, title, QuestToastLayout.TEXT_X, QuestToastLayout.TITLE_Y,
                    HudAnimUtil.withAlpha(0xFFFFFF, textAlpha), false);
        } finally {
            graphics.pose().popPose();
        }
    }

    private void cacheText(Font font, QuestToastManager.DisplayToast toast) {
        Language language = Language.getInstance();
        if (cachedVersion == toast.version() && cachedFont == font && cachedLanguage == language && cachedType == toast.type()
                && toast.title().equals(cachedTitle) && toast.detail().equals(cachedDetail)) return;
        cachedVersion = toast.version();
        cachedFont = font;
        cachedLanguage = language;
        cachedType = toast.type();
        cachedTitle = toast.title();
        cachedDetail = toast.detail();
        Component prefix = Component.translatable(toast.type().translationKey);
        Component context = toast.detail().getString().isBlank() ? prefix
                : prefix.copy().append(Component.literal(" · ")).append(toast.detail());
        title = StyledTextUtil.fitSingleLine(font, toast.title(), QuestToastLayout.TEXT_WIDTH);
        subtitle = StyledTextUtil.fitSingleLine(font, context, (int) (QuestToastLayout.TEXT_WIDTH / QuestToastLayout.SUBTITLE_SCALE));
    }
}

package org.arcadia.arc_quest.client.hud.quest.tracker;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.StyledTextUtil;
import org.arcadia.arc_quest.client.hud.quest.QuestIconRenderer;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;

import java.util.ArrayList;
import java.util.List;

public class TrackerTitleWidget {

    private static final int DESCRIPTION_VISIBLE_LINES = 3;
    private static final long DESCRIPTION_PAGE_HOLD_MS = 3_200L;
    private static final long DESCRIPTION_PAGE_TRANSITION_MS = 450L;
    private static String activeDescriptionKey = "";
    private static long activeDescriptionStartedAtMs;

    public static void renderTitle(GuiGraphics g, QuestRuntimeData tracked, int textX, int textY, float alpha, float wipeAlpha, Font font) {
        renderTitle(g, tracked, textX, textY, alpha, wipeAlpha, font, TrackerConstants.PANEL_WIDTH, 0);
    }

    public static void renderTitle(GuiGraphics g, QuestRuntimeData tracked, int textX, int textY,
                                   float alpha, float wipeAlpha, Font font, int reservedRightWidth) {
        renderTitle(g, tracked, textX, textY, alpha, wipeAlpha, font,
                TrackerConstants.PANEL_WIDTH, reservedRightWidth);
    }

    public static void renderTitle(GuiGraphics g, QuestRuntimeData tracked, int textX, int textY,
                                   float alpha, float wipeAlpha, Font font, int panelWidth,
                                   int reservedRightWidth) {
        int titleA = (int) (255 * alpha * wipeAlpha);
        if (titleA > 8) {
            int iconOffset = 0;
            QuestDefinition def = QuestRegistry.get(ResourceLocation.tryParse(tracked.getQuestId()));

            if (def != null && def.getVisualConfig().getIcon(IconPosition.HUD_TRACKER).isPresent()) {
                int finalTextY = textY;
                def.getVisualConfig().getIcon(IconPosition.HUD_TRACKER).ifPresent(icon -> {
                    RenderSystem.setShaderColor(1f, 1f, 1f, alpha * wipeAlpha);
                    QuestIconRenderer.renderIcon(g, icon, textX, finalTextY + 1, 12, 12);
                    RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                });
                iconOffset = 16;
            }

            // =========================================================
            // 极简机能风排版：计算绝对靠右的倒计时
            // =========================================================
            long remainSec = getQuestRemainSeconds(def, tracked);
            String timeStr = null;
            int timerRenderW = 0;
            float timeScale = 0.85f; // 统一、干脆的字体缩放

            // 计算面板可用总宽度（也是最右侧边缘的X坐标相对值）
            int availableW = Math.max(0, panelWidth - TrackerConstants.ACCENT_WIDTH
                    - TrackerConstants.PADDING * 2 - 4 - iconOffset - reservedRightWidth);
            int maxRightX = textX + iconOffset + availableW;

            if (remainSec >= 0) {
                // 抛弃冗余的 "TIME:"，直接使用干脆的数字格式
                timeStr = formatAsClock(remainSec);
                timerRenderW = (int) (font.width(timeStr) * timeScale);
                // 标题宽度让步给倒计时，并留出 10 像素的安全呼吸间距
                availableW -= (timerRenderW + 10);
            }

            // 渲染左侧标题
            var title = StyledTextUtil.fitSingleLine(font,
                    ClientQuestCache.INSTANCE.getQuestDisplayComponent(tracked.getQuestId()), availableW);
            g.drawString(font, title, textX + iconOffset, textY, HudAnimUtil.withAlpha(0xFFFFFF, titleA), true);

            // =========================================================
            // 渲染右侧时间：精准锚定右边缘，基线像素级对齐
            // =========================================================
            if (timerRenderW > 0 && timeStr != null) {
                int themeColor = ClientQuestCache.INSTANCE.getQuestThemeColor(tracked.getQuestId(), 0x55AAFF);

                // 低于一分钟时的危机感心跳脉冲动画
                float pulse = 1.0f;
                if (remainSec <= 60) {
                    pulse = 0.6f + 0.4f * (float) Math.sin(Util.getMillis() / (remainSec <= 10 ? 80.0 : 200.0));
                    // 危机状态下，颜色略微向红色偏移，增加紧迫感
                    if (remainSec <= 10) {
                        themeColor = 0xFF4444;
                    }
                }

                int timeColor = HudAnimUtil.withAlpha(themeColor, (int) (titleA * pulse));

                // 精准计算：靠右对齐的绝对 X 坐标
                int timeX = maxRightX - timerRenderW;
                // Y轴下沉 1 像素，完美对齐 1.0 比例标题的基线（底部横线）
                int timeY = textY + 1;

                g.pose().pushPose();
                g.pose().translate(timeX, timeY, 0);
                g.pose().scale(timeScale, timeScale, 1f);
                g.drawString(font, timeStr, 0, 0, timeColor, true);
                g.pose().popPose();
            }
        }
    }

    public static int computePhaseNameHeight(QuestRuntimeData tracked, String displayedPhaseId, Font font) {
        return computePhaseNameHeight(tracked, displayedPhaseId, font, TrackerConstants.PANEL_WIDTH);
    }

    public static int computePhaseNameHeight(QuestRuntimeData tracked, String displayedPhaseId,
                                             Font font, int panelWidth) {
        return phaseNameLines(tracked, displayedPhaseId, font, panelWidth).size()
                * (font.lineHeight + 1) + 5;
    }

    public static int renderPhaseName(GuiGraphics g, QuestRuntimeData tracked, String displayedPhaseId, int themeColor, int textX, int textY, float alpha, float wipeAlpha, Font font) {
        return renderPhaseName(g, tracked, displayedPhaseId, themeColor, textX, textY,
                alpha, wipeAlpha, font, TrackerConstants.PANEL_WIDTH);
    }

    public static int renderPhaseName(GuiGraphics g, QuestRuntimeData tracked, String displayedPhaseId,
                                      int themeColor, int textX, int textY, float alpha,
                                      float wipeAlpha, Font font, int panelWidth) {
        List<net.minecraft.util.FormattedCharSequence> lines = phaseNameLines(
                tracked, displayedPhaseId, font, panelWidth);
        int height = lines.size() * (font.lineHeight + 1) + 5;
        int subA = (int) (255 * alpha * wipeAlpha);
        if (subA > 5) {
            g.fill(textX, textY + 1, textX + 2, textY + height - 5, HudAnimUtil.withAlpha(themeColor, subA));
            g.pose().pushPose();
            g.pose().translate(textX + 7, textY + 1, 0);
            g.pose().scale(0.95f, 0.95f, 1f);
            for (int i = 0; i < lines.size(); i++) {
                g.drawString(font, lines.get(i), 0, i * (font.lineHeight + 1), HudAnimUtil.withAlpha(0xEEEEEE, subA), true);
            }
            g.pose().popPose();
        }
        return textY + height;
    }

    private static List<net.minecraft.util.FormattedCharSequence> phaseNameLines(
            QuestRuntimeData tracked, String displayedPhaseId, Font font, int panelWidth) {
        Component phaseName = ClientQuestCache.INSTANCE.getPhaseDisplayComponent(tracked.getQuestId(), displayedPhaseId);
        Component phasePrefix = Component.translatable("arc_quest.hud.phase_prefix", phaseName);
        int availableWidth = (int) ((panelWidth - TrackerConstants.ACCENT_WIDTH
                - TrackerConstants.PADDING * 2 - 11) / 0.95f);
        List<net.minecraft.util.FormattedCharSequence> wrapped = font.split(phasePrefix, Math.max(1, availableWidth));
        if (wrapped.size() <= 2) return wrapped;
        return new ArrayList<>(wrapped.subList(0, 2));
    }

    public static int computeDescriptionHeight(PhaseDefinition phase, Font font) {
        return computeDescriptionHeight(phase, font, TrackerConstants.PANEL_WIDTH);
    }

    public static int computeDescriptionHeight(PhaseDefinition phase, Font font, int panelWidth) {
        if (!phase.hasDescription()) return 0;
        float scale = 0.85f;
        List<FormattedCharSequence> descLines = descriptionLines(phase, font, scale, panelWidth);

        int unscaledLineH = font.lineHeight + 3;
        return (int) (Math.min(descLines.size(), DESCRIPTION_VISIBLE_LINES) * unscaledLineH * scale) + 4;
    }

    public static int renderDescription(GuiGraphics g, PhaseDefinition phase, int textX, int textY, float alpha, float wipeAlpha, Font font) {
        return renderDescription(g, phase, textX, textY, alpha, wipeAlpha, font,
                TrackerConstants.PANEL_WIDTH);
    }

    public static int renderDescription(GuiGraphics g, PhaseDefinition phase, int textX, int textY,
                                        float alpha, float wipeAlpha, Font font, int panelWidth) {
        if (!phase.hasDescription()) return textY;

        int descA = (int) (255 * alpha * wipeAlpha);
        if (descA > 4) {
            float scale = 0.85f;
            List<FormattedCharSequence> descLines = descriptionLines(phase, font, scale, panelWidth);

            int unscaledLineH = font.lineHeight + 3;
            int visibleLines = Math.min(descLines.size(), DESCRIPTION_VISIBLE_LINES);

            g.pose().pushPose();
            g.pose().translate(textX + 2, textY, 0);
            g.pose().scale(scale, scale, 1f);

            if (descLines.size() <= DESCRIPTION_VISIBLE_LINES) {
                renderDescriptionPage(g, font, descLines, 0, visibleLines, unscaledLineH, descA, 0f);
            } else {
                String descriptionKey = phase.getDescription().getString();
                long now = Util.getMillis();
                if (!descriptionKey.equals(activeDescriptionKey)) {
                    activeDescriptionKey = descriptionKey;
                    activeDescriptionStartedAtMs = now;
                }

                int pageCount = (descLines.size() + DESCRIPTION_VISIBLE_LINES - 1) / DESCRIPTION_VISIBLE_LINES;
                long pageDuration = DESCRIPTION_PAGE_HOLD_MS + DESCRIPTION_PAGE_TRANSITION_MS;
                long elapsed = Math.max(0L, now - activeDescriptionStartedAtMs);
                int currentPage = (int) ((elapsed / pageDuration) % pageCount);
                long pageElapsed = elapsed % pageDuration;
                float transition = pageElapsed <= DESCRIPTION_PAGE_HOLD_MS
                        ? 0f
                        : smoothStep((pageElapsed - DESCRIPTION_PAGE_HOLD_MS) / (float) DESCRIPTION_PAGE_TRANSITION_MS);

                int currentStart = currentPage * DESCRIPTION_VISIBLE_LINES;
                int nextStart = ((currentPage + 1) % pageCount) * DESCRIPTION_VISIBLE_LINES;
                renderDescriptionPage(g, font, descLines, currentStart, visibleLines, unscaledLineH,
                        (int) (descA * (1f - transition)), -2f * transition);
                if (transition > 0f) {
                    renderDescriptionPage(g, font, descLines, nextStart, visibleLines, unscaledLineH,
                            (int) (descA * transition), 2f * (1f - transition));
                }
            }
            g.pose().popPose();

            textY += (int) (visibleLines * unscaledLineH * scale) + 4;
        }
        return textY;
    }

    private static List<FormattedCharSequence> descriptionLines(
            PhaseDefinition phase, Font font, float scale, int panelWidth) {
        int maxWidth = (int) ((panelWidth - TrackerConstants.ACCENT_WIDTH
                - TrackerConstants.PADDING * 2 - 6) / scale);
        return font.split(phase.getDescription(), Math.max(1, maxWidth));
    }

    private static void renderDescriptionPage(GuiGraphics graphics, Font font,
                                              List<FormattedCharSequence> lines,
                                              int start, int maxLines, int lineHeight, int alpha, float offsetY) {
        if (alpha <= 4) return;
        int end = Math.min(lines.size(), start + maxLines);
        for (int index = start; index < end; index++) {
            graphics.drawString(font, lines.get(index), 0,
                    (int) (offsetY + (index - start) * lineHeight), HudAnimUtil.withAlpha(0xFFFFFF, alpha), false);
        }
    }

    private static float smoothStep(float value) {
        float clamped = Math.max(0f, Math.min(1f, value));
        return clamped * clamped * (3f - 2f * clamped);
    }

    private static long getQuestRemainSeconds(QuestDefinition def, QuestRuntimeData runtime) {
        if (def == null || runtime == null || runtime.getState() != QuestState.ACTIVE) return -1L;
        if (!def.hasTimeLimit()) return -1L;

        QuestTimeLimitType type = def.getTimeLimitType();
        long limit = def.getTimeLimitValue();
        if (type == null || limit <= 0L) return -1L;

        if (type == QuestTimeLimitType.REAL_SECONDS) {
            long accepted = runtime.getAcceptedAtRealMs();
            if (accepted <= 0L) return -1L;
            long elapsedSec = Math.max(0L, (System.currentTimeMillis() - accepted) / 1000L);
            return Math.max(0L, limit - elapsedSec);
        } else if (type == QuestTimeLimitType.GAME_DAY_TIME) {
            long acceptedDay = runtime.getAcceptedAtDayTime() % 24000L;
            Minecraft mc = Minecraft.getInstance();
            long nowDay = (mc.level != null) ? (mc.level.getDayTime() % 24000L) : acceptedDay;
            long elapsedTicks = (nowDay - acceptedDay + 24000L) % 24000L;
            long remainTicks = Math.max(0L, limit - elapsedTicks);
            return remainTicks / 20L;
        }
        return -1L;
    }

    private static String formatAsClock(long totalSeconds) {
        long s = Math.max(0L, totalSeconds);
        long h = s / 3600L;
        long m = (s % 3600L) / 60L;
        long sec = s % 60L;

        if (h > 0L) {
            return String.format("%02d:%02d:%02d", h, m, sec);
        }
        return String.format("%02d:%02d", m, sec);
    }
}

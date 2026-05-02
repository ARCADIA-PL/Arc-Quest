package org.arcadia.arc_quest.client.hud.quest.tracker;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.HudRenderUtil;
import org.arcadia.arc_quest.client.hud.quest.QuestIconRenderer;
import org.arcadia.arc_quest.quest.api.IconPosition;
import org.arcadia.arc_quest.quest.api.PhaseDefinition;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.api.QuestState;
import org.arcadia.arc_quest.quest.api.QuestTimeLimitType;
import org.arcadia.arc_quest.quest.capability.QuestRuntimeData;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;

import java.util.List;

public class TrackerTitleWidget {

    public static void renderTitle(GuiGraphics g, QuestRuntimeData tracked, int textX, int textY, float alpha, float wipeAlpha, Font font) {
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
            int availableW = TrackerConstants.PANEL_WIDTH - TrackerConstants.ACCENT_WIDTH - TrackerConstants.PADDING * 2 - 4 - iconOffset;
            int maxRightX = textX + iconOffset + availableW;

            if (remainSec >= 0) {
                // 抛弃冗余的 "TIME:"，直接使用干脆的数字格式
                timeStr = formatAsClock(remainSec);
                timerRenderW = (int) (font.width(timeStr) * timeScale);
                // 标题宽度让步给倒计时，并留出 10 像素的安全呼吸间距
                availableW -= (timerRenderW + 10);
            }

            // 渲染左侧标题
            String title = font.plainSubstrByWidth(
                    ClientQuestCache.INSTANCE.getQuestDisplayName(tracked.getQuestId()),
                    availableW
            );
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

                int timeColor = HudAnimUtil.withAlpha(themeColor, (int)(titleA * pulse));

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

    public static void renderPhaseName(GuiGraphics g, QuestRuntimeData tracked, String displayedPhaseId, int themeColor, int textX, int textY, float alpha, float wipeAlpha, Font font) {
        int subA = (int) (255 * alpha * wipeAlpha);
        if (subA > 5) {
            g.fill(textX, textY + 1, textX + 2, textY + 10, HudAnimUtil.withAlpha(themeColor, subA));
            g.pose().pushPose();
            g.pose().translate(textX + 7, textY + 1, 0);
            g.pose().scale(0.95f, 0.95f, 1f);

            String phaseName = ClientQuestCache.INSTANCE.getPhaseDisplayName(tracked.getQuestId(), displayedPhaseId);
            String phasePrefix = Component.translatable("arc_quest.hud.phase_prefix", phaseName).getString();
            g.drawString(font, phasePrefix, 0, 0, HudAnimUtil.withAlpha(0xEEEEEE, subA), true);

            g.pose().popPose();
        }
    }

    public static int computeDescriptionHeight(PhaseDefinition phase, Font font) {
        if (!phase.hasDescription()) return 0;
        float scale = 0.85f;
        int maxW = (int) ((TrackerConstants.PANEL_WIDTH - TrackerConstants.ACCENT_WIDTH - TrackerConstants.PADDING * 2 - 4) / scale);
        List<String> descLines = HudRenderUtil.wrapText(phase.getDescription().getString(), maxW, font);

        int unscaledLineH = font.lineHeight + 3;
        return (int) (descLines.size() * unscaledLineH * scale) + 4;
    }

    public static int renderDescription(GuiGraphics g, PhaseDefinition phase, int textX, int textY, float alpha, float wipeAlpha, Font font) {
        if (!phase.hasDescription()) return textY;

        int descA = (int) (255 * alpha * wipeAlpha);
        if (descA > 4) {
            float scale = 0.85f;
            int maxW = (int) ((TrackerConstants.PANEL_WIDTH - TrackerConstants.ACCENT_WIDTH - TrackerConstants.PADDING * 2 - 4) / scale);
            List<String> descLines = HudRenderUtil.wrapText(phase.getDescription().getString(), maxW, font);

            int unscaledLineH = font.lineHeight + 3;

            g.pose().pushPose();
            g.pose().translate(textX + 2, textY, 0);
            g.pose().scale(scale, scale, 1f);

            for (int i = 0; i < descLines.size(); i++) {
                g.drawString(font, descLines.get(i), 0, i * unscaledLineH, HudAnimUtil.withAlpha(0xFFFFFF, descA), false);
            }
            g.pose().popPose();

            textY += (int) (descLines.size() * unscaledLineH * scale) + 4;
        }
        return textY;
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
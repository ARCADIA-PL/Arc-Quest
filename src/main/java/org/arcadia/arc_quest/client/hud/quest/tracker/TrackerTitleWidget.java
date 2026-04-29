package org.arcadia.arc_quest.client.hud.quest.tracker;

import com.mojang.blaze3d.systems.RenderSystem;
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

            String title = font.plainSubstrByWidth(
                    ClientQuestCache.INSTANCE.getQuestDisplayName(tracked.getQuestId()),
                    TrackerConstants.PANEL_WIDTH - TrackerConstants.ACCENT_WIDTH - TrackerConstants.PADDING * 2 - 4 - iconOffset
            );
            g.drawString(font, title, textX + iconOffset, textY, HudAnimUtil.withAlpha(0xFFFFFF, titleA), true);
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
        int maxW = (int) ((TrackerConstants.PANEL_WIDTH - TrackerConstants.ACCENT_WIDTH - TrackerConstants.PADDING * 2) / 0.75f);
        List<String> descLines = HudRenderUtil.wrapText(phase.getDescription().getString(), maxW, font);
        return descLines.size() * (int) (font.lineHeight * 0.75f + 1) + 4;
    }

    public static int renderDescription(GuiGraphics g, PhaseDefinition phase, int textX, int textY, float alpha, float wipeAlpha, Font font) {
        if (!phase.hasDescription()) return textY;
        int descA = (int) (255 * alpha * wipeAlpha);
        if (descA > 4) {
            int maxW = (int) ((TrackerConstants.PANEL_WIDTH - TrackerConstants.ACCENT_WIDTH - TrackerConstants.PADDING * 2) / 0.75f);
            List<String> descLines = HudRenderUtil.wrapText(phase.getDescription().getString(), maxW, font);
            for (String line : descLines) {
                g.pose().pushPose();
                g.pose().translate(textX + 7, textY, 0);
                g.pose().scale(0.75f, 0.75f, 1f);
                g.drawString(font, line, 0, 0, HudAnimUtil.withAlpha(0x99BBFF, descA), false);
                g.pose().popPose();
                textY += (int) (font.lineHeight * 0.75f + 1);
            }
            textY += 4;
        }
        return textY;
    }
}
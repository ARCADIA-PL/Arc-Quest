package org.com.arc_quest.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.com.arc_quest.quest.api.QuestDefinition;
import org.com.arc_quest.quest.registry.QuestRegistry;

public class BranchChoiceToast {

    public static final int POPUP_W = 220;
    public static final int POPUP_H = 36;
    private static final int COLOR_ACCENT = 0xFFFFCC44;

    private static final float TIME_ENTER = 600f;
    private static final float TIME_EXIT  = 400f;
    private static final float FLY_DIST   = 10f;

    private final String questId;
    private long startTime;
    private boolean isDismissing = false;
    private long dismissStartTime = 0;
    private long lastRenderTime = 0;

    public BranchChoiceToast(String questId) {
        this.questId = questId;
        this.startTime = Util.getMillis();
        this.lastRenderTime = Util.getMillis();
    }

    public void dismiss() {
        if (!isDismissing) {
            isDismissing = true;
            dismissStartTime = Util.getMillis();
        }
    }

    public boolean isExpired() {
        if (!isDismissing) return false;
        return Util.getMillis() - dismissStartTime >= TIME_EXIT;
    }

    public String getQuestId() {
        return questId;
    }

    public boolean render(GuiGraphics g, int baseX, int baseY, float parentAlpha, float partialTick, boolean isFrozen) {
        long now = Util.getMillis();

        if (isFrozen) {
            long dt = now - lastRenderTime;
            this.startTime += dt;
            if (isDismissing) this.dismissStartTime += dt;
            this.lastRenderTime = now;

            return true;
        }
        this.lastRenderTime = now;

        long elapsedEnter = now - startTime;
        float alpha = 1f, textDriftX = 0f, revealProgress = 1f, wipeProgress = 0f;
        float lineWidth = POPUP_W - 20f;

        if (!isDismissing && elapsedEnter < TIME_ENTER) {
            float t = elapsedEnter / TIME_ENTER;
            float ease = QuestAnimUtil.easeOutQuintic(t);
            alpha = ease; revealProgress = ease;
            textDriftX = -(1f - ease) * FLY_DIST; lineWidth *= ease;
        } else if (!isDismissing) {
            textDriftX = (float)Math.sin(elapsedEnter / 400.0) * 0.5f;
        } else {
            long elapsedExit = now - dismissStartTime;
            if (elapsedExit >= TIME_EXIT) return false;
            float t = elapsedExit / TIME_EXIT;
            float ease = QuestAnimUtil.easeInQuartic(t);
            wipeProgress = ease; alpha = 1f - (float)Math.pow(t, 6);
            textDriftX = ease * FLY_DIST; lineWidth *= (1f - ease);
        }

        float finalAlpha = alpha * parentAlpha;
        if (finalAlpha < 0.01f) return true;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        int scLeft = baseX - 20;
        int scRight = baseX + POPUP_W + 20;
        if (!isDismissing && elapsedEnter < TIME_ENTER) scRight = baseX + (int)(POPUP_W * revealProgress);
        else if (isDismissing) scRight = baseX + (int)(POPUP_W * (1f - wipeProgress));

        g.enableScissor(scLeft, baseY - 10, scRight, baseY + POPUP_H + 20);

        int bgA = (int)(finalAlpha * 0x88);
        int accentA = (int)(finalAlpha * 255);
        int themeColor = COLOR_ACCENT & 0xFFFFFF;
        QuestRenderUtil.drawGlassPanel(g, baseX, baseY, POPUP_W, POPUP_H,
                0x121212, bgA, themeColor, accentA, 4);

        QuestDefinition def = QuestRegistry.get(ResourceLocation.tryParse(questId));
        String questName = def != null ? def.getDisplayName().getString() : questId;
        Font font = Minecraft.getInstance().font;

        float contentX = baseX + 12 + textDriftX;
        float contentY = baseY + 6;

        if (accentA > 5) {
            String subtitle = "ARES SYSTEM // BRANCH AVAILABLE";
            String prefix = "New Path Unlocked: ";
            String cutName = font.plainSubstrByWidth(questName, POPUP_W - 30 - font.width(prefix));
            String title = prefix + cutName;
            
            int subColor = QuestAnimUtil.withAlpha(0xAAAAAA, accentA);
            int titleColor = QuestAnimUtil.withAlpha(0xFFFFFF, accentA);
            QuestRenderUtil.drawDualText(g, font, contentX, contentY,
                    subtitle, title, subColor, titleColor, 1.0f);

            QuestRenderUtil.drawTextWithLine(g, font, contentX, contentY + 10,
                    "", QuestAnimUtil.withAlpha(themeColor, accentA),
                    lineWidth, 12);
        }

        g.disableScissor();
        RenderSystem.disableBlend();
        return true;
    }
}
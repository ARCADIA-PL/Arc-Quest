package org.arcadia.arc_quest.client.hud.quest.journal;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.client.hud.quest.QuestIconRenderer;
import org.arcadia.arc_quest.mutil.core.ArcGuiElement;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.api.SplashType;

public class ArcQuestJournalWatermarkElement extends ArcGuiElement {
    public ArcQuestJournalWatermarkElement() {
        super(0, 0, 0, 0);
    }

    public void render(GuiGraphics graphics, QuestDefinition def, int detailX, int scrollAreaY, int detailWidth, int scrollAreaH, float detailReveal, float detailAlpha) {
        def.getSplashConfig(SplashType.QUEST_DETAIL).ifPresent(asset -> {
            RenderSystem.enableBlend();
            float watermarkAlpha = 0.15f * detailAlpha;
            int renderWidth = (int) (detailWidth * 0.7f);
            int renderHeight = renderWidth;
            int renderX = detailX + detailWidth / 2 - renderWidth / 2 + (int) ((1f - detailReveal) * 50f);
            int renderY = scrollAreaY + scrollAreaH / 2 - renderHeight / 2;
            RenderSystem.setShaderColor(1f, 1f, 1f, watermarkAlpha);
            QuestIconRenderer.renderIcon(graphics, asset, renderX, renderY, renderWidth, renderHeight);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        });
    }
}

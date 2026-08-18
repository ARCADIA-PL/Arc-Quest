package org.arcadia.arc_quest.client.compat.xaero;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.client.hud.questmarker.MarkerHudRenderer;
import org.arcadia.arc_quest.client.hud.questmarker.MarkerRhombusRenderer;
import org.arcadia.arc_quest.client.hud.quest.journal.component.JournalTooltipRenderer;

import java.util.List;

public final class XaeroQuestMarkerIconRenderer {

    private static final float LIGHT_X = 0.15F;
    private static final float LIGHT_Y = -1.0F;

    private XaeroQuestMarkerIconRenderer() {
    }

    public static void render(GuiGraphics gui, int colorArgb) {
        render(gui, colorArgb, 1.0F);
    }

    public static void render(GuiGraphics gui, int colorArgb, float scale) {
        if ((colorArgb >>> 24) == 0) colorArgb |= 0xFF000000;
        float time = (System.currentTimeMillis() % 100000L) / 1000.0F;
        gui.pose().pushPose();
        gui.pose().scale(scale, scale, 1.0F);
        MarkerRhombusRenderer.drawIsolated(
                gui,
                colorArgb,
                time,
                1.0F,
                LIGHT_X,
                LIGHT_Y,
                MarkerHudRenderer.DistanceTier.NEAR.getTargetValue()
        );
        gui.pose().popPose();
    }

    public static void renderTooltip(GuiGraphics gui, String label, int themeColor) {
        if (label == null || label.isBlank()) return;
        Minecraft minecraft = Minecraft.getInstance();
        double mouseScaleX = (double) gui.guiWidth() / minecraft.getWindow().getScreenWidth();
        double mouseScaleY = (double) gui.guiHeight() / minecraft.getWindow().getScreenHeight();
        int mouseX = (int) Math.round(minecraft.mouseHandler.xpos() * mouseScaleX);
        int mouseY = (int) Math.round(minecraft.mouseHandler.ypos() * mouseScaleY);

        gui.pose().pushPose();
        gui.pose().setIdentity();
        gui.pose().translate(0.0F, 0.0F, 5000.0F);
        JournalTooltipRenderer.renderAtMouse(
                gui,
                minecraft.font,
                List.of(Component.literal(label)),
                mouseX,
                mouseY,
                gui.guiWidth(),
                gui.guiHeight(),
                themeColor
        );
        gui.pose().popPose();
    }
}

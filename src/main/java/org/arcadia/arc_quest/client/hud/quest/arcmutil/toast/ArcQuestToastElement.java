package org.arcadia.arc_quest.client.hud.quest.arcmutil.toast;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.mutil.animation.ArcAnimClock;
import org.arcadia.arc_quest.mutil.theme.ArcDrawUtil;
import org.arcadia.arc_quest.mutil.screen.ArcScaleResolver;
import org.arcadia.arc_quest.mutil.theme.ArcPanelChrome;
import org.arcadia.arc_quest.mutil.core.ArcGuiContext;
import org.arcadia.arc_quest.mutil.core.ArcGuiElement;

public class ArcQuestToastElement extends ArcGuiElement {
    public static final int TOAST_WIDTH = 220;
    public static final int TOAST_HEIGHT = 28;
    public static final int TOAST_GAP = 4;
    public static final int MARGIN_RIGHT = 8;
    public static final int MARGIN_TOP = 8;
    public static final float ENTER = 400f;
    public static final float HOLD = 3000f;
    public static final float EXIT = 350f;

    private ArcQuestToastViewModel model;
    private int slotIndex;

    public ArcQuestToastElement() {
        super(0, 0, TOAST_WIDTH, TOAST_HEIGHT);
    }

    public void apply(ArcQuestToastViewModel model, int slotIndex) {
        this.model = model;
        this.slotIndex = slotIndex;
        setVisible(model != null);
    }

    @Override
    public void draw(GuiGraphics graphics, ArcGuiContext context, int refX, int refY, float inheritedOpacity) {
        if (!visible || model == null) return;

        long elapsed = model.elapsed();
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int screenWidth = context.screenWidth();
        int screenHeight = context.screenHeight();

        float uiScale = ArcScaleResolver.resolveUniversalUiScale(screenWidth, screenHeight);
        float sw = screenWidth / uiScale;
        int vMarginRight = 16;
        int slotY = MARGIN_TOP + slotIndex * (TOAST_HEIGHT + TOAST_GAP);
        int vSlotY = (int) (slotY / uiScale);

        float alpha;
        float slideX;
        float slideDistance = TOAST_WIDTH + vMarginRight + 30f;

        if (elapsed < ENTER) {
            float t = elapsed / ENTER;
            float ease = ArcAnimClock.easeOutQuintic(t);
            alpha = ease;
            slideX = (1f - ease) * slideDistance;
        } else if (elapsed < ENTER + HOLD) {
            alpha = 1f;
            slideX = 0f;
        } else {
            float t = Math.min(1f, (elapsed - ENTER - HOLD) / EXIT);
            float ease = ArcAnimClock.easeInQuartic(t);
            alpha = 1f - (float) Math.pow(t, 6);
            slideX = ease * slideDistance;
        }

        alpha *= inheritedOpacity;
        if (alpha < 0.01f) return;

        graphics.pose().pushPose();
        graphics.pose().scale(uiScale, uiScale, 1f);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();

        int toastX = (int) (sw - TOAST_WIDTH - vMarginRight + slideX);
        int bgAlpha = (int) (0x88 * alpha);
        int accentAlpha = (int) (255 * alpha);
        int lineAlpha = (int) (80 * alpha);
        ArcPanelChrome.drawToastPanel(graphics, toastX, vSlotY, TOAST_WIDTH, TOAST_HEIGHT,
                0x121212, bgAlpha,
                model.type.accentColor & 0x00FFFFFF, accentAlpha, 3,
                lineAlpha);

        int textAlpha = (int) (255 * alpha);
        if (textAlpha > 8) {
            if (model.cachedNameWidth != TOAST_WIDTH - 16) {
                model.cachedNameWidth = TOAST_WIDTH - 16;
                model.cachedNameStr = font.plainSubstrByWidth(model.text, model.cachedNameWidth);
            }
            int subColor = ArcDrawUtil.withAlpha(model.type.accentColor, textAlpha);
            int titleColor = ArcDrawUtil.withAlpha(0xFFFFFF, textAlpha);
            ArcDrawUtil.drawDualText(graphics, font, toastX + 8, vSlotY + 4,
                    model.subtitle, model.cachedNameStr, subColor, titleColor, 1.0f);
        }

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        graphics.pose().popPose();
    }
}

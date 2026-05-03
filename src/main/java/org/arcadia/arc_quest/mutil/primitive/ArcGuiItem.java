package org.arcadia.arc_quest.mutil.primitive;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.arcadia.arc_quest.mutil.core.ArcGuiContext;
import org.arcadia.arc_quest.mutil.core.ArcGuiElement;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class ArcGuiItem extends ArcGuiElement {
    protected final Minecraft minecraft;
    @Nullable
    protected ItemStack itemStack;
    protected boolean showTooltip = true;
    protected float opacityThreshold = 1f;
    protected boolean resetDepthTest = true;
    protected boolean renderDecoration = true;

    public ArcGuiItem(int x, int y) {
        super(x, y, 16, 16);
        this.minecraft = Minecraft.getInstance();
        this.visible = false;
    }

    public ArcGuiItem setItem(@Nullable ItemStack itemStack) {
        this.itemStack = itemStack;
        setVisible(itemStack != null && !itemStack.isEmpty());
        return this;
    }

    public ArcGuiItem setTooltip(boolean showTooltip) {
        this.showTooltip = showTooltip;
        return this;
    }

    public ArcGuiItem setOpacityThreshold(float opacityThreshold) {
        this.opacityThreshold = opacityThreshold;
        return this;
    }

    public ArcGuiItem setResetDepthTest(boolean resetDepthTest) {
        this.resetDepthTest = resetDepthTest;
        return this;
    }

    public ArcGuiItem setRenderDecoration(boolean renderDecoration) {
        this.renderDecoration = renderDecoration;
        return this;
    }

    @Override
    public void draw(GuiGraphics graphics, ArcGuiContext context, int refX, int refY, float inheritedOpacity) {
        if (!visible || itemStack == null || itemStack.isEmpty()) return;
        float finalOpacity = inheritedOpacity * opacity;
        if (finalOpacity < opacityThreshold) return;
        RenderSystem.applyModelViewMatrix();
        RenderSystem.enableDepthTest();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        graphics.renderItem(itemStack, refX + x, refY + y);
        if (renderDecoration) {
            graphics.renderItemDecorations(minecraft.font, itemStack, refX + x, refY + y);
        }
        if (resetDepthTest) {
            RenderSystem.disableDepthTest();
        }
        drawChildren(graphics, context, refX + x, refY + y, finalOpacity);
    }

    @Override
    @Nullable
    public List<Component> getTooltipLines() {
        if (!visible || !focused || !showTooltip || itemStack == null || itemStack.isEmpty()) return null;
        return new ArrayList<>(itemStack.getTooltipLines(minecraft.player,
                minecraft.options.advancedItemTooltips ? TooltipFlag.Default.ADVANCED : TooltipFlag.Default.NORMAL));
    }
}

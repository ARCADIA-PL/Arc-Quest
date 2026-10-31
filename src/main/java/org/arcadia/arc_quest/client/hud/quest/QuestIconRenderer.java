package org.arcadia.arc_quest.client.hud.quest;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.Lighting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.ForgeRenderTypes;
import org.arcadia.arc_quest.quest.api.VisualAsset;

public class QuestIconRenderer {

    /**
     * 基础图标渲染器，支持 VisualAsset 的所有属性（缩放、偏移、染色）
     * 以及物品图标（通过 renderFakeItem 走原版物品模型渲染）。
     */
    public static void renderIcon(GuiGraphics g, VisualAsset asset, int x, int y, int width, int height) {
        renderIcon(g, asset, x, y, width, height, 1.0f);
    }

    public static void renderIcon(GuiGraphics g, VisualAsset asset, int x, int y,
                                  int width, int height, float alpha) {
        if (asset == null || !asset.enabled()) return;

        // 物品渲染：直接走原版物品模型，无需关心纹理路径
        if (asset.item() != null && !asset.item().isEmpty()) {
            renderItemIcon(g, asset.item(), x, y, width, height, asset, alpha);
            return;
        }

        if (asset.texture() == null) return;

        g.pose().pushPose();

        // 1. 应用偏移
        g.pose().translate(x + asset.offsetX(), y + asset.offsetY(), 0);

        // 2. 中心缩放逻辑
        if (asset.scale() != 1.0f) {
            float hw = width / 2f;
            float hh = height / 2f;
            g.pose().translate(hw, hh, 0);
            g.pose().scale(asset.scale(), asset.scale(), 1.0f);
            g.pose().translate(-hw, -hh, 0);
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // 3. 应用染色 (解析 ARGB)
        if (asset.tintColor() != 0xFFFFFFFF) {
            float a = ((asset.tintColor() >> 24) & 0xFF) / 255f * alpha;
            float r = ((asset.tintColor() >> 16) & 0xFF) / 255f;
            float gg = ((asset.tintColor() >> 8) & 0xFF) / 255f;
            float b = (asset.tintColor() & 0xFF) / 255f;
            RenderSystem.setShaderColor(r, gg, b, a);
        } else if (alpha < 1.0f) {
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);
        }

        // 4. 渲染
        g.blit(asset.texture(), 0, 0, 0, 0, width, height, width, height);

        // 5. 还原状态
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
        g.pose().popPose();
    }

    private static void renderItemIcon(GuiGraphics g, ItemStack stack,
                                       int x, int y, int width, int height,
                                       VisualAsset asset, float alpha) {
        if (alpha <= 0.0f) return;
        float totalScaleX = (width / 16f) * asset.scale();
        float totalScaleY = (height / 16f) * asset.scale();
        float itemW = 16f * totalScaleX;
        float itemH = 16f * totalScaleY;
        float itemX = x + asset.offsetX() + (width - itemW) / 2f;
        float itemY = y + asset.offsetY() + (height - itemH) / 2f;

        // Flush unrelated GUI vertices before changing the item's shader colour.
        g.flush();
        float[] previousColor = RenderSystem.getShaderColor().clone();
        var minecraft = Minecraft.getInstance();
        var renderer = minecraft.getItemRenderer();
        var model = renderer.getModel(stack, minecraft.level, null, 0);
        boolean flatLighting = !model.usesBlockLight();
        g.pose().pushPose();
        try {
            g.pose().translate(itemX, itemY, 0);
            g.pose().scale(totalScaleX, totalScaleY, 1.0f);
            g.pose().translate(8.0f, 8.0f, 150.0f);
            g.pose().scale(16.0f, -16.0f, 16.0f);
            if (flatLighting) Lighting.setupForFlatItems();
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, Math.min(1.0f, alpha));
            // Solid/cutout item passes disable blending themselves. Route only those
            // atlas passes through the native translucent item type during a fade.
            MultiBufferSource buffers = type -> g.bufferSource().getBuffer(alpha < 1.0f
                    && (type == Sheets.solidBlockSheet() || type == Sheets.cutoutBlockSheet()
                    || type == ForgeRenderTypes.ITEM_LAYERED_SOLID.get()
                    || type == ForgeRenderTypes.ITEM_LAYERED_CUTOUT.get()
                    || type == ForgeRenderTypes.ITEM_LAYERED_CUTOUT_MIPPED.get())
                    ? Sheets.translucentCullBlockSheet() : type);
            renderer.render(stack, ItemDisplayContext.GUI, false, g.pose(), buffers,
                    15728880, OverlayTexture.NO_OVERLAY, model);
            g.flush();
        } finally {
            if (flatLighting) Lighting.setupFor3DItems();
            RenderSystem.setShaderColor(previousColor[0], previousColor[1], previousColor[2], previousColor[3]);
            g.pose().popPose();
        }
    }
}

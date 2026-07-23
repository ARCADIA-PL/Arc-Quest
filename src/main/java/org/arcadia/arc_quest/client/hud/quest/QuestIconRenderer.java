package org.arcadia.arc_quest.client.hud.quest;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
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
        float totalScaleX = (width / 16f) * asset.scale();
        float totalScaleY = (height / 16f) * asset.scale();
        float itemW = 16f * totalScaleX;
        float itemH = 16f * totalScaleY;
        float itemX = x + asset.offsetX() + (width - itemW) / 2f;
        float itemY = y + asset.offsetY() + (height - itemH) / 2f;

        g.pose().pushPose();
        g.pose().translate(itemX, itemY, 0);
        g.pose().scale(totalScaleX, totalScaleY, 1.0f);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        if (alpha < 1.0f) {
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);
        }

        g.renderFakeItem(stack, 0, 0);

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
        g.pose().popPose();
    }
}

package org.com.arc_quest.client.gui.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import org.com.arc_quest.quest.api.VisualAsset;

public class QuestIconRenderer {

    /**
     * 基础图标渲染器，支持 VisualAsset 的所有属性（缩放、偏移、染色）
     */
    public static void renderIcon(GuiGraphics g, VisualAsset asset, int x, int y, int width, int height) {
        if (asset == null || !asset.enabled() || asset.texture() == null) return;

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
            float a = ((asset.tintColor() >> 24) & 0xFF) / 255f;
            float r = ((asset.tintColor() >> 16) & 0xFF) / 255f;
            float gg = ((asset.tintColor() >> 8) & 0xFF) / 255f;
            float b = (asset.tintColor() & 0xFF) / 255f;
            RenderSystem.setShaderColor(r, gg, b, a);
        }

        // 4. 渲染
        g.blit(asset.texture(), 0, 0, 0, 0, width, height, width, height);

        // 5. 还原状态
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
        g.pose().popPose();
    }
}
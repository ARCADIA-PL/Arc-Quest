package org.arcadia.arc_quest.quest.api;

import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 单个视觉资源的最小配置单元。
 * <p>
 * 包含纹理或物品渲染所需的基础属性。
 * 这是构建复杂视觉系统的"原子"。
 */
public record VisualAsset(
        @Nullable ResourceLocation texture,   // 纹理路径
        float scale,                           // 缩放比例
        float offsetX,                         // X轴偏移（像素）
        float offsetY,                         // Y轴偏移（像素）
        int tintColor,                         // 染色颜色（ARGB，0xFFFFFFFF=无染色）
        boolean enabled,                       // 是否启用
        @Nullable ItemStack item               // 物品渲染（非空时优先于纹理，走 renderFakeItem）
) {

    /** 向后兼容的构造函数，item 默认为 null。 */
    public VisualAsset(
            @Nullable ResourceLocation texture,
            float scale,
            float offsetX,
            float offsetY,
            int tintColor,
            boolean enabled) {
        this(texture, scale, offsetX, offsetY, tintColor, enabled, null);
    }

    /**
     * 默认禁用状态。
     */
    public static final VisualAsset DISABLED = new VisualAsset(
            null, 1.0f, 0f, 0f, 0xFFFFFFFF, false
    );

    /**
     * 构建器 便捷方法。
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 快速创建启用的纹理。
     */
    public static VisualAsset of(ResourceLocation texture) {
        return new VisualAsset(texture, 1.0f, 0f, 0f, 0xFFFFFFFF, true);
    }

    /**
     * 快速创建带缩放的纹理。
     */
    public static VisualAsset of(ResourceLocation texture, float scale) {
        return new VisualAsset(texture, scale, 0f, 0f, 0xFFFFFFFF, true);
    }

    /**
     * 快速创建物品图标。
     */
    public static VisualAsset of(ItemStack item) {
        return new VisualAsset(null, 1.0f, 0f, 0f, 0xFFFFFFFF, true, item);
    }

    /**
     * 快速创建带缩放的物品图标。
     */
    public static VisualAsset of(ItemStack item, float scale) {
        return new VisualAsset(null, scale, 0f, 0f, 0xFFFFFFFF, true, item);
    }

    public static class Builder {
        private ResourceLocation texture = null;
        private float scale = 1.0f;
        private float offsetX = 0f;
        private float offsetY = 0f;
        private int tintColor = 0xFFFFFFFF;
        private boolean enabled = true;
        private ItemStack item = null;

        public Builder texture(ResourceLocation texture) {
            this.texture = texture;
            return this;
        }

        public Builder scale(float scale) {
            this.scale = Math.max(0.1f, scale);
            return this;
        }

        public Builder offset(float x, float y) {
            offsetX = x;
            offsetY = y;
            return this;
        }

        public Builder tintColor(int color) {
            tintColor = color;
            return this;
        }

        public Builder tintColorFromChatFormatting(ChatFormatting formatting) {
            Integer color = formatting.getColor();
            if (color != null) {
                tintColor = 0xFF000000 | color;
            }
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder item(ItemStack item) {
            this.item = item;
            return this;
        }

        public VisualAsset build() {
            return new VisualAsset(texture, scale, offsetX, offsetY, tintColor, enabled, item);
        }
    }
}

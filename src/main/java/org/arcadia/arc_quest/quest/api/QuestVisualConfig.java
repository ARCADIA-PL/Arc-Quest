package org.arcadia.arc_quest.quest.api;

import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 完整的视觉配置集合（高可扩展容器）。
 * <p>
 * 使用 EnumMap 存储不同类型的立绘和图标，
 * 支持动态添加新类型而无需修改数据结构。
 */
public class QuestVisualConfig {

    /**
     * 空配置（所有视觉元素禁用）。
     */
    public static final QuestVisualConfig EMPTY = new Builder().build();
    // ═══════════════════════════════════════════
    //  立绘配置映射（SplashType → VisualAsset）
    // ═══════════════════════════════════════════
    private final EnumMap<SplashType, VisualAsset> splashAssets;
    // ═══════════════════════════════════════════
    //  图标配置映射（IconPosition → VisualAsset）
    // ═══════════════════════════════════════════
    private final EnumMap<IconPosition, VisualAsset> iconAssets;
    // ═══════════════════════════════════════════
    //  主题色（单一值，可被具体场景覆盖）
    // ═══════════════════════════════════════════
    private final int themeColor;
    private final boolean useQuestSplashPresentation;

    private QuestVisualConfig(Builder builder) {
        splashAssets = new EnumMap<>(SplashType.class);
        iconAssets = new EnumMap<>(IconPosition.class);

        // 复制Builder中的配置
        splashAssets.putAll(builder.splashAssets);
        iconAssets.putAll(builder.iconAssets);

        themeColor = builder.themeColor;
        useQuestSplashPresentation = builder.useQuestSplashPresentation;
    }

    /**
     * Builder 入口。
     */
    public static Builder builder() {
        return new Builder();
    }

    // ═══════════════════════════════════════════
    //  查询 API
    // ═══════════════════════════════════════════

    /**
     * 获取指定类型的立绘配置。
     */
    public Optional<VisualAsset> getSplash(SplashType type) {
        VisualAsset asset = splashAssets.get(type);
        return (asset != null && asset.enabled()) ? Optional.of(asset) : Optional.empty();
    }

    /**
     * 获取指定位置的图标配置。
     */
    public Optional<VisualAsset> getIcon(IconPosition position) {
        VisualAsset asset = iconAssets.get(position);
        return (asset != null && asset.enabled()) ? Optional.of(asset) : Optional.empty();
    }

    /**
     * 获取主题色。
     */
    public int getThemeColor() {
        return themeColor;
    }

    public boolean usesQuestSplashPresentation() {
        return useQuestSplashPresentation;
    }

    /**
     * 检查是否有任意启用的立绘。
     */
    public boolean hasAnySplash() {
        return splashAssets.values().stream().anyMatch(VisualAsset::enabled);
    }

    /**
     * 检查是否有任意启用的图标。
     */
    public boolean hasAnyIcon() {
        return iconAssets.values().stream().anyMatch(VisualAsset::enabled);
    }

    /**
     * 获取所有启用的立绘类型。
     */
    public Set<SplashType> getEnabledSplashTypes() {
        return splashAssets.entrySet().stream()
                .filter(e -> e.getValue().enabled())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    /**
     * 获取所有启用的图标位置。
     */
    public Set<IconPosition> getEnabledIconPositions() {
        return iconAssets.entrySet().stream()
                .filter(e -> e.getValue().enabled())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    // ═══════════════════════════════════════════
    //  Builder
    // ═══════════════════════════════════════════

    public static class Builder {
        private final EnumMap<SplashType, VisualAsset> splashAssets = new EnumMap<>(SplashType.class);
        private final EnumMap<IconPosition, VisualAsset> iconAssets = new EnumMap<>(IconPosition.class);
        private int themeColor = 0xFFFFFFFF;
        private boolean useQuestSplashPresentation;

        /**
         * 添加立绘配置。
         */
        public Builder splash(SplashType type, VisualAsset asset) {
            if (asset != null && asset.enabled()) {
                splashAssets.put(type, asset);
            }
            return this;
        }

        /**
         * 便捷方法：快速添加立绘。
         */
        public Builder splash(SplashType type, ResourceLocation texture, float scale) {
            return splash(type, VisualAsset.of(texture, scale));
        }

        /**
         * 便捷方法：快速添加立绘（默认缩放1.0）。
         */
        public Builder splash(SplashType type, ResourceLocation texture) {
            return splash(type, VisualAsset.of(texture));
        }

        /**
         * 添加图标配置。
         */
        public Builder icon(IconPosition position, VisualAsset asset) {
            if (asset != null && asset.enabled()) {
                iconAssets.put(position, asset);
            }
            return this;
        }

        /**
         * 便捷方法：快速添加图标。
         */
        public Builder icon(IconPosition position, ResourceLocation texture, float scale) {
            return icon(position, VisualAsset.of(texture, scale));
        }

        /**
         * 便捷方法：快速添加图标（默认缩放1.0）。
         */
        public Builder icon(IconPosition position, ResourceLocation texture) {
            return icon(position, VisualAsset.of(texture));
        }

        /**
         * 设置主题色。
         */
        public Builder themeColor(int color) {
            themeColor = color;
            return this;
        }

        public Builder useQuestSplashPresentation(boolean useQuestSplashPresentation) {
            this.useQuestSplashPresentation = useQuestSplashPresentation;
            return this;
        }

        /**
         * 从 ChatFormatting 设置主题色。
         */
        public Builder themeColorFromChatFormatting(ChatFormatting formatting) {
            Integer color = formatting.getColor();
            if (color != null) {
                themeColor = 0xFF000000 | color;
            }
            return this;
        }

        public QuestVisualConfig build() {
            return new QuestVisualConfig(this);
        }
    }
}

package org.com.arc_quest.trade.api;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.Objects;

/**
 * 商品分类。在完整交易窗口 ({@link org.com.arc_quest.client.gui.TradeScreen})
 * 中用于左侧分类标签页。
 */
public final class TradeCategory {

    private final String id;
    private final Component displayName;
    private final int sortOrder;
    private final int themeColor;

    public TradeCategory(String id, Component displayName, int sortOrder, int themeColor) {
        this.id = Objects.requireNonNull(id);
        this.displayName = Objects.requireNonNull(displayName);
        this.sortOrder = sortOrder;
        this.themeColor = themeColor;
    }

    public TradeCategory(String id, Component displayName) {
        this(id, displayName, 0, 0xFFFFFFFF);
    }

    public String getId() { return id; }
    public Component getDisplayName() { return displayName; }
    public int getSortOrder() { return sortOrder; }
    public int getThemeColor() { return themeColor; }

    /** "全部" 分类常量 */
    public static final TradeCategory ALL = new TradeCategory(
            "_all", Component.translatable("arc_quest.trade.category.all"), -1, 0xFFFFFFFF);

    public static TradeCategory of(String id, String literal) {
        return new TradeCategory(id, Component.literal(literal));
    }

    /**
     * 使用翻译键创建分类。
     *
     * @param id 分类 ID
     * @param translationKey 翻译键（如 {@code "arc_quest.trade.category.weapons"}）
     */
    public static TradeCategory ofTranslated(String id, String translationKey) {
        return new TradeCategory(id, Component.translatable(translationKey));
    }

    public static TradeCategory of(String id, String literal, int sortOrder, int color) {
        return new TradeCategory(id, Component.literal(literal), sortOrder, color);
    }

    /**
     * 使用翻译键创建分类（带排序和颜色）。
     */
    public static TradeCategory ofTranslated(String id, String translationKey, int sortOrder, int color) {
        return new TradeCategory(id, Component.translatable(translationKey), sortOrder, color);
    }

    /**
     * 从 ChatFormatting 创建分类（便捷方法）。
     */
    public static TradeCategory of(String id, String literal, int sortOrder, ChatFormatting formatting) {
        Integer rgb = formatting.getColor();
        int color = (rgb != null) ? (0xFF000000 | rgb) : 0xFFFFFFFF;
        return new TradeCategory(id, Component.literal(literal), sortOrder, color);
    }

    /**
     * 从 ChatFormatting 创建分类（自动添加完全不透明 Alpha 通道）。
     */
    public static TradeCategory ofColor(String id, String literal, ChatFormatting formatting) {
        Integer rgb = formatting.getColor();
        int color = (rgb != null) ? (0xFF000000 | rgb) : 0xFFFFFFFF;
        return new TradeCategory(id, Component.literal(literal), 0, color);
    }

    /**
     * 从 ChatFormatting 创建分类（带排序）。
     */
    public static TradeCategory ofColor(String id, String literal, int sortOrder, ChatFormatting formatting) {
        Integer rgb = formatting.getColor();
        int color = (rgb != null) ? (0xFF000000 | rgb) : 0xFFFFFFFF;
        return new TradeCategory(id, Component.literal(literal), sortOrder, color);
    }

    /**
     * 使用翻译键 + ChatFormatting 创建分类（推荐）。
     *
     * @param id 分类 ID
     * @param translationKey 翻译键
     * @param sortOrder 排序顺序
     * @param formatting 颜色格式化
     */
    public static TradeCategory ofTranslatedColor(String id, String translationKey, int sortOrder, ChatFormatting formatting) {
        Integer rgb = formatting.getColor();
        int color = (rgb != null) ? (0xFF000000 | rgb) : 0xFFFFFFFF;
        return new TradeCategory(id, Component.translatable(translationKey), sortOrder, color);
    }

    /**
     * 从 RGB 值创建分类（自动添加完全不透明 Alpha 通道）。
     *
     * @param id 分类 ID
     * @param literal 显示名称
     * @param r 红 (0-255)
     * @param g 绿 (0-255)
     * @param b 蓝 (0-255)
     */
    public static TradeCategory ofRGB(String id, String literal, int r, int g, int b) {
        int color = (0xFF << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
        return new TradeCategory(id, Component.literal(literal), 0, color);
    }

    /**
     * 从 RGB 值创建分类（带排序和颜色）。
     */
    public static TradeCategory ofRGB(String id, String literal, int sortOrder, int r, int g, int b) {
        int color = (0xFF << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
        return new TradeCategory(id, Component.literal(literal), sortOrder, color);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TradeCategory that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() { return id.hashCode(); }

    @Override
    public String toString() { return "TradeCategory[" + id + "]"; }
}

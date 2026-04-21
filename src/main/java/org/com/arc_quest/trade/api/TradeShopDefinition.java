package org.com.arc_quest.trade.api;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import org.com.arc_quest.quest.api.ICondition;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * 不可变的商店定义。
 * <p>
 * 由 {@link org.com.arc_quest.trade.builder.TradeShopBuilder} 构建。
 * 一个商店包含多个 {@link TradeEntry}，可按 {@link TradeCategory} 分类。
 */
public final class TradeShopDefinition {

    private final String shopId;
    private final Component displayName;
    @Nullable
    private final Component description;
    private final List<TradeCategory> categories;
    private final LinkedHashMap<String, TradeEntry> entries;
    @Nullable
    private final ICondition openCondition;
    private final boolean simpleMode;
    private final int themeColor;  // 商店主题色（ARGB）
    @Nullable
    private final SoundEvent openSound;  // 商店打开音效
    @Nullable
    private final SoundEvent closeSound;  // 商店关闭音效

    public TradeShopDefinition(String shopId,
                               Component displayName,
                               @Nullable Component description,
                               List<TradeCategory> categories,
                               LinkedHashMap<String, TradeEntry> entries,
                               @Nullable ICondition openCondition,
                               boolean simpleMode,
                               int themeColor,
                               @Nullable SoundEvent openSound,
                               @Nullable SoundEvent closeSound) {
        Objects.requireNonNull(shopId);
        Objects.requireNonNull(displayName);
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("TradeShop '" + shopId + "' must have at least one entry");
        }
        this.shopId = shopId;
        this.displayName = displayName;
        this.description = description;
        this.categories = Collections.unmodifiableList(categories);
        this.entries = new LinkedHashMap<>(entries);
        this.openCondition = openCondition;
        this.simpleMode = simpleMode;
        this.themeColor = themeColor;
        this.openSound = openSound;
        this.closeSound = closeSound;
    }

    public String getShopId() { return shopId; }
    public Component getDisplayName() { return displayName; }
    @Nullable public Component getDescription() { return description; }
    public List<TradeCategory> getCategories() { return categories; }

    public Collection<TradeEntry> getAllEntries() {
        return Collections.unmodifiableCollection(entries.values());
    }

    @Nullable
    public TradeEntry getEntry(String entryId) {
        return entries.get(entryId);
    }

    public Set<String> getEntryIds() {
        return Collections.unmodifiableSet(entries.keySet());
    }

    /**
     * 按分类筛选交易项
     */
    public List<TradeEntry> getEntriesByCategory(TradeCategory category) {
        if (category == null || category.equals(TradeCategory.ALL)) {
            return List.copyOf(entries.values());
        }
        List<TradeEntry> result = new ArrayList<>();
        for (TradeEntry entry : entries.values()) {
            if (category.equals(entry.getCategory())) {
                result.add(entry);
            }
        }
        return result;
    }

    @Nullable
    public ICondition getOpenCondition() { return openCondition; }

    /**
     * 是否为简易模式（弹窗而非完整窗口）
     */
    public boolean isSimpleMode() { return simpleMode; }

    /**
     * 获取商店主题色（ARGB 整数）
     */
    public int getThemeColor() { return themeColor; }

    /**
     * 获取商店打开音效
     */
    @Nullable
    public SoundEvent getOpenSound() { return openSound; }

    /**
     * 获取商店关闭音效
     */
    @Nullable
    public SoundEvent getCloseSound() { return closeSound; }

    /**
     * 检查开启条件
     */
    public boolean canOpen(ServerPlayer player,
                           Set<ResourceLocation> completedQuests,
                           Set<String> flags,
                           Map<String, Integer> variables) {
        if (openCondition == null) return true;
        return openCondition.test(player, completedQuests, flags, variables);
    }

    @Override
    public String toString() {
        return "TradeShop[" + shopId + ", " + entries.size() + " entries]";
    }
}

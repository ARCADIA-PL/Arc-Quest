package org.arcadia.arc_quest.trade.builder;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.quest.api.ICondition;
import org.arcadia.arc_quest.trade.api.TradeCategory;
import org.arcadia.arc_quest.trade.api.TradeEntry;
import org.arcadia.arc_quest.trade.api.TradeShopDefinition;
import org.arcadia.arc_quest.trade.api.TradeText;
import org.arcadia.arc_quest.trade.registry.TradeRegistry;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/**
 * 流式构建 {@link TradeShopDefinition}。
 *
 * <pre>{@code
 * TradeShopBuilder.create("blacksmith_shop")
 *     .displayName("铁匠铺")
 *     .description("出售武器和护甲")
 *     .category(TradeCategory.of("weapons", "武器"))
 *     .category(TradeCategory.of("armor", "护甲"))
 *     .entry(TradeEntryBuilder.create("iron_sword")
 *         .displayName("铁剑")
 *         .costItem(Items.EMERALD, 5)
 *         .rewardItem(Items.IRON_SWORD, 1)
 *         .category(weaponsCategory))
 *     .entry(TradeEntryBuilder.create("iron_chestplate")
 *         .displayName("铁胸甲")
 *         .costItem(Items.EMERALD, 12)
 *         .rewardItem(Items.IRON_CHESTPLATE, 1)
 *         .category(armorCategory))
 *     .buildAndRegister();
 * }</pre>
 */
public final class TradeShopBuilder {

    private final String shopId;
    private final List<TradeCategory> categories = new ArrayList<>();
    private final LinkedHashMap<String, TradeEntry> entries = new LinkedHashMap<>();
    private TradeText displayName;
    @Nullable
    private TradeText description;
    @Nullable
    private ICondition openCondition;
    private boolean simpleMode = false;
    private int themeColor = 0xE0C860;  // 默认金色
    @Nullable
    private SoundEvent openSound;
    @Nullable
    private SoundEvent closeSound;

    private TradeShopBuilder(String shopId) {
        this.shopId = Objects.requireNonNull(shopId);
    }

    /**
     * 创建 Builder，使用完整 ResourceLocation（推荐）。
     * <p>
     * 支持自定义命名空间，适合主模组和附属模组使用。
     *
     * @param id 完整的资源位置，如 "arc_quest:my_shop" 或 "my_mod:my_shop"
     */
    public static TradeShopBuilder create(ResourceLocation id) {
        return new TradeShopBuilder(id.toString());
    }

    /**
     * 创建 Builder，使用字符串 ID（自动解析命名空间）。
     * <p>
     * - 如果包含 ":"，则直接作为商店 ID
     * - 如果不包含 ":"，则默认使用 arc_quest 命名空间
     *
     * @param id 资源 ID，如 "arc_quest:my_shop" 或 "my_shop"
     */
    public static TradeShopBuilder create(String id) {
        String shopId;
        if (id.contains(":")) {
            shopId = id;
        } else {
            shopId = Arc_Quest.MOD_ID + ":" + id;
        }
        return new TradeShopBuilder(shopId);
    }

    // ════════════════════════════════════════
    //  基本属性
    // ════════════════════════════════════════

    public TradeShopBuilder displayName(String literal) {
        displayName = TradeText.literal(literal);
        return this;
    }

    public TradeShopBuilder displayName(Component name) {
        displayName = TradeText.component(name);
        return this;
    }

    public TradeShopBuilder displayName(TradeText name) {
        displayName = name;
        return this;
    }

    public TradeShopBuilder description(String literal) {
        description = TradeText.literal(literal);
        return this;
    }

    public TradeShopBuilder description(Component desc) {
        description = TradeText.component(desc);
        return this;
    }

    public TradeShopBuilder description(TradeText desc) {
        description = desc;
        return this;
    }

    /**
     * 标记为简易模式（弹窗而非完整窗口）
     */
    public TradeShopBuilder simpleMode() {
        simpleMode = true;
        return this;
    }

    /**
     * 设置开启条件
     */
    public TradeShopBuilder openCondition(ICondition condition) {
        openCondition = condition;
        return this;
    }

    // ════════════════════════════════════════
    //  主题色配置
    // ════════════════════════════════════════

    /**
     * 设置商店主题色（ARGB 整数）。
     * <p>
     * 示例：0xFFE0C860 (不透明金色), 0x80FF5555 (半透明红色)
     *
     * @param color ARGB 颜色值
     */
    public TradeShopBuilder themeColor(int color) {
        themeColor = color;
        return this;
    }

    /**
     * 从 RGB 值设置主题色（自动添加完全不透明 Alpha 通道）。
     *
     * @param r 红 (0-255)
     * @param g 绿 (0-255)
     * @param b 蓝 (0-255)
     */
    public TradeShopBuilder themeColorRGB(int r, int g, int b) {
        themeColor = (0xFF << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
        return this;
    }

    /**
     * 从 ChatFormatting 设置主题色。
     */
    public TradeShopBuilder themeColor(ChatFormatting formatting) {
        Integer rgb = formatting.getColor();
        if (rgb != null) {
            themeColor = 0xFF000000 | rgb;
        }
        return this;
    }

    // ════════════════════════════════════════
    //  音效配置
    // ════════════════════════════════════════

    /**
     * 设置商店打开时的音效。
     * <p>
     * 使用示例：
     * <pre>{@code
     * .openSound(SoundEvents.UI_BUTTON_CLICK.value())
     * }</pre>
     *
     * @param sound 音效事件
     * @return 当前构建器
     */
    public TradeShopBuilder openSound(SoundEvent sound) {
        openSound = sound;
        return this;
    }

    /**
     * 设置商店打开时的音效（支持 Holder.Reference）。
     * <p>
     * 使用示例：
     * <pre>{@code
     * .openSound(SoundEvents.UI_BUTTON_CLICK)
     * }</pre>
     *
     * @param sound 音效引用
     * @return 当前构建器
     */
    public TradeShopBuilder openSound(Holder.Reference<SoundEvent> sound) {
        openSound = sound.value();
        return this;
    }

    /**
     * 设置商店关闭时的音效。
     * <p>
     * 使用示例：
     * <pre>{@code
     * .closeSound(SoundEvents.UI_BUTTON_CLICK.value())
     * }</pre>
     *
     * @param sound 音效事件
     * @return 当前构建器
     */
    public TradeShopBuilder closeSound(SoundEvent sound) {
        closeSound = sound;
        return this;
    }

    /**
     * 设置商店关闭时的音效（支持 Holder.Reference）。
     * <p>
     * 使用示例：
     * <pre>{@code
     * .closeSound(SoundEvents.UI_BUTTON_CLICK)
     * }</pre>
     *
     * @param sound 音效引用
     * @return 当前构建器
     */
    public TradeShopBuilder closeSound(Holder.Reference<SoundEvent> sound) {
        closeSound = sound.value();
        return this;
    }

    // ════════════════════════════════════════
    //  分类
    // ════════════════════════════════════════

    public TradeShopBuilder category(TradeCategory category) {
        categories.add(category);
        return this;
    }

    public TradeShopBuilder category(String id, String name) {
        categories.add(TradeCategory.of(id, name));
        return this;
    }

    public TradeShopBuilder category(String id, String name, int sortOrder, int color) {
        categories.add(TradeCategory.of(id, name, sortOrder, color));
        return this;
    }

    /**
     * 从 ChatFormatting 创建分类。
     */
    public TradeShopBuilder categoryColor(String id, String name, ChatFormatting formatting) {
        categories.add(TradeCategory.ofColor(id, name, formatting));
        return this;
    }

    /**
     * 从 ChatFormatting 创建分类（带排序）。
     */
    public TradeShopBuilder categoryColor(String id, String name, int sortOrder, ChatFormatting formatting) {
        categories.add(TradeCategory.ofColor(id, name, sortOrder, formatting));
        return this;
    }

    /**
     * 从 RGB 值创建分类（自动添加完全不透明 Alpha 通道）。
     */
    public TradeShopBuilder categoryRGB(String id, String name, int r, int g, int b) {
        categories.add(TradeCategory.ofRGB(id, name, r, g, b));
        return this;
    }

    /**
     * 从 RGB 值创建分类（带排序）。
     */
    public TradeShopBuilder categoryRGB(String id, String name, int sortOrder, int r, int g, int b) {
        categories.add(TradeCategory.ofRGB(id, name, sortOrder, r, g, b));
        return this;
    }

    // ════════════════════════════════════════
    //  交易项
    // ════════════════════════════════════════

    /**
     * 添加已构建的 TradeEntry
     */
    public TradeShopBuilder entry(TradeEntry entry) {
        if (entries.containsKey(entry.getEntryId())) {
            throw new IllegalArgumentException(
                    "Duplicate entry id '" + entry.getEntryId() + "' in shop '" + shopId + "'");
        }
        entries.put(entry.getEntryId(), entry);
        return this;
    }

    /**
     * 添加 TradeEntryBuilder（自动 build）
     */
    public TradeShopBuilder entry(TradeEntryBuilder entryBuilder) {
        return entry(entryBuilder.build());
    }

    // ════════════════════════════════════════
    //  构建
    // ════════════════════════════════════════

    public TradeShopDefinition build() {
        if (displayName == null) {
            displayName = TradeText.literal(shopId);
        }
        if (entries.isEmpty()) {
            throw new IllegalStateException("TradeShop '" + shopId + "' has no entries");
        }
        return new TradeShopDefinition(
                shopId, displayName, description,
                List.copyOf(categories),
                new LinkedHashMap<>(entries),
                openCondition, simpleMode, themeColor,
                openSound, closeSound
        );
    }

    public TradeShopDefinition buildAndRegister() {
        TradeShopDefinition def = build();
        TradeRegistry.register(def);
        return def;
    }
}

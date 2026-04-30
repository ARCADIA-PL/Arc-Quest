package org.arcadia.arc_quest.trade.offer;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.arcadia.arc_quest.trade.api.CostShortfallLine;
import org.arcadia.arc_quest.trade.api.ITradeOffer;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.ToIntFunction;

/**
 * 物品交易物 —— 最常用的交易物类型。
 * <p>
 * 作为成本时：检查并扣除玩家背包中的物品。
 * 作为奖励时：给予玩家物品（背包满则掉落）。
 */
public final class ItemTradeOffer implements ITradeOffer {

    @Nullable
    private final Item item;
    @Nullable
    private final TagKey<Item> itemTag;
    private final ToIntFunction<ServerPlayer> countResolver;
    private final int previewCount;
    private final boolean isCost;
    @Nullable
    private final ResourceLocation customIcon;

    /**
     * @param item   物品类型
     * @param count  数量
     * @param isCost true=成本（扣除），false=奖励（给予）
     */
    public ItemTradeOffer(Item item, int count, boolean isCost) {
        this(item, count, isCost, null);
    }

    /**
     * @param item       物品类型
     * @param count      数量
     * @param isCost     true=成本（扣除），false=奖励（给予）
     * @param customIcon 自定义图标路径
     */
    public ItemTradeOffer(Item item, int count, boolean isCost, @Nullable ResourceLocation customIcon) {
        this(item, null, fixedCount(count), count, isCost, customIcon);
    }

    /**
     * @param itemTag    物品标签（如 minecraft:logs）
     * @param count      数量
     * @param isCost     true=成本（扣除），false=奖励（给予）
     * @param customIcon 自定义图标路径
     */
    public ItemTradeOffer(TagKey<Item> itemTag, int count, boolean isCost, @Nullable ResourceLocation customIcon) {
        this(null, itemTag, fixedCount(count), count, isCost, customIcon);
    }

    /**
     * @param itemTag       物品标签（如 minecraft:logs）
     * @param countResolver 根据玩家动态计算数量
     * @param previewCount  无玩家上下文时的展示数量
     * @param isCost        true=成本（扣除），false=奖励（给予）
     * @param customIcon    自定义图标路径
     */
    public ItemTradeOffer(TagKey<Item> itemTag,
                          ToIntFunction<ServerPlayer> countResolver,
                          int previewCount,
                          boolean isCost,
                          @Nullable ResourceLocation customIcon) {
        this(null, itemTag, countResolver, previewCount, isCost, customIcon);
    }

    /**
     * @param item          物品类型
     * @param countResolver 根据玩家动态计算数量
     * @param previewCount  无玩家上下文时的展示数量
     * @param isCost        true=成本（扣除），false=奖励（给予）
     * @param customIcon    自定义图标路径
     */
    public ItemTradeOffer(Item item,
                          ToIntFunction<ServerPlayer> countResolver,
                          int previewCount,
                          boolean isCost,
                          @Nullable ResourceLocation customIcon) {
        this(item, null, countResolver, previewCount, isCost, customIcon);
    }

    private ItemTradeOffer(@Nullable Item item,
                           @Nullable TagKey<Item> itemTag,
                           ToIntFunction<ServerPlayer> countResolver,
                           int previewCount,
                           boolean isCost,
                           @Nullable ResourceLocation customIcon) {
        if (item == null && itemTag == null) {
            throw new IllegalArgumentException("item and itemTag cannot both be null");
        }
        if (item != null && itemTag != null) {
            throw new IllegalArgumentException("item and itemTag cannot both be set");
        }
        this.item = item;
        this.itemTag = itemTag;
        this.countResolver = Objects.requireNonNull(countResolver);
        this.previewCount = validateCount(previewCount);
        this.isCost = isCost;
        this.customIcon = customIcon;
    }

    public static ItemTradeOffer cost(Item item, int count) {
        return new ItemTradeOffer(item, count, true);
    }

    public static ItemTradeOffer reward(Item item, int count) {
        return new ItemTradeOffer(item, count, false);
    }

    public static ItemTradeOffer costTag(TagKey<Item> itemTag, int count) {
        return new ItemTradeOffer(itemTag, count, true, null);
    }

    public static ItemTradeOffer rewardTag(TagKey<Item> itemTag, int count) {
        return new ItemTradeOffer(itemTag, count, false, null);
    }

    public static ItemTradeOffer cost(Item item, int previewCount, ToIntFunction<ServerPlayer> countResolver) {
        return new ItemTradeOffer(item, countResolver, previewCount, true, null);
    }

    public static ItemTradeOffer costTag(TagKey<Item> itemTag, int previewCount, ToIntFunction<ServerPlayer> countResolver) {
        return new ItemTradeOffer(itemTag, countResolver, previewCount, true, null);
    }

    private static ToIntFunction<ServerPlayer> fixedCount(int count) {
        int validated = validateCount(count);
        return player -> validated;
    }

    private static int validateCount(int count) {
        if (count < 1) throw new IllegalArgumentException("count must be >= 1");
        return count;
    }

    @Override
    public boolean canAfford(ServerPlayer player) {
        if (!isCost) return true;
        int required = resolveCount(player);
        return countOwned(player, required) >= required;
    }

    @Override
    public void execute(ServerPlayer player) {
        int required = resolveCount(player);
        if (isCost) {
            int remaining = required;
            for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (matches(stack)) {
                    int remove = Math.min(stack.getCount(), remaining);
                    stack.shrink(remove);
                    remaining -= remove;
                }
            }
        } else {
            Item rewardItem = Objects.requireNonNull(item, "reward mode requires explicit item");
            ItemStack stack = new ItemStack(rewardItem, required);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.5F, 1.0F);
        }
    }

    @Override
    public Component describe() {
        return Component.translatable("arc_quest.trade.item",
                displayName(), previewCount);
    }

    @Override
    public List<CostShortfallLine> buildShortfallLines(ServerPlayer player) {
        if (!isCost) {
            return List.of();
        }
        int required = resolveCount(player);
        int owned = countOwned(player, required);
        int missing = Math.max(0, required - owned);
        if (missing <= 0) {
            return List.of();
        }
        return List.of(new CostShortfallLine(displayName(), required, owned, missing));
    }

    @Nullable
    @Override
    public ResourceLocation getIcon() {
        return customIcon;
    }

    @Override
    public int getDisplayAmount() {
        return previewCount;
    }

    @Override
    public String getType() {
        return "item";
    }

    @Nullable
    public Item getItem() {
        return item;
    }

    @Nullable
    public TagKey<Item> getItemTag() {
        return itemTag;
    }

    public int getCount() {
        return previewCount;
    }

    public boolean isCost() {
        return isCost;
    }

    private boolean matches(ItemStack stack) {
        if (item != null) {
            return stack.is(item);
        }
        return itemTag != null && stack.is(itemTag);
    }

    private int resolveCount(ServerPlayer player) {
        return validateCount(countResolver.applyAsInt(player));
    }

    private int countOwned(ServerPlayer player, int required) {
        int found = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (matches(stack)) {
                found += stack.getCount();
                if (found >= required) {
                    return found;
                }
            }
        }
        return found;
    }

    private Component displayName() {
        if (item != null) {
            return item.getDescription();
        }
        return Component.literal("#" + Objects.requireNonNull(itemTag).location());
    }
}

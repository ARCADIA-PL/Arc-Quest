package org.com.arc_quest.trade.offer;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.com.arc_quest.trade.api.ITradeOffer;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * 物品交易物 —— 最常用的交易物类型。
 * <p>
 * 作为成本时：检查并扣除玩家背包中的物品。
 * 作为奖励时：给予玩家物品（背包满则掉落）。
 */
public final class ItemTradeOffer implements ITradeOffer {

    private final Item item;
    private final int count;
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
        Objects.requireNonNull(item);
        if (count < 1) throw new IllegalArgumentException("count must be >= 1");
        this.item = item;
        this.count = count;
        this.isCost = isCost;
        this.customIcon = customIcon;
    }

    public static ItemTradeOffer cost(Item item, int count) {
        return new ItemTradeOffer(item, count, true);
    }

    public static ItemTradeOffer reward(Item item, int count) {
        return new ItemTradeOffer(item, count, false);
    }

    @Override
    public boolean canAfford(ServerPlayer player) {
        if (!isCost) return true;
        int found = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item)) {
                found += stack.getCount();
                if (found >= count) return true;
            }
        }
        return false;
    }

    @Override
    public void execute(ServerPlayer player) {
        if (isCost) {
            int remaining = count;
            for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (stack.is(item)) {
                    int remove = Math.min(stack.getCount(), remaining);
                    stack.shrink(remove);
                    remaining -= remove;
                }
            }
        } else {
            ItemStack stack = new ItemStack(item, count);
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
                item.getDescription(), count);
    }

    @Nullable
    @Override
    public ResourceLocation getIcon() {
        return customIcon;
    }

    @Override
    public int getDisplayAmount() {
        return count;
    }

    @Override
    public String getType() {
        return "item";
    }

    public Item getItem() { return item; }
    public int getCount() { return count; }
    public boolean isCost() { return isCost; }
}

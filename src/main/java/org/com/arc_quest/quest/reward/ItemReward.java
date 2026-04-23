package org.com.arc_quest.quest.reward;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.com.arc_quest.quest.api.IReward;

import java.util.Objects;

public final class ItemReward implements IReward {

    private final Item item;
    private final int count;

    public ItemReward(Item item, int count) {
        Objects.requireNonNull(item);
        if (count < 1) throw new IllegalArgumentException("count must be >= 1");
        this.item = item;
        this.count = count;
    }

    @Override
    public void grant(ServerPlayer player) {
        ItemStack stack = new ItemStack(this.item, this.count);
        boolean added = player.getInventory().add(stack);
        if (!added) {
            // 背包满则掉落在地
            player.drop(stack, false);
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.5F, 1.0F);
    }

    @Override
    public String describe() {
        return "Item(" + ForgeRegistries.ITEMS.getKey(this.item) + " x" + this.count + ")";
    }

    /**
     * 获取奖励物品
     */
    public Item getItem() {
        return item;
    }

    /**
     * 获取奖励数量
     */
    public int getCount() {
        return count;
    }
}
package org.arcadia.arc_quest.dialogue.action;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.arcadia.arc_quest.quest.tracking.QuestEventManager;

import java.util.function.BiConsumer;

public final class PresetActions {

    private PresetActions() {
    }

    /*** 玩家获得物品*/
    public static void awardItem(ServerPlayer player, Item item, int count) {
        player.getInventory().add(new ItemStack(item, count));
    }

    /*** 玩家失去物品*/
    public static void giveItem(ServerPlayer player, Item item) {
        org.arcadia.arc_quest.inventory.PlayerItemAccess.remove(player, stack -> stack.is(item), 1, false);
    }

    /*** 玩家交换物品*/
    public static void exChangeItem(ServerPlayer player, Item item, Item item2) {
        var payment = org.arcadia.arc_quest.inventory.PlayerItemAccess.remove(player, stack -> stack.is(item), 1, false);
        try {
            org.arcadia.arc_quest.trade.runtime.InventoryDelivery.deliver(player, new ItemStack(item2));
        } catch (RuntimeException failure) {
            payment.rollback();
            throw failure;
        }
    }

    /*** 玩家获得效果*/
    public static void addEffects(ServerPlayer player, MobEffect mobEffect, int duration, int amplifier) {
        player.addEffect(new MobEffectInstance(mobEffect, duration, amplifier));
    }

    /*** 触发玩家与目标实体的任务交互标识*/
    public static void triggerInteraction(ServerPlayer player, String targetId) {
        ResourceLocation rl = ResourceLocation.tryParse(targetId);
        if (rl != null) {
            QuestEventManager.notifyInteract(player, rl);
        }
    }

    /**
     * 添加自定义事件处理器 - 允许用户定义玩家与目标实体的交互逻辑。
     *
     * @param player  玩家
     * @param target  目标实体（NPC）
     * @param handler 自定义事件处理器 (player, target) -> { ... }
     */
    public static void addEvents(ServerPlayer player, Entity target, BiConsumer<ServerPlayer, Entity> handler) {
        if (handler != null && target != null) {
            handler.accept(player, target);
        }
    }
}

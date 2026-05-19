package org.arcadia.arc_quest.dialogue.api;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.arcadia.arc_quest.dialogue.capability.DialogueNpcStateManager;
import org.arcadia.arc_quest.dialogue.runtime.DialogueSessionManager;

import javax.annotation.Nullable;

/**
 * NPC 对话接口 —— 实体直接实现此接口即可获得对话能力。
 * <p>
 * 最小实现只需重写 {@link #getDialogueId(ServerPlayer)}：
 * <pre>{@code
 * public class BlacksmithEntity extends Villager implements IDialogueNpc {
 *     @Override
 *     public String getDialogueId(ServerPlayer player) {
 *         return "arc_quest:blacksmith_greeting";
 *     }
 * }
 * }</pre>
 * <p>
 * 高级用法：根据任务/flag 状态动态选择对话，传递上下文：
 * <pre>{@code
 * @Override
 * public String getDialogueId(ServerPlayer player) {
 *     if (hasFlag(player, "quest_done")) return "arc_quest:elder_idle";
 *     return "arc_quest:elder_greeting";
 * }
 *
 * @Override
 * public DialogueContext buildDialogueContext(ServerPlayer player) {
 *     return new DialogueContext()
 *         .put("playerName", player.getName().getString())
 *         .put("reputation", getReputation(player));
 * }
 * }</pre>
 * <p>
 * 对话触发由 {@link org.arcadia.arc_quest.dialogue.runtime.NpcDialogueHandler}
 * 自动监听 {@code PlayerInteractEvent.EntityInteract}。
 */
public interface IDialogueNpc {

    // ═══════════════════════════════════════════════════════
    //  必须实现
    // ═══════════════════════════════════════════════════════

    /**
     * 根据玩家状态返回对话树 ID。
     * <p>
     * 返回 {@code null} 表示当前不可对话（例如任务进行中）。
     *
     * @param player 发起交互的玩家
     * @return 注册在 {@link org.arcadia.arc_quest.dialogue.registry.DialogueRegistry} 中的对话 ID，或 null
     */
    @Nullable
    String getDialogueId(ServerPlayer player);

    // ═══════════════════════════════════════════════════════
    //  可选重写
    // ═══════════════════════════════════════════════════════

    /**
     * 获取此接口的宿主实体。
     * <p>
     * 默认实现假定 {@code this} 即为 {@link Entity}。
     * 如果你的 NPC 系统是组件式的（接口不在 Entity 子类上），需要重写此方法。
     */
    default Entity asEntity() {
        return (Entity) this;
    }

    /**
     * 是否允许与该玩家对话。
     * <p>
     * 默认：玩家非潜行 + 在对话距离内。
     */
    default boolean canDialogueWith(Player player) {
        return !player.isCrouching()
                && player.isAlive()
                && player.distanceTo(asEntity()) <= getMaxDialogueDistance();
    }

    /**
     * 最大对话触发距离（方块数）。
     */
    default double getMaxDialogueDistance() {
        return 5.0;
    }

    /**
     * 对话期间是否注视玩家。
     */
    default boolean shouldLookAtPlayer() {
        return true;
    }

    /**
     * 对话期间是否停止移动。
     */
    default boolean shouldStopMoving() {
        return true;
    }

    /**
     * 构建传递给对话会话的运行时上下文。
     * <p>
     * 上下文中的键值对可在对话文本中通过 {@code {key}} 引用。
     * 默认实现添加 {@code playerName}。
     */
    default DialogueContext buildDialogueContext(ServerPlayer player) {
        return new DialogueContext()
                .put("playerName", player.getName().getString());
    }

    /**
     * NPC 对话显示名称（用于 speakerName 字段的 fallback）。
     */
    default Component getDialogueDisplayName() {
        return asEntity().getDisplayName();
    }

    /**
     * 发起对话。由 {@link org.arcadia.arc_quest.dialogue.runtime.NpcDialogueHandler} 调用。
     * <p>
     * 流程：检查可对话 → 获取对话ID → 构建上下文 → 委托 SessionManager
     */
    default void startDialogueWith(ServerPlayer player) {
        if (!canDialogueWith(player)) return;

        String dialogueId = getDialogueId(player);
        if (dialogueId == null) return;

        Entity self = asEntity();

        DialogueNpcPatch patch = DialogueNpcStateManager.get(self);
        if (patch.isConversing()) return;

        DialogueContext context = buildDialogueContext(player);
        context.put("npcName", getDialogueDisplayName().getString());
        context.put("defaultNpc", getDialogueDisplayName().getString());

        DialogueSessionManager.INSTANCE.startDialogue(player, self, dialogueId, context);
    }
}
package org.com.arc_quest.dialogue.api;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.com.arc_quest.dialogue.registry.DialogueActionTypes;
import org.com.arc_quest.dialogue.runtime.DialogueSession;
import org.com.arc_quest.quest.api.PhaseDefinition;
import org.com.arc_quest.quest.api.QuestDefinition;
import org.com.arc_quest.quest.api.QuestState;
import org.com.arc_quest.quest.capability.IQuestCapability;
import org.com.arc_quest.quest.capability.QuestCapabilityProvider;
import org.com.arc_quest.quest.capability.QuestRuntimeData;
import org.com.arc_quest.quest.logic.QuestProgressHandler;
import org.com.arc_quest.quest.registry.QuestRegistry;
import org.com.arc_quest.quest.tracking.QuestEventManager;
import org.slf4j.Logger;

/**
 * 对话选择触发的服务端动作。
 *
 * <pre>
 * JSON 示例:
 * { "type": "START_QUEST",    "quest_id": "rescue_villager" }
 * { "type": "COMPLETE_QUEST", "quest_id": "rescue_villager" }
 * { "type": "ADVANCE_PHASE",  "quest_id": "rescue_villager" }
 * { "type": "GIVE_XP",        "amount": 50 }
 * { "type": "GIVE_ITEM",      "item": "minecraft:diamond", "count": 3 }
 * { "type": "NOTIFY_TALK",    "npc_id": "elder" }
 * { "type": "RUN_COMMAND",    "command": "/effect give @s strength 60 1" }
 * { "type": "SET_FLAG",       "flag": "talked_to_elder" }
 * { "type": "SET_VARIABLE",   "key": "reputation", "value": 10 }
 * { "type": "CUSTOM",         "type_id": "mymod:give_coins", "data": {...} }
 * </pre>
 */
public sealed interface DialogueAction {

    Logger LOGGER = LogUtils.getLogger();

    /**
     * 在服务端执行动作。
     */
    void execute(ServerPlayer player);

    /**
     * 在服务端执行动作（带会话上下文）。
     * <p>
     * 默认实现委托给 {@link #execute(ServerPlayer)}。
     * {@link Custom} 类型重写此方法以获取会话上下文。
     */
    default void execute(ServerPlayer player, DialogueSession session) {
        execute(player);
    }

    // ═══════════════════════════════════════════════════════
    //  原有动作类型
    // ═══════════════════════════════════════════════════════

    /** 开始任务。 */
    record StartQuest(String questId) implements DialogueAction {
        @Override
        public void execute(ServerPlayer player) {
            QuestProgressHandler.acceptQuest(player, questId);
        }
    }

    /** 强制完成任务。 */
    record CompleteQuest(String questId) implements DialogueAction {
        @Override
        public void execute(ServerPlayer player) {
            IQuestCapability cap = player.getCapability(QuestCapabilityProvider.QUEST_CAP).orElse(null);
            if (cap != null) {
                QuestRuntimeData data = cap.getActiveQuest(questId);
                if (data != null) {
                    data.setState(QuestState.COMPLETED);
                    cap.markCompleted(questId);
                }
            }
        }
    }

    /** 推进任务到下一阶段。 */
    record AdvancePhase(String questId) implements DialogueAction {
        @Override
        public void execute(ServerPlayer player) {
            ResourceLocation rl = ResourceLocation.tryParse(questId);
            QuestDefinition def = rl != null ? QuestRegistry.get(rl) : null;
            if (def == null) return;

            IQuestCapability cap = player.getCapability(QuestCapabilityProvider.QUEST_CAP).orElse(null);
            if (cap == null) return;

            QuestRuntimeData data = cap.getActiveQuest(questId);
            if (data == null || data.getState() != QuestState.ACTIVE) return;

            PhaseDefinition currentPhase = def.getPhase(data.getCurrentPhaseId());
            if (currentPhase == null) return;

            String nextPhaseId = def.evaluateNextPhase(currentPhase,
                    cap.getCompletedQuests().stream().map(ResourceLocation::parse)
                            .collect(java.util.stream.Collectors.toSet()),
                    cap.getAllFlags(), cap.getAllVariables());

            if (nextPhaseId != null) {
                PhaseDefinition nextPhase = def.getPhase(nextPhaseId);
                if (nextPhase != null) {
                    data.setCurrentPhaseId(nextPhaseId);
                    data.resetObjectives(nextPhase.getObjectives().size());
                }
            }
        }
    }

    /** 给予经验。 */
    record GiveXp(int amount) implements DialogueAction {
        @Override
        public void execute(ServerPlayer player) {
            player.giveExperiencePoints(amount);
            LOGGER.debug("[Dialogue] Gave {} XP to {}", amount, player.getName().getString());
        }
    }

    /** 给予物品。 */
    record GiveItem(String itemId, int count) implements DialogueAction {
        @Override
        public void execute(ServerPlayer player) {
            ResourceLocation rl = ResourceLocation.tryParse(itemId);
            if (rl != null) {
                var item = ForgeRegistries.ITEMS.getValue(rl);
                if (item != null) {
                    ItemStack stack = new ItemStack(item, count);
                    if (!player.getInventory().add(stack)) {
                        player.drop(stack, false);
                    }
                } else {
                    LOGGER.warn("[Dialogue] Unknown item: {}", itemId);
                }
            }
        }
    }

    /** 通知对话目标（触发 TALK 类型目标检测）。 */
    record NotifyTalk(String npcId) implements DialogueAction {
        @Override
        public void execute(ServerPlayer player) {
            ResourceLocation rl = ResourceLocation.tryParse(npcId);
            if (rl != null) {
                QuestEventManager.notifyTalk(player, rl);
            }
        }
    }

    /** 通知交互目标（触发 INTERACT 类型目标检测）。 */
    record NotifyInteract(String targetId) implements DialogueAction {
        @Override
        public void execute(ServerPlayer player) {
            ResourceLocation rl = ResourceLocation.tryParse(targetId);
            if (rl != null) {
                QuestEventManager.notifyInteract(player, rl);
            }
        }
    }

    /** 空操作（仅关闭对话）。 */
    record NoOp() implements DialogueAction {
        @Override
        public void execute(ServerPlayer player) {}
    }

    /** 关闭对话（由客户端处理）。 */
    record Close() implements DialogueAction {
        @Override
        public void execute(ServerPlayer player) {
            // 关闭动作用于标记对话结束，实际关闭由客户端处理
        }
    }

    // ═══════════════════════════════════════════════════════
    //  新增动作类型
    // ═══════════════════════════════════════════════════════

    /**
     * 以执行者身份执行服务端命令。
     * <p>
     * 支持 {@code @s} 代表当前玩家。命令文本中的 {@code %player%}
     * 和 {@code %npc%} 会通过 {@link DialogueSession#processText} 替换。
     *
     * <pre>
     * { "type": "RUN_COMMAND", "command": "/effect give @s minecraft:strength 600 1" }
     * </pre>
     */
    record RunCommand(String command) implements DialogueAction {
        @Override
        public void execute(ServerPlayer player) {
            MinecraftServer server = player.getServer();
            if (server != null) {
                String cmd = command.startsWith("/") ? command.substring(1) : command;
                server.getCommands().performPrefixedCommand(
                        player.createCommandSourceStack().withSuppressedOutput(), cmd);
                LOGGER.debug("[Dialogue] Executed command '{}' for {}",
                        cmd, player.getName().getString());
            }
        }

        @Override
        public void execute(ServerPlayer player, DialogueSession session) {
            MinecraftServer server = player.getServer();
            if (server != null) {
                // 通过 session 做变量替换后再执行
                String resolved = session != null ? session.processText(command) : command;
                String cmd = resolved.startsWith("/") ? resolved.substring(1) : resolved;
                server.getCommands().performPrefixedCommand(
                        player.createCommandSourceStack().withSuppressedOutput(), cmd);
                LOGGER.debug("[Dialogue] Executed command '{}' for {}",
                        cmd, player.getName().getString());
            }
        }
    }

    /**
     * 设置任务系统 flag（布尔标记）。
     *
     * <pre>
     * { "type": "SET_FLAG", "flag": "talked_to_elder" }
     * </pre>
     */
    record SetFlag(String flagName) implements DialogueAction {
        @Override
        public void execute(ServerPlayer player) {
            IQuestCapability cap = player.getCapability(QuestCapabilityProvider.QUEST_CAP).orElse(null);
            if (cap != null) {
                cap.setFlag(flagName);
                LOGGER.debug("[Dialogue] Set flag '{}' for {}", flagName, player.getName().getString());
            }
        }
    }

    /**
     * 设置任务系统变量（整数值）。
     *
     * <pre>
     * { "type": "SET_VARIABLE", "key": "reputation", "value": 10 }
     * </pre>
     */
    record SetVariable(String key, int value) implements DialogueAction {
        @Override
        public void execute(ServerPlayer player) {
            IQuestCapability cap = player.getCapability(QuestCapabilityProvider.QUEST_CAP).orElse(null);
            if (cap != null) {
                cap.setVariable(key, value);
                LOGGER.debug("[Dialogue] Set variable '{}' = {} for {}",
                        key, value, player.getName().getString());
            }
        }
    }

    /**
     * 外部模组自定义动作。
     * <p>
     * 通过 {@link DialogueActionTypes#register} 注册处理器，
     * 再在对话中引用。
     *
     * <pre>
     * // 注册
     * DialogueActionTypes.register(
     *     new ResourceLocation("economy", "give_coins"),
     *     (player, session, data) -> EconomyAPI.addCoins(player, data.getInt("amount"))
     * );
     *
     * // 使用
     * CompoundTag data = new CompoundTag();
     * data.putInt("amount", 500);
     * new DialogueAction.Custom(new ResourceLocation("economy", "give_coins"), data)
     * </pre>
     */
    record Custom(ResourceLocation typeId, CompoundTag data) implements DialogueAction {
        @Override
        public void execute(ServerPlayer player) {
            DialogueActionTypes.execute(typeId, player, null, data != null ? data : new CompoundTag());
        }

        @Override
        public void execute(ServerPlayer player, DialogueSession session) {
            DialogueActionTypes.execute(typeId, player, session, data != null ? data : new CompoundTag());
        }
    }
}
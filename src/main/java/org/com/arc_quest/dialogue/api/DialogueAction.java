package org.com.arc_quest.dialogue.api;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
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
 * </pre>
 */
public sealed interface DialogueAction {

    Logger LOGGER = LogUtils.getLogger();

    /**
     * 在服务端执行动作。
     */
    void execute(ServerPlayer player);

    // ═══════════════════════════════════════════════════════
    //  具体动作类型
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
            // 需要获取 QuestDefinition 并计算下一阶段
            ResourceLocation rl = ResourceLocation.tryParse(questId);
            QuestDefinition def = rl != null ? QuestRegistry.get(rl) : null;
            if (def == null) return;

            IQuestCapability cap = player.getCapability(QuestCapabilityProvider.QUEST_CAP).orElse(null);
            if (cap == null) return;

            QuestRuntimeData data = cap.getActiveQuest(questId);
            if (data == null || data.getState() != QuestState.ACTIVE) return;

            PhaseDefinition currentPhase = def.getPhase(data.getCurrentPhaseId());
            if (currentPhase == null) return;

            // 评估下一阶段
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
}
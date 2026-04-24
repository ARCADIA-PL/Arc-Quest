package org.com.arc_quest.dialogue.api;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
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
import org.com.arc_quest.trade.api.TradeShopDefinition;
import org.com.arc_quest.trade.gacha.api.GachaShopDefinition;
import org.com.arc_quest.trade.gacha.registry.GachaRegistry;
import org.com.arc_quest.trade.gacha.runtime.GachaScreenOpener;
import org.com.arc_quest.trade.network.C2SRequestTradePacket;
import org.com.arc_quest.trade.registry.TradeRegistry;
import org.com.arc_quest.util.CommandExecutor;
import org.slf4j.Logger;

import java.util.function.BiConsumer;

/**
 * 对话选择触发的服务端动作。
 */
public sealed interface DialogueAction {

    Logger LOGGER = LogUtils.getLogger();


    void execute(ServerPlayer player);


    default void execute(ServerPlayer player, DialogueSession session) {
        execute(player);
    }


    /**
     * 开始任务。
     */
    record StartQuest(String questId) implements DialogueAction {
        @Override
        public void execute(ServerPlayer player) {
            QuestProgressHandler.acceptQuest(player, questId);
        }
    }

    /**
     * 强制完成任务。
     */
    record CompleteQuest(String questId) implements DialogueAction {
        @Override
        public void execute(ServerPlayer player) {
            IQuestCapability cap = QuestCapabilityProvider.getOrNull(player);
            QuestRuntimeData data = cap.getActiveQuest(questId);
            if (data != null) {
                data.setState(QuestState.COMPLETED);
                cap.markCompleted(questId);
            }
        }
    }

    /**
     * 推进任务到下一阶段。
     */
    record AdvancePhase(String questId) implements DialogueAction {
        @Override
        public void execute(ServerPlayer player) {
            ResourceLocation rl = ResourceLocation.tryParse(questId);
            QuestDefinition def = rl != null ? QuestRegistry.get(rl) : null;
            if (def == null) return;

            IQuestCapability cap = QuestCapabilityProvider.getOrNull(player);

            QuestRuntimeData data = cap.getActiveQuest(questId);
            if (data == null || data.getState() != QuestState.ACTIVE) return;

            PhaseDefinition currentPhase = def.getPhase(data.getCurrentPhaseId());
            if (currentPhase == null) return;

            String nextPhaseId = def.evaluateNextPhase(player, currentPhase,
                    cap.getCompletedQuestLocations(),
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

    /**
     * 给予经验。
     */
    record GiveXp(int amount) implements DialogueAction {
        @Override
        public void execute(ServerPlayer player) {
            player.giveExperiencePoints(amount);
            LOGGER.debug("[Dialogue] Gave {} XP to {}", amount, player.getName().getString());
        }
    }

    /**
     * 给予物品。
     */
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

    /**
     * 通知对话目标（触发 TALK 类型目标检测）。
     */
    record NotifyTalk(String npcId) implements DialogueAction {
        @Override
        public void execute(ServerPlayer player) {
            ResourceLocation rl = ResourceLocation.tryParse(npcId);
            if (rl != null) {
                QuestEventManager.notifyTalk(player, rl);
            }
        }
    }

    /**
     * 通知交互目标（触发 INTERACT 类型目标检测）。
     */
    record NotifyInteract(String targetId) implements DialogueAction {
        @Override
        public void execute(ServerPlayer player) {
            ResourceLocation rl;
            rl = ResourceLocation.tryParse(targetId);
            if (rl != null) {
                QuestEventManager.notifyInteract(player, rl);
            }
        }
    }

    /**
     * 空操作（仅关闭对话）。
     */
    record NoOp() implements DialogueAction {
        @Override
        public void execute(ServerPlayer player) {
        }
    }

    /**
     * 关闭对话（由客户端处理）。
     */
    record Close() implements DialogueAction {
        @Override
        public void execute(ServerPlayer player) {
            // 关闭动作用于标记对话结束，实际关闭由客户端处理
        }
    }


    record RunCommand(String command) implements DialogueAction {
        @Override
        public void execute(ServerPlayer player) {
            CommandExecutor.runAsPlayer(player, command);
            LOGGER.debug("[Dialogue] Executed command '{}' for {}", command, player.getName().getString());
        }

        @Override
        public void execute(ServerPlayer player, DialogueSession session) {
            String resolved = session != null ? session.processText(command) : command;
            CommandExecutor.runAsPlayer(player, resolved);
            LOGGER.debug("[Dialogue] Executed command '{}' for {}", resolved, player.getName().getString());
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
            IQuestCapability cap = QuestCapabilityProvider.getOrNull(player);
            cap.setFlag(flagName);
            LOGGER.debug("[Dialogue] Set flag '{}' for {}", flagName, player.getName().getString());
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
            IQuestCapability cap = QuestCapabilityProvider.getOrNull(player);
            cap.setVariable(key, value);
            LOGGER.debug("[Dialogue] Set variable '{}' = {} for {}",
                    key, value, player.getName().getString());
        }
    }


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

    /**
     * Lambda 动作 - 允许用户自定义回调逻辑。
     * <p>
     * 注意：此动作不支持序列化，仅用于代码驱动的对话树。
     */
    record OpenTrade(String shopId, String restoreNodeId) implements DialogueAction {
        public OpenTrade(String shopId) {
            this(shopId, null);
        }

        @Override
        public void execute(ServerPlayer player) {
            TradeShopDefinition shop = TradeRegistry.get(shopId);
            if (shop == null) {
                LOGGER.warn("[Dialogue] Unknown trade shop: {}", shopId);
                return;
            }
            C2SRequestTradePacket.handleServerOpenFromDialogue(player, shop, false, restoreNodeId);
        }
        @Override
        public void execute(ServerPlayer player, DialogueSession session) { 
            execute(player); 
        }
    }

    record OpenSimpleTrade(String shopId, String restoreNodeId) implements DialogueAction {
        public OpenSimpleTrade(String shopId) {
            this(shopId, null);
        }

        @Override
        public void execute(ServerPlayer player) {
            TradeShopDefinition shop = TradeRegistry.get(shopId);
            if (shop == null) {
                LOGGER.warn("[Dialogue] Unknown trade shop: {}", shopId);
                return;
            }
            C2SRequestTradePacket.handleServerOpenFromDialogue(player, shop, true, restoreNodeId);
        }
        @Override
        public void execute(ServerPlayer player, DialogueSession session) { 
            execute(player); 
        }
    }

    record OpenGacha(String shopId, String restoreNodeId) implements DialogueAction {
        public OpenGacha(String shopId) {
            this(shopId, null);
        }

        @Override
        public void execute(ServerPlayer player) {
            GachaShopDefinition shop = GachaRegistry.get(shopId);
            if (shop == null) {
                LOGGER.warn("[Dialogue] Unknown gacha shop: {}", shopId);
                return;
            }
            IQuestCapability cap = QuestCapabilityProvider.getOrNull(player);
            if (cap == null) {
                LOGGER.warn("[Dialogue] Missing quest capability while opening gacha: {}", shopId);
                return;
            }
            GachaScreenOpener.openGachaScreen(player, shop, cap, restoreNodeId);
        }
        @Override
        public void execute(ServerPlayer player, DialogueSession session) {
            execute(player);
        }
    }
    record LambdaAction(BiConsumer<ServerPlayer, Entity> handler, Entity target) implements DialogueAction {

        public LambdaAction(BiConsumer<ServerPlayer, Entity> handler) {
            this(handler, null);
        }

        @Override
        public void execute(ServerPlayer player) {
            if (handler != null) {
                handler.accept(player, target);
            }
        }

        @Override
        public void execute(ServerPlayer player, DialogueSession session) {
            execute(player);
        }
    }
}
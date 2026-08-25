package org.arcadia.arc_quest.dialogue.api;
import org.arcadia.arc_quest.util.log.ArcQuestLog;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.arcadia.arc_quest.dialogue.registry.DialogueActionTypes;
import org.arcadia.arc_quest.dialogue.runtime.DialogueSession;
import org.arcadia.arc_quest.guide.registry.GuideRegistry;
import org.arcadia.arc_quest.guide.runtime.GuideTriggerService;
import org.arcadia.arc_quest.guide.runtime.GuideUnlockService;
import org.arcadia.arc_quest.quest.api.PhaseDefinition;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.api.QuestState;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.logic.QuestProgressHandler;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.quest.service.TrackedQuestService;
import org.arcadia.arc_quest.quest.tracking.QuestEventManager;
import org.arcadia.arc_quest.trade.api.TradeShopDefinition;
import org.arcadia.arc_quest.trade.gacha.api.GachaShopDefinition;
import org.arcadia.arc_quest.trade.gacha.registry.GachaRegistry;
import org.arcadia.arc_quest.trade.gacha.runtime.GachaScreenOpener;
import org.arcadia.arc_quest.trade.network.C2SRequestTradePacket;
import org.arcadia.arc_quest.trade.registry.TradeRegistry;
import org.arcadia.arc_quest.util.CommandExecutor;

import java.util.function.BiConsumer;

/**
 * 对话选择触发的服务端动作。
 */
public sealed interface DialogueAction {
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
            ArcQuestPlayer data = ArcQuestPlayerManager.get(player);
            QuestRuntimeData qdata = data.getActiveQuest(questId);
            if (qdata != null) {
                qdata.setState(QuestState.COMPLETED);
                data.markCompleted(questId);
                TrackedQuestService.onQuestTerminated(player);
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

            ArcQuestPlayer data = ArcQuestPlayerManager.get(player);

            if (data == null) return;
            QuestRuntimeData qdata = data.getActiveQuest(questId);
            if (qdata == null || qdata.getState() != QuestState.ACTIVE) return;

            PhaseDefinition currentPhase = def.getPhase(qdata.getCurrentPhaseId());
            if (currentPhase == null) return;

            String nextPhaseId = def.evaluateNextPhase(player, currentPhase,
                    data.getCompletedQuestLocations(),
                    data.getAllFlags(), data.getAllVariables());

            if (nextPhaseId != null) {
                PhaseDefinition nextPhase = def.getPhase(nextPhaseId);
                if (nextPhase != null) {
                    qdata.setCurrentPhaseId(nextPhaseId);
                    qdata.resetObjectives(nextPhase.getObjectives().size());
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
            ArcQuestLog.debug(ArcQuestLog.Category.DIALOGUE, "Gave {} XP to {}", amount, player.getName().getString());
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
                    ArcQuestLog.warn(ArcQuestLog.Category.DIALOGUE, "Unknown item: {}", itemId);
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
            ArcQuestLog.debug(ArcQuestLog.Category.DIALOGUE, "Executed command '{}' for {}", command, player.getName().getString());
        }

        @Override
        public void execute(ServerPlayer player, DialogueSession session) {
            String resolved = session != null ? session.processText(command) : command;
            CommandExecutor.runAsPlayer(player, resolved);
            ArcQuestLog.debug(ArcQuestLog.Category.DIALOGUE, "Executed command '{}' for {}", resolved, player.getName().getString());
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
            ArcQuestPlayer data = ArcQuestPlayerManager.get(player);
            data.setFlag(flagName);
            ArcQuestLog.debug(ArcQuestLog.Category.DIALOGUE, "Set flag '{}' for {}", flagName, player.getName().getString());
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
            ArcQuestPlayer data = ArcQuestPlayerManager.get(player);
            data.setVariable(key, value);
            ArcQuestLog.debug(ArcQuestLog.Category.DIALOGUE, "Set variable '{}' = {} for {}",
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

    /** 相关处理说明。 */
    record OpenGuide(String guideId, int initialPage, boolean markSeenOnClose) implements DialogueAction {
        public OpenGuide(String guideId) {
            this(guideId, 0, true);
        }

        @Override
        public void execute(ServerPlayer player) {
            ResourceLocation id = ResourceLocation.tryParse(guideId);
            if (id == null || !new GuideTriggerService().open(player, id, initialPage, markSeenOnClose)) {
                ArcQuestLog.warn(ArcQuestLog.Category.DIALOGUE, "Unknown guide: {}", guideId);
            }
        }
    }

    /** 相关处理说明。 */
    record UnlockGuide(String guideId) implements DialogueAction {
        @Override
        public void execute(ServerPlayer player) {
            ResourceLocation id = ResourceLocation.tryParse(guideId);
            if (id == null || !new GuideUnlockService().grant(player, id)) {
                if (id == null || GuideRegistry.get(id) == null) {
                    ArcQuestLog.warn(ArcQuestLog.Category.DIALOGUE, "Unknown guide: {}", guideId);
                }
            }
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
                ArcQuestLog.warn(ArcQuestLog.Category.DIALOGUE, "Unknown trade shop: {}", shopId);
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
                ArcQuestLog.warn(ArcQuestLog.Category.DIALOGUE, "Unknown trade shop: {}", shopId);
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
                ArcQuestLog.warn(ArcQuestLog.Category.DIALOGUE, "Unknown gacha shop: {}", shopId);
                return;
            }
            ArcQuestPlayer data = ArcQuestPlayerManager.get(player);
            if (data == null) {
                ArcQuestLog.warn(ArcQuestLog.Category.DIALOGUE, "Missing quest capability while opening gacha: {}", shopId);
                return;
            }
            GachaScreenOpener.openGachaScreen(player, shop, data, restoreNodeId);
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

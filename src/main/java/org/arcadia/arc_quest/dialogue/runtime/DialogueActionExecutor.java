package org.arcadia.arc_quest.dialogue.runtime;
import org.arcadia.arc_quest.util.log.ArcQuestLog;

import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.dialogue.api.CooldownType;
import org.arcadia.arc_quest.dialogue.api.DialogueAction;
import org.arcadia.arc_quest.dialogue.api.DialogueChoice;
import org.arcadia.arc_quest.dialogue.api.DialogueNode;

/**
 * 负责对话会话中具体动作的执行与状态跃迁逻辑（SRP 拆分）。
 */
public final class DialogueActionExecutor {
    private DialogueActionExecutor() {
    }

    /**
     * 检查目标节点是否可用（包含一次性检查和冷却检查）。
     * <p>
     * <b>纯查询方法，不修改任何状态</b>。调用方在确认要跳转后自行记录访问。
     */
    public static boolean checkNodeAvailable(DialogueSession session, DialogueNode node,
                                             DialogueProgressStore progress,
                                             DialogueProgressStore.TimeSnapshot ts) {
        String namespace = session.getNamespace();

        // 一次性检查
        ProgressKey nodeKey = ProgressKey.ofNode(namespace, node.nodeId());
        if (!node.repeatable() && progress.hasVisitedNode(nodeKey)) {
            ArcQuestLog.debug(ArcQuestLog.Category.DIALOGUE, "One-time node '{}' already visited.", node.nodeId());
            return false;
        }

        // 冷却检查
        if (node.cooldownType() != CooldownType.NONE) {
            boolean onCooldown = progress.isNodeOnCooldown(
                    namespace, node.nodeId(),
                    node.cooldownType(), (int) node.cooldownSeconds(), node.resetTimeTicks(),
                    ts.realTime(), ts.gameTime(), ts.dayTime());
            if (onCooldown) {
                ArcQuestLog.debug(ArcQuestLog.Category.DIALOGUE, "Node '{}' on cooldown.", node.nodeId());
                return false;
            }
        }

        // 注意：不再在此处记录访问，由调用方确认跳转后自行记录
        return true;
    }

    /**
     * 检查选项是否可用（包含一次性检查和冷却检查）。
     */
    public static boolean checkChoiceAvailable(DialogueSession session, DialogueChoice choice, int choiceIndex,
                                               DialogueProgressStore progress,
                                               DialogueProgressStore.TimeSnapshot ts) {
        String namespace = session.getNamespace();
        String nodeId = session.getCurrentNode().nodeId();

        ArcQuestLog.debug(ArcQuestLog.Category.DIALOGUE, "Checking choice: node={}, idx={}, type={}, seconds={}",
                nodeId, choiceIndex, choice.cooldownType(), choice.cooldownSeconds());

        // 一次性检查
        if (!choice.repeatable() && progress.hasSelectedChoice(namespace, nodeId, choiceIndex)) {
            ArcQuestLog.debug(ArcQuestLog.Category.DIALOGUE, "One-time choice [{}/{}] already selected.", nodeId, choiceIndex);
            return false;
        }

        // 冷却检查
        if (choice.cooldownType() != CooldownType.NONE) {
            ProgressKey choiceKey = ProgressKey.ofChoice(namespace, nodeId, choiceIndex);

            // GAME_TICK 类型：先检测时间回退，若回退则清除记录并跳过冷却
            if (choice.cooldownType() == CooldownType.GAME_TICK) {
                if (progress.clearIfTimeRegressed(choiceKey, ts.dayTime())) {
                    return true;
                }
            }

            boolean onCooldown = progress.isChoiceOnCooldown(
                    namespace, nodeId, choiceIndex,
                    choice.cooldownType(), (int) choice.cooldownSeconds(), choice.resetTimeTicks(),
                    ts.realTime(), ts.gameTime(), ts.dayTime());

            ArcQuestLog.debug(ArcQuestLog.Category.DIALOGUE, "Choice [{}/{}] onCooldown={}", nodeId, choiceIndex, onCooldown);

            return !onCooldown;
        }

        return true;
    }

    /**
     * 安全执行配置的所有 Action。
     */
    public static void executeActions(ServerPlayer player, DialogueSession session, DialogueChoice choice) {
        for (DialogueAction action : choice.actions()) {
            try {
                action.execute(player, session);
            } catch (Exception e) {
                ArcQuestLog.error(ArcQuestLog.Category.DIALOGUE, "Error executing action {} in session {}",
                        action.getClass().getSimpleName(), session.getSessionId(), e);
            }
        }
    }
}

package org.com.arc_quest.dialogue.runtime;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import org.com.arc_quest.dialogue.api.CooldownType;
import org.com.arc_quest.dialogue.api.DialogueAction;
import org.com.arc_quest.dialogue.api.DialogueChoice;
import org.com.arc_quest.dialogue.api.DialogueNode;
import org.slf4j.Logger;

/**
 * 负责对话会话中具体动作的执行与状态跃迁逻辑（SRP 拆分）。
 */
public final class DialogueActionExecutor {

    private static final Logger LOGGER = LogUtils.getLogger();

    private DialogueActionExecutor() {
    }

    /**
     * 检查目标节点是否可用（包含一次性检查和冷却检查）。
     */
    public static boolean checkNodeAvailable(DialogueSession session, DialogueNode node,
                                             DialogueProgressStore progress,
                                             DialogueProgressStore.TimeSnapshot ts) {
        String namespace = session.getNamespace();

        // 一次性检查
        ProgressKey nodeKey = ProgressKey.ofNode(namespace, node.nodeId());
        if (!node.repeatable() && progress.hasVisitedNode(nodeKey)) {
            LOGGER.debug("[Dialogue] One-time node '{}' already visited.", node.nodeId());
            return false;
        }

        // 冷却检查
        if (node.cooldownType() != CooldownType.NONE) {
            boolean onCooldown = progress.isNodeOnCooldown(
                    namespace, node.nodeId(),
                    node.cooldownType(), (int) node.cooldownSeconds(), node.resetTimeTicks(),
                    ts.realTime(), ts.gameTime(), ts.dayTime());
            if (onCooldown) {
                LOGGER.debug("[Dialogue] Node '{}' on cooldown.", node.nodeId());
                return false;
            }
        }

        // 记录本次访问
        progress.recordNodeVisit(nodeKey, ts.realTime(), ts.gameTime(), ts.dayTime());
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

        LOGGER.debug("[DEBUG-Cooldown] Checking choice: node={}, idx={}, type={}, seconds={}",
                nodeId, choiceIndex, choice.cooldownType(), choice.cooldownSeconds());

        // 一次性检查
        if (!choice.repeatable() && progress.hasSelectedChoice(namespace, nodeId, choiceIndex)) {
            LOGGER.debug("[Dialogue] One-time choice [{}/{}] already selected.", nodeId, choiceIndex);
            return false;
        }

        // 冷却检查
        if (choice.cooldownType() != CooldownType.NONE) {
            ProgressKey choiceKey = ProgressKey.ofChoice(namespace, nodeId, choiceIndex);

            // GAME_TICK 类型：先检测时间回退
            if (choice.cooldownType() == CooldownType.GAME_TICK) {
                var entry = progress.getChoiceSelection(choiceKey);
                if (entry.exists() && entry.dayTime() > ts.dayTime()) {
                    progress.clearCooldownRecord(choiceKey);
                    LOGGER.warn("[Cooldown-Clear] Cleared cooldown record due to time regression: {}", choiceKey);
                    return true; // 清除后继续执行，不再检查冷却
                }
            }

            boolean onCooldown = progress.isChoiceOnCooldown(
                    namespace, nodeId, choiceIndex,
                    choice.cooldownType(), (int) choice.cooldownSeconds(), choice.resetTimeTicks(),
                    ts.realTime(), ts.gameTime(), ts.dayTime());

            LOGGER.debug("[DEBUG-Cooldown] Choice [{}/{}] onCooldown={}", nodeId, choiceIndex, onCooldown);

            if (onCooldown) return false;
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
                LOGGER.error("[Dialogue] Error executing action {} in session {}",
                        action.getClass().getSimpleName(), session.getSessionId(), e);
            }
        }
    }
}
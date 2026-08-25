package org.arcadia.arc_quest.quest.registry;

import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.network.QuestRejectCodeDictionary;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 任务接受前置规则扩展点。
 * 返回非空拒绝码时会终止接受流程，返回 null 表示交给后续规则处理。
 */
public final class QuestAcceptanceRuleRegistry {
    private static final List<Rule> RULES = new CopyOnWriteArrayList<>();

    private QuestAcceptanceRuleRegistry() {
    }

    public static void register(Rule rule) {
        if (rule != null) RULES.add(rule);
    }

    public static QuestRejectCodeDictionary.Code evaluate(
            ServerPlayer player, QuestDefinition definition, ArcQuestPlayer data) {
        for (Rule rule : RULES) {
            QuestRejectCodeDictionary.Code result = rule.evaluate(player, definition, data);
            if (result != null) return result;
        }
        return null;
    }

    @FunctionalInterface
    public interface Rule {
        QuestRejectCodeDictionary.Code evaluate(
                ServerPlayer player, QuestDefinition definition, ArcQuestPlayer data);
    }
}
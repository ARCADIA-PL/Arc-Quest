package org.arcadia.arc_quest.quest.logic.profile.collection;

import com.mojang.logging.LogUtils;
import org.arcadia.arc_quest.core.condition.ConditionGuard;
import org.arcadia.arc_quest.quest.api.CollectionCompletionRule;
import org.arcadia.arc_quest.quest.api.CollectionRuleContext;
import org.slf4j.Logger;

import java.util.List;

public final class CollectionRuleEvaluator {

    private static final Logger LOGGER = LogUtils.getLogger();

    private CollectionRuleEvaluator() {
    }

    public static boolean all(CollectionRuleContext context, List<CollectionCompletionRule> rules) {
        if (rules == null || rules.isEmpty()) return true;
        for (CollectionCompletionRule rule : rules) {
            if (!safeTest(context, rule)) return false;
        }
        return true;
    }

    public static boolean any(CollectionRuleContext context, List<CollectionCompletionRule> rules) {
        if (rules == null || rules.isEmpty()) return false;
        for (CollectionCompletionRule rule : rules) {
            if (safeTest(context, rule)) return true;
        }
        return false;
    }

    public static boolean safeTest(CollectionRuleContext context, CollectionCompletionRule rule) {
        if (context == null || rule == null) return false;
        return ConditionGuard.evaluate(
                () -> rule.test(context),
                false,
                LOGGER,
                "collection rule=" + rule.getDebugLabel()
                        + " quest=" + context.getQuestDefinition().getId()
                        + " category=" + context.getCategoryId());
    }
}

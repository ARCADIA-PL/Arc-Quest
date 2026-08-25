package org.arcadia.arc_quest.quest.logic.profile.collection;

import org.arcadia.arc_quest.core.CoreProcessors;
import org.arcadia.arc_quest.quest.api.CollectionCompletionRule;
import org.arcadia.arc_quest.quest.api.CollectionRuleContext;

import java.util.List;

public final class CollectionRuleEvaluator {
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
        return CoreProcessors.get().conditions().evaluateSafely(
                () -> rule.test(context),
                false,
                null,
                "collection rule=" + rule.getDebugLabel()
                        + " quest=" + context.getQuestDefinition().getId()
                        + " category=" + context.getCategoryId());
    }
}

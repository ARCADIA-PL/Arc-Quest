package org.arcadia.arc_quest.quest.registry;

import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.quest.api.IReward;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** 任务奖励发放策略扩展点。 */
public final class QuestRewardPolicyRegistry {
    private static final List<Policy> POLICIES = new CopyOnWriteArrayList<>();

    private QuestRewardPolicyRegistry() {
    }

    public static void register(Policy policy) {
        if (policy != null) POLICIES.add(policy);
    }

    public static Decision evaluate(ServerPlayer player, IReward reward, String context) {
        Decision result = Decision.GRANT;
        for (Policy policy : POLICIES) {
            Decision decision = policy.evaluate(player, reward, context);
            if (decision == Decision.SKIP) return Decision.SKIP;
            if (decision == Decision.REPLACE) result = Decision.REPLACE;
        }
        return result;
    }

    public enum Decision {
        GRANT,
        SKIP,
        REPLACE
    }

    @FunctionalInterface
    public interface Policy {
        Decision evaluate(ServerPlayer player, IReward reward, String context);
    }
}
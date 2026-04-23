package org.com.arc_quest.quest.condition;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.com.arc_quest.quest.api.ICondition;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

/**
 * @deprecated 使用 {@link org.com.arc_quest.quest.api.ICondition#questCompleted(net.minecraft.resources.ResourceLocation)}
 */
@Deprecated
public final class QuestCompletedCondition implements ICondition {

    private final ResourceLocation requiredQuestId;

    public QuestCompletedCondition(ResourceLocation requiredQuestId) {
        this.requiredQuestId = requiredQuestId;
    }

    @Override
    public boolean test(@Nullable ServerPlayer player,
                        Set<ResourceLocation> completedQuests,
                        Set<String> flags,
                        Map<String, Integer> variables) {
        return completedQuests.contains(this.requiredQuestId);
    }

    @Override
    public String describe() {
        return "QuestCompleted(" + this.requiredQuestId + ")";
    }

    /**
     * 获取所需完成任务的 ID。
     * 用于注册表验证等场景。
     */
    public ResourceLocation getRequiredQuestId() {
        return this.requiredQuestId;
    }
}
package org.com.arc_quest.quest.registry;

import com.mojang.logging.LogUtils;
import org.com.arc_quest.quest.logic.CrossSystemBridge;
import org.com.arc_quest.quest.logic.DependencyRule;
import org.slf4j.Logger;

/**
 * 所有任务内容在此注册。纯代码驱动，零 JSON。
 * 在 FMLCommonSetupEvent 中调用 {@link #registerAll()}.
 */
public final class ArcQuestContent {

    private static final Logger LOGGER = LogUtils.getLogger();

    private ArcQuestContent() {
    }

    public static void registerAll() {
        LOGGER.info("[ArcQuest] Registering quest content...");

        // Epic Demo：史诗级主线任务链（基于 MC 原版内容）
        EpicMainlineDemo.registerAll();
        EpicMainlineDemo.registerBranchChoice();

        QuestRegistry.freeze();
        
        //注册跨系统桥接规则
        registerCrossSystemRules();
        CrossSystemBridge.INSTANCE.freeze();

        LOGGER.info("[ArcQuest] Total registered quests: {}", QuestRegistry.getAll().size());
        LOGGER.info("[ArcQuest] Total registered bridge rules: {}", CrossSystemBridge.INSTANCE.getRuleCount());
    }
    
    /**
     * 注册任务↔对话的依赖规则。
     * <p>
     * 当前唯一有效的联动: 长老对话完成后自动标记任务目标。
     */
    private static void registerCrossSystemRules() {
        // 与村庄长老对话后标记任务目标
        // talk_villager 是 epic_prologue 的第2个阶段(索引1)
        CrossSystemBridge.INSTANCE.registerRule(
            DependencyRule.builder()
                .id("epic_elder_talk_marks_objective")
                .whenDialogueCompleted("epic_village_elder")
                .thenMarkObjectiveComplete("arc_quest:epic_prologue", 1)
                .build()
        );
    }


}
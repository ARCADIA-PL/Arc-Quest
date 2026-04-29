package org.arcadia.arc_quest.quest.registry;

import com.mojang.logging.LogUtils;
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

        LOGGER.info("[ArcQuest] Total registered quests: {}", QuestRegistry.getAll().size());
    }


}
package org.arcadia.arc_quest.quest.registry;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.api.event.registry.ArcQuestRegistrationEvent;
import org.slf4j.Logger;

/**
 * Built-in quest content registration.
 *
 * <p>The registration event path keeps the registry open for add-ons. The legacy
 * {@link #registerAll()} entry point retains its original register-and-freeze behavior.</p>
 */
@EventBusSubscriber(modid = Arc_Quest.MOD_ID)
public final class ArcQuestContent {

    private static final Logger LOGGER = LogUtils.getLogger();

    private ArcQuestContent() {
    }

    public static void registerAll() {
        registerBuiltInContent();
        QuestRegistry.freeze();
    }

    @SubscribeEvent
    public static void onQuestRegistration(ArcQuestRegistrationEvent.Quest event) {
        registerBuiltInContent();
    }

    private static void registerBuiltInContent() {
        LOGGER.info("[ArcQuest] Registering quest content...");

        // Epic Demo：史诗级主线任务链（基于 MC 原版内容）
        EpicMainlineDemo.registerAll();
        EpicMainlineDemo.registerBranchChoice();
        CollectionCodexDemo.registerAll();

        LOGGER.info("[ArcQuest] Total registered quests: {}", QuestRegistry.getAll().size());
    }


}

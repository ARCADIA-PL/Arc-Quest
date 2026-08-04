package org.arcadia.arc_quest.quest.registry;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.api.event.registry.ArcQuestRegistrationEvent;
import org.arcadia.arc_quest.quest.api.QuestGroupDefinition;
import org.slf4j.Logger;

/**
 * Built-in quest content registration.
 *
 * <p>The registration event path keeps the registry open for add-ons. The built-in registration
 * entry point no longer freezes registries; the mod lifecycle freezes all content layers together.</p>
 */
public final class ArcQuestContent {

    private static final Logger LOGGER = LogUtils.getLogger();

    private ArcQuestContent() {
    }

    public static void registerAll() {
        registerBuiltInContent();
        registerBuiltInGroups(new ArcQuestRegistrationEvent.Quest());
    }

    private static void registerBuiltInContent() {
        LOGGER.info("[ArcQuest] Registering quest content...");

        // Epic Demo：史诗级主线任务链（基于 MC 原版内容）
        EpicMainlineDemo.registerAll();
        EpicMainlineDemo.registerBranchChoice();
        CollectionCodexDemo.registerAll();

        LOGGER.info("[ArcQuest] Total registered quests: {}", QuestRegistry.getAll().size());
    }

    private static void registerBuiltInGroups(ArcQuestRegistrationEvent.Quest event) {
        ResourceLocation groupId = ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "epic_mainline");
        event.registerGroup(new QuestGroupDefinition(
                groupId,
                Component.translatable("arc_quest.quest_group.epic_mainline"),
                0,
                0x4FC3F7));
        event.assignQuestToGroup("arc_quest:epic_prologue", groupId.toString());
        event.assignQuestToGroup("arc_quest:epic_prologue_datapack", groupId.toString());
    }

}

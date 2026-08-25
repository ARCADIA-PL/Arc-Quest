package org.arcadia.arc_quest.quest.registry;
import org.arcadia.arc_quest.util.log.ArcQuestLog;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.api.event.registry.ArcQuestRegistrationEvent;
import org.arcadia.arc_quest.quest.api.QuestGroupDefinition;

/**
 * 相关处理说明。
 *
 * <p>注册事件路径会为附属模组保持注册表开放。内置注册
 * entry point no longer freezes registries; the mod lifecycle freezes all content layers together.</p>
 */
public final class ArcQuestContent {
    private ArcQuestContent() {
    }

    public static void registerAll() {
        registerBuiltInContent();
        registerBuiltInGroups(new ArcQuestRegistrationEvent.Quest());
    }

    private static void registerBuiltInContent() {
        ArcQuestLog.info(ArcQuestLog.Category.QUEST, "Registering quest content...");

        // Epic Demo：史诗级主线任务链（基于 MC 原版内容）
        EpicMainlineDemo.registerAll();
        EpicMainlineDemo.registerBranchChoice();
        CollectionCodexDemo.registerAll();

        ArcQuestLog.info(ArcQuestLog.Category.QUEST, "Total registered quests: {}", QuestRegistry.getAll().size());
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

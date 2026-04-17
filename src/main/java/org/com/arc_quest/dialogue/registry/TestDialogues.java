package org.com.arc_quest.dialogue.registry;

import net.minecraft.network.chat.Component;
import org.com.arc_quest.Arc_quest;

import org.com.arc_quest.dialogue.builder.DialogueTreeBuilder;

/**
 * 测试用对话树 —— 使用流式构建器的人性化写法。
 */
public final class TestDialogues {

    private TestDialogues() {}

    public static void registerAll() {
        registerVillagerTest();

        // 绑定 NPC
        DialogueRegistry.INSTANCE.bindNpc("villager_npc", "test_villager");
        DialogueRegistry.INSTANCE.bindNpc("测试村民", "test_villager");
    }

    /**
     * 翻新后的对话树 —— 清晰、紧凑、人性化。
     */
    private static void registerVillagerTest() {
        DialogueTreeBuilder.create("test_villager")
                .npc(Component.translatable("dialogue.test_villager.npc_name").getString())

                // 起始节点
                .node("start")
                    .say(Component.translatable("dialogue.test_villager.start.text").getString())
                    .choice(Component.translatable("dialogue.test_villager.start.choice1").getString(),
                            c -> c.goTo("ask_problem"))
                    .choice(Component.translatable("dialogue.test_villager.start.choice2").getString(),
                            DialogueTreeBuilder.ChoiceBuilder::close)

                // 询问问题节点
                .node("ask_problem")
                    .say(Component.translatable("dialogue.test_villager.ask_problem.text").getString())
                    .choice(Component.translatable("dialogue.test_villager.ask_problem.choice1").getString(),
                            c -> c
                                .startQuest("arc_quest:epic_prologue")
                                .notifyInteract("arc_quest:villager_npc")
                                .close())
                    .choice(Component.translatable("dialogue.test_villager.ask_problem.choice2").getString(),
                            c -> c.goTo("decline"))

                // 拒绝节点
                .node("decline")
                    .say(Component.translatable("dialogue.test_villager.decline.text").getString())
                    .choice(Component.translatable("dialogue.test_villager.decline.choice1").getString(),
                            DialogueTreeBuilder.ChoiceBuilder::close)

                .buildAndRegister();

        Arc_quest.LOGGER.info("[ArcQuest] Registered test dialogue: test_villager");
    }
}

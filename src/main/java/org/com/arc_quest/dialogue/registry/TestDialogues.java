package org.com.arc_quest.dialogue.registry;

import net.minecraft.network.chat.Component;
import org.com.arc_quest.Arc_quest;
import org.com.arc_quest.dialogue.api.DialogueAction;
import org.com.arc_quest.dialogue.api.DialogueChoice;
import org.com.arc_quest.dialogue.api.DialogueNode;
import org.com.arc_quest.dialogue.api.DialogueTree;

import java.util.List;

/**
 * 测试用对话树。
 */
public final class TestDialogues {

    private TestDialogues() {}

    public static void registerAll() {
        registerVillagerTest();

        // 绑定 NPC
        DialogueRegistry.INSTANCE.bindNpc("villager_npc", "test_villager");
        DialogueRegistry.INSTANCE.bindNpc("测试村民", "test_villager");
    }

    private static void registerVillagerTest() {
        DialogueTree tree = DialogueTree.builder("test_villager")
                .defaultNpc(Component.translatable("dialogue.test_villager.npc_name").getString())
                .startNode("start")
                .addNode(DialogueNode.builder("start")
                        .speaker(Component.translatable("dialogue.test_villager.npc_name").getString())
                        .text(Component.translatable("dialogue.test_villager.start.text").getString())
                        .choices(
                                new DialogueChoice(Component.translatable("dialogue.test_villager.start.choice1").getString(), "ask_problem", List.of(), List.of()),
                                new DialogueChoice(Component.translatable("dialogue.test_villager.start.choice2").getString(), null, List.of(), List.of(new DialogueAction.Close()))
                        )
                        .build())
                .addNode(DialogueNode.builder("ask_problem")
                        .speaker(Component.translatable("dialogue.test_villager.npc_name").getString())
                        .text(Component.translatable("dialogue.test_villager.ask_problem.text").getString())
                        .choices(
                                new DialogueChoice(Component.translatable("dialogue.test_villager.ask_problem.choice1").getString(), null, List.of(), List.of(
                                        new DialogueAction.StartQuest("arc_quest:epic_prologue"),
                                        new DialogueAction.NotifyInteract("arc_quest:villager_npc"),
                                        new DialogueAction.Close()
                                )),
                                new DialogueChoice(Component.translatable("dialogue.test_villager.ask_problem.choice2").getString(), "decline", List.of(), List.of())
                        )
                        .build())
                .addNode(DialogueNode.builder("decline")
                        .speaker(Component.translatable("dialogue.test_villager.npc_name").getString())
                        .text(Component.translatable("dialogue.test_villager.decline.text").getString())
                        .choices(
                                new DialogueChoice(Component.translatable("dialogue.test_villager.decline.choice1").getString(), null, List.of(), List.of(new DialogueAction.Close()))
                        )
                        .build())
                .build();

        DialogueRegistry.INSTANCE.register(tree);
        Arc_quest.LOGGER.info("[ArcQuest] Registered test dialogue: test_villager");
    }
}

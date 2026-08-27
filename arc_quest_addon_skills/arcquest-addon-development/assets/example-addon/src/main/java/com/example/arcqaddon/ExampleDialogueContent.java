package com.example.arcqaddon;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.api.event.registry.ArcQuestRegistrationEvent;
import org.arcadia.arc_quest.dialogue.api.DialogueText;
import org.arcadia.arc_quest.dialogue.api.DialogueTree;
import org.arcadia.arc_quest.dialogue.builder.DialogueTreeBuilder;

public final class ExampleDialogueContent {
    public static final ResourceLocation FOREMAN_DIALOGUE_ID = id("foreman_dialogue");

    private ExampleDialogueContent() {
    }

    public static void register(ArcQuestRegistrationEvent.Dialogue event) {
        DialogueTree tree = DialogueTreeBuilder.create(FOREMAN_DIALOGUE_ID)
                .npc(DialogueText.translatable("dialogue.example_arcq_addon.foreman.name"))
                .startNode("start")
                .node("start")
                .say(DialogueText.translatable("dialogue.example_arcq_addon.foreman.start"),
                        "example_arcq_addon:foreman/start")
                .choice("report", DialogueText.translatable(
                                "dialogue.example_arcq_addon.foreman.choice.report"),
                        choice -> choice
                                .action(new org.arcadia.arc_quest.dialogue.api.DialogueAction.LambdaAction(
                                        (player, entity) -> ExampleQuestRuntimeHooks
                                                .onForemanConversationCompleted(player)))
                                .goTo("reported"))
                .choice("leave", DialogueText.translatable(
                                "dialogue.example_arcq_addon.foreman.choice.leave"),
                        choice -> choice.close())
                .node("reported")
                .say(DialogueText.translatable("dialogue.example_arcq_addon.foreman.reported"),
                        "example_arcq_addon:foreman/reported")
                .choice("close", DialogueText.translatable(
                                "dialogue.example_arcq_addon.common.close"),
                        choice -> choice.close())
                .build();

        event.register(tree);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ExampleArcQuestAddon.MOD_ID, path);
    }
}

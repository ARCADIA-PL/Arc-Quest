package com.example.arcqaddon;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.api.event.registry.ArcQuestRegistrationEvent;
import org.arcadia.arc_quest.dialogue.api.DialogueAction;
import org.arcadia.arc_quest.dialogue.api.DialogueCondition;
import org.arcadia.arc_quest.dialogue.api.DialogueText;
import org.arcadia.arc_quest.dialogue.builder.DialogueTreeBuilder;

public final class ScenarioConditionalDialogueContent {
    public static final ResourceLocation DIALOGUE_ID = id("tour_coordinator");

    private ScenarioConditionalDialogueContent() {
    }

    public static void register(ArcQuestRegistrationEvent.Dialogue event) {
        String questId = ScenarioParallelQuestContent.QUEST_ID.toString();
        event.register(DialogueTreeBuilder.create(DIALOGUE_ID)
                .npc(DialogueText.translatable("dialogue.example_arcq_addon.coordinator.name"))
                .startNode("start")
                .node("start")
                .say(DialogueText.translatable("dialogue.example_arcq_addon.coordinator.start"),
                        "example_arcq_addon:coordinator/start")
                .choiceIf(
                        "accept_tour",
                        new DialogueCondition.Not(new DialogueCondition.HasQuest(questId)),
                        DialogueText.translatable("dialogue.example_arcq_addon.coordinator.accept"),
                        choice -> choice
                                .action(new DialogueAction.LambdaAction((player, entity) ->
                                        ScenarioQuestProgressAdapter.ensureAccepted(
                                                player, ScenarioParallelQuestContent.QUEST_ID)))
                                .goTo("accepted"))
                .choiceIf(
                        "open_market",
                        new DialogueCondition.QuestPhaseActive(
                                questId, ScenarioParallelQuestContent.VISIT_MARKET_PHASE),
                        DialogueText.translatable("dialogue.example_arcq_addon.coordinator.market"),
                        choice -> choice
                                .openTrade(ScenarioAdvancedTradeGachaContent.TRADE_ID.toString())
                                .restoreToCurrentNode())
                .choiceIf(
                        "completed",
                        new DialogueCondition.QuestCompleted(questId),
                        DialogueText.translatable("dialogue.example_arcq_addon.coordinator.completed"),
                        choice -> choice.goTo("thanks"))
                .choice("leave", DialogueText.translatable(
                                "dialogue.example_arcq_addon.common.close"),
                        choice -> choice.close())
                .node("accepted")
                .say(DialogueText.translatable("dialogue.example_arcq_addon.coordinator.accepted"),
                        "example_arcq_addon:coordinator/accepted")
                .choice("close", DialogueText.translatable(
                                "dialogue.example_arcq_addon.common.close"),
                        choice -> choice.close())
                .node("thanks")
                .say(DialogueText.translatable("dialogue.example_arcq_addon.coordinator.thanks"),
                        "example_arcq_addon:coordinator/thanks")
                .choice("close", DialogueText.translatable(
                                "dialogue.example_arcq_addon.common.close"),
                        choice -> choice.close())
                .build());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ExampleArcQuestAddon.MOD_ID, path);
    }
}

package com.example.arcqaddon;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.arcadia.arc_quest.api.event.registry.ArcQuestRegistrationEvent;
import org.arcadia.arc_quest.quest.api.QuestCategories;
import org.arcadia.arc_quest.quest.api.QuestCompletionPolicy;
import org.arcadia.arc_quest.quest.api.QuestText;
import org.arcadia.arc_quest.quest.builder.ObjectiveBuilder;
import org.arcadia.arc_quest.quest.builder.PhaseBuilder;
import org.arcadia.arc_quest.quest.builder.QuestBuilder;
import org.arcadia.arc_quest.quest.reward.ItemReward;
import org.arcadia.arc_quest.questmarker.api.MarkSpec;
import org.arcadia.arc_quest.questmarker.api.MarkableObject;

public final class ScenarioParallelQuestContent {
    public static final ResourceLocation QUEST_ID = id("settlement_tour");
    public static final String BOOTSTRAP_PHASE = "start_tour";
    public static final String VISIT_MARKET_PHASE = "visit_market";
    public static final String VISIT_WORKSHOP_PHASE = "visit_workshop";
    public static final String VISIT_ARCHIVE_PHASE = "visit_archive";

    private ScenarioParallelQuestContent() {
    }

    public static void register(ArcQuestRegistrationEvent.Quest event) {
        event.register(QuestBuilder.create(QUEST_ID)
                .category(QuestCategories.ADVENTURE)
                .displayName(QuestText.translatable("quest.example_arcq_addon.settlement_tour.title"))
                .description(QuestText.translatable("quest.example_arcq_addon.settlement_tour.description"))
                .icon(Items.COMPASS)
                .completionPolicy(QuestCompletionPolicy.ALL)
                .phase(PhaseBuilder.create(BOOTSTRAP_PHASE)
                        .displayName(QuestText.translatable(
                                "quest.example_arcq_addon.settlement_tour.start"))
                        .objective(ObjectiveBuilder.nullObjective()
                                .id("start_tour")
                                .hidden())
                        .thenGoTo(VISIT_MARKET_PHASE, VISIT_WORKSHOP_PHASE, VISIT_ARCHIVE_PHASE))
                .phase(visitPhase(
                        VISIT_MARKET_PHASE,
                        "market",
                        32,
                        70,
                        -18))
                .phase(visitPhase(
                        VISIT_WORKSHOP_PHASE,
                        "workshop",
                        -44,
                        66,
                        21))
                .phase(visitPhase(
                        VISIT_ARCHIVE_PHASE,
                        "archive",
                        8,
                        74,
                        56))
                .setInitialPhase(BOOTSTRAP_PHASE)
                .setFlagOnComplete("example_arcq_addon:settlement_tour_complete")
                .reward(new ItemReward(Items.EMERALD, 6))
                .build());
    }

    private static PhaseBuilder visitPhase(String phaseId, String targetPath,
                                           int x, int y, int z) {
        return PhaseBuilder.create(phaseId)
                .displayName(QuestText.translatable(
                        "quest.example_arcq_addon.settlement_tour." + targetPath))
                .objective(ObjectiveBuilder.custom(id("visit_" + targetPath), 1)
                        .id("visit_" + targetPath)
                        .display(QuestText.translatable(
                                "quest.example_arcq_addon.settlement_tour.objective." + targetPath)))
                .trackingMarker(MarkSpec.translated(
                        "example_arcq_addon:settlement_tour/" + targetPath,
                        "marker.example_arcq_addon." + targetPath,
                        new MarkableObject.DimensionPos(Level.OVERWORLD, x, y, z)))
                .grantGuideOnEnter(ExampleGuideContent.GUIDE_ID);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ExampleArcQuestAddon.MOD_ID, path);
    }
}

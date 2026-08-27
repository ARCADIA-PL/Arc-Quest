package com.example.arcqaddon;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.arcadia.arc_quest.api.event.registry.ArcQuestRegistrationEvent;
import org.arcadia.arc_quest.quest.api.QuestCategories;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.api.QuestGroupDefinition;
import org.arcadia.arc_quest.quest.api.QuestText;
import org.arcadia.arc_quest.quest.builder.ObjectiveBuilder;
import org.arcadia.arc_quest.quest.builder.PhaseBuilder;
import org.arcadia.arc_quest.quest.builder.QuestBuilder;
import org.arcadia.arc_quest.quest.reward.ItemReward;
import org.arcadia.arc_quest.questmarker.api.MarkSpec;
import org.arcadia.arc_quest.questmarker.api.MarkableObject;

public final class ExampleQuestContent {
    public static final ResourceLocation QUEST_ID = id("forest_supplies");
    public static final ResourceLocation GROUP_ID = id("field_work");
    public static final String GATHER_PHASE = "gather_logs";
    public static final String REPORT_PHASE = "report_back";

    private ExampleQuestContent() {
    }

    public static void register(ArcQuestRegistrationEvent.Quest event) {
        event.registerGroup(new QuestGroupDefinition(
                GROUP_ID,
                Component.translatable("quest_group.example_arcq_addon.field_work"),
                10,
                0xFF4F7D45
        ));

        QuestDefinition quest = QuestBuilder.create(QUEST_ID)
                .category(QuestCategories.ADVENTURE)
                .displayName(QuestText.translatable("quest.example_arcq_addon.forest_supplies.title"))
                .description(QuestText.translatable("quest.example_arcq_addon.forest_supplies.description"))
                .icon(Items.IRON_AXE)
                .abandonable(true)
                .setFlagOnComplete("example_arcq_addon:forest_supplies_complete")
                .phase(PhaseBuilder.create(GATHER_PHASE)
                        .displayName(QuestText.translatable(
                                "quest.example_arcq_addon.forest_supplies.gather.title"))
                        .objective(ObjectiveBuilder.collect(Items.OAK_LOG, 8)
                                .id("collect_oak_logs")
                                .display(QuestText.translatable(
                                        "quest.example_arcq_addon.forest_supplies.gather.objective")))
                        .trackingMarker(MarkSpec.translated(
                                "example_arcq_addon:forest_supplies/gather_target",
                                "marker.example_arcq_addon.forest",
                                new MarkableObject.DimensionPos(Level.OVERWORLD, 0, 64, 0)))
                        .thenGoTo(REPORT_PHASE))
                .phase(PhaseBuilder.create(REPORT_PHASE)
                        .displayName(QuestText.translatable(
                                "quest.example_arcq_addon.forest_supplies.report.title"))
                        .objective(ObjectiveBuilder.custom(id("report_to_foreman"), 1)
                                .id("report_to_foreman")
                                .display(QuestText.translatable(
                                        "quest.example_arcq_addon.forest_supplies.report.objective")))
                        .reward(new ItemReward(Items.EMERALD, 2)))
                .setInitialPhase(GATHER_PHASE)
                .reward(new ItemReward(Items.IRON_INGOT, 4))
                .build();

        event.register(quest);
        event.assignQuestToGroup(QUEST_ID, GROUP_ID);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ExampleArcQuestAddon.MOD_ID, path);
    }
}

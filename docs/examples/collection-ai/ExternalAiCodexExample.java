package ai_codex.integration;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import org.arcadia.arc_quest.api.ArcQuestAPI;
import org.arcadia.arc_quest.quest.api.CollectionContentBlock;
import org.arcadia.arc_quest.quest.api.CollectionContentReveal;
import org.arcadia.arc_quest.quest.api.CollectionMediaFit;
import org.arcadia.arc_quest.quest.api.QuestCategory;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.api.QuestMode;
import org.arcadia.arc_quest.quest.api.QuestText;
import org.arcadia.arc_quest.quest.builder.CollectionEntryBuilder;
import org.arcadia.arc_quest.quest.builder.CollectionQuestConfigBuilder;
import org.arcadia.arc_quest.quest.builder.CollectionSheetBuilder;
import org.arcadia.arc_quest.quest.builder.EntryRequirementBuilder;
import org.arcadia.arc_quest.quest.builder.ObjectiveBuilder;
import org.arcadia.arc_quest.quest.builder.PhaseBuilder;
import org.arcadia.arc_quest.quest.builder.QuestBuilder;
import org.arcadia.arc_quest.quest.data.CollectionRunDefinitionStore;
import org.arcadia.arc_quest.quest.reward.ItemReward;

/** Call register from ArcQuestRegistrationEvent.Quest on the mod event bus. */
public final class ExternalAiCodexExample {
    public static final ResourceLocation QUEST_ID = id("ai_codex:zombie_survey");
    public static final ResourceLocation ENTRY_ID = id("ai_codex:codex/zombie");
    public static final String RULE_VERSION = "zombie-survey-v1";
    private static boolean registered;

    private ExternalAiCodexExample() {}

    public static synchronized void register() {
        if (registered) return;
        CollectionRunDefinitionStore.registerCodeDefinitionFactory(
                QUEST_ID, RULE_VERSION, ExternalAiCodexExample::buildV1, true);
        ArcQuestAPI.registerQuest(buildV1());
        registered = true;
    }

    /** Preserve this method when introducing a later rule version. No registration side effects. */
    public static QuestDefinition buildV1() {
        var zombie = CollectionEntryBuilder.create(ENTRY_ID)
                .category("living").displayName(QuestText.translatable("ai_codex.example.entry.zombie.name"))
                .description(QuestText.translatable("ai_codex.example.entry.zombie.description"))
                .entity(EntityType.ZOMBIE)
                .discover(ObjectiveBuilder.kill(EntityType.ZOMBIE, 1)
                        .id("first_defeat").display(QuestText.translatable("ai_codex.example.objective.first_defeat")))
                .outcome("anatomy", QuestText.translatable("ai_codex.example.outcome.anatomy"))
                .discoveryReward("first_record", new ItemReward(Items.COAL, 1))
                .outcomeReward("anatomy", "first_anatomy", new ItemReward(Items.IRON_NUGGET, 3))
                .relatedItem(Items.ROTTEN_FLESH).relatedItem(Items.SHIELD)
                .content(textBlock("overview", QuestText.translatable("ai_codex.example.content.overview"),
                        CollectionContentReveal.ALWAYS, ""))
                .image("habitat", id("arc_quest:textures/gui/collection/field_notes.png"),
                        240, 120, QuestText.translatable("ai_codex.example.content.habitat.caption"))
                .content(textBlock("anatomy_notes", QuestText.translatable("ai_codex.example.content.anatomy_notes"),
                        CollectionContentReveal.OUTCOME, "anatomy"))
                .build();

        return QuestBuilder.create(QUEST_ID)
                .category(QuestCategory.COLLECTION).mode(QuestMode.COLLECTION)
                .displayName(QuestText.translatable("ai_codex.example.quest.name"))
                .description(QuestText.translatable("ai_codex.example.quest.description"))
                .themeColor(0x85C6AE)
                .collectionConfig(CollectionQuestConfigBuilder.create()
                        .category("living", QuestText.translatable("ai_codex.example.category.living")).entry(zombie).build())
                .phase(PhaseBuilder.create("survey").displayName(QuestText.translatable("ai_codex.example.phase.survey.name"))
                        .description(QuestText.translatable("ai_codex.example.phase.survey.description"))
                        .objective(ObjectiveBuilder.kill(EntityType.ZOMBIE, 3)
                                .id("defeats").display(QuestText.translatable("ai_codex.example.objective.defeats")))
                        .objective(ObjectiveBuilder.offer(Items.ROTTEN_FLESH, 2)
                                .id("samples").display(QuestText.translatable("ai_codex.example.objective.samples")))
                        .collectionSheet(CollectionSheetBuilder.create().all()
                                .binding(EntryRequirementBuilder.create("zombie", ENTRY_ID)
                                        .objectives("defeats", "samples")
                                        .recordOutcome("anatomy")
                                        .reward("survey_payment", new ItemReward(Items.EMERALD, 1))))
                        .autoAdvanceOnComplete(false))
                .reward(new ItemReward(Items.EMERALD, 2))
                .build();
    }

    private static CollectionContentBlock textBlock(String blockId, QuestText text,
                                                    CollectionContentReveal reveal, String outcomeId) {
        return new CollectionContentBlock(blockId, text, null, QuestText.literal(""),
                CollectionMediaFit.CONTAIN, false, reveal, outcomeId);
    }

    // tryParse exists in both Minecraft 1.20.1 and 1.21.1.
    private static ResourceLocation id(String value) {
        return Objects.requireNonNull(ResourceLocation.tryParse(value), "Invalid resource ID: " + value);
    }
}

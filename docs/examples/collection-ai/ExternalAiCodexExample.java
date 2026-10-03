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
                .category("living").displayName("僵尸")
                .description("记录夜间威胁与腐肉样本的用途。")
                .entity(EntityType.ZOMBIE)
                .discover(ObjectiveBuilder.kill(EntityType.ZOMBIE, 1)
                        .id("first_defeat").display("首次亲手击败 1 只僵尸"))
                .outcome("anatomy", "腐肉与行为记录")
                .discoveryReward("first_record", new ItemReward(Items.COAL, 1))
                .outcomeReward("anatomy", "first_anatomy", new ItemReward(Items.IRON_NUGGET, 3))
                .relatedItem(Items.ROTTEN_FLESH).relatedItem(Items.SHIELD)
                .content(textBlock("overview", "僵尸常在夜间或阴暗处出现；准备盾牌并保持退路。",
                        CollectionContentReveal.ALWAYS, ""))
                .image("habitat", id("arc_quest:textures/gui/collection/field_notes.png"),
                        240, 120, "发现后公开的野外观察图。")
                .content(textBlock("anatomy_notes", "腐肉样本已归档。腐肉可以饲喂狼，也能与牧师村民交易。",
                        CollectionContentReveal.OUTCOME, "anatomy"))
                .build();

        return QuestBuilder.create(QUEST_ID)
                .category(QuestCategory.COLLECTION).mode(QuestMode.COLLECTION)
                .displayName("僵尸调查 · AI 接入范例")
                .description("亲手击败 3 只僵尸，再交付 2 份腐肉；完成后手动确认。")
                .themeColor(0x85C6AE)
                .collectionConfig(CollectionQuestConfigBuilder.create()
                        .category("living", "野外生物").entry(zombie).build())
                .phase(PhaseBuilder.create("survey").displayName("夜间调查")
                        .description("只统计当前阶段的击败，样本交付实际消耗库存。")
                        .objective(ObjectiveBuilder.kill(EntityType.ZOMBIE, 3)
                                .id("defeats").display("亲手击败 3 只僵尸"))
                        .objective(ObjectiveBuilder.offer(Items.ROTTEN_FLESH, 2)
                                .id("samples").display("交付 2 份腐肉样本（消耗）"))
                        .collectionSheet(CollectionSheetBuilder.create().all()
                                .binding(EntryRequirementBuilder.create("zombie", ENTRY_ID)
                                        .objectives("defeats", "samples")
                                        .recordOutcome("anatomy")
                                        .reward("survey_payment", new ItemReward(Items.EMERALD, 1))))
                        .autoAdvanceOnComplete(false))
                .reward(new ItemReward(Items.EMERALD, 2))
                .build();
    }

    private static CollectionContentBlock textBlock(String blockId, String text,
                                                    CollectionContentReveal reveal, String outcomeId) {
        return new CollectionContentBlock(blockId, QuestText.literal(text), null, QuestText.literal(""),
                CollectionMediaFit.CONTAIN, false, reveal, outcomeId);
    }

    // tryParse exists in both Minecraft 1.20.1 and 1.21.1.
    private static ResourceLocation id(String value) {
        return Objects.requireNonNull(ResourceLocation.tryParse(value), "Invalid resource ID: " + value);
    }
}

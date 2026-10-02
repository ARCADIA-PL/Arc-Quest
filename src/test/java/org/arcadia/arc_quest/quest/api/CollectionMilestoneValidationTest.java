package org.arcadia.arc_quest.quest.api;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.data.sync.CollectionDefinitionSpecExporter;
import org.arcadia.arc_quest.quest.builder.*;
import org.arcadia.arc_quest.quest.spec.*;
import org.arcadia.arc_quest.quest.spec.compile.QuestSpecCompiler;
import org.arcadia.arc_quest.quest.spec.validate.QuestSpecValidator;
import org.arcadia.arc_quest.testsupport.MinecraftRegistryTestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CollectionMilestoneValidationTest {
    private static final ResourceLocation ID = ResourceLocation.parse("example:milestone_validation");
    private static final ResourceLocation ENTRY = ResourceLocation.parse("example:milestone_subject");
    @BeforeAll static void setup() { MinecraftRegistryTestBootstrap.initialize(); }

    @Test void builderAndDirectConstructorRejectDuplicateIdsBlankIdsWrongScopeAndWrongOwners() {
        for (CollectionQuestConfig config : List.of(
                config(node("same", RewardScope.QUEST, null), node("same", RewardScope.CATEGORY, null)),
                config(node(" ", RewardScope.QUEST, null), node("category", RewardScope.CATEGORY, null)),
                config(node("quest", RewardScope.CATEGORY, null), node("category", RewardScope.CATEGORY, null)),
                config(node("quest", RewardScope.QUEST, "example:another_quest"), node("category", RewardScope.CATEGORY, null)),
                config(node("quest", RewardScope.QUEST, null), node("category", RewardScope.CATEGORY, "wrong_category")))) {
            assertThrows(IllegalArgumentException.class, () -> builder(config, modernPhase()).build());
            assertThrows(IllegalArgumentException.class, () -> direct(config, modernPhase()));
        }
        var valid = config(node("quest", RewardScope.QUEST, null), node("category", RewardScope.CATEGORY, null));
        assertDoesNotThrow(() -> builder(valid, modernPhase()).build());
        assertDoesNotThrow(() -> direct(valid, modernPhase()));
    }

    @Test void jsonReportsTheExactDuplicateStableIdScopeAndOwnerPaths() {
        var duplicate = validSpec(); duplicate.collectionConfig.categories.get(0).rewardNodes.get(0).nodeId = "quest";
        assertError(duplicate, "collectionConfig.categories[0].rewardNodes[0].nodeId");
        var blank = validSpec(); blank.collectionConfig.rewardNodes.get(0).nodeId = "";
        assertError(blank, "collectionConfig.rewardNodes[0].nodeId");
        var scope = validSpec(); scope.collectionConfig.rewardNodes.get(0).scope = "ENTRY";
        assertError(scope, "collectionConfig.rewardNodes[0].scope");
        var owner = validSpec(); owner.collectionConfig.categories.get(0).rewardNodes.get(0).scopeRefId = "other";
        assertError(owner, "collectionConfig.categories[0].rewardNodes[0].scopeRefId");
        var defaults = validSpec();
        defaults.collectionConfig.rewardNodes.get(0).scopeRefId = "";
        defaults.collectionConfig.categories.get(0).rewardNodes.get(0).scopeRefId = "";
        assertFalse(new QuestSpecValidator().validate(defaults).hasErrors());
        assertDoesNotThrow(() -> new QuestSpecCompiler().compile(defaults));
    }

    @Test void nonFiniteOrOutOfRangeExactRatiosAreDiagnosedAndCannotBypassClientCompilation() {
        for (float bad : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -0.01f, 1.01f}) {
            var spec = validSpec();
            var expression = new ConditionSpec(); expression.type = "not";
            expression.left = new ConditionSpec(); expression.left.type = "completed_entry_ratio"; expression.left.ratio = bad;
            spec.collectionConfig.rewardNodes.get(0).completionRules.add(expression);
            assertError(spec, "collectionConfig.rewardNodes[0].completionRules[0].left.ratio");
            assertThrows(RuntimeException.class, () -> QuestSpecCompiler.compileClientPresentation(spec, Map.of()));
        }
        var valid = validSpec();
        var ratio = new ConditionSpec(); ratio.type = "category_completed_ratio"; ratio.ratio = 0.375f;
        valid.collectionConfig.categories.get(0).completionRules.add(ratio);
        assertFalse(new QuestSpecValidator().validate(valid).hasErrors());
        assertDoesNotThrow(() -> new QuestSpecCompiler().compile(valid));
    }

    @Test void legacyQuestsKeepTheirHistoricalDuplicateRewardIds() {
        var duplicates = config(node("same", RewardScope.QUEST, null), node("same", RewardScope.CATEGORY, null));
        var phase = PhaseBuilder.create("survey").objective(ObjectiveBuilder.custom(ENTRY, 1))
                .collectionEntryConfig(new CollectionEntryConfig("field", null, null, List.of(), null, 1,
                        false, false, 0, null, List.of(), 0, true)).build();
        assertDoesNotThrow(() -> builder(duplicates, phase).build());
        assertDoesNotThrow(() -> direct(duplicates, phase));
        var spec = validSpec(); spec.phases.get(0).collectionSheet = null;
        var objective = new ObjectiveSpec(); objective.type = "arc_quest:custom"; objective.targetId = ENTRY.toString();
        spec.phases.get(0).objectives.add(objective);
        spec.collectionConfig.categories.get(0).rewardNodes.get(0).nodeId = "quest";
        assertFalse(new QuestSpecValidator().validate(spec).getIssues().stream().anyMatch(issue -> issue.path.endsWith(".nodeId")));
    }

    private static CollectionRewardNode node(String id, RewardScope scope, String owner) {
        return new CollectionRewardNode(id, scope, EntryRewardGrantMode.MANUAL, List.of(), List.of(), owner);
    }
    private static CollectionQuestConfig config(CollectionRewardNode quest, CollectionRewardNode category) {
        return CollectionQuestConfigBuilder.create()
                .category(new CollectionCategoryDefinition("field", QuestText.literal("Field"), null, 0, List.of(), List.of(category), List.of()))
                .entry(CollectionEntryBuilder.create(ENTRY).category("field")).reward(quest).build();
    }
    private static PhaseDefinition modernPhase() {
        return PhaseBuilder.create("survey").collectionSheet(CollectionSheetBuilder.create()
                .binding(EntryRequirementBuilder.create("subject", ENTRY).discovered())).build();
    }
    private static QuestBuilder builder(CollectionQuestConfig config, PhaseDefinition phase) {
        return QuestBuilder.create(ID).category(QuestCategory.ADVENTURE).mode(QuestMode.COLLECTION).collectionConfig(config).phase(phase);
    }
    @SuppressWarnings("deprecation")
    private static QuestDefinition direct(CollectionQuestConfig config, PhaseDefinition phase) {
        var phases = new LinkedHashMap<String, PhaseDefinition>(); phases.put(phase.getPhaseId(), phase);
        return new QuestDefinition(ID, QuestCategory.ADVENTURE, QuestText.literal("Milestones"), QuestText.literal(""),
                null, 0, false, List.of(), phases, "survey", List.of(), List.of(), List.of(), List.of(),
                QuestVisualConfig.EMPTY, QuestMode.COLLECTION, config, null, ChapterShopType.TRADE, false);
    }
    private static QuestSpec validSpec() {
        return CollectionDefinitionSpecExporter.quest(builder(
                config(node("quest", RewardScope.QUEST, ID.toString()), node("category", RewardScope.CATEGORY, "field")),
                modernPhase()).build(), null);
    }
    private static void assertError(QuestSpec spec, String path) {
        var report = new QuestSpecValidator().validate(spec);
        assertTrue(report.getIssues().stream().anyMatch(issue -> issue.path.equals(path)),
                () -> "Missing diagnostic " + path + ": " + report.getIssues());
    }
}

package org.arcadia.arc_quest.quest.spec.compile;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.reward.CommandReward;
import org.arcadia.arc_quest.quest.reward.FlagReward;
import org.arcadia.arc_quest.quest.reward.ItemReward;
import org.arcadia.arc_quest.quest.reward.VariableReward;
import org.arcadia.arc_quest.quest.spec.*;
import org.arcadia.arc_quest.quest.spec.validate.QuestSpecValidator;
import org.arcadia.arc_quest.questmarker.api.MarkActivation;
import org.arcadia.arc_quest.questmarker.api.MarkActivations;
import org.arcadia.arc_quest.questmarker.api.MarkSpec;
import org.arcadia.arc_quest.questmarker.api.MarkableObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class QuestSpecCompiler {
    private final QuestSpecValidator validator = new QuestSpecValidator();

    public QuestDefinition compile(QuestSpec spec) {
        var report = validator.validate(spec);
        if (report.hasErrors()) {
            throw new QuestCompileException("QuestSpec validation failed for '" + (spec == null ? "null" : spec.id) + "'");
        }

        LinkedHashMap<String, PhaseDefinition> phases = new LinkedHashMap<>();
        for (PhaseSpec phaseSpec : spec.phases) {
            phases.put(phaseSpec.phaseId, compilePhase(phaseSpec));
        }

        return new QuestDefinition(
                parseId(spec.id),
                spec.category,
                compileText(spec.displayName),
                compileText(spec.description),
                parseNullableId(spec.iconTexture),
                spec.sortOrder,
                spec.repeatable,
                compileConditions(spec.unlockConditions),
                phases,
                spec.initialPhaseId,
                compileRewards(spec.completionRewards),
                listOrEmpty(spec.flagsToSetOnAccept),
                listOrEmpty(spec.flagsToSetOnComplete),
                compileMarks(spec.relatedMarks),
                compileVisual(spec.visualConfig),
                spec.mode,
                null,
                blankToNull(spec.chapterShopId),
                spec.chapterShopType,
                spec.chapterShopPersistent,
                parseNullableSound(spec.chapterStartSound),
                parseNullableSound(spec.chapterFailSound),
                parseNullableSound(spec.chapterCompleteSound),
                spec.completionPolicy,
                spec.completionRequiredCount,
                blankToNull(spec.completionTargetPhaseId),
                spec.timeLimitType,
                spec.timeLimitValue
        );
    }

    private PhaseDefinition compilePhase(PhaseSpec spec) {
        List<ObjectiveEntry> objectives = new ArrayList<>();
        for (ObjectiveSpec objectiveSpec : spec.objectives) {
            objectives.add(compileObjective(objectiveSpec));
        }
        List<PhaseTransition> transitions = new ArrayList<>();
        int priority = 0;
        for (TransitionSpec transitionSpec : listOrEmpty(spec.transitions)) {
            transitions.add(new PhaseTransition(transitionSpec.targetPhaseId, compileCondition(transitionSpec.condition), priority++));
        }
        List<ChoiceOption> choices = new ArrayList<>();
        for (ChoiceSpec choiceSpec : listOrEmpty(spec.choices)) {
            choices.add(new ChoiceOption(
                    compileText(choiceSpec.text).resolve(null, QuestTextContext.empty()),
                    choiceSpec.flagToSet == null ? "" : choiceSpec.flagToSet,
                    choiceSpec.targetPhaseId,
                    compileCondition(choiceSpec.visibleCondition)
            ));
        }
        return new PhaseDefinition(
                spec.phaseId,
                compileText(spec.displayName),
                compileText(spec.description),
                compileText(spec.story),
                objectives,
                transitions,
                choices,
                compileRewards(spec.phaseRewards),
                listOrEmpty(spec.flagsToSetOnEnter),
                listOrEmpty(spec.flagsToSetOnComplete),
                compileMarks(spec.relatedMarks),
                compileVisual(spec.visualConfig),
                blankToNull(spec.tradeShopId),
                parseNullableSound(spec.phaseStartSound),
                parseNullableSound(spec.phaseCompleteSound),
                null,
                parseNullableId(spec.intelSceneId),
                compileCondition(spec.enterCondition),
                spec.autoEnterByCondition
        );
    }

    private ObjectiveEntry compileObjective(ObjectiveSpec spec) {
        LinkedHashMap<String, String> extraData = new LinkedHashMap<>(spec.extraData);
        putIfPresent(extraData, "npc_id", spec.npcId);
        putIfPresent(extraData, "target_tag", spec.itemTag);
        putIfPresent(extraData, "x", spec.x);
        putIfPresent(extraData, "y", spec.y);
        putIfPresent(extraData, "z", spec.z);
        putIfPresent(extraData, "radius", spec.radius);
        putIfPresent(extraData, "count_mode", spec.countMode);
        putIfPresent(extraData, "count_base", spec.countBase);
        putIfPresent(extraData, "count_per_level", spec.countPerLevel);
        putIfPresent(extraData, "count_min", spec.countMin);
        putIfPresent(extraData, "count_max", spec.countMax);
        return new ObjectiveEntry(spec.type, parseId(spec.targetId), spec.requiredCount, compileText(spec.displayText), spec.hidden, spec.optional, extraData, compileMarks(spec.relatedMarks), null);
    }

    private List<IReward> compileRewards(List<RewardSpec> specs) {
        List<IReward> rewards = new ArrayList<>();
        for (RewardSpec spec : listOrEmpty(specs)) rewards.add(compileReward(spec));
        return rewards;
    }

    private List<MarkSpec> compileMarks(List<MarkSpecData> specs) {
        List<MarkSpec> marks = new ArrayList<>();
        for (MarkSpecData spec : listOrEmpty(specs)) {
            marks.add(new MarkSpec(
                    spec.id,
                    compileMarkTarget(spec.target),
                    compileMarkActivation(spec.activateWhen),
                    compileMarkActivation(spec.deactivateWhen),
                    spec.markerType,
                    spec.priority,
                    spec.maxDistance,
                    spec.refreshTicks,
                    spec.trackMovingEntity,
                    spec.oneShot,
                    spec.styleHints
            ));
        }
        return marks;
    }

    private MarkableObject compileMarkTarget(MarkTargetSpec spec) {
        if (spec == null) throw new QuestCompileException("Mark target is required");
        return switch (spec.type) {
            case "pos" ->
                    new MarkableObject.Pos(requireInt(spec.x, "mark x"), requireInt(spec.y, "mark y"), requireInt(spec.z, "mark z"));
            case "dimension_pos" ->
                    new MarkableObject.DimensionPos(ResourceKey.create(Registries.DIMENSION, parseId(spec.dimension)), requireInt(spec.x, "mark x"), requireInt(spec.y, "mark y"), requireInt(spec.z, "mark z"));
            case "entity_type_nearest" ->
                    new MarkableObject.EntityByTypeNearest(requireEntityType(spec.entityType), requirePositiveInt(spec.searchRadius, "mark searchRadius"));
            case "entity_npc_id" ->
                    new MarkableObject.EntityByNpcId(spec.npcId, requirePositiveInt(spec.searchRadius, "mark searchRadius"));
            case "structure_nearest" ->
                    new MarkableObject.StructureNearest(TagKey.create(Registries.STRUCTURE, parseId(spec.structureTag)), requirePositiveInt(spec.searchRadius, "mark searchRadius"));
            case "custom" -> new MarkableObject.CustomResolver(spec.resolverId, spec.args);
            default -> throw new QuestCompileException("Unsupported mark target type: " + spec.type);
        };
    }

    private MarkActivation compileMarkActivation(MarkActivationSpec spec) {
        if (spec == null) return null;
        return switch (spec.type) {
            case "always" -> MarkActivations.always();
            case "never" -> MarkActivations.never();
            case "flag_set" -> MarkActivations.flagSet(spec.flag);
            case "flag_not_set" -> MarkActivations.flagNotSet(spec.flag);
            case "quest_active" -> MarkActivations.questActive(spec.questId);
            case "and" -> MarkActivations.and(compileMarkActivation(spec.left), compileMarkActivation(spec.right));
            case "or" -> MarkActivations.or(compileMarkActivation(spec.left), compileMarkActivation(spec.right));
            case "not" -> MarkActivations.not(compileMarkActivation(spec.left));
            default -> throw new QuestCompileException("Unsupported mark activation type: " + spec.type);
        };
    }

    private IReward compileReward(RewardSpec spec) {
        return switch (spec.type) {
            case "item" -> new ItemReward(requireItem(spec.itemId), Math.max(1, spec.count));
            case "flag_set" -> FlagReward.set(spec.flag);
            case "flag_clear" -> FlagReward.clear(spec.flag);
            case "command" -> new CommandReward(spec.command);
            case "var_set" -> VariableReward.set(spec.variable, spec.value);
            case "var_add" -> VariableReward.add(spec.variable, spec.value);
            case "var_subtract" -> new VariableReward(spec.variable, VariableReward.Op.SUBTRACT, spec.value);
            case "var_multiply" -> new VariableReward(spec.variable, VariableReward.Op.MULTIPLY, spec.value);
            default -> throw new QuestCompileException("Unsupported reward type: " + spec.type);
        };
    }

    private List<ICondition> compileConditions(List<ConditionSpec> specs) {
        List<ICondition> conditions = new ArrayList<>();
        for (ConditionSpec spec : listOrEmpty(specs)) conditions.add(compileCondition(spec));
        return conditions;
    }

    private ICondition compileCondition(ConditionSpec spec) {
        if (spec == null) return null;
        return switch (spec.type) {
            case "always" -> ICondition.always();
            case "flag_set" -> ICondition.flagSet(spec.flag);
            case "flag_not_set" -> ICondition.flagNotSet(spec.flag);
            case "quest_completed" -> ICondition.questCompleted(parseId(spec.questId));
            case "variable" -> ICondition.variable(spec.variable, spec.compareOp, spec.value);
            case "and" -> compileCondition(spec.left).and(compileCondition(spec.right));
            case "or" -> compileCondition(spec.left).or(compileCondition(spec.right));
            case "not" -> compileCondition(spec.left).negate();
            default -> throw new QuestCompileException("Unsupported condition type: " + spec.type);
        };
    }

    private QuestVisualConfig compileVisual(QuestVisualSpec spec) {
        QuestVisualConfig.Builder builder = QuestVisualConfig.builder().themeColor(spec == null ? 0xFFFFFF : spec.themeColor);
        if (spec != null) {
            for (Map.Entry<String, QuestVisualSpec.AssetSpec> entry : spec.splashes.entrySet()) {
                SplashType type = SplashType.valueOf(entry.getKey());
                builder.splash(type, parseId(entry.getValue().texture), entry.getValue().scale);
            }
            for (Map.Entry<String, QuestVisualSpec.AssetSpec> entry : spec.icons.entrySet()) {
                IconPosition pos = IconPosition.valueOf(entry.getKey());
                builder.icon(pos, parseId(entry.getValue().texture), entry.getValue().scale);
            }
        }
        return builder.build();
    }

    private QuestText compileText(QuestTextSpec spec) {
        if (spec == null) return QuestText.component(Component.empty());
        if ("translatable".equals(spec.mode)) {
            if (spec.args == null || spec.args.isEmpty()) {
                return QuestText.translatable(spec.value);
            }
            QuestText.Arg[] args = spec.args.stream()
                    .map(value -> QuestText.Arg.of((player, ctx) -> value))
                    .toArray(QuestText.Arg[]::new);
            return QuestText.translatable(spec.value, args);
        }
        return QuestText.literal(spec.value);
    }

    private ResourceLocation parseId(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) throw new QuestCompileException("Invalid resource id: " + id);
        return rl;
    }

    private ResourceLocation parseNullableId(String id) {
        return id == null || id.isBlank() ? null : parseId(id);
    }

    private SoundEvent parseNullableSound(String id) {
        if (id == null || id.isBlank()) return null;
        SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(parseId(id));
        if (sound == null) throw new QuestCompileException("Unknown sound id: " + id);
        return sound;
    }

    private EntityType<?> requireEntityType(String id) {
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(parseId(id));
        if (type == null) throw new QuestCompileException("Unknown entity type id: " + id);
        return type;
    }

    private net.minecraft.world.item.Item requireItem(String itemId) {
        var item = ForgeRegistries.ITEMS.getValue(parseId(itemId));
        if (item == null) throw new QuestCompileException("Unknown item id: " + itemId);
        return item;
    }

    private int requirePositiveInt(Integer value, String label) {
        if (value == null || value <= 0) throw new QuestCompileException(label + " must be > 0");
        return value;
    }

    private int requireInt(Integer value, String label) {
        if (value == null) throw new QuestCompileException("Missing required integer: " + label);
        return value;
    }

    private <T> List<T> listOrEmpty(List<T> values) {
        return values == null ? List.of() : values;
    }

    private void putIfPresent(Map<String, String> map, String key, Object value) {
        if (value == null) return;
        String text = String.valueOf(value);
        if (!text.isBlank()) map.put(key, text);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}

package org.arcadia.arc_quest.dialogue.spec.compile;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import org.arcadia.arc_quest.condition.ConditionBridge;
import org.arcadia.arc_quest.dialogue.api.*;
import org.arcadia.arc_quest.dialogue.spec.*;
import org.arcadia.arc_quest.dialogue.spec.validate.DialogueSpecValidator;
import org.arcadia.arc_quest.dialogue.spec.validate.DialogueValidationIssue;
import org.arcadia.arc_quest.quest.api.IconPosition;
import org.arcadia.arc_quest.quest.api.QuestVisualConfig;
import org.arcadia.arc_quest.quest.spec.compile.QuestVisualSpecCompiler;
import org.arcadia.arc_quest.quest.spec.compile.QuestSpecCompiler;
import org.arcadia.arc_quest.questmarker.api.MarkSpec;
import org.arcadia.arc_quest.questmarker.api.MarkTrigger;
import org.arcadia.arc_quest.questmarker.api.MarkTriggers;
import org.arcadia.arc_quest.quest.api.SplashType;
import org.arcadia.arc_quest.quest.spec.QuestVisualSpec;
import org.arcadia.arc_quest.quest.spec.MarkSpecData;

import java.util.*;

public final class DialogueSpecCompiler {

    private final DialogueSpecValidator validator = new DialogueSpecValidator();
    private final QuestSpecCompiler questSpecCompiler = new QuestSpecCompiler();

    public DialogueTree compile(DialogueSpec spec) {
        var report = validator.validate(spec);
        if (report.hasErrors()) {
            throw new DialogueCompileException("DialogueSpec validation failed for '" + (spec == null ? "null" : spec.id) + "'");
        }

        Map<String, DialogueNode> nodes = new LinkedHashMap<>();
        List<MarkSpec> nodeMarks = new ArrayList<>();
        for (DialogueNodeSpec nodeSpec : spec.nodes) {
            nodes.put(nodeSpec.nodeId, compileNode(nodeSpec));
            for (MarkSpec triggered : compileTriggeredMarks(
                    nodeSpec.relatedMarks, MarkTrigger.DIALOGUE_NODE_ENTERED)) {
                nodeMarks.add(MarkTriggers.forDialogueNode(triggered, nodeSpec.nodeId));
            }
        }

        String startNodeId = spec.startNodeId != null && !spec.startNodeId.isBlank()
                ? spec.startNodeId
                : spec.nodes.get(0).nodeId;

        return new DialogueTree(
                spec.id,
                compileText(spec.defaultNpc),
                startNodeId,
                nodes,
                compileVisual(spec.visualConfig),
                spec.repeatable,
                spec.cooldownSeconds,
                parseCooldownType(spec.cooldownType),
                spec.resetTimeTicks,
                nodeMarks
        );
    }

    private DialogueNode compileNode(DialogueNodeSpec spec) {
        Map<String, ConditionalSay> conditionalTexts = new LinkedHashMap<>();
        if (spec.conditionalTexts != null) {
            for (var entry : spec.conditionalTexts.entrySet()) {
                ConditionalSaySpec saySpec = entry.getValue();
                if (saySpec == null) continue;
                String conditionKey = ConditionBridge.compileConditionKey(saySpec.conditions);
                if (conditionKey == null) continue;
                String mapKey = saySpec.priority + "|" + conditionKey;
                conditionalTexts.put(mapKey, compileConditionalSay(saySpec));
            }
        }

        List<DialogueChoice> choices = new ArrayList<>();
        if (spec.choices != null) {
            for (DialogueChoiceSpec choiceSpec : spec.choices) {
                choices.add(compileChoice(choiceSpec));
            }
        }

        SoundEvent enterSound = parseNullableSound(spec.nodeEnterSound);

        return new DialogueNode(
                spec.nodeId,
                compileText(spec.speaker),
                compileText(spec.text),
                conditionalTexts,
                choices,
                blankToNull(spec.autoNextId),
                spec.delayMs,
                spec.repeatable,
                spec.cooldownSeconds,
                parseCooldownType(spec.cooldownType),
                spec.resetTimeTicks,
                enterSound,
                blankToNull(spec.defaultSayId)
        );
    }

    private DialogueChoice compileChoice(DialogueChoiceSpec spec) {
        List<DialogueCondition> conditions = ConditionBridge.toDialogueConditions(spec.conditions);

        List<DialogueAction> actions = new ArrayList<>();
        if (spec.actions != null) {
            for (DialogueActionSpec actionSpec : spec.actions) {
                DialogueAction action = compileAction(actionSpec);
                if (action != null) {
                    actions.add(action);
                }
            }
        }

        SoundEvent selectSound = parseNullableSound(spec.selectSound);

        return new DialogueChoice(
                spec.choiceId,
                compileText(spec.text),
                blankToNull(spec.nextNodeId),
                conditions,
                actions,
                spec.repeatable,
                spec.cooldownSeconds,
                parseCooldownType(spec.cooldownType),
                spec.resetTimeTicks,
                spec.priority,
                blankToNull(spec.restoreNodeId),
                selectSound,
                compileTriggeredMarks(spec.relatedMarks, MarkTrigger.DIALOGUE_CHOICE_SELECTED)
        );
    }

    private List<MarkSpec> compileTriggeredMarks(List<MarkSpecData> specs, MarkTrigger trigger) {
        List<MarkSpec> marks = new ArrayList<>();
        if (specs == null) return marks;
        for (MarkSpecData spec : specs) {
            if (spec == null) continue;
            List<MarkSpec> compiled = questSpecCompiler.compileMarks(List.of(spec));
            if (!compiled.isEmpty()) {
                marks.add(MarkTriggers.withTrigger(compiled.get(0), trigger, spec.durationTicks));
            }
        }
        return marks;
    }

    private ConditionalSay compileConditionalSay(ConditionalSaySpec spec) {
        SoundEvent sound = parseNullableSound(spec.soundEvent);
        return new ConditionalSay(
                spec.sayId,
                compileText(spec.text),
                sound,
                List.of()
        );
    }

    private DialogueText compileText(DialogueTextSpec spec) {
        if (spec == null) return DialogueText.literal("");

        if ("translatable".equals(spec.mode)) {
            if (spec.args == null || spec.args.isEmpty()) {
                return DialogueText.translatable(spec.value);
            }
            DialogueText.DialogueArg[] args = spec.args.stream()
                    .map(this::resolveArg)
                    .toArray(DialogueText.DialogueArg[]::new);
            return DialogueText.translatable(spec.value, args);
        }

        return DialogueText.literal(spec.value);
    }

    private DialogueText.DialogueArg resolveArg(String argName) {
        return switch (argName) {
            case "player_name" -> DialogueText.DialogueArg.playerName();
            case "npc_name" -> DialogueText.DialogueArg.npcName();
            case "npc_pos" -> DialogueText.DialogueArg.npcPos();
            case "npc_display_name" -> DialogueText.DialogueArg.npcDisplayName();
            default -> DialogueText.DialogueArg.of(ctx -> argName);
        };
    }

    private DialogueAction compileAction(DialogueActionSpec spec) {
        if (spec == null || spec.type == null || spec.type.isBlank()) {
            return null;
        }

        return switch (spec.type) {
            case "start_quest" -> new DialogueAction.StartQuest(spec.questId);
            case "complete_quest" -> new DialogueAction.CompleteQuest(spec.questId);
            case "advance_phase" -> new DialogueAction.AdvancePhase(spec.questId);
            case "give_xp" -> new DialogueAction.GiveXp(spec.amount);
            case "give_item" -> new DialogueAction.GiveItem(spec.itemId, spec.count);
            case "notify_talk" -> new DialogueAction.NotifyTalk(spec.npcId);
            case "notify_interact" -> new DialogueAction.NotifyInteract(spec.targetId);
            case "no_op" -> new DialogueAction.NoOp();
            case "close" -> new DialogueAction.Close();
            case "run_command" -> new DialogueAction.RunCommand(spec.command);
            case "set_flag" -> new DialogueAction.SetFlag(spec.flagName);
            case "set_variable" -> new DialogueAction.SetVariable(spec.key, spec.value);
            case "open_trade" -> new DialogueAction.OpenTrade(spec.shopId, spec.restoreNodeId);
            case "open_simple_trade" -> new DialogueAction.OpenSimpleTrade(spec.shopId, spec.restoreNodeId);
            case "open_gacha" -> new DialogueAction.OpenGacha(spec.shopId, spec.restoreNodeId);
            case "custom" -> {
                ResourceLocation typeId = ResourceLocation.tryParse(spec.customTypeId);
                CompoundTag data = new CompoundTag();
                if (spec.customData != null) {
                    for (var entry : spec.customData.entrySet()) {
                        putNbtValue(data, entry.getKey(), entry.getValue());
                    }
                }
                yield typeId != null ? new DialogueAction.Custom(typeId, data) : null;
            }
            default -> null;
        };
    }

    private void putNbtValue(CompoundTag tag, String key, Object value) {
        if (value instanceof String s) {
            tag.putString(key, s);
        } else if (value instanceof Number n) {
            if (value instanceof Double || value instanceof Float) {
                tag.putDouble(key, n.doubleValue());
            } else {
                tag.putInt(key, n.intValue());
            }
        } else if (value instanceof Boolean b) {
            tag.putBoolean(key, b);
        }
    }

    private CooldownType parseCooldownType(String type) {
        if (type == null || type.isBlank()) return CooldownType.NONE;
        return switch (type.toUpperCase()) {
            case "SECONDS" -> CooldownType.SECONDS;
            case "GAME_DAY" -> CooldownType.GAME_DAY;
            case "GAME_TICK" -> CooldownType.GAME_TICK;
            default -> CooldownType.NONE;
        };
    }

    private QuestVisualConfig compileVisual(QuestVisualSpec spec) {
        return spec == null ? null : QuestVisualSpecCompiler.compile(spec);
    }

    private ResourceLocation parseNullableId(String id) {
        return id == null || id.isBlank() ? null : ResourceLocation.tryParse(id);
    }

    private SoundEvent parseNullableSound(String id) {
        if (id == null || id.isBlank()) return null;
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return null;
        return BuiltInRegistries.SOUND_EVENT.get(rl);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}

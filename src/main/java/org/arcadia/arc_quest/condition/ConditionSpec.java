package org.arcadia.arc_quest.condition;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 统一条件 Spec —— 对齐原版 predicate 风格。
 * <p>
 * JSON 格式：{@code {"condition": "arc_quest:quest_completed", "quest_id": "arc_quest:epic_prologue"}}
 * <p>
 * 同时支持原版 predicate 透传：{@code {"condition": "minecraft:location_check", "predicate": {...}}}
 */
public class ConditionSpec {

    public String condition = "arc_quest:always";

    public String questId;
    public String phaseId;
    public String targetPhaseId;
    public String fromPhaseId;
    public String toPhaseId;

    public String flag;
    public String key;
    public String op;
    public int value;

    public ConditionSpec inner;
    public List<ConditionSpec> conditions = new ArrayList<>();

    public JsonObject predicate;

    public String nodeId;
    public String choiceId;
    public String dialogueId;
    public long cooldownSeconds;
    public int startTick;
    public int endTick;
    public String name;

    public boolean isAlways() {
        return "arc_quest:always".equals(condition) || condition == null || condition.isBlank();
    }
}
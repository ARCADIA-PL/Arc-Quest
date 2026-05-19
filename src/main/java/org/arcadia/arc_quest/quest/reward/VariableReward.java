package org.arcadia.arc_quest.quest.reward;

import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.quest.api.IReward;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;

/**
 * 修改全局变量的奖励（SET / ADD / SUBTRACT / MULTIPLY）。
 */
public final class VariableReward implements IReward {

    private final String variableName;
    private final Op operation;
    private final int value;

    public VariableReward(String variableName, Op operation, int value) {
        this.variableName = variableName;
        this.operation = operation;
        this.value = value;
    }

    public static VariableReward set(String name, int value) {
        return new VariableReward(name, Op.SET, value);
    }

    public static VariableReward add(String name, int value) {
        return new VariableReward(name, Op.ADD, value);
    }

    public String getVariableName() {
        return variableName;
    }

    public Op getOperation() {
        return operation;
    }

    public int getValue() {
        return value;
    }

    public int apply(int current) {
        return switch (operation) {
            case SET -> value;
            case ADD -> current + value;
            case SUBTRACT -> current - value;
            case MULTIPLY -> current * value;
        };
    }

    @Override
    public void grant(ServerPlayer player) {
        ArcQuestPlayer data = ArcQuestPlayerManager.get(player);
        if (data == null) return;
        int current = data.getVariable(variableName);
        data.setVariable(variableName, apply(current));
    }

    @Override
    public String describe() {
        return "Var(" + variableName + " " + operation + " " + value + ")";
    }

    public enum Op {SET, ADD, SUBTRACT, MULTIPLY}
}

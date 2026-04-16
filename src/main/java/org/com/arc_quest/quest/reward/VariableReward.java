package org.com.arc_quest.quest.reward;

import net.minecraft.server.level.ServerPlayer;
import org.com.arc_quest.quest.api.IReward;

/**
 * 修改全局变量的"奖励"。
 */
public final class VariableReward implements IReward {

    public enum Op {SET, ADD, SUBTRACT, MULTIPLY}

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
        return this.variableName;
    }

    public Op getOperation() {
        return this.operation;
    }

    public int getValue() {
        return this.value;
    }

    /**
     * 给定当前值，计算新值
     */
    public int apply(int current) {
        return switch (this.operation) {
            case SET -> this.value;
            case ADD -> current + this.value;
            case SUBTRACT -> current - this.value;
            case MULTIPLY -> current * this.value;
        };
    }

    @Override
    public void grant(ServerPlayer player) {
        // Phase 2: Capability 接入
    }

    @Override
    public String describe() {
        return "Var(" + this.variableName + " " + this.operation + " " + this.value + ")";
    }
}
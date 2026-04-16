package org.com.arc_quest.quest.condition;

import net.minecraft.resources.ResourceLocation;
import org.com.arc_quest.quest.api.ICondition;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 检测全局变量是否满足数值条件。
 */
public final class VariableCondition implements ICondition {

    public enum Op {
        EQUAL("=="),
        NOT_EQUAL("!="),
        GREATER(">"),
        GREATER_OR_EQUAL(">="),
        LESS("<"),
        LESS_OR_EQUAL("<=");

        private final String symbol;

        Op(String symbol) {
            this.symbol = symbol;
        }

        public boolean evaluate(int actual, int expected) {
            return switch (this) {
                case EQUAL -> actual == expected;
                case NOT_EQUAL -> actual != expected;
                case GREATER -> actual > expected;
                case GREATER_OR_EQUAL -> actual >= expected;
                case LESS -> actual < expected;
                case LESS_OR_EQUAL -> actual <= expected;
            };
        }

        @Override
        public String toString() {
            return this.symbol;
        }
    }

    private final String variableName;
    private final Op operator;
    private final int expectedValue;

    public VariableCondition(String variableName, Op operator, int expectedValue) {
        Objects.requireNonNull(variableName);
        Objects.requireNonNull(operator);
        this.variableName = variableName;
        this.operator = operator;
        this.expectedValue = expectedValue;
    }

    @Override
    public boolean test(Set<ResourceLocation> completedQuests,
                        Set<String> flags,
                        Map<String, Integer> variables) {
        int actual = variables.getOrDefault(this.variableName, 0);
        return this.operator.evaluate(actual, this.expectedValue);
    }

    @Override
    public String describe() {
        return "Var(" + this.variableName + " " + this.operator + " " + this.expectedValue + ")";
    }
}
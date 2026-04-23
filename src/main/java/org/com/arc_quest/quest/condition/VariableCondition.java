package org.com.arc_quest.quest.condition;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.com.arc_quest.quest.api.CompareOp;
import org.com.arc_quest.quest.api.ICondition;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * @deprecated 使用 {@link org.com.arc_quest.quest.api.ICondition#variable(String, org.com.arc_quest.quest.api.CompareOp, int)}
 */
@Deprecated
public final class VariableCondition implements ICondition {

    private final String variableName;
    private final CompareOp operator;
    private final int expectedValue;

    public VariableCondition(String variableName, CompareOp operator, int expectedValue) {
        Objects.requireNonNull(variableName);
        Objects.requireNonNull(operator);
        this.variableName = variableName;
        this.operator = operator;
        this.expectedValue = expectedValue;
    }

    @Override
    public boolean test(@Nullable ServerPlayer player,
                        Set<ResourceLocation> completedQuests,
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

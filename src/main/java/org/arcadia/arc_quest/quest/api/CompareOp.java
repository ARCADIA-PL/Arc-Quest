package org.arcadia.arc_quest.quest.api;

/**
 * 通用数值比较操作符。
 * <p>
 * 被以下两处共用，避免平行实现：
 * <ul>
 *   <li>{@code quest.condition.VariableCondition} — 任务前置条件</li>
 *   <li>{@code dialogue.api.DialogueCondition.VariableCheck} — 对话条件</li>
 * </ul>
 */
public enum CompareOp {
    EQUAL("=="),
    NOT_EQUAL("!="),
    GREATER(">"),
    GREATER_OR_EQUAL(">="),
    LESS("<"),
    LESS_OR_EQUAL("<=");

    private final String symbol;

    CompareOp(String symbol) {
        this.symbol = symbol;
    }

    /**
     * 根据操作符符号字符串查找对应的枚举值。
     *
     * @param symbol 操作符字符串，如 {@code "=="}, {@code ">="} 等
     * @return 对应的 CompareOp，找不到时返回 {@code EQUAL}
     */
    public static CompareOp fromSymbol(String symbol) {
        for (CompareOp op : values()) {
            if (op.symbol.equals(symbol)) return op;
        }
        return EQUAL;
    }

    /**
     * 对两个整数执行比较。
     *
     * @param actual   实际值
     * @param expected 期望值
     * @return 比较结果
     */
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

package org.arcadia.arc_quest.dialogue.runtime;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** 动作具有不可逆副作用：中断或失败后不继续，也不自动重试已执行部分。 */
final class DialogueActionSequence {
    enum Status { COMPLETED, INTERRUPTED, FAILED }

    record Result(Status status, int actionIndex, Exception failure) {
    }

    private DialogueActionSequence() {
    }

    static <A> Result execute(List<A> actions, BooleanSupplier canContinue, Consumer<A> execute) {
        for (int index = 0; index < actions.size(); index++) {
            if (!canContinue.getAsBoolean()) return new Result(Status.INTERRUPTED, index, null);
            try {
                execute.accept(actions.get(index));
            } catch (Exception failure) {
                return new Result(Status.FAILED, index, failure);
            }
        }
        return new Result(canContinue.getAsBoolean() ? Status.COMPLETED : Status.INTERRUPTED,
                actions.size(), null);
    }
}

package org.arcadia.arc_quest.questmarker.api;

import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.quest.capability.IQuestCapability;

import java.util.Arrays;
import java.util.Objects;

public final class MarkActivations {
    private MarkActivations() {}

    public static MarkActivation always() { return (p, c) -> true; }

    public static MarkActivation never() { return (p, c) -> false; }

    public static MarkActivation questActive(String questId) {
        return (p, c) -> c != null && c.isQuestActive(questId);
    }

    public static MarkActivation flagSet(String flag) {
        return (p, c) -> c != null && c.hasFlag(flag);
    }

    public static MarkActivation flagNotSet(String flag) {
        return (p, c) -> c != null && !c.hasFlag(flag);
    }

    public static MarkActivation and(MarkActivation... all) {
        Objects.requireNonNull(all, "all");
        return (p, c) -> Arrays.stream(all).allMatch(a -> a != null && a.test(p, c));
    }

    public static MarkActivation or(MarkActivation... any) {
        Objects.requireNonNull(any, "any");
        return (p, c) -> Arrays.stream(any).anyMatch(a -> a != null && a.test(p, c));
    }

    public static MarkActivation not(MarkActivation inner) {
        Objects.requireNonNull(inner, "inner");
        return (p, c) -> !inner.test(p, c);
    }
}

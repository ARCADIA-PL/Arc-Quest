package org.com.arc_quest.quest.api;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.function.BiFunction;

public final class QuestText {

    private final BiFunction<ServerPlayer, QuestTextContext, Component> resolver;
    private final Component fallback;

    private QuestText(BiFunction<ServerPlayer, QuestTextContext, Component> resolver, Component fallback) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.fallback = fallback == null ? Component.empty() : fallback;
    }

    public static QuestText of(BiFunction<ServerPlayer, QuestTextContext, Component> resolver) {
        return new QuestText(resolver, Component.empty());
    }

    public static QuestText of(BiFunction<ServerPlayer, QuestTextContext, Component> resolver, Component fallback) {
        return new QuestText(resolver, fallback);
    }

    public static QuestText literal(String text) {
        Component comp = Component.literal(text == null ? "" : text);
        return new QuestText((player, ctx) -> comp, comp);
    }

    public static QuestText component(Component component) {
        Component comp = component == null ? Component.empty() : component;
        return new QuestText((player, ctx) -> comp, comp);
    }

    public static QuestText translatable(String key, Arg... args) {
        Component fallback = Component.translatable(key);
        return new QuestText((player, ctx) -> {
            Object[] resolved = new Object[args == null ? 0 : args.length];
            if (args != null) {
                for (int i = 0; i < args.length; i++) {
                    Arg arg = args[i];
                    resolved[i] = arg == null ? "" : arg.resolve(player, ctx);
                }
            }
            return Component.translatable(key, resolved);
        }, fallback);
    }

    public Component resolve(@Nullable ServerPlayer player, @Nullable QuestTextContext context) {
        if (player == null) {
            return fallback;
        }
        return resolver.apply(player, context == null ? QuestTextContext.empty() : context);
    }

    @FunctionalInterface
    public interface Arg {
        Object resolve(ServerPlayer player, QuestTextContext context);

        static Arg playerName() {
            return (player, context) -> player.getName();
        }

        static Arg phaseDisplayName() {
            return (player, context) -> context.phaseDisplayNameOrEmpty();
        }

        static Arg of(BiFunction<ServerPlayer, QuestTextContext, Object> fn) {
            return fn::apply;
        }
    }
}

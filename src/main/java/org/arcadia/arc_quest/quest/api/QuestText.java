package org.arcadia.arc_quest.quest.api;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.function.BiFunction;

public final class QuestText {

    private static final Object NO_FALLBACK = new Object();
    private static final Component MISSING_FALLBACK = Component.literal("<?>");

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
        Object[] fallbackArgs = new Object[args == null ? 0 : args.length];
        boolean hasFallback = false;
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                Arg arg = args[i];
                Object fallbackArg = arg == null ? NO_FALLBACK : arg.resolveFallback();
                if (fallbackArg == NO_FALLBACK) {
                    fallbackArgs[i] = MISSING_FALLBACK;
                } else {
                    fallbackArgs[i] = fallbackArg;
                    hasFallback = true;
                }
            }
        }
        Component fallback = hasFallback
                ? Component.translatable(key, fallbackArgs)
                : Component.translatable(key);
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
        static Arg playerName() {
            return (player, context) -> player.getName();
        }

        static Arg phaseDisplayName() {
            return (player, context) -> context.phaseDisplayNameOrEmpty();
        }

        static Arg of(BiFunction<ServerPlayer, QuestTextContext, Object> fn) {
            return fn::apply;
        }

        /**
         * 创建一个由服务端解析的参数，并为无玩家客户端渲染提供明确值。
         */
        static Arg of(BiFunction<ServerPlayer, QuestTextContext, Object> fn, Object fallback) {
            Objects.requireNonNull(fn, "fn");
            return new Arg() {
                @Override
                public Object resolve(ServerPlayer player, QuestTextContext context) {
                    return fn.apply(player, context);
                }

                @Override
                public Object resolveFallback() {
                    return fallback == null ? "" : fallback;
                }
            };
        }

        /**
         * 创建一个在服务端和客户端上值相同的参数。
         */
        static Arg constant(Object value) {
            Object normalized = value == null ? "" : value;
            return new Arg() {
                @Override
                public Object resolve(ServerPlayer player, QuestTextContext context) {
                    return normalized;
                }

                @Override
                public Object resolveFallback() {
                    return normalized;
                }
            };
        }

        default Object resolveFallback() {
            return NO_FALLBACK;
        }

        Object resolve(ServerPlayer player, QuestTextContext context);
    }
}

package org.com.arc_quest.trade.api;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class TradeText {

    private final Function<TradeTextContext, Component> resolver;
    private final Component fallback;

    private TradeText(Function<TradeTextContext, Component> resolver, Component fallback) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.fallback = fallback == null ? Component.empty() : fallback;
    }

    public static TradeText of(Function<TradeTextContext, Component> resolver) {
        return new TradeText(resolver, Component.empty());
    }

    public static TradeText of(BiFunction<ServerPlayer, TradeTextContext, Component> resolver) {
        return new TradeText(ctx -> resolver.apply(ctx.player(), ctx), Component.empty());
    }

    public static TradeText literal(String text) {
        Component comp = Component.literal(text == null ? "" : text);
        return new TradeText(ctx -> comp, comp);
    }

    public static TradeText component(Component component) {
        Component comp = component == null ? Component.empty() : component;
        return new TradeText(ctx -> comp, comp);
    }

    public static TradeText translatable(String key, Arg... args) {
        return new TradeText(ctx -> {
            Object[] resolved = new Object[args == null ? 0 : args.length];
            if (args != null) {
                for (int i = 0; i < args.length; i++) {
                    Arg arg = args[i];
                    resolved[i] = arg == null ? "" : arg.resolve(ctx);
                }
            }
            return Component.translatable(key, resolved);
        }, Component.translatable(key));
    }

    public Component resolve(TradeTextContext context) {
        return resolver.apply(context);
    }

    public Component resolveFallback() {
        return fallback;
    }

    @FunctionalInterface
    public interface Arg {
        Object resolve(TradeTextContext context);

        static Arg playerName() {
            return ctx -> ctx.player().getName();
        }

        static Arg shopId() {
            return TradeTextContext::shopId;
        }

        static Arg of(Function<TradeTextContext, Object> fn) {
            return fn::apply;
        }
    }
}

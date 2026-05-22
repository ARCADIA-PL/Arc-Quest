package org.arcadia.arc_quest.guide.api;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.function.BiFunction;

public final class GuideText {

    private final BiFunction<ServerPlayer, GuideTextContext, Component> resolver;
    private final Component fallback;

    private GuideText(BiFunction<ServerPlayer, GuideTextContext, Component> resolver, Component fallback) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.fallback = fallback == null ? Component.empty() : fallback;
    }

    public static GuideText of(BiFunction<ServerPlayer, GuideTextContext, Component> resolver) {
        return new GuideText(resolver, Component.empty());
    }

    public static GuideText of(BiFunction<ServerPlayer, GuideTextContext, Component> resolver, Component fallback) {
        return new GuideText(resolver, fallback);
    }

    public static GuideText literal(String text) {
        Component comp = Component.literal(text == null ? "" : text);
        return new GuideText((player, ctx) -> comp, comp);
    }

    public static GuideText component(Component component) {
        Component comp = component == null ? Component.empty() : component;
        return new GuideText((player, ctx) -> comp, comp);
    }

    public static GuideText translatable(String key, Arg... args) {
        Component fallback = Component.translatable(key == null ? "" : key);
        return new GuideText((player, ctx) -> {
            Object[] resolved = new Object[args == null ? 0 : args.length];
            if (args != null) {
                for (int i = 0; i < args.length; i++) {
                    Arg arg = args[i];
                    resolved[i] = arg == null ? "" : arg.resolve(player, ctx == null ? GuideTextContext.empty() : ctx);
                }
            }
            return Component.translatable(key, resolved);
        }, fallback);
    }

    public Component resolve(@Nullable ServerPlayer player, @Nullable GuideTextContext context) {
        if (player == null) {
            return fallback;
        }
        return resolver.apply(player, context == null ? GuideTextContext.empty() : context);
    }

    @FunctionalInterface
    public interface Arg {
        static Arg playerName() {
            return (player, context) -> player.getName();
        }

        static Arg of(BiFunction<ServerPlayer, GuideTextContext, Object> fn) {
            return fn::apply;
        }

        Object resolve(ServerPlayer player, GuideTextContext context);
    }
}

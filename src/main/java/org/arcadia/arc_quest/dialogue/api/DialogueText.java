package org.arcadia.arc_quest.dialogue.api;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class DialogueText {

    private final Function<DialogueTextContext, Component> resolver;

    private DialogueText(Function<DialogueTextContext, Component> resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    public static DialogueText of(Function<DialogueTextContext, Component> resolver) {
        return new DialogueText(resolver);
    }

    public static DialogueText of(BiFunction<ServerPlayer, Entity, Component> resolver) {
        return new DialogueText(ctx -> resolver.apply(ctx.player(), ctx.npc()));
    }

    public static DialogueText literal(String text) {
        return new DialogueText(ctx -> Component.literal(text == null ? "" : text));
    }

    public static DialogueText translatable(String key, DialogueArg... args) {
        return new DialogueText(ctx -> {
            Object[] resolved = new Object[args == null ? 0 : args.length];
            if (args != null) {
                for (int i = 0; i < args.length; i++) {
                    DialogueArg arg = args[i];
                    resolved[i] = arg == null ? "" : arg.resolve(ctx);
                }
            }
            return Component.translatable(key, resolved);
        });
    }

    public Component resolve(DialogueTextContext context) {
        return resolver.apply(context);
    }

    public Component resolve(ServerPlayer player, @Nullable Entity npc) {
        return resolver.apply(DialogueTextContext.of(player, npc));
    }

    @FunctionalInterface
    public interface DialogueArg {
        Object resolve(DialogueTextContext context);

        static DialogueArg playerName() {
            return context -> context.player().getName();
        }

        static DialogueArg npcName() {
            return context -> context.npc() == null ? Component.literal("Unknown") : context.npc().getName();
        }

        static DialogueArg npcPos() {
            return context -> {
                Entity npc = context.npc();
                if (npc == null) return Component.literal("Unknown");
                int x = (int) Math.floor(npc.getX());
                int y = (int) Math.floor(npc.getY());
                int z = (int) Math.floor(npc.getZ());
                return Component.literal(x + ", " + y + ", " + z);
            };
        }

        static DialogueArg of(Function<DialogueTextContext, Object> fn) {
            return fn::apply;
        }

        static DialogueArg of(BiFunction<ServerPlayer, Entity, Object> fn) {
            return context -> fn.apply(context.player(), context.npc());
        }
    }
}

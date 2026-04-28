package org.com.arc_quest.dialogue.api;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.function.BiFunction;

public final class DialogueText {

    private final BiFunction<ServerPlayer, Entity, Component> resolver;

    private DialogueText(BiFunction<ServerPlayer, Entity, Component> resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    public static DialogueText of(BiFunction<ServerPlayer, Entity, Component> resolver) {
        return new DialogueText(resolver);
    }

    public static DialogueText literal(String text) {
        return new DialogueText((player, npc) -> Component.literal(text == null ? "" : text));
    }

    public static DialogueText translatable(String key, DialogueArg... args) {
        return new DialogueText((player, npc) -> {
            Object[] resolved = new Object[args == null ? 0 : args.length];
            if (args != null) {
                for (int i = 0; i < args.length; i++) {
                    DialogueArg arg = args[i];
                    resolved[i] = arg == null ? "" : arg.resolve(player, npc);
                }
            }
            return Component.translatable(key, resolved);
        });
    }

    public Component resolve(ServerPlayer player, @Nullable Entity npc) {
        return resolver.apply(player, npc);
    }

    @FunctionalInterface
    public interface DialogueArg {
        Object resolve(ServerPlayer player, @Nullable Entity npc);

        static DialogueArg playerName() {
            return (player, npc) -> player.getName();
        }

        static DialogueArg npcName() {
            return (player, npc) -> npc == null ? Component.literal("Unknown") : npc.getName();
        }

        static DialogueArg npcPos() {
            return (player, npc) -> {
                if (npc == null) return Component.literal("Unknown");
                int x = (int) Math.floor(npc.getX());
                int y = (int) Math.floor(npc.getY());
                int z = (int) Math.floor(npc.getZ());
                return Component.literal(x + ", " + y + ", " + z);
            };
        }

        static DialogueArg of(BiFunction<ServerPlayer, Entity, Object> fn) {
            return fn::apply;
        }
    }
}

package org.arcadia.arc_quest.dialogue.api;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/** A dialogue's sample catalogue or fixed ingredient bundle. Actions own the atomic payment. */
public record DialogueSubmissionDefinition(String id, Component title, List<Entry> entries, Bundle bundle) {
    /** Preserve the original one-sample catalogue API for existing addons. */
    public DialogueSubmissionDefinition(String id, Component title, List<Entry> entries) {
        this(id, title, entries, null);
    }

    public DialogueSubmissionDefinition {
        Objects.requireNonNull(id);
        Objects.requireNonNull(title);
        entries = List.copyOf(entries);
        if (entries.size() > 128 || entries.stream().map(Entry::id).distinct().count() != entries.size())
            throw new IllegalArgumentException("Invalid submission entries: " + id);
        if (bundle != null && !entries.isEmpty())
            throw new IllegalArgumentException("A submission cannot combine sample choices and a fixed bundle: " + id);
    }

    public static DialogueSubmissionDefinition bundle(String id, Component title, Bundle bundle) {
        return new DialogueSubmissionDefinition(id, title, List.of(), Objects.requireNonNull(bundle));
    }

    public record Ingredient(String itemId, int count) {
        public Ingredient {
            if (net.minecraft.resources.ResourceLocation.tryParse(itemId) == null || count < 1 || count > 4096)
                throw new IllegalArgumentException("Invalid submission ingredient: " + itemId + " x" + count);
        }
    }

    /** Every ingredient is required; availability controls the story gate, not inventory counts. */
    public record Bundle(List<Ingredient> ingredients, Predicate<ServerPlayer> available,
                         List<DialogueAction> actions, String successNodeId) {
        public Bundle {
            ingredients = List.copyOf(ingredients);
            Objects.requireNonNull(available);
            actions = List.copyOf(actions);
            if (ingredients.isEmpty() || ingredients.size() > 128
                    || ingredients.stream().map(Ingredient::itemId).distinct().count() != ingredients.size())
                throw new IllegalArgumentException("A fixed submission needs distinct, bounded ingredients");
        }
    }

    /** Each accepted submission requires and consumes exactly one sample via its actions. */
    public record Entry(String id, String itemId, Predicate<ServerPlayer> available,
                        List<DialogueAction> actions, String successNodeId) {
        public Entry {
            Objects.requireNonNull(id);
            Objects.requireNonNull(itemId);
            Objects.requireNonNull(available);
            actions = List.copyOf(actions);
        }
    }
}

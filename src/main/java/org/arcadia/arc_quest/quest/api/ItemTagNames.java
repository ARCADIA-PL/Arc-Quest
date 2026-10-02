package org.arcadia.arc_quest.quest.api;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Server-safe tag labels. Components resolve the current language only when displayed. */
public final class ItemTagNames {
    private static final Set<String> DEFAULT_LABELS = Set.of("arc_quest.obj.collect", "arc_quest.obj.deliver", "arc_quest.obj.craft");

    private ItemTagNames() {}

    public static String translationKey(ResourceLocation tagId) {
        Objects.requireNonNull(tagId, "tagId");
        return "tag.item." + tagId.getNamespace() + "." + tagId.getPath().replace('/', '.');
    }

    public static Component name(ResourceLocation tagId) {
        return Component.translatableWithFallback(translationKey(tagId), friendlyFallback(tagId));
    }

    /** Author/resource-pack translations win; unidentified groups retain a readable, distinct name. */
    public static String friendlyFallback(ResourceLocation tagId) {
        Objects.requireNonNull(tagId, "tagId");
        String[] parts = tagId.getPath().split("/");
        StringBuilder label = new StringBuilder();
        // Conventional material paths read naturally as "Copper Ingots", rather than "Ingots Copper".
        for (int i = parts.length - 1; i >= 0; i--) {
            if (!label.isEmpty()) label.append(' ');
            label.append(words(parts[i]));
        }
        if (!Set.of("minecraft", "forge", "c").contains(tagId.getNamespace()))
            label.append(" (").append(words(tagId.getNamespace())).append(')');
        return label.toString();
    }

    private static String words(String value) {
        return Arrays.stream(value.split("[_\\-.]+"))
                .filter(word -> !word.isEmpty())
                .map(word -> word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1))
                .collect(Collectors.joining(" "));
    }

    /** Rebind standard target arguments after JSON/server export flattens component arguments to strings. */
    public static Component objectiveLabel(Component label, ResourceLocation tagId) {
        if (tagId == null || !(label.getContents() instanceof TranslatableContents translated)
                || !DEFAULT_LABELS.contains(translated.getKey()) || translated.getArgs().length < 2) return label;
        Object[] args = translated.getArgs().clone();
        args[0] = name(tagId);
        var rebuilt = Component.translatableWithFallback(translated.getKey(), translated.getFallback(), args)
                .withStyle(label.getStyle());
        label.getSiblings().forEach(sibling -> rebuilt.append(sibling.copy()));
        return rebuilt;
    }
}

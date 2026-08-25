package org.arcadia.arc_quest.command;

import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.function.Function;
import java.util.concurrent.CompletableFuture;

public final class ArcQuestSuggestionUtil {
    private ArcQuestSuggestionUtil() {
    }

    public static CompletableFuture<Suggestions> suggest(Iterable<String> values,
                                                          SuggestionsBuilder builder,
                                                          Function<String, Component> tooltipFactory) {
        String remaining = builder.getRemaining();
        for (String value : values) {
            if (SharedSuggestionProvider.matchesSubStr(remaining, value)) {
                builder.suggest(value, tooltipFactory.apply(value));
            }
        }
        return builder.buildFuture();
    }

    public static Component idTooltip(String type, String id) {
        return Component.literal(id);
    }

    public static Component displayTooltip(String type, Component displayName, String id) {
        return displayName.copy();
    }
}

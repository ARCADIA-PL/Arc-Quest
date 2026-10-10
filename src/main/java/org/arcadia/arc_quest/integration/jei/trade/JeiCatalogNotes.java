package org.arcadia.arc_quest.integration.jei.trade;

import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.core.time.CooldownStatus;
import org.arcadia.arc_quest.dialogue.api.CooldownType;
import org.arcadia.arc_quest.integration.jei.api.JeiDisplayAdapters;
import org.arcadia.arc_quest.integration.jei.api.JeiIngredient;
import java.util.List;

/** Common, non-executable explanations for trade and gacha catalog rows. */
public final class JeiCatalogNotes {
    private JeiCatalogNotes() {}

    public static Component text(String suffix, String fallback, Object... args) {
        return Component.translatableWithFallback("arc_quest.jei." + suffix, fallback, args);
    }

    public static void append(JeiDisplayAdapters.Presentation display, boolean cost,
                              List<JeiIngredient> ingredients, List<Component> notes) {
        ingredients.addAll(display.ingredients());
        for (Component note : display.notes()) {
            notes.add(cost ? text("cost_detail", "Cost: %s", note)
                    : text("reward_detail", "Reward: %s", note));
        }
    }

    public static void availability(List<Component> notes, JeiAvailability state, int limit) {
        if (!state.qualified()) notes.add(text("qualification_unmet", "Conditions not met"));
        if (limit > 0) notes.add(text("limit_remaining", "Remaining uses: %s / %s", state.remaining(), limit));
    }

    public static void cooldown(List<Component> notes, CooldownType type, long value, int resetTick,
                                 int limit, CooldownStatus status, boolean active) {
        if (type == CooldownType.NONE) return;
        if (active) {
            int seconds = type == CooldownType.SECONDS ? status.remainingRealSecondsCeiling()
                    : (int) Math.min(Integer.MAX_VALUE, (status.remainingGameTicks() + 19L) / 20L);
            notes.add(type == CooldownType.SECONDS
                    ? text("cooldown_remaining_real", "Ready in %s s", seconds)
                    : text("cooldown_remaining_game", "Ready in %s game seconds", seconds));
            return;
        }
        Component duration = switch (type) {
            case SECONDS -> text("cooldown_seconds", "Cooldown: %s s", value);
            case GAME_DAY -> text("cooldown_day", "Cooldown: until the next game day");
            case GAME_TICK -> text("cooldown_time", "Cooldown: until %s in game", gameTime(resetTick));
            case NONE -> throw new IllegalStateException("No cooldown");
        };
        notes.add(limit > 0 ? text("cooldown_after_limit", "After all uses: %s", duration) : duration);
    }

    private static String gameTime(int tick) {
        int minute = Math.floorMod(tick, 24000) * 60 / 1000;
        return String.format(java.util.Locale.ROOT, "%02d:%02d", (minute / 60 + 6) % 24, minute % 60);
    }
}

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
        notes.add(state.qualified() ? text("qualification_met", "Additional conditions: met")
                : text("qualification_unmet", "Additional conditions: not met"));
        if (limit > 0) notes.add(text("limit_remaining", "Quota remaining: %s / %s", state.remaining(), limit));
        if (state.resetDue()) notes.add(text("reset_due", "A due reset will be applied when the shop validates the next action."));
        if (!state.meetsStateConditions()) notes.add(text("state_unavailable", "Currently unavailable because of conditions, quota, or cooldown."));
        notes.add(text("readonly_payment", "Read-only snapshot. Payment, eligibility and events are checked again by the server."));
    }

    public static void cooldown(List<Component> notes, CooldownType type, long value, int resetTick,
                                 int limit, CooldownStatus status, boolean active) {
        switch (type) {
            case NONE -> { return; }
            case SECONDS -> notes.add(text("cooldown_seconds", "Cooldown: %s real seconds", value));
            case GAME_DAY -> notes.add(text("cooldown_day", "Cooldown: next game day"));
            case GAME_TICK -> notes.add(text("cooldown_tick", "Cooldown: game-day reset tick %s", resetTick));
        }
        if (limit > 0) notes.add(text("cooldown_after_quota", "Cooldown starts only when the quota is exhausted."));
        if (active) {
            int seconds = type == CooldownType.SECONDS ? status.remainingRealSecondsCeiling()
                    : (int) Math.min(Integer.MAX_VALUE, (status.remainingGameTicks() + 19L) / 20L);
            notes.add(type == CooldownType.SECONDS
                    ? text("cooldown_remaining_real", "Remaining at snapshot: %s real seconds", seconds)
                    : text("cooldown_remaining_game", "Remaining at snapshot: %s game seconds", seconds));
        }
    }
}

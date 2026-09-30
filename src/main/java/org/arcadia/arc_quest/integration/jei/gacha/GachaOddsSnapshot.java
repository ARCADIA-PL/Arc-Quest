package org.arcadia.arc_quest.integration.jei.gacha;

import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.trade.gacha.api.GachaItem;
import org.arcadia.arc_quest.trade.gacha.api.GachaShopDefinition;
import org.arcadia.arc_quest.trade.gacha.api.PityConfig;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * Deterministic read-only probability snapshot. Every condition and weight is evaluated once.
 * This does not fire PreDrawEvent: extensions may change the pity counter at actual draw time.
 */
public final class GachaOddsSnapshot {
    public record Outcome(GachaItem item, int baseWeight, int effectiveWeight,
                          double ordinaryPercent, double nextPercent) {}
    private final List<Outcome> outcomes;
    private final boolean pityActive;
    private final boolean hasNextOutcome;

    public static GachaOddsSnapshot capture(GachaShopDefinition shop, ServerPlayer player, ArcQuestPlayer data) {
        return new GachaOddsSnapshot(shop.getGachaPool().getItems(), item -> item.isVisible(player, data),
                item -> item.getEffectiveWeight(player, data), shop.getPityConfig(), data.getGachaPityCounter(shop.getShopId()));
    }

    GachaOddsSnapshot(List<GachaItem> items, Predicate<GachaItem> visible, ToIntFunction<GachaItem> weight,
                      @Nullable PityConfig pity, int counter) {
        List<Weighted> evaluated = new ArrayList<>();
        long ordinaryTotal = 0L;
        for (GachaItem item : items) {
            if (!visible.test(item)) continue;
            int value = Math.max(0, weight.applyAsInt(item));
            evaluated.add(new Weighted(item, value));
            ordinaryTotal += value;
        }
        pityActive = pity != null && counter >= pity.getPityThreshold();
        long nextTotal = 0L;
        for (Weighted candidate : evaluated) nextTotal += nextWeight(candidate, pityActive ? pity : null);
        hasNextOutcome = nextTotal > 0;
        List<Outcome> described = new ArrayList<>();
        for (Weighted candidate : evaluated) {
            described.add(new Outcome(candidate.item(), candidate.item().getBaseWeight(), candidate.weight(),
                    percent(candidate.weight(), ordinaryTotal),
                    percent(nextWeight(candidate, pityActive ? pity : null), nextTotal)));
        }
        outcomes = List.copyOf(described);
    }

    public List<Outcome> outcomes() { return outcomes; }
    public boolean pityActive() { return pityActive; }
    public boolean hasNextOutcome() { return hasNextOutcome; }

    private static int nextWeight(Weighted candidate, @Nullable PityConfig pity) {
        if (pity == null) return candidate.weight();
        if (pity.getGuaranteedItemId() != null) {
            // Explicit named guarantees intentionally ignore weight, but still require visibility.
            return pity.getGuaranteedItemId().equals(candidate.item().getItemId()) ? 1 : 0;
        }
        return pity.getGuaranteedRarity() == candidate.item().getRarity() ? candidate.weight() : 0;
    }

    private static double percent(int weight, long total) { return total == 0L ? 0D : weight * 100D / total; }
    private record Weighted(GachaItem item, int weight) {}
}

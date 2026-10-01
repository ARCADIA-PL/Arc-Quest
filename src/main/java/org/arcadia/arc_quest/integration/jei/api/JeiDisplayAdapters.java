package org.arcadia.arc_quest.integration.jei.api;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.arcadia.arc_quest.quest.api.IReward;
import org.arcadia.arc_quest.quest.api.ObjectiveEntry;
import org.arcadia.arc_quest.quest.api.ObjectiveItemResolver;
import org.arcadia.arc_quest.quest.logic.QuestProgressHandler;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import org.arcadia.arc_quest.quest.api.ObjectiveType;
import org.arcadia.arc_quest.quest.api.QuestTextContext;
import org.arcadia.arc_quest.quest.reward.ItemReward;
import org.arcadia.arc_quest.trade.api.ITradeOffer;
import org.arcadia.arc_quest.trade.offer.CompositeTradeOffer;
import org.arcadia.arc_quest.trade.offer.ItemTradeOffer;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Read-only extension point. Adapters must never grant rewards, execute offers, or consume items. */
public final class JeiDisplayAdapters {
    private static final Map<Class<?>, OfferAdapter<?>> OFFERS = new LinkedHashMap<>();
    private static final Map<Class<?>, RewardAdapter<?>> REWARDS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, ObjectiveAdapter> OBJECTIVES = new LinkedHashMap<>();
    private JeiDisplayAdapters() {}

    public record Presentation(List<JeiIngredient> ingredients, List<Component> notes) {
        public Presentation {
            ingredients = List.copyOf(ingredients);
            notes = notes.stream().map(Component::copy).map(note -> (Component) note).toList();
        }
        @Override public List<Component> notes() {
            return notes.stream().map(Component::copy).map(note -> (Component) note).toList();
        }
        public static Presentation empty() { return new Presentation(List.of(), List.of()); }
    }
    @FunctionalInterface public interface OfferAdapter<T extends ITradeOffer> {
        Presentation describe(T offer, @Nullable ServerPlayer player, boolean consumed);
    }
    @FunctionalInterface public interface RewardAdapter<T extends IReward> {
        Presentation describe(T reward, @Nullable ServerPlayer player);
    }
    @FunctionalInterface public interface ObjectiveAdapter {
        Presentation describe(ObjectiveEntry objective, @Nullable ServerPlayer player);
    }
    public static synchronized <T extends ITradeOffer> void registerOffer(Class<T> type, OfferAdapter<T> adapter) {
        OFFERS.put(Objects.requireNonNull(type), Objects.requireNonNull(adapter));
    }
    public static synchronized <T extends IReward> void registerReward(Class<T> type, RewardAdapter<T> adapter) {
        REWARDS.put(Objects.requireNonNull(type), Objects.requireNonNull(adapter));
    }
    public static synchronized void registerObjective(ResourceLocation type, ObjectiveAdapter adapter) {
        OBJECTIVES.put(Objects.requireNonNull(type), Objects.requireNonNull(adapter));
    }

    public static Presentation offer(ITradeOffer offer, @Nullable ServerPlayer player, boolean consumed) {
        validateBuiltinOffers(Objects.requireNonNull(offer), consumed,
                Collections.newSetFromMap(new IdentityHashMap<>()));
        return describeOffer(offer, player, consumed);
    }

    @SuppressWarnings("unchecked")
    private static Presentation describeOffer(ITradeOffer offer, @Nullable ServerPlayer player, boolean consumed) {
        OfferAdapter<ITradeOffer> custom;
        synchronized (JeiDisplayAdapters.class) { custom = (OfferAdapter<ITradeOffer>) find(OFFERS, offer); }
        if (custom != null) return Objects.requireNonNull(custom.describe(offer, player, consumed), "offer presentation");
        if (offer instanceof CompositeTradeOffer composite) {
            List<JeiIngredient> ingredients = new ArrayList<>();
            List<Component> notes = new ArrayList<>();
            for (ITradeOffer child : composite.getChildren()) {
                Presentation part = describeOffer(child, player, consumed);
                ingredients.addAll(part.ingredients());
                notes.addAll(part.notes());
            }
            return new Presentation(ingredients, notes);
        }
        if (offer instanceof ItemTradeOffer item) {
            int amount = item.getDisplayCount(player);
            List<ItemStack> alternatives = item.getDisplayStacks();
            Component name = item.hasItemTag() ? Component.literal("#" + item.getItemTagId())
                    : alternatives.stream().filter(stack -> !stack.isEmpty()).findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("Item offer has no real item")).getHoverName();
            // describe() intentionally uses previewCount; server catalogs need the resolved amount.
            var ingredient = new JeiIngredient(alternatives, amount, consumed,
                    Component.translatable("arc_quest.trade.item", name, amount), consumed && item.requiresExactNbt());
            return new Presentation(List.of(ingredient), List.of());
        }
        return new Presentation(List.of(), List.of(offer.describe()));
    }

    @SuppressWarnings("unchecked")
    public static Presentation reward(IReward reward, @Nullable ServerPlayer player) {
        Objects.requireNonNull(reward);
        RewardAdapter<IReward> custom;
        synchronized (JeiDisplayAdapters.class) { custom = (RewardAdapter<IReward>) find(REWARDS, reward); }
        if (custom != null) return Objects.requireNonNull(custom.describe(reward, player), "reward presentation");
        if (reward instanceof ItemReward item) {
            ItemStack stack = new ItemStack(item.getItem());
            if (stack.isEmpty()) throw new IllegalArgumentException("Item reward has no real item");
            return new Presentation(List.of(JeiIngredient.of(stack, item.getCount(), false)), List.of());
        }
        return new Presentation(List.of(), List.of(Component.literal(reward.describe())));
    }

    public static Presentation objective(ObjectiveEntry objective, @Nullable ServerPlayer player) {
        Objects.requireNonNull(objective);
        ObjectiveAdapter custom;
        synchronized (JeiDisplayAdapters.class) { custom = OBJECTIVES.get(objective.getType().getId()); }
        if (custom != null) return Objects.requireNonNull(custom.describe(objective, player), "objective presentation");
        ObjectiveType type = objective.getType();
        boolean consumed = ObjectiveType.OFFER.equals(type) || ObjectiveType.DELIVER.equals(type);
        if (!consumed && !ObjectiveType.COLLECT.equals(type) && !ObjectiveType.CRAFT.equals(type)) return Presentation.empty();
        List<ItemStack> alternatives = ObjectiveItemResolver.candidates(objective);
        ResourceLocation tag = objective.getTargetTagResourceLocation();
        Component name = tag == null ? objective.getDisplayText(player, QuestTextContext.empty()) : Component.literal("#" + tag);
        int required = player == null ? objective.resolveRequiredCount(null) : QuestProgressHandler.resolveRequiredCount(
                player, objective, ArcQuestPlayerManager.getOrCreate(player));
        return new Presentation(List.of(new JeiIngredient(alternatives, required, consumed, name)), List.of());
    }

    /**
     * The enclosing list does not change ItemTradeOffer.execute's intrinsic direction. Reject invalid
     * Java-defined trees before even invoking extension adapters, so no partial transaction is indexed.
     */
    private static void validateBuiltinOffers(ITradeOffer offer, boolean consumed, Set<ITradeOffer> path) {
        Objects.requireNonNull(offer, "composite child");
        if (path.size() >= 64 || !path.add(offer)) {
            throw new IllegalArgumentException("Cyclic or excessively nested offer presentation");
        }
        try {
            if (offer instanceof ItemTradeOffer item) {
                if (item.isCost() != consumed) throw new IllegalArgumentException("Item offer direction does not match its cost/reward role");
                if (!item.isCost() && item.getItem() == null) {
                    throw new IllegalArgumentException("Item tag rewards cannot execute: an explicit reward item is required");
                }
            } else if (offer instanceof CompositeTradeOffer composite) {
                for (ITradeOffer child : composite.getChildren()) validateBuiltinOffers(child, consumed, path);
            }
        } finally {
            path.remove(offer);
        }
    }

    private static <T> T find(Map<Class<?>, T> adapters, Object value) {
        T exact = adapters.get(value.getClass());
        if (exact != null) return exact;
        for (var entry : adapters.entrySet()) if (entry.getKey().isInstance(value)) return entry.getValue();
        return null;
    }
}

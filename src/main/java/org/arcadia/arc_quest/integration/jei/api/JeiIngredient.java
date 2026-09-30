package org.arcadia.arc_quest.integration.jei.api;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.List;
import java.util.Objects;

/** A read-only requirement/output; alternatives are OR, separate ingredients are AND. No JEI dependency. */
public record JeiIngredient(List<ItemStack> alternatives, int amount, boolean consumed, Component description, boolean exactNbt) {
    public JeiIngredient(List<ItemStack> alternatives, int amount, boolean consumed, Component description) {
        this(alternatives, amount, consumed, description, false);
    }

    public JeiIngredient {
        Objects.requireNonNull(alternatives);
        if (amount < 1) throw new IllegalArgumentException("Ingredient amount must be positive");
        alternatives = alternatives.stream().filter(s -> s != null && !s.isEmpty()).map(s -> {
            ItemStack copy = s.copy();
            copy.setCount(1);
            return copy;
        }).toList();
        description = Objects.requireNonNull(description).copy();
    }

    @Override public List<ItemStack> alternatives() { return alternatives.stream().map(ItemStack::copy).toList(); }
    @Override public Component description() { return description.copy(); }

    /** Matches input lookup semantics without requiring the focused stack to contain the full amount. */
    public boolean matchesInput(ItemStack focus) {
        if (focus == null || focus.isEmpty()) return false;
        return alternatives.stream().anyMatch(alternative -> exactNbt
                ? ItemStack.isSameItemSameTags(alternative, focus)
                : focus.is(alternative.getItem()));
    }

    public boolean sameContent(JeiIngredient other) {
        if (other == null || amount != other.amount || consumed != other.consumed || exactNbt != other.exactNbt
                || !description.equals(other.description) || alternatives.size() != other.alternatives.size()) return false;
        for (int i = 0; i < alternatives.size(); i++)
            if (!ItemStack.isSameItemSameTags(alternatives.get(i), other.alternatives.get(i))) return false;
        return true;
    }

    public static JeiIngredient of(ItemStack stack, int amount, boolean consumed) {
        return new JeiIngredient(List.of(stack), amount, consumed, stack.getHoverName());
    }
}

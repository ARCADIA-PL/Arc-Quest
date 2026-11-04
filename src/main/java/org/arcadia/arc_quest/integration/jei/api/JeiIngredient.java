package org.arcadia.arc_quest.integration.jei.api;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.List;
import java.util.Objects;

/** A read-only requirement/output; alternatives are OR, separate ingredients are AND. No JEI dependency. */
public record JeiIngredient(List<ItemStack> alternatives, int amount, boolean consumed, Component description, boolean exactNbt, org.arcadia.arc_quest.quest.api.StackMatchSpec matchSpec) {
    public JeiIngredient(List<ItemStack> alternatives, int amount, boolean consumed, Component description, boolean exactNbt) {
        this(alternatives, amount, consumed, description, exactNbt, null);
    }

    public JeiIngredient(List<ItemStack> alternatives, int amount, boolean consumed, Component description) {
        this(alternatives, amount, consumed, description, false);
    }

    public JeiIngredient {
        Objects.requireNonNull(alternatives);
        if (matchSpec == null) matchSpec = new org.arcadia.arc_quest.quest.api.StackMatchSpec(exactNbt ? org.arcadia.arc_quest.quest.api.StackMatchSpec.Mode.EXACT_NBT : org.arcadia.arc_quest.quest.api.StackMatchSpec.Mode.ITEM, "");
        exactNbt = matchSpec.mode() == org.arcadia.arc_quest.quest.api.StackMatchSpec.Mode.EXACT_NBT;
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
        return alternatives.stream().anyMatch(alternative -> matchSpec.matches(alternative, focus));
    }

    public boolean sameContent(JeiIngredient other) {
        if (other == null || amount != other.amount || consumed != other.consumed || exactNbt != other.exactNbt
                || !matchSpec.equals(other.matchSpec) || !description.equals(other.description) || alternatives.size() != other.alternatives.size()) return false;
        for (int i = 0; i < alternatives.size(); i++)
            if (!ItemStack.isSameItemSameTags(alternatives.get(i), other.alternatives.get(i))) return false;
        return true;
    }

    public static JeiIngredient of(ItemStack stack, int amount, boolean consumed) {
        return new JeiIngredient(List.of(stack), amount, consumed, stack.getHoverName());
    }
}

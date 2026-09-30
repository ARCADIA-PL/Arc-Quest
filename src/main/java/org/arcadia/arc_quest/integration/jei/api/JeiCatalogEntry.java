package org.arcadia.arc_quest.integration.jei.api;

import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.Objects;

/** A player-authorized catalog row. Navigation targets are identifiers, never executable commands. */
public record JeiCatalogEntry(String id, Kind kind, Component title, List<JeiIngredient> inputs,
                              List<JeiIngredient> outputs, List<Component> notes,
                              String navigationTarget, String navigationDetail) {
    public enum Kind { TRADE, GACHA, QUEST_REQUIREMENT, QUEST_REWARD, GUIDE }

    public JeiCatalogEntry {
        if (Objects.requireNonNull(id).isBlank()) throw new IllegalArgumentException("Blank catalog id");
        Objects.requireNonNull(kind);
        title = Objects.requireNonNull(title).copy();
        inputs = List.copyOf(inputs);
        outputs = List.copyOf(outputs);
        notes = notes.stream().map(Component::copy).map(c -> (Component)c).toList();
        navigationTarget = Objects.requireNonNull(navigationTarget);
        navigationDetail = Objects.requireNonNull(navigationDetail);
    }
    @Override public Component title() { return title.copy(); }
    @Override public List<Component> notes() { return notes.stream().map(Component::copy).map(c -> (Component)c).toList(); }

    /** Preserve open layouts when only unrelated catalog rows changed. */
    public boolean sameContent(JeiCatalogEntry other) {
        return other != null && id.equals(other.id) && kind == other.kind && title.equals(other.title)
                && notes.equals(other.notes) && navigationTarget.equals(other.navigationTarget)
                && navigationDetail.equals(other.navigationDetail)
                && sameIngredients(inputs, other.inputs) && sameIngredients(outputs, other.outputs);
    }
    private static boolean sameIngredients(List<JeiIngredient> left, List<JeiIngredient> right) {
        if (left.size() != right.size()) return false;
        for (int i = 0; i < left.size(); i++) if (!left.get(i).sameContent(right.get(i))) return false;
        return true;
    }
}

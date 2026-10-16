package org.arcadia.arc_quest.quest.api;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.List;
import java.util.Objects;

/** Read-only, per-player supplementary details. These never become collection requirements or rewards. */
public record CollectionDetailDocument(Component title, List<Section> sections) {
    public static final CollectionDetailDocument EMPTY = new CollectionDetailDocument(Component.empty(), List.of());
    public CollectionDetailDocument {
        title = Objects.requireNonNull(title).copy();
        sections = List.copyOf(sections);
        if (sections.size() > 16 || sections.stream().mapToInt(section -> section.lines().size()).sum() > 128)
            throw new IllegalArgumentException("Too many supplementary detail sections/lines");
    }
    public record Section(Component title, Component status, Component hint, List<Line> lines) {
        public Section {
            title = Objects.requireNonNull(title).copy();
            status = Objects.requireNonNull(status).copy();
            hint = Objects.requireNonNull(hint).copy();
            lines = List.copyOf(lines);
            if (lines.size() > 64) throw new IllegalArgumentException("Too many detail lines");
        }
    }
    /** Negative owned means the count is informational, e.g. an already completed one-time payment. */
    public record Line(List<ItemStack> alternatives, Component label, int required, int owned) {
        public Line {
            if (alternatives.size() > 64 || required < 0 || owned < -1)
                throw new IllegalArgumentException("Invalid supplementary material line");
            alternatives = alternatives.stream().map(ItemStack::copy).toList();
            label = Objects.requireNonNull(label).copy();
        }
    }
}

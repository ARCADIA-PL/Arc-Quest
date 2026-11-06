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
    /** Layout semantics are reusable across providers; no renderer depends on an addon's IDs/text. */
    public enum Layout {
        ROWS, ITEM_GRID, LARGE_ITEM_GRID, ITEM_CARDS,
        SOURCE_CARDS, UPGRADE_PATH, VALUABLE_ITEM_GRID, LOCATION_INFO
    }
    public record Section(Component title, Component status, Component hint, List<Line> lines, Layout layout) {
        public Section(Component title, Component status, Component hint, List<Line> lines) {
            this(title, status, hint, lines, Layout.ROWS);
        }
        public static Section itemGrid(Component title, List<ItemStack> items) {
            return new Section(title, Component.empty(), Component.empty(), items.stream()
                    .map(item -> new Line(List.of(item), item.getHoverName(), 0, -1)).toList(), Layout.ITEM_GRID);
        }
        public Section {
            layout = Objects.requireNonNull(layout);
            title = Objects.requireNonNull(title).copy();
            status = Objects.requireNonNull(status).copy();
            hint = Objects.requireNonNull(hint).copy();
            lines = List.copyOf(lines);
            if (lines.size() > 64) throw new IllegalArgumentException("Too many detail lines");
        }
    }
    /** Negative owned means the count is informational, e.g. an already completed one-time payment. */
    public record Link(net.minecraft.resources.ResourceLocation entry, String variant) {
        public Link {
            Objects.requireNonNull(entry);
            variant = Objects.requireNonNull(variant);
            if (variant.length() > 128) throw new IllegalArgumentException("Detail variant too long");
        }
    }
    public record Line(List<ItemStack> alternatives, Component label, int required, int owned,
                       List<Component> details, Link link) {
        public Line(List<ItemStack> alternatives, Component label, int required, int owned) {
            this(alternatives, label, required, owned, List.of(), null);
        }
        public Line {
            if (alternatives.size() > 64 || required < 0 || owned < -1)
                throw new IllegalArgumentException("Invalid supplementary material line");
            alternatives = alternatives.stream().map(ItemStack::copy).toList();
            label = Objects.requireNonNull(label).copy();
            details = details.stream().map(Component::copy).map(c -> (Component) c).toList();
            if (details.size() > 16) throw new IllegalArgumentException("Too many detail tooltip lines");
        }
    }
}

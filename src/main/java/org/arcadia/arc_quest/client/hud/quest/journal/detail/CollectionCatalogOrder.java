package org.arcadia.arc_quest.client.hud.quest.journal.detail;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.api.CollectionCategoryDefinition;
import org.arcadia.arc_quest.quest.api.CollectionQuestConfig;
import org.arcadia.arc_quest.quest.api.CollectionSheetDefinition;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/** Bookmark appearance is live; catalog ordering changes only at an actual browser refresh. */
public final class CollectionCatalogOrder {
    private Object progress;
    private String filter;
    private Set<ResourceLocation> favorites = Set.of();
    private boolean initialized;
    private CollectionQuestConfig config;
    private CollectionSheetDefinition sheet;
    private List<CollectionCategoryDefinition> categories = List.of();
    private Map<ResourceLocation, EntryOrder> entryOrders = Map.of();
    private Map<String, BindingOrder> bindingOrders = Map.of();
    private static final EntryOrder UNCONFIGURED_ENTRY = new EntryOrder(Integer.MAX_VALUE, 0, Integer.MAX_VALUE);
    private static final BindingOrder UNCONFIGURED_BINDING = new BindingOrder(0, Integer.MAX_VALUE);

    /** Cache immutable author ordering independently of which binding represents a card's progress. */
    public boolean configure(CollectionQuestConfig config, CollectionSheetDefinition sheet) {
        Objects.requireNonNull(config);
        Objects.requireNonNull(sheet);
        if (this.config == config && this.sheet == sheet) return false;
        this.config = config;
        this.sheet = sheet;
        initialized = false;
        categories = config.getCategories().stream()
                .sorted(Comparator.comparingInt(CollectionCategoryDefinition::getSortOrder)).toList();
        Map<String, Integer> categoryRanks = new HashMap<>();
        for (int index = 0; index < categories.size(); index++)
            categoryRanks.putIfAbsent(categories.get(index).getCategoryId(), index);
        Map<ResourceLocation, EntryOrder> entries = new HashMap<>();
        Map<String, BindingOrder> bindings = new HashMap<>();
        for (int index = 0; index < sheet.getBindings().size(); index++) {
            var binding = sheet.getBindings().get(index);
            bindings.put(binding.getBindingId(), new BindingOrder(binding.getSortOrder(), index));
            var entry = config.getEntry(binding.getEntryId());
            if (entry != null) entries.putIfAbsent(entry.getEntryId(), new EntryOrder(
                    categoryRanks.getOrDefault(entry.getCategoryId(), Integer.MAX_VALUE), entry.getSortOrder(), index));
        }
        entryOrders = Map.copyOf(entries);
        bindingOrders = Map.copyOf(bindings);
        return true;
    }

    public List<CollectionCategoryDefinition> categories() { return categories; }

    public void refresh(Object progress, String filter, Supplier<Set<ResourceLocation>> bookmarks) {
        if (initialized && this.progress == progress && Objects.equals(this.filter, filter)) return;
        this.progress = progress;
        this.filter = filter;
        this.favorites = Set.copyOf(bookmarks.get());
        initialized = true;
    }

    public <T> List<T> sort(List<T> entries, Function<T, ResourceLocation> identity) {
        Comparator<T> comparator = Comparator.comparing(entry -> !favorites.contains(identity.apply(entry)));
        comparator = comparator.thenComparingInt(entry -> entryOrder(identity.apply(entry)).categoryRank)
                .thenComparingInt(entry -> entryOrder(identity.apply(entry)).sortOrder)
                .thenComparingInt(entry -> entryOrder(identity.apply(entry)).declarationIndex);
        return entries.stream().sorted(comparator).toList();
    }

    public <T> List<T> sortBindings(List<T> bindings, Function<T, String> identity) {
        return bindings.stream().sorted(Comparator
                .comparingInt((T binding) -> bindingOrder(identity.apply(binding)).sortOrder)
                .thenComparingInt(binding -> bindingOrder(identity.apply(binding)).declarationIndex)).toList();
    }

    /** Keep a scrolled reader's top specimen and row offset when the same browser view is reordered. */
    public static <T> double preserveScroll(List<T> previous, List<T> next, Function<T, ResourceLocation> identity,
                                          double scroll, int previousColumns, int nextColumns, int rowHeight) {
        if (scroll <= 0 || previous.isEmpty() || next.isEmpty() || previousColumns < 1 || nextColumns < 1 || rowHeight < 1)
            return scroll;
        int row = (int) Math.floor(scroll / rowHeight);
        int first = Math.min(previous.size() - 1, row * previousColumns);
        ResourceLocation anchor = identity.apply(previous.get(first));
        for (int index = 0; index < next.size(); index++) {
            if (Objects.equals(anchor, identity.apply(next.get(index))))
                return (index / nextColumns) * (double) rowHeight + scroll - row * (double) rowHeight;
        }
        return scroll;
    }

    private EntryOrder entryOrder(ResourceLocation id) { return entryOrders.getOrDefault(id, UNCONFIGURED_ENTRY); }
    private BindingOrder bindingOrder(String id) { return bindingOrders.getOrDefault(id, UNCONFIGURED_BINDING); }

    private record EntryOrder(int categoryRank, int sortOrder, int declarationIndex) {}
    private record BindingOrder(int sortOrder, int declarationIndex) {}

    public void reset() {
        initialized = false;
        progress = null;
        filter = null;
        favorites = Set.of();
        config = null;
        sheet = null;
        categories = List.of();
        entryOrders = Map.of();
        bindingOrders = Map.of();
    }
}

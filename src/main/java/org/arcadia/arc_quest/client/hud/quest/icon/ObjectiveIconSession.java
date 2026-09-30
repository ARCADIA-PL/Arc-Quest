package org.arcadia.arc_quest.client.hud.quest.icon;

import net.minecraft.Util;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import org.arcadia.arc_quest.quest.api.ObjectiveEntry;
import org.arcadia.arc_quest.quest.api.ObjectiveItemResolver;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded view state belongs to the journal, so a JEI round trip retains its selection. */
public final class ObjectiveIconSession {
    private final Map<String, Entry> entries = new LinkedHashMap<>(32, 0.75f, true);
    private final Map<String, FocusTarget> visible = new LinkedHashMap<>();
    private String focused;

    public void beginFrame() {
        visible.clear();
        for (Entry entry : entries.values()) { if (!entry.seen) entry.cycle.suspend(); entry.seen = false; }
    }
    public ResolvedObjectiveIcon resolve(ObjectiveIconContext context) { return entry(context).icon; }
    public int targetCount(ObjectiveIconContext context) { return entry(context).targets.size(); }
    public ItemStack singleTarget(ObjectiveIconContext context) {
        var targets = entry(context).targets;
        return targets.size() == 1 ? targets.get(0).copy() : ItemStack.EMPTY;
    }
    public boolean isFocused(String key) { return key.equals(focused); }
    public boolean hasFocus() { return focused != null; }
    public float hoverAmount(ObjectiveIconContext context, boolean highlighted, float dt) {
        Entry state = entry(context);
        float target = highlighted ? 1f : 0f;
        state.hover += (target - state.hover) * Math.min(1f, Math.max(0f, dt) * 16f);
        return state.hover;
    }
    public void endFrame() {
        if (!visible.containsKey(focused)) focused = null;
        for (Entry entry : entries.values()) if (!entry.seen) entry.cycle.suspend();
    }
    public IconFrameSelection select(ObjectiveIconContext context, boolean hovered, boolean interactive) {
        Entry state = entry(context);
        state.seen = true;
        int index = state.cycle.select(state.keys, Util.getMillis(), !interactive || hovered || isFocused(context.key()));
        ItemStack stack = index < 0 ? ItemStack.EMPTY : state.items.get(index);
        return new IconFrameSelection(context.key(), index < 0 ? "visual" : state.keys.get(index),
                stack, state.icon.visual(), index, state.items.size(), context.generation());
    }
    public void recordFocus(String key, double x1, double y1, double x2, double y2, long generation) {
        if (x2 > x1 && y2 > y1) visible.put(key, new FocusTarget((x1 + x2) / 2, (y1 + y2) / 2, generation));
    }
    public boolean focusNext(boolean backwards) {
        if (visible.isEmpty()) return false;
        List<String> order = new ArrayList<>(visible.keySet());
        int current = order.indexOf(focused);
        int next = current < 0 ? (backwards ? order.size() - 1 : 0)
                : Math.floorMod(current + (backwards ? -1 : 1), order.size());
        focused = order.get(next);
        return true;
    }
    public FocusTarget focusedTarget() {
        FocusTarget target = visible.get(focused);
        return target != null && target.generation == ObjectiveIconsClient.generation() ? target : null;
    }
    public void clearFocus() { focused = null; }
    public void suspend() { entries.values().forEach(entry -> entry.cycle.suspend()); visible.clear(); }
    public void clear() { entries.clear(); visible.clear(); focused = null; }

    private Entry entry(ObjectiveIconContext context) {
        Entry state = entries.get(context.key());
        if (state == null) {
            if (entries.size() >= 256) entries.remove(entries.keySet().iterator().next());
            state = new Entry();
            entries.put(context.key(), state);
        }
        if (state.objective != context.objective() || state.generation != context.generation()
                || state.progress != context.progress() || state.required != context.requiredCount()) {
            state.objective = context.objective();
            state.generation = context.generation();
            state.progress = context.progress();
            state.required = context.requiredCount();
            state.icon = ObjectiveIconRegistry.resolve(context);
            state.items = state.icon.items();
            state.keys = state.items.stream().map(ObjectiveIconSession::itemKey).toList();
            state.targets = ObjectiveItemResolver.candidates(context.objective());
        }
        return state;
    }
    public static String itemKey(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()) + "/" + (stack.hasTag() ? stack.getTag().toString() : "");
    }
    public record FocusTarget(double x, double y, long generation) {}
    private static final class Entry {
        ObjectiveEntry objective;
        long generation = -1;
        int progress, required;
        boolean seen;
        float hover;
        ResolvedObjectiveIcon icon = ResolvedObjectiveIcon.none();
        List<ItemStack> items = List.of(), targets = List.of();
        List<String> keys = List.of();
        final ItemIconCycleController cycle = new ItemIconCycleController();
    }
}

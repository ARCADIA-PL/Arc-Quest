package org.arcadia.arc_quest.client.hud.quest.icon;

import net.minecraft.world.item.ItemStack;
import java.util.List;

/** Declarative result: a real item sequence, a visual, or no icon. */
public final class ResolvedObjectiveIcon {
    private static final ResolvedObjectiveIcon NONE = new ResolvedObjectiveIcon(List.of(), null);
    private final List<ItemStack> items;
    private final ObjectiveIconVisual visual;
    private ResolvedObjectiveIcon(List<ItemStack> items, ObjectiveIconVisual visual) {
        this.items = items.stream().filter(stack -> stack != null && !stack.isEmpty()).map(ItemStack::copy).toList();
        this.visual = visual;
    }
    public static ResolvedObjectiveIcon none() { return NONE; }
    public static ResolvedObjectiveIcon items(List<ItemStack> items) { return new ResolvedObjectiveIcon(items, null); }
    public static ResolvedObjectiveIcon visual(ObjectiveIconVisual visual) { return new ResolvedObjectiveIcon(List.of(), visual); }
    public List<ItemStack> items() { return items.stream().map(ItemStack::copy).toList(); }
    public ObjectiveIconVisual visual() { return visual; }
    public boolean available() { return !items.isEmpty() || visual != null && visual.available(); }
}

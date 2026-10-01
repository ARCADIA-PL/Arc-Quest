package org.arcadia.arc_quest.client.compat.jei;

import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.helpers.IStackHelper;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.advanced.ISimpleRecipeManagerPlugin;
import net.minecraft.world.item.Item;
import org.arcadia.arc_quest.integration.jei.api.JeiCatalogEntry;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Query the current authorized snapshot without retaining obsolete recipes in JEI's static recipe maps. */
final class JeiCatalogLookup implements ISimpleRecipeManagerPlugin<JeiCatalogEntry> {
    private final JeiCatalogEntry.Kind kind;
    private final IStackHelper stackHelper;
    private List<JeiCatalogEntry> source;
    private List<JeiCatalogEntry> recipes = List.of();
    private final Map<Item, List<JeiCatalogEntry>> inputs = new IdentityHashMap<>(), outputs = new IdentityHashMap<>();
    JeiCatalogLookup(JeiCatalogEntry.Kind kind, IStackHelper stackHelper) { this.kind = kind; this.stackHelper = stackHelper; }
    private void refresh() {
        List<JeiCatalogEntry> current = JeiCatalogClient.entries();
        if (current == source) return;
        source = current; inputs.clear(); outputs.clear();
        recipes = current.stream().filter(entry -> entry.kind() == kind).toList();
        for (JeiCatalogEntry recipe : recipes) {
            index(inputs, recipe, true); index(outputs, recipe, false);
        }
    }
    private static void index(Map<Item, List<JeiCatalogEntry>> target, JeiCatalogEntry entry, boolean input) {
        var items = new LinkedHashSet<Item>();
        for (var ingredient : input ? entry.inputs() : entry.outputs())
            ingredient.alternatives().forEach(stack -> items.add(stack.getItem()));
        items.forEach(item -> target.computeIfAbsent(item, ignored -> new ArrayList<>()).add(entry));
    }
    @Override public boolean isHandledInput(ITypedIngredient<?> value) { return !getRecipesForInput(value).isEmpty(); }
    @Override public boolean isHandledOutput(ITypedIngredient<?> value) { return !getRecipesForOutput(value).isEmpty(); }
    @Override public List<JeiCatalogEntry> getRecipesForInput(ITypedIngredient<?> value) {
        refresh(); return value.getItemStack().map(stack -> inputs.getOrDefault(stack.getItem(), List.of()).stream()
                .filter(entry -> entry.inputs().stream().anyMatch(ingredient -> ingredient.matchesInput(stack)))
                .toList()).orElse(List.of());
    }
    @Override public List<JeiCatalogEntry> getRecipesForOutput(ITypedIngredient<?> value) {
        refresh(); return value.getItemStack().map(stack -> outputs.getOrDefault(stack.getItem(), List.of()).stream()
                .filter(entry -> entry.outputs().stream().flatMap(ingredient -> ingredient.alternatives().stream())
                        .anyMatch(output -> stackHelper.isEquivalent(output, stack, UidContext.Recipe)))
                .toList()).orElse(List.of());
    }
    @Override public List<JeiCatalogEntry> getAllRecipes() { refresh(); return recipes; }
}

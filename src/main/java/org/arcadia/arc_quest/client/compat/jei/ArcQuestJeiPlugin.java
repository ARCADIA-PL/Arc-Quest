package org.arcadia.arc_quest.client.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IAdvancedRegistration;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.integration.jei.api.JeiCatalogEntry;
import org.arcadia.arc_quest.client.compat.jei.screen.ArcQuestJeiScreenHandlers;
import java.util.EnumMap;
import java.util.Map;

@JeiPlugin
public final class ArcQuestJeiPlugin implements IModPlugin {
    private final Map<JeiCatalogEntry.Kind, JeiCatalogCategory> categories = new EnumMap<>(JeiCatalogEntry.Kind.class);
    @Override public ResourceLocation getPluginUid() { return ResourceLocation.fromNamespaceAndPath("arc_quest", "jei"); }
    @Override public void registerCategories(IRecipeCategoryRegistration registration) {
        categories.clear();
        for (var kind : JeiCatalogEntry.Kind.values()) {
            var category = new JeiCatalogCategory(kind, registration.getJeiHelpers().getGuiHelper(), registration.getJeiHelpers().getStackHelper());
            categories.put(kind, category); registration.addRecipeCategories(category);
        }
    }
    @Override public void registerAdvanced(IAdvancedRegistration registration) {
        categories.forEach((kind, category) -> registration.addTypedRecipeManagerPlugin(category.getRecipeType(),
                new JeiCatalogLookup(kind, registration.getJeiHelpers().getStackHelper())));
    }
    @Override public void registerGuiHandlers(IGuiHandlerRegistration registration) { ArcQuestJeiScreenHandlers.register(registration); }
    @Override public void onRuntimeAvailable(IJeiRuntime runtime) {
        categories.values().forEach(category -> category.runtime(runtime));
        ArcQuestJeiScreenHandlers.runtimeAvailable(runtime);
        JeiCatalogClient.activate(() -> categories.values().forEach(category -> {
            // Public API invalidates JEI's visible-category cache even when already unhidden.
            runtime.getRecipeManager().unhideRecipeCategory(category.getRecipeType());
        }));
    }
    @Override public void onRuntimeUnavailable() {
        JeiCatalogClient.deactivate();
        ArcQuestJeiScreenHandlers.runtimeUnavailable();
        categories.values().forEach(category -> category.runtime(null));
    }
}

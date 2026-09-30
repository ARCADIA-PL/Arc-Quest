package org.arcadia.arc_quest.client.compat.jei;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.inputs.IJeiInputHandler;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.helpers.IStackHelper;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.arcadia.arc_quest.client.compat.jei.screen.ArcQuestJeiScreenHandlers;
import org.arcadia.arc_quest.integration.jei.api.JeiCatalogEntry;
import org.arcadia.arc_quest.integration.jei.api.JeiIngredient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** All slots and all notes remain accessible through independent scroll areas. */
final class JeiCatalogCategory implements IRecipeCategory<JeiCatalogEntry> {
    private final JeiCatalogEntry.Kind kind;
    private final RecipeType<JeiCatalogEntry> type;
    private final IDrawable icon;
    private final IStackHelper stackHelper;
    private final Map<JeiCatalogEntry, IFocusGroup> focuses = new WeakHashMap<>();
    private IJeiRuntime runtime;
    private boolean refreshQueued;
    JeiCatalogCategory(JeiCatalogEntry.Kind kind, IGuiHelper gui, IStackHelper stackHelper) {
        this.kind = kind;
        this.stackHelper = stackHelper;
        type = RecipeType.create("arc_quest", kind.name().toLowerCase(Locale.ROOT), JeiCatalogEntry.class);
        icon = gui.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(switch (kind) {
            case TRADE -> Items.EMERALD; case GACHA -> Items.ENDER_EYE;
            case QUEST_REQUIREMENT -> Items.WRITABLE_BOOK; case QUEST_REWARD -> Items.CHEST; case GUIDE -> Items.BOOK;
        }));
    }
    void runtime(IJeiRuntime value) { runtime = value; if (value == null) { focuses.clear(); refreshQueued = false; } }
    @Override public RecipeType<JeiCatalogEntry> getRecipeType() { return type; }
    @Override public int getWidth() { return 200; }
    @Override public int getHeight() { return 166; }
    @Override public IDrawable getIcon() { return icon; }
    @Override public Component getTitle() {
        return Component.translatableWithFallback("arc_quest.jei.category." + kind.name().toLowerCase(Locale.ROOT), switch (kind) {
            case TRADE -> "ArcQ Trading"; case GACHA -> "ArcQ Prize Pools"; case QUEST_REQUIREMENT -> "ArcQ Quest Requirements";
            case QUEST_REWARD -> "ArcQ Quest Rewards"; case GUIDE -> "ArcQ Guides";
        });
    }
    @Override public void setRecipe(IRecipeLayoutBuilder builder, JeiCatalogEntry recipe, IFocusGroup focus) {
        focuses.put(recipe, focus);
        addSlots(builder, recipe.inputs(), RecipeIngredientRole.INPUT);
        addSlots(builder, recipe.outputs(), RecipeIngredientRole.OUTPUT);
    }
    private static void addSlots(IRecipeLayoutBuilder builder, List<JeiIngredient> ingredients, RecipeIngredientRole role) {
        for (JeiIngredient ingredient : ingredients) {
            var alternatives = ingredient.alternatives();
            alternatives.forEach(stack -> stack.setCount(ingredient.amount()));
            builder.addSlot(role).addItemStacks(alternatives).addRichTooltipCallback((slot, tooltip) -> {
                tooltip.add(ingredient.description());
                tooltip.add(Component.translatableWithFallback("arc_quest.jei.amount", "Quantity: %s", ingredient.amount()));
                if (role == RecipeIngredientRole.INPUT) tooltip.add(Component.translatableWithFallback(
                        ingredient.consumed() ? "arc_quest.jei.consumed" : "arc_quest.jei.not_consumed",
                        ingredient.consumed() ? "Consumed when this action is performed" : "Requirement or subject; not consumed by viewing"));
                if (alternatives.isEmpty()) tooltip.add(Component.translatableWithFallback("arc_quest.jei.empty_tag", "No matching items in the current tags"));
                if (alternatives.stream().anyMatch(ItemStack::hasTag)) tooltip.add(Component.translatableWithFallback(
                        "arc_quest.jei.nbt", "Configured item data is shown; matching follows the original action"));
            });
        }
    }
    @Override public void createRecipeExtras(IRecipeExtrasBuilder builder, JeiCatalogEntry recipe, IFocusGroup focus) {
        if (!recipe.inputs().isEmpty()) builder.addScrollGridWidget(builder.getRecipeSlots().getSlots(RecipeIngredientRole.INPUT), 4, 2).setPosition(3, 33);
        if (!recipe.outputs().isEmpty()) builder.addScrollGridWidget(builder.getRecipeSlots().getSlots(RecipeIngredientRole.OUTPUT), 4, 2).setPosition(112, 33);
        List<FormattedText> notes = new ArrayList<>();
        if (kind == JeiCatalogEntry.Kind.GACHA) notes.add(Component.translatableWithFallback("arc_quest.jei.random_output", "Possible outcome of a random draw, not a guaranteed crafting output."));
        notes.addAll(recipe.notes());
        for (JeiIngredient ingredient : recipe.inputs()) if (ingredient.alternatives().isEmpty()) notes.add(ingredient.description());
        builder.addScrollBoxWidget(194, 57, 3, 82).setContents(notes);
        if (canNavigate()) builder.addInputHandler(new IJeiInputHandler() {
            @Override public ScreenRectangle getArea() { return new ScreenRectangle(3, 146, 194, 17); }
            @Override public boolean handleInput(double mouseX, double mouseY, IJeiUserInput input) {
                if (input.getKey().getType() != InputConstants.Type.MOUSE || input.getKey().getValue() != 0) return false;
                JeiCatalogEntry current = JeiCatalogClient.find(recipe.id());
                if (current == null) return false;
                if (!input.isSimulate()) return ArcQuestJeiScreenHandlers.navigate(current);
                return true;
            }
        });
    }
    private boolean canNavigate() { return kind == JeiCatalogEntry.Kind.QUEST_REQUIREMENT || kind == JeiCatalogEntry.Kind.QUEST_REWARD || kind == JeiCatalogEntry.Kind.GUIDE; }
    @Override public void draw(JeiCatalogEntry recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        graphics.drawString(font, font.plainSubstrByWidth(recipe.title().getString(), 194), 3, 3, 0xFF263344, false);
        graphics.drawString(font, Component.translatableWithFallback("arc_quest.jei.inputs", "Inputs / requirements"), 3, 20, 0xFF505050, false);
        graphics.drawString(font, Component.translatableWithFallback("arc_quest.jei.outputs", "Outputs"), 112, 20, 0xFF505050, false);
        if (canNavigate()) {
            boolean hover = mouseX >= 3 && mouseX < 197 && mouseY >= 146 && mouseY < 163;
            graphics.fill(3, 146, 197, 163, hover ? 0xFF2C6682 : 0xFF314957);
            graphics.drawCenteredString(font, Component.translatableWithFallback("arc_quest.jei.open_source", "Open source page"), 100, 150, 0xFFFFFFFF);
        }
        if (JeiCatalogClient.find(recipe.id()) != recipe) {
            graphics.fill(0, 0, getWidth(), getHeight(), 0xF0EEEEEE);
            graphics.drawCenteredString(font, Component.translatableWithFallback("arc_quest.jei.refreshing", "Updating available sources…"), 100, 75, 0xFF333333);
            refreshOpenView(recipe);
        }
    }
    private void refreshOpenView(JeiCatalogEntry previous) {
        if (runtime == null || refreshQueued) return;
        refreshQueued = true;
        var minecraft = Minecraft.getInstance();
        var screen = minecraft.screen;
        IFocusGroup focus = focuses.get(previous);
        JeiCatalogClient.defer(() -> {
            refreshQueued = false;
            if (runtime == null || minecraft.screen != screen) return;
            List<JeiCatalogEntry> current = JeiCatalogClient.entries().stream()
                    .filter(entry -> entry.kind() == kind).filter(entry -> matches(entry, focus)).toList();
            if (current.isEmpty()) minecraft.setScreen(runtime.getRecipesGui().getParentScreen().orElse(null));
            else runtime.getRecipesGui().showRecipes(this, current, focus == null ? List.of() : focus.getAllFocuses());
        });
    }
    private boolean matches(JeiCatalogEntry entry, IFocusGroup focuses) {
        if (focuses == null || focuses.isEmpty()) return true;
        return focuses.getItemStackFocuses().anyMatch(focus -> {
            var item = focus.getTypedValue().getIngredient();
            if (focus.getRole() == RecipeIngredientRole.INPUT)
                return entry.inputs().stream().anyMatch(ingredient -> ingredient.matchesInput(item));
            if (focus.getRole() == RecipeIngredientRole.OUTPUT)
                return entry.outputs().stream().flatMap(ingredient -> ingredient.alternatives().stream())
                        .anyMatch(stack -> stackHelper.isEquivalent(stack, item, UidContext.Recipe));
            return false;
        });
    }
    @Override public void getTooltip(ITooltipBuilder tooltip, JeiCatalogEntry recipe, IRecipeSlotsView slots, double mouseX, double mouseY) {
        if (mouseY < 18) tooltip.add(recipe.title());
    }
    @Override public ResourceLocation getRegistryName(JeiCatalogEntry recipe) {
        return ResourceLocation.fromNamespaceAndPath("arc_quest", "jei/" + UUID.nameUUIDFromBytes(recipe.id().getBytes(StandardCharsets.UTF_8)));
    }
}

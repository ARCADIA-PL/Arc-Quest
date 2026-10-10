package org.arcadia.arc_quest.integration.jei.api;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.ArrayList;
import java.util.List;

/** Player-facing labels and supplemental tooltips, independent of the JEI client API. */
public final class JeiCatalogText {
    private JeiCatalogText() {}

    public static Component heading(JeiCatalogEntry.Kind kind, boolean input) {
        return switch (kind) {
            case TRADE -> input ? text("costs", "Cost") : text("goods", "Goods");
            case GACHA -> input ? text("draw_costs", "Cost per draw") : text("possible_rewards", "Possible rewards");
            case QUEST_REQUIREMENT -> text("requirements", "Required items");
            case QUEST_REWARD -> text("rewards", "Rewards");
            case GUIDE -> text("related_items", "Related items");
        };
    }

    public static Component navigation(JeiCatalogEntry.Kind kind) {
        return kind == JeiCatalogEntry.Kind.GUIDE ? text("read_guide", "Read guide") : text("view_quest", "View quest");
    }

    /** Vanilla/JEI already supplies the item name and item data; add only action-specific details. */
    public static List<Component> tooltip(JeiCatalogEntry recipe, JeiIngredient ingredient, boolean input) {
        List<Component> lines = new ArrayList<>();
        Component description = ingredient.description();
        // Built-in trade descriptions wrap the name and count. The count has its own short line.
        if (description.getContents() instanceof TranslatableContents content
                && content.getKey().equals("arc_quest.trade.item") && content.getArgs().length > 0) {
            Component name = componentArgument(content.getArgs()[0]);
            if (name != null) description = name;
        }
        String label = description.getString();
        boolean tag = description.getContents() instanceof TranslatableContents content
                && content.getKey().startsWith("tag.item.");
        boolean itemName = ingredient.alternatives().stream().anyMatch(stack -> stack.getHoverName().getString().equals(label));
        boolean inNotes = recipe.notes().stream().anyMatch(note -> note.getString().equals(label));
        if (!label.isBlank() && (tag || (!itemName && !inNotes))) lines.add(description);
        if (ingredient.amount() > 1) lines.add(text("amount", "Quantity: %s", ingredient.amount()));
        if (input && ingredient.exactNbt()) lines.add(text("exact_data", "Matching item data required"));
        if (input && recipe.kind() == JeiCatalogEntry.Kind.QUEST_REQUIREMENT && ingredient.consumed())
            lines.add(text("submitted", "Consumed on submission"));
        return List.copyOf(lines);
    }

    /** Preserve authored details while removing blank and repeated lines. */
    public static List<Component> notes(JeiCatalogEntry recipe) {
        List<Component> lines = new ArrayList<>();
        for (Component note : recipe.notes()) {
            if (note.getContents() instanceof TranslatableContents content
                    && (content.getKey().equals("arc_quest.jei.quest.source") || content.getKey().equals("arc_quest.jei.quest.phase"))
                    && content.getArgs().length > 0) {
                Component name = componentArgument(content.getArgs()[0]);
                if (name != null && name.getString().equals(recipe.title().getString())) continue;
            }
            appendDistinct(lines, note);
        }
        for (JeiIngredient ingredient : recipe.inputs()) {
            if (ingredient.alternatives().isEmpty()) appendDistinct(lines,
                    text("missing_items", "No matching items: %s", ingredient.description()));
        }
        return List.copyOf(lines);
    }

    private static void appendDistinct(List<Component> lines, Component note) {
        if (!note.getString().isBlank() && lines.stream().noneMatch(existing -> existing.getString().equals(note.getString()))) lines.add(note);
    }

    /** Minecraft's component JSON can shorten a literal argument to a plain string. */
    private static Component componentArgument(Object argument) {
        if (argument instanceof Component component) return component;
        return argument instanceof String literal ? Component.literal(literal) : null;
    }

    private static Component text(String suffix, String fallback, Object... args) {
        return Component.translatableWithFallback("arc_quest.jei." + suffix, fallback, args);
    }
}

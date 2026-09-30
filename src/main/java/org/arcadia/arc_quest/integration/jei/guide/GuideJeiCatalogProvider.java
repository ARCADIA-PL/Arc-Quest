package org.arcadia.arc_quest.integration.jei.guide;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import org.arcadia.arc_quest.guide.api.GuideDefinition;
import org.arcadia.arc_quest.guide.api.GuideItemAssociation;
import org.arcadia.arc_quest.guide.api.GuideTextContext;
import org.arcadia.arc_quest.guide.registry.GuideRegistry;
import org.arcadia.arc_quest.integration.jei.api.JeiCatalogEntry;
import org.arcadia.arc_quest.integration.jei.api.JeiCatalogProvider;
import org.arcadia.arc_quest.integration.jei.api.JeiIngredient;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Uses granted guide IDs, never eligibility checks (which can grant guides as a side effect). */
public final class GuideJeiCatalogProvider implements JeiCatalogProvider {
    @Override
    public void collect(ServerPlayer player, ArcQuestPlayer data, Consumer<JeiCatalogEntry> sink) {
        data.getUnlockedGuides().stream().sorted().forEach(id -> {
            GuideDefinition guide = GuideRegistry.get(id);
            if (guide != null) collectGuide(player, data, guide, sink);
        });
    }

    static void collectGuide(ServerPlayer player, ArcQuestPlayer data, GuideDefinition guide,
                             Consumer<JeiCatalogEntry> sink) {
        if (!data.isGuideUnlocked(guide.getId())) return;
        GuideTextContext context = GuideTextContext.of(player, data);
        List<GuideItemAssociation> associations = guide.getItemAssociations().stream().distinct().toList();
        for (int index = 0; index < associations.size(); index++) {
            GuideItemAssociation association = associations.get(index);
            List<ItemStack> candidates = resolveItems(association);
            if (candidates.isEmpty()) continue;
            List<Component> notes = new ArrayList<>();
            notes.add(Component.translatableWithFallback("arc_quest.jei.guide.subject",
                    "Guide subject — this entry does not produce or consume items"));
            notes.add(Component.translatableWithFallback("arc_quest.jei.guide.page",
                    "Page %s of %s", association.pageIndex() + 1, guide.getPageCount()));
            Component summary = guide.getSummary(player, context);
            if (!summary.getString().isBlank()) notes.add(summary);
            notes.add(guide.getPage(association.pageIndex()).getDescriptionText().resolve(player, context));
            Component label = association.tag() ? Component.literal("#" + association.id()) : candidates.get(0).getHoverName();
            sink.accept(new JeiCatalogEntry("guide/" + guide.getId() + "/" + index,
                    JeiCatalogEntry.Kind.GUIDE, guide.getTitle(player, context),
                    List.of(new JeiIngredient(candidates, 1, false, label)), List.of(), notes,
                    guide.getId().toString(), Integer.toString(association.pageIndex())));
        }
    }

    static List<ItemStack> resolveItems(GuideItemAssociation association) {
        List<ItemStack> result = new ArrayList<>();
        if (association.tag()) {
            BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, association.id())).ifPresent(
                    holders -> holders.forEach(holder -> result.add(new ItemStack(holder.value()))));
        } else if (BuiltInRegistries.ITEM.containsKey(association.id())) {
            result.add(new ItemStack(BuiltInRegistries.ITEM.get(association.id())));
        }
        return result.stream().filter(stack -> !stack.isEmpty()).toList();
    }
}

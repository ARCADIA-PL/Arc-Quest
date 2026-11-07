package org.arcadia.arc_quest.integration.jei.collection;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.integration.jei.api.*;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.logic.CollectionRunAccess;
import org.arcadia.arc_quest.quest.logic.profile.collection.CollectionProgressProjector;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.data.sync.CollectionContentDisclosure;
import java.util.*;
import java.util.function.Consumer;

/** Catalog rows come from the same frozen, player-authorized binding projection as the journal. */
public final class CollectionJeiCatalogProvider implements JeiCatalogProvider {
    @Override public void collect(ServerPlayer player, ArcQuestPlayer data, Consumer<JeiCatalogEntry> sink) {
        if (player == null) return;
        collectKnown(player, data, runtime -> CollectionRunAccess.resolve(player.server, runtime), sink);
    }

    static void collectKnown(ServerPlayer player, ArcQuestPlayer data,
            java.util.function.Function<org.arcadia.arc_quest.quest.data.QuestRuntimeData, QuestDefinition> definitions,
            Consumer<JeiCatalogEntry> sink) {
        var view = new org.arcadia.arc_quest.quest.logic.CollectionAccessProjection(player, data, definitions);
        for (var row : view.bindings()) {
            var entry = row.entry();
            var presentation = entry.getPresentation();
            emit(sink, row.runtime().getQuestId(), row.phase().getPhaseId(), row.view().ref().bindingId(), entry,
                    "", presentation.subject(), row.view().ref().run().runId(), List.of());
            emitSources(sink, player, view, row, "", presentation.subject());
            for (var variant : presentation.variants()) {
                if (!row.view().variantIds().contains(variant.variantId())) continue;
                var context = new QuestTextContext(row.quest(), row.phase(), row.phase().getPhaseId(), Map.of());
                emit(sink, row.runtime().getQuestId(), row.phase().getPhaseId(), row.view().ref().bindingId(), entry,
                        variant.variantId(), variant.subject(), row.view().ref().run().runId(), List.of(variant.label().resolve(player, context)));
                emitSources(sink, player, view, row, variant.variantId(), variant.subject());
            }
        }
    }

    private static void emitSources(Consumer<JeiCatalogEntry> sink, ServerPlayer player,
            org.arcadia.arc_quest.quest.logic.CollectionAccessProjection view,
            org.arcadia.arc_quest.quest.logic.CollectionAccessProjection.Binding row,
            String variant, CollectionSubjectSpec subject) {
        if (subject == null) return;
        var stack = subject.resolveStack();
        if (stack.isEmpty()) return;
        var context = new QuestTextContext(row.quest(), row.phase(), row.phase().getPhaseId(), Map.of());
        for (var link : view.relations()) {
            if (!link.target().view().ref().equals(row.view().ref()) || !link.matches(variant)) continue;
            var relation = link.definition();
            List<Component> notes = new ArrayList<>();
            notes.add(Component.translatableWithFallback("arc_quest.jei.collection.source", "来源：%s", link.source().entry().getDisplayName()));
            if (!relation.method().isBlank()) notes.add(Component.literal(relation.method()));
            notes.add(relation.description().resolve(player, context));
            var ingredient = new JeiIngredient(List.of(stack), 1, false, stack.getHoverName(), false, subject.identity());
            var ref = row.view().ref();
            var address = new CollectionNavigationTarget(ref.run().questId(), ref.phaseId(), ref.bindingId(),
                    row.entry().getEntryId().toString(), variant, ref.run().runId());
            sink.accept(new JeiCatalogEntry("collection_source/" + ref.run().questId() + "/" + ref.phaseId() + "/"
                    + ref.bindingId() + "/" + ref.run().runId() + "/" + variant + "/" + link.set().id() + "/" + relation.relationId(),
                    JeiCatalogEntry.Kind.COLLECTION_SOURCE, row.entry().getDisplayName(), List.of(), List.of(ingredient), notes,
                    ref.run().questId(), ref.phaseId(), List.of(), address));
        }
    }

    private static void emit(Consumer<JeiCatalogEntry> sink, String quest, String phase, String binding,
            CollectionEntryDefinition entry, String variant, CollectionSubjectSpec subject, String run, List<Component> notes) {
        if (subject == null) return;
        var stack = subject.resolveStack();
        if (stack.isEmpty()) return;
        var ingredient = new JeiIngredient(List.of(stack), 1, false, stack.getHoverName(), false, subject.identity());
        var target = new CollectionNavigationTarget(quest, phase, binding, entry.getEntryId().toString(), variant, run);
        String key = "collection/" + quest + "/" + phase + "/" + binding + "/" + run + "/" + variant;
        sink.accept(new JeiCatalogEntry(key, JeiCatalogEntry.Kind.COLLECTION_INFO, entry.getDisplayName(), List.of(),
                List.of(), notes, quest, phase, List.of(ingredient), target));
    }
}

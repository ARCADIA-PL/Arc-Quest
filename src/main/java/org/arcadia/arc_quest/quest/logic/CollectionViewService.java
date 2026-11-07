package org.arcadia.arc_quest.quest.logic;

import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.quest.api.collection.CollectionViews;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;

/** Stateless facade: authorization cannot outlive a reset, reload, epoch or knowledge change. */
public final class CollectionViewService implements CollectionViews {
    public static final CollectionViewService INSTANCE = new CollectionViewService();
    private CollectionViewService() {}
    public static CollectionAccessProjection project(ServerPlayer player) {
        if (player == null || !player.server.isSameThread()) throw new IllegalStateException("Collection queries require the server thread");
        var data = ArcQuestPlayerManager.get(player);
        return data == null ? null : new CollectionAccessProjection(player, data, run -> CollectionRunAccess.resolve(player.server, run));
    }
    @Override public Snapshot snapshot(ServerPlayer player) {
        var view = project(player);
        if (view == null) return new Snapshot(List.of(), List.of());
        return new Snapshot(view.bindings().stream().map(CollectionAccessProjection.Binding::view).toList(),
                view.relations().stream().map(r -> new RelationView(r.definition().relationId(), r.source().view(), r.target().view(),
                        r.variants(), r.definition().method())).toList());
    }
    @Override public Optional<EntryView> resolve(ServerPlayer player, EntryRef ref, String variant) {
        var view = project(player);
        var row = view == null ? null : view.resolve(ref, variant);
        return Optional.ofNullable(row == null ? null : row.view());
    }
}

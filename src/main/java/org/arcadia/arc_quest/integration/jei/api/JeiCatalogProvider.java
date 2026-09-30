package org.arcadia.arc_quest.integration.jei.api;

import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import java.util.function.Consumer;

/** Invoked on the server thread. Emit only entries the player may see; never mutate player state. */
@FunctionalInterface
public interface JeiCatalogProvider {
    void collect(ServerPlayer player, ArcQuestPlayer data, Consumer<JeiCatalogEntry> output);
}

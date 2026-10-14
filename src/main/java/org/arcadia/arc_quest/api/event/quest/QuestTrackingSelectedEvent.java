package org.arcadia.arc_quest.api.event.quest;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;
import org.jetbrains.annotations.Nullable;

/** A validated manual tracking selection, including reselecting the current quest or phase.
 * Published on the server before tracking markers are reconciled. */
public final class QuestTrackingSelectedEvent extends Event {
    private final ServerPlayer player;
    private final String questId;
    @Nullable private final String phaseId;

    public QuestTrackingSelectedEvent(ServerPlayer player, String questId, @Nullable String phaseId) {
        this.player = java.util.Objects.requireNonNull(player);
        this.questId = java.util.Objects.requireNonNull(questId);
        this.phaseId = phaseId;
    }
    public ServerPlayer getPlayer() { return player; }
    public String getQuestId() { return questId; }
    @Nullable public String getPhaseId() { return phaseId; }
}

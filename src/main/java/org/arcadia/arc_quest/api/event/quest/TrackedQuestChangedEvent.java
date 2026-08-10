package org.arcadia.arc_quest.api.event.quest;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.Event;
import org.arcadia.arc_quest.quest.tracking.api.QuestTrackingChangeReason;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * 客户端或服务端追踪任务发生变化时，通过 Forge 事件总线发布。
 */
public class TrackedQuestChangedEvent extends Event {

    private final Level level;
    private final Player player;
    @Nullable
    private final String oldQuestId;
    @Nullable
    private final String newQuestId;
    private final QuestTrackingChangeReason reason;
    private final long revision;
    private final boolean authoritative;

    public TrackedQuestChangedEvent(Level level, Player player,
                                    @Nullable String oldQuestId, @Nullable String newQuestId) {
        this(level, player, oldQuestId, newQuestId,
                QuestTrackingChangeReason.UNKNOWN, 0L, false);
    }

    public TrackedQuestChangedEvent(Level level, Player player,
                                    @Nullable String oldQuestId, @Nullable String newQuestId,
                                    QuestTrackingChangeReason reason, long revision,
                                    boolean authoritative) {
        this.level = Objects.requireNonNull(level, "level");
        this.player = Objects.requireNonNull(player, "player");
        this.oldQuestId = oldQuestId;
        this.newQuestId = newQuestId;
        this.reason = Objects.requireNonNull(reason, "reason");
        this.revision = Math.max(0L, revision);
        this.authoritative = authoritative;
    }

    public Level getLevel() {
        return level;
    }

    public Player getPlayer() {
        return player;
    }

    @Nullable
    public String getOldQuestId() {
        return oldQuestId;
    }

    @Nullable
    public String getNewQuestId() {
        return newQuestId;
    }

    public boolean isTrackingStarted() {
        return oldQuestId == null && newQuestId != null;
    }

    public boolean isTrackingStopped() {
        return oldQuestId != null && newQuestId == null;
    }

    public boolean isTrackingSwitched() {
        return oldQuestId != null && newQuestId != null && !oldQuestId.equals(newQuestId);
    }

    public QuestTrackingChangeReason getReason() {
        return reason;
    }

    public long getRevision() {
        return revision;
    }

    public boolean isAuthoritative() {
        return authoritative;
    }

    public boolean isManualChange() {
        return reason == QuestTrackingChangeReason.USER_TRACK
                || reason == QuestTrackingChangeReason.USER_UNTRACK;
    }

    public boolean isClientSide() {
        return level.isClientSide();
    }

    public boolean isServerSide() {
        return !level.isClientSide();
    }
}

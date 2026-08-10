package org.arcadia.arc_quest.client.quest.tracking;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.common.NeoForge;
import org.arcadia.arc_quest.api.event.quest.TrackedQuestChangedEvent;
import org.arcadia.arc_quest.client.hud.quest.journal.history.QuestChangeNotificationManager;
import org.arcadia.arc_quest.quest.tracking.api.QuestTrackingChangeReason;
import org.arcadia.arc_quest.quest.tracking.api.QuestTrackingSnapshot;
import org.arcadia.arc_quest.quest.tracking.api.QuestTrackingState;

import java.util.Objects;

public final class ClientQuestTrackingStore {

    public enum SyncState {
        DISCONNECTED,
        AWAITING_SNAPSHOT,
        SYNCED,
        REQUEST_PENDING,
        RECONCILING
    }

    public static final ClientQuestTrackingStore INSTANCE = new ClientQuestTrackingStore();

    private QuestTrackingSnapshot snapshot = new QuestTrackingSnapshot(
            null, QuestTrackingState.EMPTY, 0L);
    private SyncState syncState = SyncState.DISCONNECTED;

    private ClientQuestTrackingStore() {
    }

    public QuestTrackingSnapshot snapshot() {
        return snapshot;
    }

    public String trackedQuestId() {
        return snapshot.questId();
    }

    public SyncState syncState() {
        return syncState;
    }

    public void beginConnection() {
        syncState = SyncState.AWAITING_SNAPSHOT;
    }

    public void markRequestPending() {
        if (syncState != SyncState.DISCONNECTED) syncState = SyncState.REQUEST_PENDING;
    }

    public void markReconciling() {
        if (syncState != SyncState.DISCONNECTED) syncState = SyncState.RECONCILING;
    }

    public void applyAuthoritative(QuestTrackingSnapshot incoming,
                                   QuestTrackingChangeReason reason) {
        QuestTrackingSnapshot previous = snapshot;
        snapshot = incoming;
        syncState = SyncState.SYNCED;
        QuestTrackingPresentationState.INSTANCE.onTrackedQuestChanged(incoming.questId());
        if (Objects.equals(previous.questId(), incoming.questId())) return;

        QuestChangeNotificationManager.INSTANCE.acknowledgeTrackedQuestTransition(
                previous.questId(), incoming.questId());
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && minecraft.player != null) {
            NeoForge.EVENT_BUS.post(new TrackedQuestChangedEvent(
                    minecraft.level, minecraft.player,
                    previous.questId(), incoming.questId(),
                    reason, incoming.revision(), true));
        }
    }

    public void clear() {
        snapshot = new QuestTrackingSnapshot(null, QuestTrackingState.EMPTY, 0L);
        syncState = SyncState.DISCONNECTED;
        QuestTrackingPresentationState.INSTANCE.clear();
    }
}

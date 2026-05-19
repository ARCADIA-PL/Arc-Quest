package org.arcadia.arc_quest.questplayer.restore;

import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.quest.logic.QuestProgressHandler;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerLifecycleHandler;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import org.arcadia.arc_quest.questplayer.migration.ArcQuestPlayerMigrationBundle;
import org.arcadia.arc_quest.questplayer.migration.ArcQuestPlayerMigrationMode;
import org.arcadia.arc_quest.questplayer.snapshot.ArcQuestPlayerSnapshotRef;
import org.arcadia.arc_quest.questplayer.snapshot.ArcQuestSnapshotReason;
import org.arcadia.arc_quest.questplayer.snapshot.FileArcQuestPlayerSnapshotStore;

import java.util.Set;

public final class ArcQuestPlayerRestoreService {

    private final ArcQuestPlayerMigrationValidator validator = new ArcQuestPlayerMigrationValidator();
    private final ArcQuestPlayerMigrationImporter importer = new ArcQuestPlayerMigrationImporter();

    public ArcQuestPlayerMigrationApplyResult restore(ServerPlayer player,
                                                      ArcQuestPlayerMigrationBundle bundle,
                                                      Set<String> sections) {
        ArcQuestPlayerMigrationReport report = validator.validate(player, bundle, sections);
        if (report.isBlocking()) {
            return new ArcQuestPlayerMigrationApplyResult(false, report, null);
        }

        ArcQuestPlayer targetData = ArcQuestPlayerManager.getOrCreate(player);
        ArcQuestPlayerSnapshotRef preRestoreSnapshot = FileArcQuestPlayerSnapshotStore.INSTANCE
                .writeSnapshot(player, targetData, ArcQuestSnapshotReason.PRE_RESTORE);

        importer.apply(targetData, bundle, ArcQuestPlayerMigrationMode.REPLACE_ALL, sections);
        ArcQuestPlayerManager.persistSnapshot(player, targetData);
        ArcQuestPlayerLifecycleHandler.validateAndFixQuestData(player, targetData);
        QuestProgressHandler.rebuildTrackingIndex(player, targetData);
        ArcQuestNetwork.syncFullData(player, targetData);

        return new ArcQuestPlayerMigrationApplyResult(true, report, preRestoreSnapshot);
    }
}

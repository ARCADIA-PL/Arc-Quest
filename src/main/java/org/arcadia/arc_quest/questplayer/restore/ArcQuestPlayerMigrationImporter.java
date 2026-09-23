package org.arcadia.arc_quest.questplayer.restore;

import net.minecraft.nbt.CompoundTag;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.migration.ArcQuestPlayerMigrationBundle;
import org.arcadia.arc_quest.questplayer.migration.ArcQuestPlayerMigrationMode;

import java.util.Set;

public final class ArcQuestPlayerMigrationImporter {

    public void apply(ArcQuestPlayer targetData,
                      ArcQuestPlayerMigrationBundle bundle,
                      ArcQuestPlayerMigrationMode mode,
                      Set<String> sections) {
        if (mode != ArcQuestPlayerMigrationMode.REPLACE_ALL) {
            throw new IllegalArgumentException("Unsupported migration mode: " + mode);
        }

        if (!ArcQuestPlayerMigrationBundle.FORMAT.equals(bundle.getFormat())
                || bundle.getVersion() < 1 || bundle.getVersion() > ArcQuestPlayerMigrationBundle.VERSION) {
            throw new IllegalArgumentException("Unsupported player migration format/version");
        }
        if (sections.isEmpty() || !bundle.getMeta().getExportedSections().containsAll(sections)) {
            throw new IllegalArgumentException("Selected sections were not exported");
        }
        int sourceVersion = bundle.getMeta().getSourceArcQuestDataVersion();
        if (sourceVersion < 0 || sourceVersion > ArcQuestPlayer.getCurrentDataVersion()) {
            throw new IllegalArgumentException("Unsupported source player data version: " + sourceVersion);
        }
        CompoundTag root = bundle.getSections().applyTo(targetData.serializeNBT(), sections);

        targetData.deserializeNBT(root);
        targetData.clearDirty();
    }
}

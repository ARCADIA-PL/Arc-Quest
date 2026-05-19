package org.arcadia.arc_quest.questplayer.restore;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.migration.ArcQuestPlayerMigrationBundle;
import org.arcadia.arc_quest.questplayer.migration.ArcQuestPlayerMigrationMode;
import org.arcadia.arc_quest.questplayer.migration.ArcQuestPlayerMigrationSections;

import java.util.Set;

public final class ArcQuestPlayerMigrationImporter {

    public void apply(ArcQuestPlayer targetData,
                      ArcQuestPlayerMigrationBundle bundle,
                      ArcQuestPlayerMigrationMode mode,
                      Set<String> sections) {
        if (mode != ArcQuestPlayerMigrationMode.REPLACE_ALL) {
            throw new IllegalArgumentException("Unsupported migration mode: " + mode);
        }

        ArcQuestPlayerMigrationSections source = bundle.getSections();
        CompoundTag root = targetData.serializeNBT();

        if (sections.contains("flagsVars")) {
            CompoundTag flagsVars = source.getFlagsVars();
            root.put("Flags", flagsVars.contains("Flags") ? flagsVars.get("Flags").copy() : new ListTag());
            root.put("Variables", flagsVars.getCompound("Variables").copy());
        }

        if (sections.contains("questState")) {
            CompoundTag questState = source.getQuestState();
            root.put("ActiveQuests", questState.contains("ActiveQuests") ? questState.get("ActiveQuests").copy() : new ListTag());
            root.put("CompletedQuests", questState.contains("CompletedQuests") ? questState.get("CompletedQuests").copy() : new ListTag());
            root.put("FailedQuests", questState.contains("FailedQuests") ? questState.get("FailedQuests").copy() : new ListTag());
        }

        if (sections.contains("dialogue")) {
            root.put("DialogueProgress", source.getDialogue().copy());
        }

        if (sections.contains("trade")) {
            root.put("TradeData", source.getTrade().copy());
        }

        if (sections.contains("gacha")) {
            root.put("GachaData", source.getGacha().copy());
        }

        if (sections.contains("markers")) {
            CompoundTag markers = source.getMarkers();
            root.put("Markers", markers.contains("Markers") ? markers.get("Markers").copy() : new ListTag());
        }

        targetData.deserializeNBT(root);
        targetData.clearDirty();
    }
}

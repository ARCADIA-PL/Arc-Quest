package org.arcadia.arc_quest.questplayer.migration;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

public final class ArcQuestPlayerMigrationSerializer {

    public CompoundTag serialize(ArcQuestPlayerMigrationBundle bundle) {
        CompoundTag root = new CompoundTag();
        root.putString("Format", bundle.getFormat());
        root.putInt("Version", bundle.getVersion());
        root.put("Meta", serializeMeta(bundle.getMeta()));
        root.put("Sections", serializeSections(bundle.getSections()));
        return root;
    }

    private CompoundTag serializeMeta(ArcQuestPlayerMigrationMeta meta) {
        CompoundTag tag = new CompoundTag();
        tag.putString("SourcePlayerUuid", meta.getSourcePlayerUuid().toString());
        tag.putString("SourcePlayerName", meta.getSourcePlayerName());
        tag.putLong("ExportedAt", meta.getExportedAt());
        tag.putInt("SourceArcQuestDataVersion", meta.getSourceArcQuestDataVersion());
        tag.putString("SourceModVersion", meta.getSourceModVersion());
        tag.putString("SourceWorldHint", meta.getSourceWorldHint());

        ListTag exportedSections = new ListTag();
        for (String section : meta.getExportedSections().stream().sorted().toList()) {
            exportedSections.add(StringTag.valueOf(section));
        }
        tag.put("ExportedSections", exportedSections);
        return tag;
    }

    private CompoundTag serializeSections(ArcQuestPlayerMigrationSections sections) {
        return sections.toTag();
    }
}

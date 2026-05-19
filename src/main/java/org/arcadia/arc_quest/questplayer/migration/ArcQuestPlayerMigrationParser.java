package org.arcadia.arc_quest.questplayer.migration;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class ArcQuestPlayerMigrationParser {

    public ArcQuestPlayerMigrationBundle parse(CompoundTag root) {
        String format = root.getString("Format");
        int version = root.contains("Version", Tag.TAG_INT) ? root.getInt("Version") : ArcQuestPlayerMigrationBundle.VERSION;
        CompoundTag metaTag = root.getCompound("Meta");
        CompoundTag sectionsTag = root.getCompound("Sections");

        ArcQuestPlayerMigrationMeta meta = new ArcQuestPlayerMigrationMeta(
                parseUuid(metaTag.getString("SourcePlayerUuid")),
                metaTag.getString("SourcePlayerName"),
                metaTag.getLong("ExportedAt"),
                metaTag.contains("SourceArcQuestDataVersion", Tag.TAG_INT) ? metaTag.getInt("SourceArcQuestDataVersion") : 0,
                metaTag.getString("SourceModVersion"),
                metaTag.getString("SourceWorldHint"),
                parseSections(metaTag.getList("ExportedSections", Tag.TAG_STRING))
        );

        ArcQuestPlayerMigrationSections sections = new ArcQuestPlayerMigrationSections(
                sectionsTag.getCompound("FlagsVars"),
                sectionsTag.getCompound("QuestState"),
                sectionsTag.getCompound("Dialogue"),
                sectionsTag.getCompound("Trade"),
                sectionsTag.getCompound("Gacha"),
                sectionsTag.getCompound("Markers")
        );

        return new ArcQuestPlayerMigrationBundle(format, version, meta, sections);
    }

    private static UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (Exception e) {
            return new UUID(0L, 0L);
        }
    }

    private static Set<String> parseSections(ListTag listTag) {
        Set<String> result = new LinkedHashSet<>();
        for (int i = 0; i < listTag.size(); i++) {
            result.add(listTag.getString(i));
        }
        return result;
    }
}

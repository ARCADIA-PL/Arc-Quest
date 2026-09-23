package org.arcadia.arc_quest.questplayer.migration;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class ArcQuestPlayerMigrationParser {

    public ArcQuestPlayerMigrationBundle parse(CompoundTag root) {
        requireType(root, "Format", Tag.TAG_STRING);
        requireType(root, "Version", Tag.TAG_INT);
        requireType(root, "Meta", Tag.TAG_COMPOUND);
        requireType(root, "Sections", Tag.TAG_COMPOUND);
        String format = root.getString("Format");
        if (!ArcQuestPlayerMigrationBundle.FORMAT.equals(format)) {
            throw new IllegalArgumentException("Unsupported player migration format: " + format);
        }
        int version = root.getInt("Version");
        CompoundTag metaTag = root.getCompound("Meta");
        CompoundTag sectionsTag = root.getCompound("Sections");
        requireType(metaTag, "ExportedSections", Tag.TAG_LIST);
        ListTag exported = (ListTag) metaTag.get("ExportedSections");
        if (!exported.isEmpty() && exported.getElementType() != Tag.TAG_STRING) {
            throw new IllegalArgumentException("Invalid ExportedSections list");
        }

        ArcQuestPlayerMigrationMeta meta = new ArcQuestPlayerMigrationMeta(
                parseUuid(metaTag.getString("SourcePlayerUuid")),
                metaTag.getString("SourcePlayerName"),
                metaTag.getLong("ExportedAt"),
                metaTag.contains("SourceArcQuestDataVersion", Tag.TAG_INT) ? metaTag.getInt("SourceArcQuestDataVersion") : 0,
                metaTag.getString("SourceModVersion"),
                metaTag.getString("SourceWorldHint"),
                parseSections(metaTag.getList("ExportedSections", Tag.TAG_STRING))
        );

        ArcQuestPlayerMigrationSections sections = ArcQuestPlayerMigrationSections.fromTag(sectionsTag);
        if (!sections.getAvailableSections().containsAll(meta.getExportedSections())) {
            throw new IllegalArgumentException("Snapshot advertises missing or unsupported sections");
        }

        return new ArcQuestPlayerMigrationBundle(format, version, meta, sections);
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.length() != 36) throw new IllegalArgumentException("Invalid source player UUID");
        return UUID.fromString(raw);
    }

    private static void requireType(CompoundTag tag, String key, int type) {
        if (!tag.contains(key, type)) throw new IllegalArgumentException("Missing or invalid snapshot field: " + key);
    }

    private static Set<String> parseSections(ListTag listTag) {
        Set<String> result = new LinkedHashSet<>();
        for (int i = 0; i < listTag.size(); i++) {
            result.add(listTag.getString(i));
        }
        return result;
    }
}

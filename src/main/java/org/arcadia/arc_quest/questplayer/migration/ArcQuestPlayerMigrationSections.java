package org.arcadia.arc_quest.questplayer.migration;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class ArcQuestPlayerMigrationSections {
    private final Map<String, CompoundTag> sections = new LinkedHashMap<>();

    public ArcQuestPlayerMigrationSections(CompoundTag flagsVars, CompoundTag questState,
                                           CompoundTag dialogue, CompoundTag trade,
                                           CompoundTag gacha, CompoundTag markers) {
        this(flagsVars, questState, dialogue, trade, gacha, markers, null);
    }

    public ArcQuestPlayerMigrationSections(CompoundTag flagsVars, CompoundTag questState,
                                           CompoundTag dialogue, CompoundTag trade,
                                           CompoundTag gacha, CompoundTag markers, CompoundTag guide) {
        sections.put("flagsVars", copyOrEmpty(flagsVars));
        sections.put("questState", copyOrEmpty(questState));
        sections.put("dialogue", copyOrEmpty(dialogue));
        sections.put("trade", copyOrEmpty(trade));
        sections.put("gacha", copyOrEmpty(gacha));
        sections.put("markers", copyOrEmpty(markers));
        if (guide != null) sections.put("guide", guide.copy());
    }

    private ArcQuestPlayerMigrationSections() { }

    public CompoundTag getFlagsVars() { return get("flagsVars"); }
    public CompoundTag getQuestState() { return get("questState"); }
    public CompoundTag getDialogue() { return get("dialogue"); }
    public CompoundTag getTrade() { return get("trade"); }
    public CompoundTag getGacha() { return get("gacha"); }
    public CompoundTag getMarkers() { return get("markers"); }
    public CompoundTag getGuide() { return get("guide"); }

    public Set<String> getAvailableSections() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(sections.keySet()));
    }

    public static ArcQuestPlayerMigrationSections fromPlayerData(CompoundTag root) {
        ArcQuestPlayerMigrationSections result = new ArcQuestPlayerMigrationSections();
        for (PlayerMigrationSection section : PlayerMigrationSection.values()) {
            result.sections.put(section.id, section.extract(root));
        }
        return result;
    }

    /** 返回独立的合并结果，不修改目标或快照本身。 */
    public CompoundTag applyTo(CompoundTag target, Set<String> selectedSections) {
        CompoundTag result = target.copy();
        for (String id : selectedSections) {
            PlayerMigrationSection section = PlayerMigrationSection.byId(id);
            CompoundTag source = sections.get(id);
            if (source == null) throw new IllegalArgumentException("Missing player section: " + id);
            section.replace(result, source);
        }
        return result;
    }

    static ArcQuestPlayerMigrationSections fromTag(CompoundTag tag) {
        ArcQuestPlayerMigrationSections result = new ArcQuestPlayerMigrationSections();
        for (PlayerMigrationSection section : PlayerMigrationSection.values()) {
            if (!tag.contains(section.tagName)) continue;
            if (!tag.contains(section.tagName, Tag.TAG_COMPOUND)) {
                throw new IllegalArgumentException("Invalid player section type: " + section.id);
            }
            CompoundTag data = tag.getCompound(section.tagName);
            section.validate(data);
            result.sections.put(section.id, data.copy());
        }
        return result;
    }

    CompoundTag toTag() {
        CompoundTag result = new CompoundTag();
        sections.forEach((id, tag) -> result.put(PlayerMigrationSection.byId(id).tagName, tag.copy()));
        return result;
    }

    private CompoundTag get(String id) { return copyOrEmpty(sections.get(id)); }

    private static CompoundTag copyOrEmpty(CompoundTag tag) {
        return tag == null ? new CompoundTag() : tag.copy();
    }
}

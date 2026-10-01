package org.arcadia.arc_quest.questplayer.migration;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** 导出和恢复共用字段归属，缺省字段在 REPLACE_ALL 中清除。 */
enum PlayerMigrationSection {
    FLAGS_VARS("flagsVars", "FlagsVars", null,
            field("Flags", Tag.TAG_LIST, Tag.TAG_STRING), field("Variables", Tag.TAG_COMPOUND)),
    QUEST_STATE("questState", "QuestState", null,
            field("ActiveQuests", Tag.TAG_LIST, Tag.TAG_COMPOUND),
            field("CompletedQuests", Tag.TAG_LIST, Tag.TAG_STRING),
            field("FailedQuests", Tag.TAG_LIST, Tag.TAG_STRING),
            field("ReadPhaseStories", Tag.TAG_LIST, Tag.TAG_COMPOUND),
            field("TrackedQuestId", Tag.TAG_STRING), field("TrackedPhaseId", Tag.TAG_STRING),
            field("TrackedQuestState", Tag.TAG_STRING), field("TrackedQuestRevision", Tag.TAG_LONG),
            field("TrackedQuestChangeReason", Tag.TAG_STRING)),
    DIALOGUE("dialogue", "Dialogue", "DialogueProgress",
            field("Nodes", Tag.TAG_COMPOUND), field("Choices", Tag.TAG_COMPOUND),
            field("Dialogues", Tag.TAG_COMPOUND), field("Trade", Tag.TAG_COMPOUND)),
    TRADE("trade", "Trade", "TradeData",
            field("TradePurchases", Tag.TAG_COMPOUND), field("TradeCooldowns", Tag.TAG_COMPOUND)),
    GACHA("gacha", "Gacha", "GachaData",
            field("DrawCounts", Tag.TAG_COMPOUND), field("PityCounters", Tag.TAG_COMPOUND),
            field("Histories", Tag.TAG_COMPOUND), field("DrawCooldowns", Tag.TAG_COMPOUND)),
    MARKERS("markers", "Markers", null,
            field("Markers", Tag.TAG_LIST, Tag.TAG_COMPOUND),
            field("ConsumedOneShotMarkers", Tag.TAG_LIST, Tag.TAG_STRING)),
    GUIDE("guide", "Guide", null,
            field("UnlockedGuides", Tag.TAG_LIST, Tag.TAG_STRING),
            field("SeenGuides", Tag.TAG_LIST, Tag.TAG_STRING), field("GuideProgress", Tag.TAG_COMPOUND));

    final String id;
    final String tagName;
    private final String rootKey;
    private final List<Field> fields;

    PlayerMigrationSection(String id, String tagName, String rootKey, Field... fields) {
        this.id = id;
        this.tagName = tagName;
        this.rootKey = rootKey;
        this.fields = List.of(fields);
    }

    static PlayerMigrationSection byId(String id) {
        for (PlayerMigrationSection section : values()) {
            if (section.id.equals(id)) return section;
        }
        throw new IllegalArgumentException("Unsupported player section: " + id);
    }

    CompoundTag extract(CompoundTag root) {
        if (rootKey != null) {
            if (root.contains(rootKey) && !root.contains(rootKey, Tag.TAG_COMPOUND)) {
                throw new IllegalArgumentException("Invalid player field: " + rootKey);
            }
            return root.getCompound(rootKey).copy();
        }
        CompoundTag result = new CompoundTag();
        for (Field field : fields) {
            Tag value = root.get(field.name);
            if (value != null) result.put(field.name, value.copy());
        }
        validate(result);
        return result;
    }

    void replace(CompoundTag root, CompoundTag source) {
        validate(source);
        if (rootKey != null) {
            root.put(rootKey, source.copy());
            return;
        }
        for (Field field : fields) {
            Tag value = source.get(field.name);
            if (value == null) root.remove(field.name);
            else root.put(field.name, value.copy());
        }
    }

    void validate(CompoundTag source) {
        for (Field field : fields) {
            Tag value = source.get(field.name);
            if (value == null) continue;
            if (value.getId() != field.type
                    || value instanceof ListTag list && !list.isEmpty() && list.getElementType() != field.elementType) {
                throw new IllegalArgumentException("Invalid player field type: " + id + "/" + field.name);
            }
        }
        switch (this) {
            case FLAGS_VARS -> validateMap(source.getCompound("Variables"), Tag.TAG_INT);
            case DIALOGUE -> {
                for (Field field : fields) validateMap(source.getCompound(field.name), Tag.TAG_COMPOUND);
            }
            case TRADE -> {
                CompoundTag purchases = source.getCompound("TradePurchases");
                validateMap(purchases, Tag.TAG_COMPOUND);
                for (String shop : purchases.getAllKeys()) validateMap(purchases.getCompound(shop), Tag.TAG_INT);
                CompoundTag cooldowns = source.getCompound("TradeCooldowns");
                validateMap(cooldowns, Tag.TAG_COMPOUND);
                for (String shop : cooldowns.getAllKeys()) validateMap(cooldowns.getCompound(shop), Tag.TAG_COMPOUND);
            }
            case GACHA -> {
                validateMap(source.getCompound("DrawCounts"), Tag.TAG_INT);
                validateMap(source.getCompound("PityCounters"), Tag.TAG_INT);
                validateMap(source.getCompound("DrawCooldowns"), Tag.TAG_COMPOUND);
                CompoundTag histories = source.getCompound("Histories");
                validateMap(histories, Tag.TAG_LIST);
                for (String shop : histories.getAllKeys()) {
                    ListTag history = (ListTag) histories.get(shop);
                    if (!history.isEmpty() && history.getElementType() != Tag.TAG_COMPOUND) {
                        throw new IllegalArgumentException("Invalid gacha history: " + shop);
                    }
                }
            }
            case GUIDE -> {
                for (String field : List.of("UnlockedGuides", "SeenGuides")) {
                    ListTag ids = source.getList(field, Tag.TAG_STRING);
                    for (int i = 0; i < ids.size(); i++) validateGuideId(ids.getString(i));
                }
                CompoundTag progress = source.getCompound("GuideProgress");
                validateMap(progress, Tag.TAG_INT);
                for (String guide : progress.getAllKeys()) {
                    validateGuideId(guide);
                    if (progress.getInt(guide) < 0) throw new IllegalArgumentException("Negative guide progress: " + guide);
                }
            }
            default -> { }
        }
    }

    private static void validateMap(CompoundTag tag, int type) {
        for (String key : tag.getAllKeys()) {
            if (!tag.contains(key, type)) throw new IllegalArgumentException("Invalid player map entry: " + key);
        }
    }

    private static void validateGuideId(String id) {
        if (ResourceLocation.tryParse(id) == null) throw new IllegalArgumentException("Invalid guide ID: " + id);
    }

    private static Field field(String name, int type) {
        return field(name, type, Tag.TAG_END);
    }

    private static Field field(String name, int type, int elementType) {
        return new Field(name, type, elementType);
    }

    private record Field(String name, int type, int elementType) { }
}

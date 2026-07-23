package org.arcadia.arc_quest.quest.registry;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.api.QuestGroupDefinition;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class QuestGroupRegistry {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static Map<ResourceLocation, QuestGroupDefinition> groups = new LinkedHashMap<>();
    private static Map<ResourceLocation, ResourceLocation> questGroups = new LinkedHashMap<>();
    private static boolean frozen;

    private QuestGroupRegistry() {
    }

    public static void register(QuestGroupDefinition definition) {
        ensureMutable();
        Objects.requireNonNull(definition, "definition");
        QuestGroupDefinition previous = groups.putIfAbsent(definition.getId(), definition);
        if (previous != null) {
            throw new IllegalStateException("Duplicate quest group ID: " + definition.getId());
        }
    }

    public static void assign(ResourceLocation questId, ResourceLocation groupId) {
        ensureMutable();
        Objects.requireNonNull(questId, "questId");
        Objects.requireNonNull(groupId, "groupId");
        if (!groups.containsKey(groupId)) {
            throw new IllegalStateException("Quest group must be registered before assignment: " + groupId);
        }
        ResourceLocation previous = questGroups.putIfAbsent(questId, groupId);
        if (previous != null && !previous.equals(groupId)) {
            throw new IllegalStateException("Quest '" + questId + "' is already assigned to group '" + previous + "'");
        }
    }

    public static void freeze() {
        if (frozen) return;
        groups = Collections.unmodifiableMap(new LinkedHashMap<>(groups));
        questGroups = Collections.unmodifiableMap(new LinkedHashMap<>(questGroups));
        frozen = true;
        LOGGER.info("[ArcQuest] QuestGroupRegistry frozen. groups={}, assignments={}", groups.size(), questGroups.size());
    }

    @Nullable
    public static QuestGroupDefinition get(ResourceLocation groupId) {
        return groups.get(groupId);
    }

    @Nullable
    public static QuestGroupDefinition getGroupForQuest(ResourceLocation questId) {
        ResourceLocation groupId = questGroups.get(questId);
        return groupId != null ? groups.get(groupId) : null;
    }

    @Nullable
    public static QuestGroupDefinition getGroupForQuest(String questId) {
        ResourceLocation id = ResourceLocation.tryParse(questId);
        return id != null ? getGroupForQuest(id) : null;
    }

    public static Collection<QuestGroupDefinition> getAll() {
        return Collections.unmodifiableCollection(groups.values());
    }

    public static List<QuestGroupDefinition> getAllSorted() {
        return groups.values().stream()
                .sorted(Comparator.comparingInt(QuestGroupDefinition::getSortOrder)
                        .thenComparing(group -> group.getId().toString()))
                .toList();
    }

    public static boolean isFrozen() {
        return frozen;
    }

    private static void ensureMutable() {
        if (frozen) {
            throw new IllegalStateException("QuestGroupRegistry is frozen");
        }
    }

    static void resetForTests() {
        groups = new LinkedHashMap<>();
        questGroups = new LinkedHashMap<>();
        frozen = false;
    }
}

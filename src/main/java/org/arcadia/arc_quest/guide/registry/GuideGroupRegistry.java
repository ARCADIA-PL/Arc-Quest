package org.arcadia.arc_quest.guide.registry;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.guide.api.GuideGroupDefinition;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class GuideGroupRegistry {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static Map<ResourceLocation, GuideGroupDefinition> groups = new LinkedHashMap<>();
    private static Map<ResourceLocation, ResourceLocation> guideGroups = new LinkedHashMap<>();
    private static boolean frozen;

    private GuideGroupRegistry() {
    }

    public static void register(GuideGroupDefinition definition) {
        ensureMutable();
        Objects.requireNonNull(definition, "definition");
        GuideGroupDefinition previous = groups.putIfAbsent(definition.getId(), definition);
        if (previous != null) {
            throw new IllegalStateException("Duplicate guide group ID: " + definition.getId());
        }
    }

    public static void assign(ResourceLocation guideId, ResourceLocation groupId) {
        ensureMutable();
        Objects.requireNonNull(guideId, "guideId");
        Objects.requireNonNull(groupId, "groupId");
        if (!groups.containsKey(groupId)) {
            throw new IllegalStateException("Guide group must be registered before assignment: " + groupId);
        }
        ResourceLocation previous = guideGroups.putIfAbsent(guideId, groupId);
        if (previous != null && !previous.equals(groupId)) {
            throw new IllegalStateException("Guide '" + guideId + "' is already assigned to group '" + previous + "'");
        }
    }

    public static void freeze() {
        if (frozen) return;
        groups = Collections.unmodifiableMap(new LinkedHashMap<>(groups));
        guideGroups = Collections.unmodifiableMap(new LinkedHashMap<>(guideGroups));
        frozen = true;
        LOGGER.info("[ArcQuest] GuideGroupRegistry frozen. groups={}, assignments={}", groups.size(), guideGroups.size());
    }

    @Nullable
    public static GuideGroupDefinition get(ResourceLocation groupId) {
        return groups.get(groupId);
    }

    @Nullable
    public static GuideGroupDefinition getGroupForGuide(ResourceLocation guideId) {
        ResourceLocation groupId = guideGroups.get(guideId);
        return groupId != null ? groups.get(groupId) : null;
    }

    @Nullable
    public static GuideGroupDefinition getGroupForGuide(String guideId) {
        ResourceLocation id = ResourceLocation.tryParse(guideId);
        return id != null ? getGroupForGuide(id) : null;
    }

    public static Collection<GuideGroupDefinition> getAll() {
        return Collections.unmodifiableCollection(groups.values());
    }

    public static List<GuideGroupDefinition> getAllSorted() {
        return groups.values().stream()
                .sorted(Comparator.comparingInt(GuideGroupDefinition::getSortOrder)
                        .thenComparing(group -> group.getId().toString()))
                .toList();
    }

    public static boolean isFrozen() {
        return frozen;
    }

    private static void ensureMutable() {
        if (frozen) throw new IllegalStateException("GuideGroupRegistry is frozen");
    }

    static void resetForTests() {
        groups = new LinkedHashMap<>();
        guideGroups = new LinkedHashMap<>();
        frozen = false;
    }
}

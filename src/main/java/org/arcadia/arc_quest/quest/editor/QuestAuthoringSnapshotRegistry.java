package org.arcadia.arc_quest.quest.editor;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public final class QuestAuthoringSnapshotRegistry {
    private static volatile Map<ResourceLocation, QuestAuthoringEntry> datapackSnapshot = Map.of();

    private QuestAuthoringSnapshotRegistry() {
    }

    @Nullable
    public static QuestAuthoringEntry get(ResourceLocation questId) {
        return datapackSnapshot.get(questId);
    }

    public static Map<ResourceLocation, QuestAuthoringEntry> getDatapackSnapshot() {
        return datapackSnapshot;
    }

    public static void replaceDatapackSnapshot(Map<ResourceLocation, QuestAuthoringEntry> snapshot) {
        datapackSnapshot = Map.copyOf(snapshot);
    }
}

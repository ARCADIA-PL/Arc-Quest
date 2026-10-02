package org.arcadia.arc_quest.quest.logic;

import net.minecraft.server.MinecraftServer;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.data.CollectionRunDefinitionStore;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.util.log.ArcQuestLog;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Missing historical authoring data blocks that run, without interrupting unrelated investigations. */
public final class CollectionRunAccess {
    private static final Map<QuestRuntimeData, String> REPORTED = Collections.synchronizedMap(new WeakHashMap<>());
    private CollectionRunAccess() { }

    public static QuestDefinition resolve(MinecraftServer server, QuestRuntimeData runtime) {
        return resolve(server, runtime, null, false);
    }
    public static QuestDefinition resolve(MinecraftServer server, QuestRuntimeData runtime, QuestDefinition fallback) {
        return resolve(server, runtime, fallback, true);
    }
    private static QuestDefinition resolve(MinecraftServer server, QuestRuntimeData runtime, QuestDefinition fallback, boolean supplied) {
        try {
            var definition = supplied ? CollectionRunDefinitions.resolve(server, runtime, fallback)
                    : CollectionRunDefinitions.resolve(server, runtime);
            REPORTED.remove(runtime);
            return definition;
        } catch (CollectionRunDefinitionStore.UnsupportedSnapshotException unavailable) {
            if (!unavailable.getMessage().equals(REPORTED.put(runtime, unavailable.getMessage())))
                ArcQuestLog.warn(ArcQuestLog.Category.QUEST_PROGRESS, "Cannot progress stored investigation {}: {}",
                        runtime.getQuestId(), unavailable.getMessage());
            return null;
        }
    }
}

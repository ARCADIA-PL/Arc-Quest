package org.arcadia.arc_quest.quest.spec.io;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.arcadia.arc_quest.quest.spec.QuestSpec;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public final class QuestSpecResourceLoader {
    private static final String PREFIX = "arc_quest/quests";
    private static final String SUFFIX = ".json";

    public Map<ResourceLocation, QuestSpec> load(ResourceManager manager) {
        Map<ResourceLocation, QuestSpec> result = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, Resource> entry : manager.listResources(PREFIX, path -> path.getPath().endsWith(SUFFIX)).entrySet()) {
            try {
                String json = new String(entry.getValue().open().readAllBytes());
                QuestSpec spec = QuestSpecJsonReader.read(json);
                if (spec != null) result.put(entry.getKey(), spec);
            } catch (IOException ignored) {
            }
        }
        return result;
    }
}

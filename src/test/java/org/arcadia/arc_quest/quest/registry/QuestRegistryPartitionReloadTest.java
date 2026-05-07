package org.arcadia.arc_quest.quest.registry;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuestRegistryPartitionReloadTest {

    @AfterEach
    void resetRegistry() throws Exception {
        setField("CODE_REGISTRY", new LinkedHashMap<ResourceLocation, Object>());
        setField("DATAPACK_REGISTRY", new LinkedHashMap<ResourceLocation, Object>());
        setField("MERGED_REGISTRY", new LinkedHashMap<ResourceLocation, Object>());
        setField("SOURCE_INFO", new LinkedHashMap<ResourceLocation, Object>());
        setField("frozen", false);
        setField("datapackLoadOrder", 0);
        QuestRegistry.rebuildMergedRegistry();
    }

    @Test
    void clearDatapack_shouldKeepCodePartitionUntouched() throws Exception {
        Map<ResourceLocation, Object> code = new LinkedHashMap<>();
        code.put(ResourceLocation.tryParse("arc_quest:code_only"), null);
        Map<ResourceLocation, Object> datapack = new LinkedHashMap<>();
        datapack.put(ResourceLocation.tryParse("arc_quest:datapack_only"), null);

        setField("CODE_REGISTRY", code);
        setField("DATAPACK_REGISTRY", datapack);
        QuestRegistry.rebuildMergedRegistry();

        assertEquals(1, QuestRegistry.codeSize());
        assertEquals(1, QuestRegistry.datapackSize());
        assertEquals(2, QuestRegistry.size());

        QuestRegistry.clearDatapack();

        assertEquals(1, QuestRegistry.codeSize());
        assertEquals(0, QuestRegistry.datapackSize());
        assertEquals(1, QuestRegistry.size());
    }

    private static void setField(String name, Object value) throws Exception {
        Field field = QuestRegistry.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }
}

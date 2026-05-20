package org.arcadia.arc_quest.websocket;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
import org.arcadia.arc_quest.quest.api.ObjectiveType;
import org.arcadia.arc_quest.quest.api.QuestCategory;
import org.arcadia.arc_quest.quest.registry.ObjectiveTypeRegistry;
import org.arcadia.arc_quest.quest.registry.QuestCategoryRegistry;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;

import java.util.Map;

public final class RegistryCollector {

    private static final int MAX_PER_REGISTRY = 20000;

    private RegistryCollector() {
    }

    public static JsonObject collectAll() {
        JsonObject root = new JsonObject();
        root.addProperty("v", 1);
        root.addProperty("timestamp", System.currentTimeMillis());

        JsonObject registries = new JsonObject();
        registries.add("items", collectRegistry(ForgeRegistries.ITEMS));
        registries.add("entityTypes", collectRegistry(ForgeRegistries.ENTITY_TYPES));
        registries.add("blocks", collectRegistry(ForgeRegistries.BLOCKS));
        registries.add("soundEvents", collectRegistry(ForgeRegistries.SOUND_EVENTS));
        registries.add("mobEffects", collectRegistry(ForgeRegistries.MOB_EFFECTS));
        registries.add("biomes", collectRegistry(ForgeRegistries.BIOMES));
        root.add("registries", registries);

        JsonArray questIds = new JsonArray();
        for (ResourceLocation id : QuestRegistry.getAllIds()) questIds.add(id.toString());
        root.add("questIds", questIds);
        root.add("objectiveTypes", collectObjectiveTypes());
        root.add("questCategories", collectQuestCategories());

        return root;
    }

    private static JsonArray collectObjectiveTypes() {
        JsonArray arr = new JsonArray();
        for (ObjectiveType type : ObjectiveTypeRegistry.all()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("id", type.getId().toString());
            obj.addProperty("counting", type.isCounting());
            obj.addProperty("builtin", type.isBuiltin());
            obj.addProperty("displayKey", type.getDisplayKey());
            obj.addProperty("targetKind", type.getDefaultTargetKind());
            arr.add(obj);
        }
        return arr;
    }

    private static JsonArray collectQuestCategories() {
        JsonArray arr = new JsonArray();
        for (QuestCategory category : QuestCategoryRegistry.all()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("id", category.getId().toString());
            obj.addProperty("displayKey", category.getTranslationKey());
            obj.addProperty("themeColor", category.getThemeColor());
            obj.addProperty("builtin", category.isBuiltin());
            arr.add(obj);
        }
        return arr;
    }

    private static <T> JsonArray collectRegistry(IForgeRegistry<T> registry) {
        JsonArray arr = new JsonArray();
        int count = 0;
        for (Map.Entry<ResourceKey<T>, T> entry : registry.getEntries()) {
            if (count >= MAX_PER_REGISTRY) break;
            ResourceLocation id = entry.getKey().location();
            JsonObject obj = new JsonObject();
            obj.addProperty("id", id.toString());
            obj.addProperty("label", id.getPath());
            arr.add(obj);
            count++;
        }
        return arr;
    }
}

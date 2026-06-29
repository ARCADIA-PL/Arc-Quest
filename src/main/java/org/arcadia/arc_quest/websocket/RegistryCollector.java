package org.arcadia.arc_quest.websocket;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import org.arcadia.arc_quest.guide.api.GuideCategory;
import org.arcadia.arc_quest.guide.api.GuideDefinition;
import org.arcadia.arc_quest.guide.api.GuideMediaType;
import org.arcadia.arc_quest.guide.registry.GuideRegistry;
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
        registries.add("items", collectRegistry(BuiltInRegistries.ITEM));
        registries.add("entityTypes", collectRegistry(BuiltInRegistries.ENTITY_TYPE));
        registries.add("blocks", collectRegistry(BuiltInRegistries.BLOCK));
        registries.add("soundEvents", collectRegistry(BuiltInRegistries.SOUND_EVENT));
        registries.add("mobEffects", collectRegistry(BuiltInRegistries.MOB_EFFECT));
        // biomes 为 datapack 注册表,枚举需 RegistryAccess,此处暂不收集。
        root.add("registries", registries);

        JsonArray questIds = new JsonArray();
        for (ResourceLocation id : QuestRegistry.getAllIds()) questIds.add(id.toString());
        root.add("questIds", questIds);
        root.add("objectiveTypes", collectObjectiveTypes());
        root.add("questCategories", collectQuestCategories());
        root.add("guideCategories", collectGuideCategories());
        root.add("guides", collectGuides());
        root.add("guideMediaTypes", collectGuideMediaTypes());

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

    private static JsonArray collectGuideCategories() {
        JsonArray arr = new JsonArray();
        for (GuideCategory category : GuideRegistry.getAllCategories()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("id", category.getId().toString());
            obj.addProperty("label", category.getDisplayName().getString());
            obj.addProperty("themeColor", category.getThemeColor());
            obj.addProperty("sortOrder", category.getSortOrder());
            obj.addProperty("builtin", category.isBuiltin());
            if (category.getIconTexture() != null) {
                obj.addProperty("iconTexture", category.getIconTexture().toString());
            }
            arr.add(obj);
        }
        return arr;
    }

    private static JsonArray collectGuides() {
        JsonArray arr = new JsonArray();
        for (GuideDefinition guide : GuideRegistry.getAll()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("id", guide.getId().toString());
            obj.addProperty("category", guide.getCategory().getId().toString());
            obj.addProperty("title", guide.getTitle().getString());
            obj.addProperty("sortOrder", guide.getSortOrder());
            obj.addProperty("hidden", guide.isHidden());
            obj.addProperty("repeatablePopup", guide.isRepeatablePopup());
            obj.addProperty("pageCount", guide.getPageCount());
            arr.add(obj);
        }
        return arr;
    }

    private static JsonArray collectGuideMediaTypes() {
        JsonArray arr = new JsonArray();
        for (GuideMediaType type : GuideMediaType.values()) {
            arr.add(type.name().toLowerCase());
        }
        return arr;
    }

    private static <T> JsonArray collectRegistry(Registry<T> registry) {
        JsonArray arr = new JsonArray();
        int count = 0;
        for (Map.Entry<ResourceKey<T>, T> entry : registry.entrySet()) {
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

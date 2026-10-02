package org.arcadia.arc_quest.client.hud.quest.icon.portrait;

import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import org.arcadia.arc_quest.testsupport.MinecraftRegistryTestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.lang.reflect.ParameterizedType;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies the actual Minecraft Mob declarations without creating entities or entity models. */
class VanillaEntityPortraitCoverageTest {
    @BeforeAll static void initializeRegistry() { MinecraftRegistryTestBootstrap.initialize(); }

    @Test void automaticSourcesCoverEveryRegisteredVanillaMobClass() throws Exception {
        var heads = declarations("HEADS");
        var textures = declarations("BUILTIN_TEXTURE_FILES");
        Set<ResourceLocation> vanillaMobs = new HashSet<>();
        for (var field : EntityType.class.getFields()) {
            if (!(field.getGenericType() instanceof ParameterizedType type)
                    || type.getRawType() != EntityType.class
                    || !(type.getActualTypeArguments()[0] instanceof Class<?> entityClass)
                    || !Mob.class.isAssignableFrom(entityClass)) continue;
            var entityType = (EntityType<?>) field.get(null);
            var id = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
            assertEquals("minecraft", id.getNamespace());
            vanillaMobs.add(id);
            assertTrue(heads.containsKey(id) || textures.containsKey(id), "Missing automatic vanilla Mob portrait: " + id);
        }
        assertEquals(79, vanillaMobs.size(), "Coverage is derived from actual EntityType generic classes, including summon-only mobs");
        assertEquals(vanillaMobs, union(heads.keySet(), textures.keySet()), "Default portrait sources include an unsupported technical entity");
        assertTrue(heads.containsKey(ResourceLocation.parse("minecraft:ender_dragon")), "The existing head strategy retains priority");
        assertFalse(textures.containsKey(ResourceLocation.parse("minecraft:player")), "Player profiles remain explicit");
        assertFalse(textures.containsKey(ResourceLocation.parse("minecraft:armor_stand")), "Decorative entities are outside Mob defaults");
    }

    @Test void everyRegisteredTextureSourceLoadsACompleteValidatedUvDefinition() throws Exception {
        for (var declaration : declarations("BUILTIN_TEXTURE_FILES").entrySet()) {
            var file = (ResourceLocation) declaration.getValue();
            String resource = "/assets/" + file.getNamespace() + "/" + file.getPath();
            try (var input = getClass().getResourceAsStream(resource)) {
                assertNotNull(input, "Registered vanilla portrait JSON is missing: " + declaration.getKey());
                try (var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                    var rule = EntityPortraitRule.parse(JsonParser.parseReader(reader));
                    assertEquals(EntityPortraitRule.Kind.ENTITY_TEXTURE_PORTRAIT, rule.kind());
                    assertEquals("minecraft", rule.portrait().texture().getNamespace());
                    assertFalse(rule.portrait().layers().isEmpty());
                    for (var layer : rule.portrait().layers()) {
                        assertTrue(layer.region().fits(rule.portrait().referenceSize()));
                        assertTrue(layer.destination().fits(rule.portrait().canvasSize()));
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<ResourceLocation, ?> declarations(String name) throws Exception {
        var field = EntityPortraits.class.getDeclaredField(name);
        field.setAccessible(true);
        return (Map<ResourceLocation, ?>) field.get(null);
    }

    private static Set<ResourceLocation> union(Set<ResourceLocation> heads, Set<ResourceLocation> textures) {
        var all = new HashSet<>(heads);
        all.addAll(textures);
        return all;
    }
}

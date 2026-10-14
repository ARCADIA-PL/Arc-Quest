package org.arcadia.arc_quest.quest.builder;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.registry.ObjectiveTypeRegistry;
import org.arcadia.arc_quest.testsupport.MinecraftRegistryTestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AddonObjectiveBuilderTest {
    @BeforeAll static void setup() { MinecraftRegistryTestBootstrap.initialize(); }

    @Test void registeredAddonRetainsTypeTargetCountAndSaveIdentity() {
        var id=ResourceLocation.parse("addon_builder_test:activate_waystone");
        var type=ObjectiveTypeRegistry.register(id,ObjectiveTypeDefinition.builder().counting(true)
                .displayKey("addon.objective.activate_waystone").defaultTargetKind("waystone").build());
        var target=ResourceLocation.parse("addon_builder_test:town");
        var objective=ObjectiveBuilder.ofType(type,target,2).id("activate").extra("waystone_uuid","known-uuid").build();
        assertSame(type,objective.getType());assertNotEquals(ObjectiveType.CUSTOM,objective.getType());
        assertEquals(target,objective.getTargetId());assertEquals(2,objective.getRequiredCount());
        assertEquals("known-uuid",objective.getExtraData().get("waystone_uuid"));
        assertSame(type,ObjectiveTypeRegistry.require(objective.getType().getId()));
    }

    @Test void unregisteredAndForgedTypesCannotEnterQuestDefinitions() {
        var target=ResourceLocation.parse("addon_builder_test:target");
        var invented=new ObjectiveType(ResourceLocation.parse("addon_builder_test:unregistered"),true,false,"",true,"event");
        assertThrows(IllegalArgumentException.class,()->ObjectiveBuilder.ofType(invented,target,1));
        assertThrows(IllegalArgumentException.class,()->ObjectiveBuilder.ofType(ObjectiveType.INTERACT,target,0));
    }
}

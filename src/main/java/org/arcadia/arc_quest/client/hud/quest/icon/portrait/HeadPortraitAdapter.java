package org.arcadia.arc_quest.client.hud.quest.icon.portrait;

import net.minecraft.client.model.SkullModelBase;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.Objects;

/**
 * Creates an owned head-only model during render-thread preparation. Implementations must not
 * create entities, return a model shared with a world renderer, or change global render state.
 * The baker supplies the projection, static animation, full-bright lighting and 2D cache.
 */
@FunctionalInterface
public interface HeadPortraitAdapter {
    HeadModel create(HeadPortraitDefinition definition, EntityModelSet models, ResourceManager resources);

    record HeadModel(SkullModelBase model, ResourceLocation texture) {
        public HeadModel {
            Objects.requireNonNull(model, "model");
            Objects.requireNonNull(texture, "texture");
        }
    }
}

package org.arcadia.arc_quest.client.hud.quest.icon;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.event.IModBusEvent;
import org.arcadia.arc_quest.client.hud.quest.icon.portrait.EntityPortraits;
import org.arcadia.arc_quest.client.hud.quest.icon.portrait.HeadPortraitDefinition;
import org.arcadia.arc_quest.client.hud.quest.icon.portrait.TexturePortraitDefinition;
import org.arcadia.arc_quest.client.hud.quest.icon.portrait.HeadPortraitAdapter;

/** Client MOD bus event, posted once during enqueued client setup. */
public final class RegisterObjectiveIconsEvent extends Event implements IModBusEvent {
    public void registerProvider(ResourceLocation id, ObjectiveIconProvider provider) {
        ObjectiveIconRegistry.registerProvider(id, provider);
    }
    public void bindDefault(ResourceLocation objectiveType, ResourceLocation provider) {
        ObjectiveIconRegistry.bindDefault(objectiveType, provider);
    }
    public void registerHeadPortrait(ResourceLocation entityId, HeadPortraitDefinition definition) {
        EntityPortraits.registerHeadPortrait(entityId, definition);
    }
    public void registerTexturePortrait(ResourceLocation entityId, TexturePortraitDefinition definition) {
        EntityPortraits.registerTexturePortrait(entityId, definition);
    }
    public void registerHeadAdapter(ResourceLocation adapterId, HeadPortraitAdapter adapter) {
        EntityPortraits.registerHeadAdapter(adapterId, adapter);
    }
}

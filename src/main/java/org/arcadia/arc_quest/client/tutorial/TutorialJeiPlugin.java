package org.arcadia.arc_quest.client.tutorial;
import java.util.*;
import mezz.jei.api.*;
import mezz.jei.api.gui.handlers.IGlobalGuiHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
/** Optional, annotation-discovered JEI integration. No JEI types appear in tutorial core. */
@JeiPlugin
public final class TutorialJeiPlugin implements IModPlugin {
    @Override public ResourceLocation getPluginUid(){return ResourceLocation.parse("arc_quest:tutorial_exclusions");}
    @Override public void registerGuiHandlers(IGuiHandlerRegistration registration){registration.addGlobalGuiHandler(new IGlobalGuiHandler(){
        @Override public Collection<Rect2i> getGuiExtraAreas(){var layout=WorkstationTutorialOverlay.ensureLayout();if(layout==null)return List.of();var r=layout.card();return List.of(new Rect2i(r.x(),r.y(),r.w(),r.h()));}
    });}
}

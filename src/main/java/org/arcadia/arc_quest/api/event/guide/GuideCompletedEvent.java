package org.arcadia.arc_quest.api.event.guide;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

/** Fired on the server when a player finishes reading every page of a guide. */
public class GuideCompletedEvent extends Event {

    private final ServerPlayer player;
    private final ResourceLocation guideId;

    public GuideCompletedEvent(ServerPlayer player, ResourceLocation guideId) {
        this.player = player;
        this.guideId = guideId;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public ResourceLocation getGuideId() {
        return guideId;
    }
}

package org.arcadia.arc_quest.api.event.guide;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;
import java.util.UUID;

/** Acknowledged screen lifecycle for a server-issued presentation, separate from read progress. */
public final class GuidePresentationEvent extends Event {
    public enum Stage { OPENED, SUSPENDED, RESUMED, CLOSED, CANCELLED }
    private final ServerPlayer player;
    private final ResourceLocation guideId;
    private final UUID token;
    private final Stage stage;
    public GuidePresentationEvent(ServerPlayer player, ResourceLocation guideId, UUID token, Stage stage) {
        this.player = player; this.guideId = guideId; this.token = token; this.stage = stage;
    }
    public ServerPlayer getPlayer() { return player; }
    public ResourceLocation getGuideId() { return guideId; }
    public UUID getToken() { return token; }
    public Stage getStage() { return stage; }
}

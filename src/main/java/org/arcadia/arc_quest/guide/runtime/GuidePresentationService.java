package org.arcadia.arc_quest.guide.runtime;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.arcadia.arc_quest.api.event.guide.GuidePresentationEvent;
import org.arcadia.arc_quest.guide.network.S2COpenGuidePacket;
import org.arcadia.arc_quest.guide.registry.GuideRegistry;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import java.util.*;

/** One outstanding correlated presentation per player; callers own gameplay timeout policy. */
@Mod.EventBusSubscriber(modid = "arc_quest")
public final class GuidePresentationService {
    private record Presentation(ResourceLocation guide, UUID token, GuidePresentationEvent.Stage stage) {}
    private static final Map<UUID, Presentation> ACTIVE = new HashMap<>();
    private GuidePresentationService() {}
    public static UUID open(ServerPlayer player, ResourceLocation guide) {
        if (player == null || GuideRegistry.get(guide) == null) return null;
        cancel(player);
        UUID token = UUID.randomUUID();
        ACTIVE.put(player.getUUID(), new Presentation(guide, token, null));
        ArcQuestNetwork.sendGuideOpenPacket(player, new S2COpenGuidePacket(guide, 0, true, token));
        return token;
    }
    public static void acknowledge(ServerPlayer player, ResourceLocation guide, UUID token, GuidePresentationEvent.Stage stage) {
        var old = ACTIVE.get(player.getUUID());
        if (old == null || !old.guide.equals(guide) || !old.token.equals(token)) return;
        boolean valid = switch (stage) {
            case OPENED -> old.stage == null;
            case SUSPENDED -> old.stage == GuidePresentationEvent.Stage.OPENED || old.stage == GuidePresentationEvent.Stage.RESUMED;
            case RESUMED -> old.stage == GuidePresentationEvent.Stage.SUSPENDED;
            case CLOSED -> old.stage != null;
            case CANCELLED -> true;
        };
        if (!valid) return;
        if (stage == GuidePresentationEvent.Stage.CLOSED || stage == GuidePresentationEvent.Stage.CANCELLED) ACTIVE.remove(player.getUUID());
        else ACTIVE.put(player.getUUID(), new Presentation(guide, token, stage));
        MinecraftForge.EVENT_BUS.post(new GuidePresentationEvent(player, guide, token, stage));
    }
    public static void cancel(ServerPlayer player) {
        var old = ACTIVE.remove(player.getUUID());
        if (old != null) {
            ArcQuestNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                    new org.arcadia.arc_quest.guide.network.S2CCancelGuidePresentationPacket(old.token));
            MinecraftForge.EVENT_BUS.post(new GuidePresentationEvent(player, old.guide, old.token, GuidePresentationEvent.Stage.CANCELLED));
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) cancel(player);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { ACTIVE.clear(); }
}

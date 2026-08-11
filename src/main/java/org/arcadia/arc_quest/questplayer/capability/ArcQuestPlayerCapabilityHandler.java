package org.arcadia.arc_quest.questplayer.capability;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.arcadia.arc_quest.Arc_Quest;

@Mod.EventBusSubscriber(modid = Arc_Quest.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ArcQuestPlayerCapabilityHandler {

    private static final ResourceLocation PLAYER_DATA_ID =
            ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "player_data");

    private ArcQuestPlayerCapabilityHandler() {
    }

    @SubscribeEvent
    public static void attachPlayerCapability(AttachCapabilitiesEvent<Entity> event) {
        if (!(event.getObject() instanceof ServerPlayer)) return;

        ArcQuestPlayerCapabilityProvider provider = new ArcQuestPlayerCapabilityProvider();
        event.addCapability(PLAYER_DATA_ID, provider);
        event.addListener(provider::invalidate);
    }
}

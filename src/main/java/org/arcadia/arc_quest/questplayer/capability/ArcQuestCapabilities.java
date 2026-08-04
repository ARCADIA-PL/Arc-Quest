package org.arcadia.arc_quest.questplayer.capability;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;

public final class ArcQuestCapabilities {

    public static final Capability<ArcQuestPlayerCapability> PLAYER_DATA =
            CapabilityManager.get(new CapabilityToken<>() {
            });

    private ArcQuestCapabilities() {
    }

    public static void register(RegisterCapabilitiesEvent event) {
        event.register(ArcQuestPlayerCapability.class);
    }
}

package org.arcadia.arc_quest.integration.jei.network;

import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.Optional;

public final class JeiCatalogNetwork {
    private JeiCatalogNetwork() {}
    public static int register(SimpleChannel channel, int nextId) {
        channel.registerMessage(nextId++, C2SJeiCatalogRequest.class, C2SJeiCatalogRequest::encode,
                C2SJeiCatalogRequest::decode, C2SJeiCatalogRequest::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        channel.registerMessage(nextId++, S2CJeiCatalogChunk.class, S2CJeiCatalogChunk::encode,
                S2CJeiCatalogChunk::decode, S2CJeiCatalogChunk::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        return nextId;
    }
}

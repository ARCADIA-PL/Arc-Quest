package org.arcadia.arc_quest.integration.jei.network;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Optional integration uses ArcQ's normal protocol and remains safe on dedicated servers. */
public final class JeiCatalogNetwork {
    private JeiCatalogNetwork() {}
    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(C2SJeiCatalogRequest.TYPE, C2SJeiCatalogRequest.STREAM_CODEC, C2SJeiCatalogRequest::handle);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            registrar.playToClient(S2CJeiCatalogChunk.TYPE, S2CJeiCatalogChunk.STREAM_CODEC, S2CJeiCatalogChunk::handle);
        } else {
            registrar.playToClient(S2CJeiCatalogChunk.TYPE, S2CJeiCatalogChunk.STREAM_CODEC, (packet, context) -> {});
        }
    }
}

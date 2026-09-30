package org.arcadia.arc_quest.testsupport;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraftforge.network.NetworkEvent;
import java.util.ArrayList;

/** Real registries for plain JUnit; this does not replace a ModLauncher/client/server smoke test. */
public final class MinecraftRegistryTestBootstrap {
    private static boolean initialized;
    private MinecraftRegistryTestBootstrap() {}

    public static synchronized void initialize() {
        if (initialized) return;
        SharedConstants.tryDetectVersion();
        // The plain JUnit classloader does not inject Forge's synthetic no-arg event constructors.
        // Event#getListenerList supports untransformed instances and seeds the same helper cache
        // that NetworkHooks consults. Use its public constructor rather than ignoring a failed bootstrap.
        // The supplier is never invoked: this event is not posted and represents no connection.
        new NetworkEvent(() -> null).getListenerList();
        // NetworkInitialization builds SimpleChannel listeners for precisely NetworkEvent and
        // GatherLoginPayloadsEvent; MC registration channels also listen to NetworkEvent.
        new NetworkEvent.GatherLoginPayloadsEvent(new ArrayList<>(), false).getListenerList();
        Bootstrap.bootStrap();
        initialized = true;
    }
}

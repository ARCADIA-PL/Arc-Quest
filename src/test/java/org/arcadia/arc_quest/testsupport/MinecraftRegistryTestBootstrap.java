package org.arcadia.arc_quest.testsupport;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

/** Real registries for plain JUnit; this does not replace a ModLauncher/client/server smoke test. */
public final class MinecraftRegistryTestBootstrap {
    private static boolean initialized;
    private MinecraftRegistryTestBootstrap() {}

    public static synchronized void initialize() {
        if (initialized) return;
        SharedConstants.tryDetectVersion();
        // ModDevGradle's unit-test launcher supplies NeoForge's transformed classes.
        Bootstrap.bootStrap();
        initialized = true;
    }
}

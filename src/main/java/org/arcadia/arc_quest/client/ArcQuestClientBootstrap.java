package org.arcadia.arc_quest.client;

import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;
import org.arcadia.arc_quest.client.config.ArcQuestModConfigScreen;

public final class ArcQuestClientBootstrap {

    private ArcQuestClientBootstrap() {
    }

    public static void registerConfigScreen() {
        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (minecraft, parent) -> new ArcQuestModConfigScreen(parent)));
    }
}

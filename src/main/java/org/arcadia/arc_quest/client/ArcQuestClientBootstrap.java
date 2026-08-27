package org.arcadia.arc_quest.client;

import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import org.arcadia.arc_quest.client.config.ArcQuestModConfigScreen;

public final class ArcQuestClientBootstrap {

    private ArcQuestClientBootstrap() {
    }

    public static void registerConfigScreen(ModContainer modContainer) {
        modContainer.registerExtensionPoint(IConfigScreenFactory.class,
                (minecraft, parent) -> new ArcQuestModConfigScreen(parent));
    }
}

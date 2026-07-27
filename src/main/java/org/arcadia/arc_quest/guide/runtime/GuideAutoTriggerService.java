package org.arcadia.arc_quest.guide.runtime;

import net.minecraft.server.level.ServerPlayer;

public final class GuideAutoTriggerService {

    private GuideAutoTriggerService() {
    }

    public static void onPlayerLogin(ServerPlayer player) {
        // Registration only defines content. Grants are explicit through GuideUnlockService,
        // commands, datapack phase hooks, or integration code.
    }
}

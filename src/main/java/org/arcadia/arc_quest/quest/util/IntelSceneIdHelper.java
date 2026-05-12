package org.arcadia.arc_quest.quest.util;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.Arc_Quest;

public final class IntelSceneIdHelper {

    private static final String MOD = Arc_Quest.MOD_ID;
    private static final String NS_SEP = "__";

    private IntelSceneIdHelper() {
    }

    public static ResourceLocation questPhaseId(String questId, String phaseId) {
        return ResourceLocation.fromNamespaceAndPath(MOD,
                "quest_phase/" + encodePath(questId) + "/" + encodePath(phaseId));
    }

    static String encodePath(String idOrPath) {
        int colon = idOrPath.indexOf(':');
        if (colon < 0) return idOrPath;
        String namespace = idOrPath.substring(0, colon);
        String path = idOrPath.substring(colon + 1);
        if (MOD.equals(namespace)) return path;
        return namespace + NS_SEP + path;
    }
}

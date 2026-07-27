package org.arcadia.arc_quest.client.hud.guide;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.guide.api.GuideDefinition;
import org.arcadia.arc_quest.guide.network.C2SMarkGuideSeenPacket;
import org.arcadia.arc_quest.guide.network.ClientGuideCache;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;

final class GuideCompletionClient {

    private GuideCompletionClient() {
    }

    static boolean completeIfFinalPage(GuideDefinition guide, ResourceLocation guideId, int pageIndex) {
        if (guide == null || guideId == null || !ClientGuideCache.INSTANCE.isUnlocked(guideId)
                || ClientGuideCache.INSTANCE.isSeen(guideId)
                || pageIndex < guide.getPageCount() - 1) {
            return false;
        }
        ClientGuideCache.INSTANCE.applyLocalSeen(guideId);
        ArcQuestNetwork.sendMarkGuideSeen(new C2SMarkGuideSeenPacket(guideId.toString()));
        return true;
    }
}

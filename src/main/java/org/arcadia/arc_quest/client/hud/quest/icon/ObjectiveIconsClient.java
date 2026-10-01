package org.arcadia.arc_quest.client.hud.quest.icon;

import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.client.hud.quest.icon.portrait.EntityPortraits;

/** Invalidations are independent of the optional recipe viewer. */
@EventBusSubscriber(modid = Arc_Quest.MOD_ID, value = Dist.CLIENT)
public final class ObjectiveIconsClient {
    private static long generation;
    private ObjectiveIconsClient() {}
    public static long generation() { return generation; }
    public static void invalidate() { generation++; ObjectiveIconRegistry.invalidate(); }
    public static void reload(ResourceManager manager) { invalidate(); ObjectiveIconAlpha.clear(); EntityPortraits.reload(manager); }
    public static void clearSession() { invalidate(); ObjectiveIconAlpha.clear(); EntityPortraits.clearSession(); }
    @SubscribeEvent public static void tagsUpdated(TagsUpdatedEvent event) {
        if (event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.CLIENT_PACKET_RECEIVED) invalidate();
    }
}

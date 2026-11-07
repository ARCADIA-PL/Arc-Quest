package org.arcadia.arc_quest.client.ponder;

import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.ponder.foundation.ui.AbstractPonderScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.client.hud.shop.AbstractTradeScreen;

/** Keep the NPC dialogue's shop handoff intact while viewing an item's Ponder scenes. */
@Mod.EventBusSubscriber(modid = Arc_Quest.MOD_ID, value = Dist.CLIENT)
public final class TradePonderNavigation {
    private static org.arcadia.arc_quest.presentation.FocusCoordinator.Handle focus;
    private TradePonderNavigation() {}

    /** External Ponder owns presentation while open; returning resumes the previous owner. */
    @SubscribeEvent
    public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        if(Minecraft.getInstance().screen instanceof AbstractPonderScreen) {
            if(focus==null||focus.ended())focus=org.arcadia.arc_quest.client.presentation.ClientPresentationFocus.coordinator().begin(
                    net.minecraft.resources.ResourceLocation.parse("arc_quest:external_ponder"),1000,
                    new org.arcadia.arc_quest.presentation.FocusCoordinator.Lifecycle(){});
        } else if(focus!=null) { focus.close();focus=null; }
    }

    public static boolean returnToShop(Screen closing) {
        if (!(closing instanceof AbstractPonderScreen) || Minecraft.getInstance().screen != closing) return false;
        for (Screen previous : ScreenOpener.getScreenHistory()) {
            if (previous instanceof AbstractTradeScreen) {
                // Ponder's ordinary onClose clears its history and returns directly to
                // the world. Return before that happens so the shop can restore/close
                // its parent NPC dialogue through the normal server packet flow.
                ScreenOpener.open(null, previous);
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        if(focus!=null){focus.end(org.arcadia.arc_quest.presentation.FocusCoordinator.EndReason.DISCONNECTED);focus=null;}
        if (ScreenOpener.getScreenHistory().stream().anyMatch(AbstractTradeScreen.class::isInstance)) {
            ScreenOpener.clearStack();
        }
    }
}

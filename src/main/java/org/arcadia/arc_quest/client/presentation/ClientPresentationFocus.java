package org.arcadia.arc_quest.client.presentation;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.arcadia.arc_quest.presentation.FocusCoordinator;
/** Client-thread access point, with no Epic Fight dependency. Adapters alone write camera poses. */
@Mod.EventBusSubscriber(modid="arc_quest",value=Dist.CLIENT)
public final class ClientPresentationFocus {
    private static final FocusCoordinator FOCUS=new FocusCoordinator();
    private static ResourceLocation dimension;
    private ClientPresentationFocus(){}
    public static FocusCoordinator coordinator(){if(!Minecraft.getInstance().isSameThread())throw new IllegalStateException("Focus requires client thread");return FOCUS;}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){if(event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null){FOCUS.endAll(FocusCoordinator.EndReason.DISCONNECTED);dimension=null;return;}
        var current=mc.level.dimension().location();
        if(dimension!=null&&!dimension.equals(current))FOCUS.endAll(FocusCoordinator.EndReason.DIMENSION_CHANGED);
        dimension=current;
        if(!mc.player.isAlive()||mc.player.isSpectator())FOCUS.endAll(FocusCoordinator.EndReason.INVALID_PLAYER);
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){FOCUS.endAll(FocusCoordinator.EndReason.DISCONNECTED);dimension=null;}
}

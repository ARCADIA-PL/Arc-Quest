package org.arcadia.arc_quest.client;

import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLConstructModEvent;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.client.config.ArcQuestModConfigScreen;

@Mod.EventBusSubscriber(modid = Arc_Quest.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ArcQuestClientBootstrap {

    private ArcQuestClientBootstrap() {
    }

    @SubscribeEvent
    public static void onConstructMod(FMLConstructModEvent event) {
        // 让 Forge 在加载类之前筛选物理侧，避免专服解析配置界面的 Screen 类型。
        registerConfigScreen();
    }

    public static void registerConfigScreen() {
        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (minecraft, parent) -> new ArcQuestModConfigScreen(parent)));
    }
}

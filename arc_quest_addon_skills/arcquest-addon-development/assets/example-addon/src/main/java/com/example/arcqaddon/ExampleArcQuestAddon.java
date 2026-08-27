package com.example.arcqaddon;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(ExampleArcQuestAddon.MOD_ID)
@SuppressWarnings("removal")
public final class ExampleArcQuestAddon {
    public static final String MOD_ID = "example_arcq_addon";

    public ExampleArcQuestAddon() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(ExampleQuestContent::register);
        modEventBus.addListener(ScenarioParallelQuestContent::register);
        modEventBus.addListener(ExampleDialogueContent::register);
        modEventBus.addListener(ScenarioConditionalDialogueContent::register);
        modEventBus.addListener(ExampleVillagerDialogueExtension::register);
        modEventBus.addListener(ExampleGuideContent::register);
        modEventBus.addListener(ExampleTradeGachaContent::registerTrade);
        modEventBus.addListener(ExampleTradeGachaContent::registerGacha);
        modEventBus.addListener(ScenarioAdvancedTradeGachaContent::registerTrade);
        modEventBus.addListener(ScenarioAdvancedTradeGachaContent::registerGacha);
        modEventBus.addListener(ExampleMarkerContent::register);
        modEventBus.addListener(ScenarioLayeredMarkerResolver::register);
    }
}

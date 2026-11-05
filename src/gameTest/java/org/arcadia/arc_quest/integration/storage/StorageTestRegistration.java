package org.arcadia.arc_quest.integration.storage;

import net.minecraftforge.event.RegisterGameTestsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.arcadia.arc_quest.Arc_Quest;

/** Optional tests cannot themselves force absent mod classes to link during Forge's scan. */
@Mod.EventBusSubscriber(modid = Arc_Quest.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class StorageTestRegistration {
    @SubscribeEvent public static void register(RegisterGameTestsEvent event) {
        if (!Boolean.getBoolean("arc_quest.storage.audit")) return;
        if (ModList.get().isLoaded("beyonddimensions")) {
            event.register(BeyondDimensionsGameTests.class);
            if (Boolean.getBoolean("arc_quest.storage.rules.audit")) event.register(BeyondDimensionsRuleGameTests.class);
        } else event.register(WithoutBeyondDimensionsGameTests.class);
    }
}

package org.arcadia.arc_quest.guide.registry;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.api.ArcQuestAPI;
import org.arcadia.arc_quest.guide.api.GuideCategories;
import org.arcadia.arc_quest.guide.builder.GuideBuilder;
import org.slf4j.Logger;

public final class ArcQuestGuideContent {

    private static final Logger LOGGER = LogUtils.getLogger();

    private ArcQuestGuideContent() {
    }

    public static void registerAll() {
        LOGGER.info("[ArcQuest] Registering builtin guides...");
        registerBuiltinGuides();
        LOGGER.info("[ArcQuest] Total registered guides: {}", GuideRegistry.size());
    }

    private static void registerBuiltinGuides() {
        ArcQuestAPI.registerGuide(
                GuideBuilder.create("arc_quest:movement_basics")
                        .category(GuideCategories.BASICS)
                        .title(Component.translatable("guide.arc_quest.movement_basics.title"))
                        .sortOrder(0)
                        .imagePage(
                                ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "textures/gui/guide/movement.png"),
                                Component.translatable("guide.arc_quest.movement_basics.page_1")
                        )
                        .ponderPage(
                                ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "tutorial/movement_basics"),
                                Component.translatable("guide.arc_quest.movement_basics.page_2")
                        )
                        .build()
        );
    }
}

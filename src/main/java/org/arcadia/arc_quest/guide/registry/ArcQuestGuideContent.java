package org.arcadia.arc_quest.guide.registry;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.guide.api.GuideCategory;
import org.arcadia.arc_quest.guide.api.GuideGroupDefinition;
import org.arcadia.arc_quest.guide.builder.GuideBuilder;
import org.arcadia.arc_quest.guide.builder.GuidePageBuilder;
import org.slf4j.Logger;

public final class ArcQuestGuideContent {

    private static final Logger LOGGER = LogUtils.getLogger();

    private ArcQuestGuideContent() {
    }

    public static void registerAll() {
        LOGGER.info("[ArcQuest] Registering builtin guides...");
        registerBuiltinGuides();
        registerBuiltinGroups();
        LOGGER.info("[ArcQuest] Total registered guides: {}", GuideRegistry.size());
    }

    private static void registerBuiltinGuides() {
        GuideBuilder.create("arc_quest:diamond_demo")
                .category(GuideCategory.BASICS)
                .title("这就是钻石？")
                .summary("第一次获得了钻石")
                .icon(Items.DIAMOND)
                .renderLargeIconOnIntro(true)
                .unlockPopup(true)
                .page(GuidePageBuilder.create()
                        .none()
                        .description("钻石是个好东西，可以用来巴拉巴拉啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊可以用来巴拉巴拉啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊可以用来巴拉巴拉啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊可以用来巴拉巴拉啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊可以用来巴拉巴拉啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊可以用来巴拉巴拉啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊可以用来巴拉巴拉啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊可以用来巴拉巴拉啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊可以用来巴拉巴拉啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊可以用来巴拉巴拉啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊啊"))
                .buildAndRegister();

        GuideBuilder.create("arc_quest:gold_ingot_demo")
                .category(GuideCategory.BASICS)
                .title("这就是金锭？")
                .summary("第一次获得了金锭")
                .icon(Items.GOLD_INGOT)
                .renderLargeIconOnIntro(true)
                .unlockPopup(true)
                .page(GuidePageBuilder.create()
                        .none()
                        .description("金锭是个好东西，可以用来巴拉巴拉"))
                .buildAndRegister();
//        ArcQuestAPI.registerGuide(
//                GuideBuilder.create("arc_quest:movement_basics")
//                        .category(GuideCategories.BASICS)
//                        .title(Component.translatable("guide.arc_quest.movement_basics.title"))
//                        .sortOrder(0)
//                        .imagePage(
//                                ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "textures/gui/splash/prologue_acquire.png"),
//                                Component.translatable("guide.arc_quest.movement_basics.page_1")
//                        )
//                        .ponderQuestPhasePage(
//                                "arc_quest:epic_prologue",
//                                "arc_quest:defend_village",
//                                Component.translatable("guide.arc_quest.movement_basics.page_2")
//                        )
//                        .build()
//        );
    }

    private static void registerBuiltinGroups() {
        ResourceLocation minerals = ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "minerals");
        GuideGroupRegistry.register(new GuideGroupDefinition(
                minerals,
                Component.translatable("arc_quest.guide_group.minerals"),
                0,
                0x55D6E8));
        GuideGroupRegistry.assign(ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "diamond_demo"), minerals);
        GuideGroupRegistry.assign(ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "gold_ingot_demo"), minerals);
    }
}

package org.arcadia.arc_quest.guide.registry;
import org.arcadia.arc_quest.util.log.ArcQuestLog;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.guide.api.GuideCategory;
import org.arcadia.arc_quest.guide.api.GuideGroupDefinition;
import org.arcadia.arc_quest.guide.builder.GuideBuilder;
import org.arcadia.arc_quest.guide.builder.GuidePageBuilder;

public final class ArcQuestGuideContent {
    public static final ResourceLocation JOURNAL_BASICS_GUIDE_ID =
            ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "journal_basics");
    public static final ResourceLocation TRACKING_MENU_GUIDE_ID =
            ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "tracking_menu_basics");
    public static final ResourceLocation PARALLEL_PHASES_GUIDE_ID =
            ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "parallel_phases_basics");

    private ArcQuestGuideContent() {
    }

    public static void registerAll() {
        ArcQuestLog.info(ArcQuestLog.Category.GUIDE, "Registering builtin guides...");
        registerBuiltinGuides();
        registerBuiltinGroups();
        ArcQuestLog.info(ArcQuestLog.Category.GUIDE, "Total registered guides: {}", GuideRegistry.size());
    }

    private static void registerBuiltinGuides() {
        GuideBuilder.create(JOURNAL_BASICS_GUIDE_ID)
                .category(GuideCategory.BASICS)
                .title(Component.translatable("guide.arc_quest.journal_basics.title"))
                .summary(Component.translatable("guide.arc_quest.journal_basics.summary"))
                .icon(Items.BOOK)
                .renderLargeIconOnIntro(true)
                .unlockPopup(true,false)
                .sortOrder(-100)
                .page(GuidePageBuilder.create()
                        .none()
                        .description(Component.translatable("guide.arc_quest.journal_basics.page_1")))
                .buildAndRegister();

        GuideBuilder.create(TRACKING_MENU_GUIDE_ID)
                .category(GuideCategory.BASICS)
                .title(Component.translatable("guide.arc_quest.tracking_menu_basics.title"))
                .summary(Component.translatable("guide.arc_quest.tracking_menu_basics.summary"))
                .icon(Items.COMPASS)
                .renderLargeIconOnIntro(true)
                .unlockPopup(false)
                .sortOrder(-90)
                .page(GuidePageBuilder.create()
                        .none()
                        .description(Component.translatable("guide.arc_quest.tracking_menu_basics.page_1")))
                .buildAndRegister();

        GuideBuilder.create(PARALLEL_PHASES_GUIDE_ID)
                .category(GuideCategory.BASICS)
                .title(Component.translatable("guide.arc_quest.parallel_phases_basics.title"))
                .summary(Component.translatable("guide.arc_quest.parallel_phases_basics.summary"))
                .icon(Items.CLOCK)
                .renderLargeIconOnIntro(true)
                .unlockPopup(false)
                .sortOrder(-80)
                .page(GuidePageBuilder.create()
                        .none()
                        .description(Component.translatable("guide.arc_quest.parallel_phases_basics.page_1")))
                .buildAndRegister();

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
//                Guide构建器.create("arc_quest:movement_basics")
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

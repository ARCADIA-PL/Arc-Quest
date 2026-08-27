package com.example.arcqaddon;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import org.arcadia.arc_quest.api.event.registry.ArcQuestRegistrationEvent;
import org.arcadia.arc_quest.guide.api.GuideCategories;
import org.arcadia.arc_quest.guide.api.GuideDefinition;
import org.arcadia.arc_quest.guide.api.GuideGroupDefinition;
import org.arcadia.arc_quest.guide.api.GuideText;
import org.arcadia.arc_quest.guide.builder.GuideBuilder;
import org.arcadia.arc_quest.guide.builder.GuidePageBuilder;

public final class ExampleGuideContent {
    public static final ResourceLocation GUIDE_ID = id("field_manual");
    public static final ResourceLocation GROUP_ID = id("field_guides");

    private ExampleGuideContent() {
    }

    public static void register(ArcQuestRegistrationEvent.Guide event) {
        event.registerGroup(new GuideGroupDefinition(
                GROUP_ID,
                Component.translatable("guide_group.example_arcq_addon.field_guides"),
                10,
                0xFF4F7D45
        ));

        GuideDefinition guide = GuideBuilder.create(GUIDE_ID)
                .category(GuideCategories.BASICS)
                .title(GuideText.translatable("guide.example_arcq_addon.field_manual.title"))
                .summary(GuideText.translatable("guide.example_arcq_addon.field_manual.summary"))
                .icon(Items.WRITABLE_BOOK)
                .unlockPopup(true, false)
                .page(GuidePageBuilder.create()
                        .none()
                        .description(GuideText.translatable(
                                "guide.example_arcq_addon.field_manual.page.prepare")))
                .page(GuidePageBuilder.create()
                        .none()
                        .description(GuideText.translatable(
                                "guide.example_arcq_addon.field_manual.page.return")))
                .build();

        event.register(guide);
        event.assignGuideToGroup(GUIDE_ID, GROUP_ID);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ExampleArcQuestAddon.MOD_ID, path);
    }
}

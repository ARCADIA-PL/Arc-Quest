package org.arcadia.arc_quest.guide.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import org.arcadia.arc_quest.guide.builder.*;
import org.arcadia.arc_quest.testsupport.MinecraftRegistryTestBootstrap;
import org.arcadia.arc_quest.testsupport.ItemStackTestData;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class GuideMediaTest {
    @BeforeAll static void bootstrap() { MinecraftRegistryTestBootstrap.initialize(); }

    @Test void nativeItemMediaOwnsIndependentNbt() {
        var item=new ItemStack(Items.COMPASS); ItemStackTestData.setString(item, "kind", "original");
        var media=GuideMediaBuilder.item(item).build();
        ItemStackTestData.setString(item, "kind", "changed");
        assertEquals(GuideMediaType.ITEM,media.getType());
        assertEquals("original",ItemStackTestData.read(media.getItem()).getString("kind"));
        ItemStackTestData.setString(media.getItem(), "kind", "changed_again");
        assertEquals("original",ItemStackTestData.read(media.getItem()).getString("kind"));
        assertThrows(IllegalArgumentException.class,()->GuideMediaBuilder.item(ItemStack.EMPTY).build());
    }

    @Test void imageFitIsDeclaredAndTextureIconsNeedNoPlaceholderItem() {
        var texture=ResourceLocation.fromNamespaceAndPath("example", "textures/icon.png");
        var media=GuideMediaBuilder.image(texture).size(16,16).contain().build();
        assertEquals(GuideMediaDefinition.ImageFit.CONTAIN,media.getImageFit());
        var visual=new GuideVisualConfig(ItemStack.EMPTY,true,true,true,false,null,media);
        assertTrue(visual.hasIcon()); assertTrue(visual.shouldRenderLargeIconOnIntro());
        assertTrue(visual.getIcon().isEmpty());
        assertEquals(texture,visual.getIconMedia().getTexture());
    }
}

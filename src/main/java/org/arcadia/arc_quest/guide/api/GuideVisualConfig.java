package org.arcadia.arc_quest.guide.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

public final class GuideVisualConfig {

    public static final GuideVisualConfig EMPTY = new GuideVisualConfig(
            ItemStack.EMPTY, false, false, true, false, null);

    private final ItemStack icon;
    @Nullable private final GuideMediaDefinition iconMedia;
    private final boolean renderLargeIconOnIntro;
    private final boolean showUnlockPopup;
    private final boolean forceOpenWithScreen;
    private final boolean renderPopupBackground;
    @Nullable
    private final ResourceLocation popupBackground;

    public GuideVisualConfig(ItemStack icon,
                             boolean renderLargeIconOnIntro,
                             boolean showUnlockPopup,
                             boolean renderPopupBackground,
                             @Nullable ResourceLocation popupBackground) {
        this(icon, renderLargeIconOnIntro, showUnlockPopup, true,
                renderPopupBackground, popupBackground);
    }

    public GuideVisualConfig(ItemStack icon,
                             boolean renderLargeIconOnIntro,
                             boolean showUnlockPopup,
                             boolean forceOpenWithScreen,
                             boolean renderPopupBackground,
                             @Nullable ResourceLocation popupBackground) {
        this(icon, renderLargeIconOnIntro, showUnlockPopup, forceOpenWithScreen,
                renderPopupBackground, popupBackground, null);
    }

    public GuideVisualConfig(ItemStack icon, boolean renderLargeIconOnIntro,
                             boolean showUnlockPopup, boolean forceOpenWithScreen,
                             boolean renderPopupBackground, @Nullable ResourceLocation popupBackground,
                             @Nullable GuideMediaDefinition iconMedia) {
        if (iconMedia != null && iconMedia.getType() != GuideMediaType.IMAGE)
            throw new IllegalArgumentException("Guide texture icon requires IMAGE media");
        this.iconMedia = iconMedia;
        this.icon = icon == null ? ItemStack.EMPTY : icon.copy();
        this.renderLargeIconOnIntro = renderLargeIconOnIntro;
        this.showUnlockPopup = showUnlockPopup;
        this.forceOpenWithScreen = forceOpenWithScreen;
        this.renderPopupBackground = renderPopupBackground;
        this.popupBackground = popupBackground;
    }

    public ItemStack getIcon() {
        return icon.copy();
    }

    public boolean hasIcon() {
        return iconMedia != null || !icon.isEmpty();
    }

    @Nullable public GuideMediaDefinition getIconMedia() { return iconMedia; }

    public boolean shouldRenderLargeIconOnIntro() {
        return renderLargeIconOnIntro && hasIcon();
    }

    public boolean shouldShowUnlockPopup() {
        return showUnlockPopup;
    }

    public boolean shouldForceOpenWithScreen() {
        return forceOpenWithScreen;
    }

    public boolean shouldRenderPopupBackground() {
        return renderPopupBackground && popupBackground != null;
    }

    @Nullable
    public ResourceLocation getPopupBackground() {
        return popupBackground;
    }
}

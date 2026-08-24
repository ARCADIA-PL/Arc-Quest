package org.arcadia.arc_quest.guide.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

public final class GuideVisualConfig {

    public static final GuideVisualConfig EMPTY = new GuideVisualConfig(
            ItemStack.EMPTY, false, false, true, false, null);

    private final ItemStack icon;
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
        return !icon.isEmpty();
    }

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

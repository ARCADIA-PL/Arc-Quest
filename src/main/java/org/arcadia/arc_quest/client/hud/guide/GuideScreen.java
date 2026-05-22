package org.arcadia.arc_quest.client.hud.guide;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.arcadia.arc_quest.client.hud.ponder.EmbeddedPonderScenePanel;
import org.arcadia.arc_quest.guide.api.GuideDefinition;
import org.arcadia.arc_quest.guide.api.GuideMediaDefinition;
import org.arcadia.arc_quest.guide.api.GuideMediaType;
import org.arcadia.arc_quest.guide.api.GuidePageDefinition;
import org.arcadia.arc_quest.guide.registry.GuideRegistry;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class GuideScreen extends Screen {

    private final GuideDefinition guide;
    private final ResourceLocation guideId;
    private final boolean markSeenOnClose;
    private final EmbeddedPonderScenePanel ponderPanel = new EmbeddedPonderScenePanel();
    private int currentPage;

    public GuideScreen(GuideDefinition guide, int initialPage, boolean markSeenOnClose) {
        super(guide.getTitle());
        this.guide = guide;
        this.guideId = guide.getId();
        this.markSeenOnClose = markSeenOnClose;
        this.currentPage = clampPage(initialPage);
    }

    public static boolean tryOpen(ResourceLocation guideId, int initialPage, boolean markSeenOnClose) {
        GuideDefinition guide = GuideRegistry.get(guideId);
        Minecraft mc = Minecraft.getInstance();
        if (guide == null || mc == null) {
            return false;
        }
        mc.setScreen(new GuideScreen(guide, initialPage, markSeenOnClose));
        return true;
    }

    @Override
    protected void init() {
        super.init();
        refreshMediaBinding();
    }

    @Override
    public void tick() {
        super.tick();
        ponderPanel.tick();
    }

    @Override
    public void removed() {
        ponderPanel.onScreenClosed();
        super.removed();
    }

    @Override
    public void onClose() {
        ponderPanel.onScreenClosed();
        if (markSeenOnClose && minecraft != null && minecraft.player != null && !ClientGuideCache.INSTANCE.isSeen(guideId)) {
            ClientGuideCache.INSTANCE.applyLocalSeen(guideId);
            ArcQuestNetwork.sendMarkGuideSeen(new org.arcadia.arc_quest.guide.network.C2SMarkGuideSeenPacket(guideId.toString()));
        }
        super.onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        if (keyCode == 263) {
            previousPage();
            return true;
        }
        if (keyCode == 262) {
            nextPage();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta > 0) {
            previousPage();
            return true;
        }
        if (delta < 0) {
            nextPage();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int panelWidth = Math.min(420, width - 40);
        int panelHeight = Math.min(230, height - 40);
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2;
        int right = left + panelWidth;

        int textLeft = left + 14;
        int mediaTop = top + 54;
        int mediaHeight = 48;
        int mediaWidth = right - 14 - textLeft;
        if (currentMedia().getType() == GuideMediaType.PONDER
                && ponderPanel.mouseClicked(mouseX, mouseY, button, textLeft, mediaTop, mediaWidth, mediaHeight)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        int panelWidth = Math.min(420, width - 40);
        int panelHeight = Math.min(230, height - 40);
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2;
        int right = left + panelWidth;
        int bottom = top + panelHeight;

        graphics.fill(left, top, right, bottom, 0xE0101016);
        graphics.fill(left, top, right, top + 1, 0xFF4FC3F7);
        graphics.fill(left, bottom - 1, right, bottom, 0xFF4FC3F7);
        graphics.fill(left, top, left + 1, bottom, 0xFF4FC3F7);
        graphics.fill(right - 1, top, right, bottom, 0xFF4FC3F7);

        GuidePageDefinition page = guide.getPage(currentPage);
        GuideMediaDefinition media = page.getMedia();

        int textLeft = left + 14;
        int y = top + 12;
        graphics.drawString(font, guide.getTitle(), textLeft, y, 0xFFFFFF, false);
        y += 14;
        graphics.drawString(font, Component.literal(guide.getCategory().getId().toString()), textLeft, y, 0x7FD8F6, false);
        y += 12;
        graphics.drawString(font, Component.literal("Page " + (currentPage + 1) + " / " + guide.getPageCount()), textLeft, y, 0xB8C7D9, false);
        y += 16;

        int mediaTop = y;
        int mediaHeight = 48;
        int mediaWidth = right - 14 - textLeft;
        if (media.getType() == GuideMediaType.PONDER) {
            ponderPanel.render(graphics, textLeft, mediaTop, mediaWidth, mediaHeight, mouseX, mouseY, partialTick);
        } else {
            graphics.fill(textLeft, mediaTop, right - 14, mediaTop + mediaHeight, 0x501A2430);
            graphics.drawString(font, describeMedia(media), textLeft + 8, mediaTop + 18, 0xA8C9E8, false);
        }
        y = mediaTop + mediaHeight + 10;

        List<FormattedCharSequence> wrapped = font.split(page.getDescriptionText().resolve(null, null), panelWidth - 28);
        int maxLines = Math.max(1, (bottom - y - 24) / 10);
        for (int i = 0; i < Math.min(maxLines, wrapped.size()); i++) {
            graphics.drawString(font, wrapped.get(i), textLeft, y + i * 10, 0xE6EDF7);
        }

        graphics.drawString(font, Component.literal("←/→ or mouse wheel to switch pages"), textLeft, bottom - 20, 0x8CA0B3, false);
        graphics.drawString(font, Component.literal("ESC to close"), right - 88, bottom - 20, 0x8CA0B3, false);
    }

    private Component describeMedia(GuideMediaDefinition media) {
        return switch (media.getType()) {
            case NONE -> Component.literal("No media");
            case IMAGE -> Component.literal("Image: " + media.getTexture());
            case PONDER -> Component.literal("Ponder: " + media.getSceneId());
        };
    }

    private void nextPage() {
        if (currentPage < guide.getPageCount() - 1) {
            currentPage++;
            refreshMediaBinding();
        }
    }

    private void previousPage() {
        if (currentPage > 0) {
            currentPage--;
            refreshMediaBinding();
        }
    }

    private void refreshMediaBinding() {
        GuideMediaDefinition media = currentMedia();
        if (media.getType() == GuideMediaType.PONDER && media.getSceneId() != null) {
            ponderPanel.bind(media.getSceneId(), guide.getCategory().getThemeColor(), media.isAutoplay());
        } else {
            ponderPanel.unbind();
        }
    }

    private GuideMediaDefinition currentMedia() {
        return guide.getPage(currentPage).getMedia();
    }

    private int clampPage(int page) {
        return Math.max(0, Math.min(page, guide.getPageCount() - 1));
    }
}

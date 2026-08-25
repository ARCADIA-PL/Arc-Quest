package org.arcadia.arc_quest.client.hud.guide;


import org.arcadia.arc_quest.client.hud.HudText;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.HudRenderUtil;
import org.arcadia.arc_quest.client.hud.StyledTextUtil;
import org.arcadia.arc_quest.client.hud.dialogue.DialogueScreen;
import org.arcadia.arc_quest.client.hud.ponder.EmbeddedPonderScenePanel;
import org.arcadia.arc_quest.guide.api.GuideDefinition;
import org.arcadia.arc_quest.guide.api.GuideMediaDefinition;
import org.arcadia.arc_quest.guide.api.GuideMediaType;
import org.arcadia.arc_quest.guide.network.C2SUpdateGuideProgressPacket;
import org.arcadia.arc_quest.guide.network.ClientGuideCache;
import org.arcadia.arc_quest.guide.registry.GuideRegistry;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** 相关处理说明。 */
public final class GuidePopupOverlay {

    public static final GuidePopupOverlay INSTANCE = new GuidePopupOverlay();

    private final EmbeddedPonderScenePanel ponderPanel = new EmbeddedPonderScenePanel();
    private final List<FormattedCharSequence> descriptionLines = new ArrayList<>();
    private GuideDefinition guide;
    private ResourceLocation guideId;
    private int pageIndex;
    private boolean markSeenOnClose;
    private boolean closing;
    private float animation;
    private long lastRenderTime;
    private double scroll;
    private double targetScroll;
    private boolean draggingScrollbar;
    private double dragScrollbarOffset;
    private int contentHeight;
    private int maxScroll;
    private int cachedTextWidth = -1;
    private int cachedPage = -1;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int mediaX;
    private int mediaY;
    private int mediaWidth;
    private int mediaHeight;
    private int descriptionX;
    private int descriptionY;
    private int descriptionWidth;
    private int descriptionHeight;
    private int navigationY;

    private GuidePopupOverlay() {
    }

    public boolean open(ResourceLocation id, int initialPage, boolean markSeen) {
        GuideDefinition definition = GuideRegistry.get(id);
        if (definition == null) return false;
        guide = definition;
        guideId = id;
        pageIndex = Math.max(0, Math.min(initialPage, definition.getPageCount() - 1));
        markSeenOnClose = markSeen;
        closing = false;
        animation = 0f;
        lastRenderTime = 0L;
        scroll = 0;
        targetScroll = 0;
        draggingScrollbar = false;
        invalidateTextCache();
        refreshMediaBinding();
        return true;
    }

    public boolean isActive() {
        return guide != null;
    }

    public void tick() {
        if (isActive()) ponderPanel.tick();
    }

    public void clear() {
        clearState();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof InputHostScreen) {
            minecraft.setScreen(null);
        }
    }

    private void clearState() {
        guide = null;
        guideId = null;
        closing = false;
        animation = 0f;
        draggingScrollbar = false;
        ponderPanel.unbind();
    }

    /** 相关处理说明。 */
    public void ensureInputScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (isActive() && minecraft.screen == null) {
            minecraft.setScreen(new InputHostScreen());
        }
    }

    public void close() {
        if (!isActive() || closing) return;
        closing = true;
        if (markSeenOnClose) GuideCompletionClient.completeIfFinalPage(guide, guideId, pageIndex);
    }

    public void render(@Nullable Screen owner, GuiGraphics graphics,
                       int screenWidth, int screenHeight, float partialTick) {
        if (!isActive()) return;
        long now = Util.getMillis();
        if (lastRenderTime == 0L) lastRenderTime = now;
        float deltaTime = Math.min(0.1f, (now - lastRenderTime) / 1000f);
        lastRenderTime = now;
        animation = HudAnimUtil.step(animation, closing ? 0f : 1f, closing ? 5.5f : 4.2f, deltaTime);
        if (closing && animation <= 0.01f) {
            Minecraft minecraft = Minecraft.getInstance();
            boolean hosted = minecraft.screen instanceof InputHostScreen;
            clearState();
            if (hosted) minecraft.setScreen(DialogueScreen.fromCurrentSession());
            return;
        }

        float eased = closing ? HudAnimUtil.easeInCubic(animation) : HudAnimUtil.easeOutCubic(animation);
        Font font = Minecraft.getInstance().font;
        panelWidth = GuideConstants.guidePanelWidth(screenWidth);
        int contentWidth = panelWidth - 40;
        descriptionWidth = contentWidth - 10;
        List<FormattedCharSequence> summaryLines = summaryLines(font, contentWidth);
        int summaryHeight = summaryLines.isEmpty()
                ? 0
                : (int) Math.ceil(summaryLines.size() * (font.lineHeight + 2) * 0.82f);
        int dividerOffsetY = summaryLines.isEmpty() ? 34 : 29 + summaryHeight + 5;
        int mediaOffsetY = dividerOffsetY + 11;

        rebuildTextCache(descriptionWidth);
        contentHeight = descriptionLines.isEmpty() ? 0 : descriptionLines.size() * (font.lineHeight + 4) + 6;
        mediaHeight = getMediaHeight(contentWidth);
        int descriptionOffsetY = mediaOffsetY + mediaHeight + (contentHeight > 0 ? 8 : 2);
        int desiredPanelHeight = descriptionOffsetY + contentHeight + 33;
        panelHeight = GuideConstants.guidePopupHeight(screenHeight, desiredPanelHeight);
        panelX = (screenWidth - panelWidth) / 2;
        int targetY = (screenHeight - panelHeight) / 2;
        panelY = Math.round(screenHeight + 20 + (targetY - screenHeight - 20) * eased);
        int alpha = Math.max(0, Math.min(255, (int) (255 * animation)));
        int theme = guide.getCategory().getThemeColor();

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 4600);
        graphics.fill(0, 0, screenWidth, screenHeight, HudAnimUtil.withAlpha(0x000000, (int) (70 * animation)));
        HudAnimUtil.drawFrame(graphics, panelX, panelY, panelWidth, panelHeight,
                HudAnimUtil.withAlpha(0x101318, (int) (238 * animation)),
                HudAnimUtil.withAlpha(0x58636F, (int) (190 * animation)));
        HudRenderUtil.drawCyberneticEdge(graphics, panelX, panelY, panelHeight, theme, alpha);

        int contentX = panelX + 20;
        var fittedTitle = StyledTextUtil.fitSingleLine(font, guide.getTitle(), contentWidth - 35);
        graphics.drawString(font, fittedTitle, contentX, panelY + 15,
                HudAnimUtil.withAlpha(0xFFFFFF, alpha), true);
        if (!summaryLines.isEmpty()) {
            graphics.pose().pushPose();
            graphics.pose().translate(contentX, panelY + 29, 0);
            graphics.pose().scale(0.82f, 0.82f, 1f);
            for (FormattedCharSequence line : summaryLines) {
                graphics.drawString(font, line, 0, 0,
                        HudAnimUtil.withAlpha(0xAAB3BD, alpha), false);
                graphics.pose().translate(0, font.lineHeight + 2, 0);
            }
            graphics.pose().popPose();
        }
        graphics.drawString(font, "x", panelX + panelWidth - 25, panelY + 15,
                HudAnimUtil.withAlpha(theme, alpha), false);
        int dividerY = panelY + dividerOffsetY;
        graphics.fill(contentX, dividerY, contentX + contentWidth, dividerY + 1,
                HudAnimUtil.withAlpha(theme, (int) (150 * animation)));

        mediaX = contentX;
        mediaY = panelY + mediaOffsetY;
        mediaWidth = contentWidth;
        if (pageIndex == 0 && guide.getVisualConfig().shouldRenderLargeIconOnIntro()) {
            graphics.pose().pushPose();
            graphics.pose().translate(panelX + panelWidth / 2f - GuideConstants.INTRO_ICON_SIZE / 2f,
                    mediaY + (mediaHeight - GuideConstants.INTRO_ICON_SIZE) / 2f, 0);
            graphics.pose().scale(GuideConstants.INTRO_ICON_SCALE, GuideConstants.INTRO_ICON_SCALE, 1f);
            graphics.renderItem(guide.getVisualConfig().getIcon(), 0, 0);
            graphics.pose().popPose();
        } else if (currentMedia().getType() != GuideMediaType.NONE) {
            GuideMediaRenderer.drawMedia(owner, graphics, mediaX, mediaY, mediaWidth, mediaHeight,
                    currentMedia(), ponderPanel, 0, 0, partialTick, alpha, theme);
        }

        descriptionX = contentX;
        descriptionY = mediaY + mediaHeight + (contentHeight > 0 ? 8 : 2);
        navigationY = panelY + panelHeight - 25;
        descriptionHeight = Math.max(0, navigationY - descriptionY - 8);
        maxScroll = Math.max(0, contentHeight - descriptionHeight);
        targetScroll = Math.max(0, Math.min(maxScroll, targetScroll));
        scroll += (targetScroll - scroll) * Math.min(1f, deltaTime * 16f);

        if (descriptionHeight > 0) {
            graphics.enableScissor(descriptionX, descriptionY,
                    descriptionX + descriptionWidth, descriptionY + descriptionHeight);
            int lineY = descriptionY - (int) Math.round(scroll);
            for (FormattedCharSequence line : descriptionLines) {
                if (lineY + font.lineHeight >= descriptionY && lineY <= descriptionY + descriptionHeight) {
                    graphics.drawString(font, line, descriptionX, lineY,
                            HudAnimUtil.withAlpha(0xD8D8D8, alpha), false);
                }
                lineY += font.lineHeight + 4;
            }
            graphics.disableScissor();
        }

        if (maxScroll > 0 && descriptionHeight > 0) {
            int barX = panelX + panelWidth - 12;
            int thumbHeight = Math.max(16,
                    (int) (descriptionHeight * (descriptionHeight / (float) contentHeight)));
            int travel = Math.max(0, descriptionHeight - thumbHeight);
            int thumbY = descriptionY + (int) (travel * (scroll / maxScroll));
            graphics.fill(barX, descriptionY, barX + 4, descriptionY + descriptionHeight,
                    HudAnimUtil.withAlpha(0xFFFFFF, (int) (35 * animation)));
            graphics.fill(barX, thumbY, barX + 4, thumbY + thumbHeight,
                    HudAnimUtil.withAlpha(draggingScrollbar ? 0xFFFFFF : theme, alpha));
        }

        graphics.drawString(font, HudText.of("guide.page", pageIndex + 1, guide.getPageCount()),
                contentX, navigationY, HudAnimUtil.withAlpha(0x777F88, alpha), false);
        if (pageIndex > 0) graphics.drawString(font, "<", panelX + panelWidth - 52, navigationY,
                HudAnimUtil.withAlpha(0xFFFFFF, alpha), false);
        if (pageIndex + 1 < guide.getPageCount()) {
            graphics.drawString(font, ">", panelX + panelWidth - 28,
                    navigationY, HudAnimUtil.withAlpha(0xFFFFFF, alpha), false);
            if (!ClientGuideCache.INSTANCE.isSeen(guideId)) {
                HudRenderUtil.drawBreathingRedDot(graphics, panelX + panelWidth - 20,
                        navigationY - 1, alpha / 255f);
            }
        }
        graphics.pose().popPose();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isActive()) return false;
        if (button != 0) return true;
        if (!hit(mouseX, mouseY, panelX, panelY, panelWidth, panelHeight)) {
            close();
            return true;
        }
        if (hit(mouseX, mouseY, panelX + panelWidth - 32, panelY + 8, 28, 28)) {
            close();
            return true;
        }
        int navigationHitY = navigationY - 5;
        if (pageIndex > 0 && hit(mouseX, mouseY, panelX + panelWidth - 62, navigationHitY, 24, 24)) {
            changePage(-1);
            return true;
        }
        if (pageIndex + 1 < guide.getPageCount()
                && hit(mouseX, mouseY, panelX + panelWidth - 38, navigationHitY, 24, 24)) {
            changePage(1);
            return true;
        }
        if (maxScroll > 0 && descriptionHeight > 0
                && hit(mouseX, mouseY, panelX + panelWidth - 18,
                descriptionY, 14, descriptionHeight)) {
            draggingScrollbar = true;
            int thumbHeight = Math.max(16,
                    (int) (descriptionHeight * (descriptionHeight / (float) contentHeight)));
            int travel = Math.max(1, descriptionHeight - thumbHeight);
            int thumbY = descriptionY + (int) (travel * (scroll / maxScroll));
            dragScrollbarOffset = mouseY >= thumbY && mouseY <= thumbY + thumbHeight
                    ? mouseY - thumbY : thumbHeight / 2.0;
            updateScrollFromMouse(mouseY, thumbHeight);
            return true;
        }
        if (currentMedia().getType() == GuideMediaType.PONDER) {
            ponderPanel.mouseClicked(mouseX, mouseY, button,
                    mediaX, mediaY, mediaWidth, mediaHeight);
        }
        return true;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button) {
        if (!isActive()) return false;
        if (draggingScrollbar) {
            int thumbHeight = Math.max(16,
                    (int) (descriptionHeight * (descriptionHeight / (float) contentHeight)));
            updateScrollFromMouse(mouseY, thumbHeight);
        }
        return true;
    }

    public boolean mouseReleased(int button) {
        if (!isActive()) return false;
        if (button == 0) draggingScrollbar = false;
        return true;
    }

    public boolean mouseScrolled(double delta) {
        if (!isActive()) return false;
        targetScroll = Math.max(0, Math.min(maxScroll, targetScroll - delta * 24.0));
        return true;
    }

    public boolean keyPressed(int keyCode) {
        if (!isActive()) return false;
        if (keyCode == 256) close();
        else if (keyCode == 263 || keyCode == 65) changePage(-1);
        else if (keyCode == 262 || keyCode == 68) changePage(1);
        return true;
    }

    private void changePage(int direction) {
        int next = Math.max(0, Math.min(guide.getPageCount() - 1, pageIndex + direction));
        if (next == pageIndex) return;
        pageIndex = next;
        scroll = 0;
        targetScroll = 0;
        invalidateTextCache();
        refreshMediaBinding();
        if (ClientGuideCache.INSTANCE.isUnlocked(guideId)) {
            ClientGuideCache.INSTANCE.applyLocalProgress(guideId, pageIndex);
            ArcQuestNetwork.sendGuideProgress(new C2SUpdateGuideProgressPacket(guideId, pageIndex));
        }
        if (direction > 0) GuideCompletionClient.completeIfFinalPage(guide, guideId, pageIndex);
    }

    private void rebuildTextCache(int width) {
        if (cachedPage == pageIndex && cachedTextWidth == width) return;
        descriptionLines.clear();
        descriptionLines.addAll(Minecraft.getInstance().font.split(
                GuideClientTextResolver.resolve(guide.getPage(pageIndex).getDescriptionText().resolve(null, null)), Math.max(1, width)));
        cachedPage = pageIndex;
        cachedTextWidth = width;
    }

    private void invalidateTextCache() {
        cachedPage = -1;
        cachedTextWidth = -1;
        descriptionLines.clear();
    }

    private List<FormattedCharSequence> summaryLines(Font font, int contentWidth) {
        if (guide.getSummary().getString().isBlank()) return List.of();
        int width = Math.max(20, (int) ((contentWidth - 10) / 0.82f));
        List<FormattedCharSequence> lines = font.split(guide.getSummary(), width);
        return lines.size() <= 3 ? lines : lines.subList(0, 3);
    }

    private int getMediaHeight(int contentWidth) {
        boolean iconIntro = pageIndex == 0 && guide.getVisualConfig().shouldRenderLargeIconOnIntro();
        if (iconIntro && currentMedia().getType() == GuideMediaType.NONE) {
            return GuideConstants.INTRO_ICON_SECTION_HEIGHT;
        }
        boolean hasMedia = iconIntro || currentMedia().getType() != GuideMediaType.NONE;
        return hasMedia ? Math.max(54, Math.min(104, (int) (contentWidth * 9f / 16f))) : 0;
    }

    private GuideMediaDefinition currentMedia() {
        return guide.getPage(pageIndex).getMedia();
    }

    private void refreshMediaBinding() {
        GuideMediaDefinition media = currentMedia();
        if (media.getType() == GuideMediaType.PONDER && media.getSceneId() != null) {
            ponderPanel.bind(media.getSceneId(), guide.getCategory().getThemeColor(), media.isAutoplay());
        } else {
            ponderPanel.unbind();
        }
    }

    private void updateScrollFromMouse(double mouseY, int thumbHeight) {
        int travel = Math.max(1, descriptionHeight - thumbHeight);
        targetScroll = Math.max(0.0, Math.min(1.0,
                (mouseY - descriptionY - dragScrollbarOffset) / travel)) * maxScroll;
    }

    private boolean hit(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    /** 相关处理说明。 */
    private static final class InputHostScreen extends Screen {
        private InputHostScreen() {
            super(Component.empty());
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public boolean shouldCloseOnEsc() {
            return false;
        }

        @Override
        public void onClose() {
            GuidePopupOverlay.INSTANCE.close();
        }
    }
}

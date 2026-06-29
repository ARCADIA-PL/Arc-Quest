// file_name: GuideListScreen.java
package org.arcadia.arc_quest.client.hud.guide;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import org.arcadia.arc_quest.client.events.ClientEventHandler;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.ponder.EmbeddedPonderScenePanel;
import org.arcadia.arc_quest.guide.api.GuideCategory;
import org.arcadia.arc_quest.guide.api.GuideDefinition;
import org.arcadia.arc_quest.guide.api.GuideMediaDefinition;
import org.arcadia.arc_quest.guide.api.GuideMediaType;
import org.arcadia.arc_quest.guide.network.ClientGuideCache;
import org.arcadia.arc_quest.guide.registry.GuideRegistry;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class GuideListScreen extends Screen {
    private final EmbeddedPonderScenePanel ponderPanel = new EmbeddedPonderScenePanel();
    private final GuideCategoryTabs tabs = new GuideCategoryTabs(this);
    private final GuideListPanel listPanel = new GuideListPanel(this);
    private final GuideContentPanel contentPanel = new GuideContentPanel(this);

    private ResourceLocation selectedCategoryId, selectedGuideId;
    private int selectedPageIndex;
    private GuideDefinition selectedGuide;

    private float transitionAlpha = 0f;
    private boolean isClosing = false;
    private float effectiveAlpha = 0f;
    private float dt = 0f;
    private long lastRenderTime = 0;
    private int currentThemeColor = 0xFFFFFF;

    public GuideListScreen() {
        super(Component.translatable("gui.arc_quest.guide_list.title"));
    }

    public float getUiScale() {
        if (minecraft == null) return 1.0f;
        double guiScale = minecraft.getWindow().getGuiScale();
        if (guiScale == 0) guiScale = 1.0;
        float scale = (float) (3.0 / guiScale);
        float sw = width / scale, sh = height / scale;
        float minW = 480f, minH = 260f;
        if (sw < minW) { scale = width / minW; sh = height / scale; }
        if (sh < minH) scale = height / minH;
        return scale;
    }

    public int getScaledWidth() { return (int) (width / getUiScale()); }
    public int getScaledHeight() { return (int) (height / getUiScale()); }

    public void enableScissor(GuiGraphics g, int x, int y, int x2, int y2) {
        float s = getUiScale();
        g.enableScissor((int) (x * s), (int) (y * s), (int) (x2 * s), (int) (y2 * s));
    }

    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) mc.setScreen(new GuideListScreen());
    }

    @Override
    protected void init() {
        super.init();
        transitionAlpha = 0f;
        isClosing = false;
        lastRenderTime = 0;
        rebuildSelection();
        refreshMediaBinding();
    }

    @Override
    public void tick() {
        super.tick();
        ponderPanel.tick();
    }

    @Override
    public void onClose() {
        if (!isClosing) isClosing = true;
    }

    @Override
    public void removed() {
        ponderPanel.onScreenClosed();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 || minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        if (contentPanel != null && contentPanel.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (isClosing || button != 0) return super.mouseClicked(mx, my, button);

        float uiScale = getUiScale();
        double smx = mx / uiScale, smy = my / uiScale;
        int sw = getScaledWidth(), sh = getScaledHeight();

        float slideOffset = (1f - getEaseProgress()) * 200f;
        int listX = GuideConstants.LIST_MARGIN - (int) slideOffset;
        int listY = 38 + GuideConstants.TAB_HEIGHT + 6, listH = sh - 20 - listY;
        int detailX = GuideConstants.LIST_MARGIN + GuideConstants.LIST_WIDTH + GuideConstants.DETAIL_MARGIN + (int) slideOffset;
        int detailW = sw - detailX - GuideConstants.DETAIL_MARGIN;

        if (tabs.mouseClicked(smx, smy, listX, detailW + GuideConstants.LIST_WIDTH + GuideConstants.DETAIL_MARGIN)) return true;
        if (listPanel.mouseClicked(smx, smy, listX, listY, GuideConstants.LIST_WIDTH, listH)) return true;
        if (contentPanel.mouseClicked(smx, smy, detailX, listY, detailW, listH)) return true;

        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dragX, double dragY) {
        float uiScale = getUiScale();
        double smx = mx / uiScale, smy = my / uiScale;
        int sh = getScaledHeight();

        int listY = 38 + GuideConstants.TAB_HEIGHT + 6, listH = sh - 20 - listY;
        if (listPanel.mouseDragged(smx, smy, listY, listH)) return true;
        if (contentPanel.mouseDragged(smx, smy, listY, listH)) return true;
        return super.mouseDragged(mx, my, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        listPanel.mouseReleased(button);
        contentPanel.mouseReleased(button);
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        double delta = scrollY;
        float uiScale = getUiScale();
        double smx = mx / uiScale, smy = my / uiScale;
        int sw = getScaledWidth(), sh = getScaledHeight();
        if (isClosing) return false;

        float slideOffset = (1f - getEaseProgress()) * 200f;
        int listX = GuideConstants.LIST_MARGIN - (int) slideOffset;
        int listY = 38 + GuideConstants.TAB_HEIGHT + 6, listH = sh - 20 - listY;
        int detailX = GuideConstants.LIST_MARGIN + GuideConstants.LIST_WIDTH + GuideConstants.DETAIL_MARGIN + (int) slideOffset;
        int detailW = sw - detailX - GuideConstants.DETAIL_MARGIN;

        if (tabs.mouseScrolled(smx, smy, delta, listX, detailW + GuideConstants.LIST_WIDTH + GuideConstants.DETAIL_MARGIN)) return true;
        if (listPanel.mouseScrolled(smx, smy, delta, listX, listY, GuideConstants.LIST_WIDTH, listH)) return true;
        if (contentPanel.mouseScrolled(smx, smy, delta, detailX, listY, detailW, listH)) return true;

        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    private float getEaseProgress() {
        return isClosing ? HudAnimUtil.easeInCubic(transitionAlpha) : HudAnimUtil.easeOutCubic(transitionAlpha);
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        float uiScale = getUiScale();
        int smx = (int) (mouseX / uiScale), smy = (int) (mouseY / uiScale);
        int sw = getScaledWidth(), sh = getScaledHeight();

        long now = Util.getMillis();
        if (lastRenderTime == 0) lastRenderTime = now;
        dt = (now - lastRenderTime) / 1000f;
        lastRenderTime = now;
        if (dt > 0.1f) dt = 0.1f;

        transitionAlpha = HudAnimUtil.lerp(transitionAlpha, isClosing ? 0f : 1f, isClosing ? 0.2f : 0.12f, dt);
        if (isClosing && transitionAlpha <= 0.01f) {
            if (minecraft != null) minecraft.setScreen(null);
            return;
        }

        effectiveAlpha = transitionAlpha;
        float easeProgress = getEaseProgress();
        float slideOffset = (1f - easeProgress) * 200f;
        int safeAlpha = (int) (255 * effectiveAlpha);

        g.pose().pushPose();
        g.pose().scale(uiScale, uiScale, 1f);

        // 纯正的深邃黑底，极少量的主题色渗透 (对标 Journal)
        int bgTint = HudAnimUtil.lerpColor(0x000000, currentThemeColor, 0.03f);
        g.fill(0, 0, sw, sh, HudAnimUtil.withAlpha(bgTint, (int) (180 * effectiveAlpha)));

        if (safeAlpha > 8) {
            g.pose().pushPose();
            g.pose().translate(sw / 2f, 14, 0);
            float titleScale = 0.95f + 0.05f * easeProgress;
            g.pose().scale(titleScale, titleScale, 1f);
            g.pose().translate(-sw / 2f, -14, 0);
            g.drawCenteredString(font, title, sw / 2, 14, HudAnimUtil.withAlpha(0xFFFFFF, safeAlpha));
            g.pose().popPose();
        }

        int listX = GuideConstants.LIST_MARGIN - (int) slideOffset;
        int listY = 38 + GuideConstants.TAB_HEIGHT + 6;
        int listH = sh - 20 - listY;
        int detailX = GuideConstants.LIST_MARGIN + GuideConstants.LIST_WIDTH + GuideConstants.DETAIL_MARGIN + (int) slideOffset;
        int detailW = sw - detailX - GuideConstants.DETAIL_MARGIN;

        // 渲染顶部可滑动的分类 Tabs
        tabs.render(g, smx, smy, safeAlpha, listX, detailW + GuideConstants.LIST_WIDTH + GuideConstants.DETAIL_MARGIN, currentThemeColor, dt);

        // 渲染列表面板外框 (降低边框 Alpha，消除光污染)
        HudAnimUtil.drawFrame(g, listX, listY, GuideConstants.LIST_WIDTH, listH,
                HudAnimUtil.withAlpha(0x000000, (int) (0x44 * effectiveAlpha)),
                HudAnimUtil.withAlpha(0x333333, safeAlpha));
        listPanel.render(g, listX, listY, GuideConstants.LIST_WIDTH, listH, smx, smy, currentThemeColor, dt);

        // 渲染内容面板外框 (移除主题色，替换为极简灰 0x333333)
        HudAnimUtil.drawFrame(g, detailX, listY, detailW, listH,
                HudAnimUtil.withAlpha(0x000000, (int) (0x44 * effectiveAlpha)),
                HudAnimUtil.withAlpha(0x333333, safeAlpha));
        contentPanel.render(g, detailX, listY, detailW, listH, smx, smy, currentThemeColor, dt);

        g.pose().popPose();
    }

    void rebuildSelection() {
        List<GuideCategory> cats = visibleCategories();
        if (cats.isEmpty()) {
            selectedCategoryId = null; selectedGuideId = null; selectedGuide = null;
            selectedPageIndex = 0; contentPanel.resetState(); listPanel.resetState(); return;
        }
        if (selectedCategoryId == null || cats.stream().noneMatch(c -> c.getId().equals(selectedCategoryId)))
            selectedCategoryId = cats.get(0).getId();

        List<GuideDefinition> guides = guidesForSelectedCategory();
        if (guides.isEmpty()) {
            selectedGuideId = null; selectedGuide = null; selectedPageIndex = 0;
            contentPanel.resetState(); listPanel.resetState(); return;
        }
        if (selectedGuideId == null || guides.stream().noneMatch(g -> g.getId().equals(selectedGuideId)))
            selectedGuideId = guides.get(0).getId();

        selectedGuide = guides.stream().filter(g -> g.getId().equals(selectedGuideId)).findFirst().orElse(guides.get(0));
        selectedGuideId = selectedGuide.getId();
        currentThemeColor = selectedGuide.getCategory().getThemeColor();
        selectedPageIndex = Math.max(0, Math.min(selectedPageIndex, selectedGuide.getPageCount() - 1));
        listPanel.scrollToSelected();
    }

    List<GuideCategory> visibleCategories() {
        List<GuideCategory> out = new ArrayList<>();
        for (GuideCategory cat : GuideRegistry.getAllCategories())
            if (GuideRegistry.getAll().stream().anyMatch(g -> isVisibleGuide(g) && g.getCategory().getId().equals(cat.getId())))
                out.add(cat);
        out.sort(Comparator.comparingInt(GuideCategory::getSortOrder).thenComparing(c -> c.getId().toString()));
        return out;
    }

    List<GuideDefinition> guidesForSelectedCategory() {
        List<GuideDefinition> out = new ArrayList<>();
        if (selectedCategoryId == null) return out;
        for (GuideDefinition guide : GuideRegistry.getAll())
            if (isVisibleGuide(guide) && guide.getCategory().getId().equals(selectedCategoryId)) out.add(guide);
        out.sort(Comparator.comparingInt(GuideDefinition::getSortOrder).thenComparing(g -> g.getId().toString()));
        return out;
    }

    boolean isVisibleGuide(GuideDefinition guide) {
        return guide != null && !guide.isHidden() && ClientGuideCache.INSTANCE.isUnlocked(guide.getId()) && guide.getCategory() != null;
    }

    void selectCategory(ResourceLocation id) {
        if (Objects.equals(selectedCategoryId, id)) return;
        selectedCategoryId = id; selectedGuideId = null; selectedPageIndex = 0;
        contentPanel.resetState(); listPanel.resetState(); rebuildSelection(); refreshMediaBinding(); playClick();
    }

    void selectGuide(ResourceLocation id) {
        if (Objects.equals(selectedGuideId, id)) return;
        selectedGuideId = id; selectedPageIndex = 0;
        contentPanel.resetState(); rebuildSelection(); refreshMediaBinding(); playClick();
    }

    public void prevPage() {
        if (selectedGuide != null && selectedPageIndex > 0) {
            selectedPageIndex--; contentPanel.resetState(); refreshMediaBinding(); playClick();
        }
    }
    public void nextPage() {
        if (selectedGuide != null && selectedPageIndex < selectedGuide.getPageCount() - 1) {
            selectedPageIndex++; contentPanel.resetState(); refreshMediaBinding(); playClick();
        }
    }

    void refreshMediaBinding() {
        GuideMediaDefinition m = currentMedia();
        if (m != null && m.getType() == GuideMediaType.PONDER && m.getSceneId() != null)
            ponderPanel.bind(m.getSceneId(), currentThemeColor, m.isAutoplay());
        else ponderPanel.unbind();
    }

    public void playClick() {
        if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    public Font getFont() { return font; }
    public float getEffectiveAlpha() { return effectiveAlpha; }
    public ResourceLocation getSelectedCategoryId() { return selectedCategoryId; }
    public ResourceLocation getSelectedGuideId() { return selectedGuideId; }
    public GuideDefinition getSelectedGuide() { return selectedGuide; }
    public int getSelectedPageIndex() { return selectedPageIndex; }
    public EmbeddedPonderScenePanel ponderPanel() { return ponderPanel; }
    GuideMediaDefinition currentMedia() { return selectedGuide == null ? null : selectedGuide.getPage(selectedPageIndex).getMedia(); }
}
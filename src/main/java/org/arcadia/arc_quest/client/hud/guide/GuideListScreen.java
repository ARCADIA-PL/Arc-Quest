package org.arcadia.arc_quest.client.hud.guide;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
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
    private float listScroll = 0.0F, listTargetScroll = 0.0F;
    private GuideDefinition selectedGuide;
    private float openAnim = 0.0F;
    private double descScroll = 0.0, descTargetScroll = 0.0;
    private float tabAnimX = 0.0F, tabAnimW = 0.0F;
    private float tabScrollOffset = 0.0F, tabTargetScroll = 0.0F;
    private long lastRenderTime;
    private transient GuideScreenLayout.TerminalLayout cachedLayout;

    public GuideListScreen() {
        super(Component.translatable("gui.arc_quest.guide_list.title"));
    }

    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) mc.setScreen(new GuideListScreen());
    }

    @Override
    protected void init() {
        super.init();
        openAnim = 0.0F;
        lastRenderTime = System.currentTimeMillis();
        tabScrollOffset = 0.0F;
        tabTargetScroll = 0.0F;
        rebuildSelection();
        refreshMediaBinding();
    }

    @Override
    public void tick() {
        super.tick();
        long now = System.currentTimeMillis();
        float dt = (now - lastRenderTime) / 1000f;
        if (dt <= 0f || dt > 0.3f) dt = 1f / 60f;
        lastRenderTime = now;

        openAnim = HudAnimUtil.advanceByDuration(openAnim, GuideConstants.OPEN_DURATION, dt);
        descScroll += (descTargetScroll - descScroll) * Math.min(1.0f, dt * GuideConstants.SCROLL_SPEED);
        listScroll += (listTargetScroll - listScroll) * Math.min(1.0f, dt * GuideConstants.SCROLL_SPEED);
        tabScrollOffset += (tabTargetScroll - tabScrollOffset) * Math.min(1.0f, dt * GuideConstants.TAB_INDICATOR_SPEED);
        ponderPanel.tick();
    }

    void updateTabIndicator(float targetX, float targetW) {
        if (tabAnimX == 0.0F) {
            tabAnimX = targetX;
            tabAnimW = targetW;
        } else {
            float dt = (System.currentTimeMillis() - lastRenderTime) / 1000f;
            if (dt <= 0f || dt > 0.3f) dt = 1f / 60f;
            tabAnimX += (targetX - tabAnimX) * Math.min(1.0f, dt * GuideConstants.TAB_INDICATOR_SPEED);
            tabAnimW += (targetW - tabAnimW) * Math.min(1.0f, dt * GuideConstants.TAB_INDICATOR_SPEED);
        }
    }

    void adjustTabScroll(float delta) {
        tabTargetScroll = Math.max(0, tabTargetScroll + delta);
    }

    float getTabScrollOffset() { return tabScrollOffset; }

    @Override
    public void removed() {
        ponderPanel.onScreenClosed();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        if (tabs.mouseClicked(mouseX, mouseY)) return true;
        if (listPanel.mouseClicked(mouseX, mouseY)) return true;
        if (contentPanel.mouseClicked(mouseX, mouseY)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (listPanel.mouseScrolled(mouseX, mouseY, delta)) return true;
        if (contentPanel.mouseScrolled(mouseX, mouseY, delta)) return true;
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        cachedLayout = GuideScreenLayout.computeTerminal(width, height);
        float reveal = HudAnimUtil.easeOutCubic(openAnim);
        int alpha = (int) (255 * reveal);

        g.fill(0, 0, width, height, HudAnimUtil.withAlpha(0x02050A, (int) (160 * reveal)));
        drawHolographicScanlines(g, alpha);

        int titleY = 14;
        g.drawCenteredString(font, title, width / 2, titleY, HudAnimUtil.withAlpha(GuideConstants.TEXT, alpha));
        g.fill(width / 2 - 60, titleY + 12, width / 2 + 60, titleY + 13, HudAnimUtil.withAlpha(getThemeColor(), (int) (alpha * 0.5F)));

        tabs.render(g, mouseX, mouseY, alpha);
        listPanel.render(g, mouseX, mouseY, alpha);
        contentPanel.render(g, mouseX, mouseY, partialTick, alpha);
    }

    private void drawHolographicScanlines(GuiGraphics g, int alpha) {
        long time = System.currentTimeMillis();
        int scanY = (int) ((time / 14) % height);
        g.fill(0, scanY - 1, width, scanY, HudAnimUtil.withAlpha(getThemeColor(), (int) (alpha * 0.03F)));
        g.fill(0, scanY, width, scanY + 1, HudAnimUtil.withAlpha(getThemeColor(), (int) (alpha * 0.06F)));
        g.fill(0, scanY + 1, width, scanY + 2, HudAnimUtil.withAlpha(getThemeColor(), (int) (alpha * 0.03F)));
    }

    void rebuildSelection() {
        List<GuideCategory> cats = visibleCategories();
        if (cats.isEmpty()) {
            selectedCategoryId = null; selectedGuideId = null; selectedGuide = null;
            selectedPageIndex = 0; listTargetScroll = 0; listScroll = 0; resetDesc(); return;
        }
        if (selectedCategoryId == null || cats.stream().noneMatch(c -> c.getId().equals(selectedCategoryId)))
            selectedCategoryId = cats.get(0).getId();
        List<GuideDefinition> guides = guidesForSelectedCategory();
        if (guides.isEmpty()) {
            selectedGuideId = null; selectedGuide = null; selectedPageIndex = 0;
            listTargetScroll = 0; listScroll = 0; resetDesc(); return;
        }
        if (selectedGuideId == null || guides.stream().noneMatch(g -> g.getId().equals(selectedGuideId)))
            selectedGuideId = guides.get(0).getId();
        selectedGuide = guides.stream().filter(g -> g.getId().equals(selectedGuideId)).findFirst().orElse(guides.get(0));
        selectedGuideId = selectedGuide.getId();
        selectedPageIndex = Math.max(0, Math.min(selectedPageIndex, selectedGuide.getPageCount() - 1));
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
        listTargetScroll = 0; listScroll = 0; tabScrollOffset = 0; tabTargetScroll = 0;
        resetDesc(); rebuildSelection(); refreshMediaBinding();
    }

    void selectGuide(ResourceLocation id) {
        if (Objects.equals(selectedGuideId, id)) return;
        selectedGuideId = id; selectedPageIndex = 0; resetDesc(); rebuildSelection(); refreshMediaBinding();
    }

    void selectPrevCategory() {
        List<GuideCategory> cats = visibleCategories();
        for (int i = 0; i < cats.size(); i++)
            if (cats.get(i).getId().equals(selectedCategoryId) && i > 0) { selectCategory(cats.get(i - 1).getId()); return; }
    }

    void selectNextCategory() {
        List<GuideCategory> cats = visibleCategories();
        for (int i = 0; i < cats.size(); i++)
            if (cats.get(i).getId().equals(selectedCategoryId) && i < cats.size() - 1) { selectCategory(cats.get(i + 1).getId()); return; }
    }

    void prevPage() { if (selectedGuide != null && selectedPageIndex > 0) { selectedPageIndex--; resetDesc(); refreshMediaBinding(); } }
    void nextPage() { if (selectedGuide != null && selectedPageIndex < selectedGuide.getPageCount() - 1) { selectedPageIndex++; resetDesc(); refreshMediaBinding(); } }

    void adjustListScroll(int delta) {
        List<GuideDefinition> guides = guidesForSelectedCategory();
        int visible = Math.max(1, (listRect()[3] - 12) / 24);
        int maxScroll = Math.max(0, guides.size() - visible);
        listTargetScroll = Math.max(0, Math.min(maxScroll, listTargetScroll + delta));
    }

    void adjustDescScroll(double delta) {
        int max = Math.max(0, contentPanel.descriptionLineCount() * GuideScreenLayout.textLineHeight() - contentPanel.descriptionRect()[3]);
        descTargetScroll = Math.max(0, Math.min(max, descTargetScroll + delta));
    }

    void resetDesc() { descScroll = 0.0; descTargetScroll = 0.0; }

    void refreshMediaBinding() {
        GuideMediaDefinition m = currentMedia();
        if (m != null && m.getType() == GuideMediaType.PONDER && m.getSceneId() != null)
            ponderPanel.bind(m.getSceneId(), getThemeColor(), m.isAutoplay());
        else ponderPanel.unbind();
    }

    int getThemeColor() { return selectedGuide != null ? selectedGuide.getCategory().getThemeColor() : 0x00D1FF; }
    ResourceLocation getSelectedCategoryId() { return selectedCategoryId; }
    ResourceLocation getSelectedGuideId() { return selectedGuideId; }
    float getSmoothListScroll() { return listScroll; }
    double getDescScroll() { return descScroll; }
    void setDescScroll(double v) { descScroll = v; }
    double getDescTargetScroll() { return descTargetScroll; }
    void setDescTargetScroll(double v) { descTargetScroll = v; }
    boolean hasAnyVisibleGuide() { return !visibleCategories().isEmpty(); }
    float getTabAnimX() { return tabAnimX; }
    float getTabAnimW() { return tabAnimW; }

    GuidePageView currentPageView() { return selectedGuide == null ? null : new GuidePageView(selectedGuide, selectedGuide.getPage(selectedPageIndex), selectedPageIndex); }
    GuideMediaDefinition currentMedia() { return selectedGuide == null ? null : selectedGuide.getPage(selectedPageIndex).getMedia(); }
    List<FormattedCharSequence> descriptionLines(int width) {
        GuidePageView view = currentPageView();
        return view == null ? List.of() : font.split(view.page().getDescriptionText().resolve(null, null), width);
    }
    EmbeddedPonderScenePanel ponderPanel() { return ponderPanel; }

    int[] tabsRect() {
        GuideScreenLayout.TerminalLayout l = cachedLayout;
        return new int[]{l.cx(), l.cy(), l.cw(), 30};
    }
    int[] listRect() {
        GuideScreenLayout.TerminalLayout l = cachedLayout;
        return new int[]{l.lx(), l.ly(), l.lw(), l.lh()};
    }
    int[] contentRect() {
        GuideScreenLayout.TerminalLayout l = cachedLayout;
        return new int[]{l.cx(), l.cy() + 30, l.cw(), l.ch() - 30};
    }

    record GuidePageView(GuideDefinition guide, org.arcadia.arc_quest.guide.api.GuidePageDefinition page, int pageIndex) {}
}

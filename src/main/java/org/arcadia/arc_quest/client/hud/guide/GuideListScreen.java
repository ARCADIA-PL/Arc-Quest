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
    static final int TEXT = 0xF2F7FF, SUB = 0x9FB4C7, MUTED = 0x7E90A0, DIS = 0x5E6C79, BG = 0xD0101016, SEC = 0x50162028;
    private final EmbeddedPonderScenePanel ponderPanel = new EmbeddedPonderScenePanel();
    private final GuideCategoryTabs tabs = new GuideCategoryTabs(this);
    private final GuideListPanel listPanel = new GuideListPanel(this);
    private final GuideContentPanel contentPanel = new GuideContentPanel(this);
    private ResourceLocation selectedCategoryId, selectedGuideId;
    private int selectedPageIndex, listScroll;
    private GuideDefinition selectedGuide;
    private float openAnim = 0f;
    private double descScroll = 0d, descTargetScroll = 0d;

    public GuideListScreen() { super(Component.translatable("gui.arc_quest.guide_list.title")); }
    public static void open() { Minecraft mc = Minecraft.getInstance(); if (mc != null) mc.setScreen(new GuideListScreen()); }
    @Override protected void init() { super.init(); openAnim = 0f; rebuildSelection(); refreshMediaBinding(); }
    @Override public void tick() { super.tick(); openAnim = HudAnimUtil.advanceByDuration(openAnim, .24f, 1f / 20f); descScroll += (descTargetScroll - descScroll) * .35d; ponderPanel.tick(); }
    @Override public void removed() { ponderPanel.onScreenClosed(); super.removed(); }
    @Override public boolean isPauseScreen() { return true; }
    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) { if (keyCode == 256) return super.keyPressed(keyCode, scanCode, modifiers); if (keyCode == 263) { selectPrevGuide(); return true; } if (keyCode == 262) { selectNextGuide(); return true; } if (keyCode == 265) { selectPrevCategory(); return true; } if (keyCode == 264) { selectNextCategory(); return true; } if (keyCode == 81) { prevPage(); return true; } if (keyCode == 69) { nextPage(); return true; } return super.keyPressed(keyCode, scanCode, modifiers); }
    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) { if (button != 0) return super.mouseClicked(mouseX, mouseY, button); if (tabs.mouseClicked(mouseX, mouseY)) return true; if (listPanel.mouseClicked(mouseX, mouseY)) return true; if (contentPanel.mouseClicked(mouseX, mouseY)) return true; return super.mouseClicked(mouseX, mouseY, button); }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double delta) { if (listPanel.mouseScrolled(mouseX, mouseY, delta)) return true; if (contentPanel.mouseScrolled(mouseX, mouseY, delta)) return true; return super.mouseScrolled(mouseX, mouseY, delta); }
    @Override public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) { renderBackground(g); super.render(g, mouseX, mouseY, partialTick); float reveal = HudAnimUtil.easeOutCubic(openAnim); int alpha = (int) (255 * reveal); g.fill(0, 0, width, height, HudAnimUtil.withAlpha(0x000000, (int) (170 * reveal))); g.drawCenteredString(font, title, width / 2, 14, HudAnimUtil.withAlpha(0xFFFFFF, alpha)); tabs.render(g, mouseX, mouseY, alpha); listPanel.render(g, mouseX, mouseY, alpha); contentPanel.render(g, mouseX, mouseY, partialTick, alpha); }

    void rebuildSelection() { List<GuideCategory> cats = visibleCategories(); if (cats.isEmpty()) { selectedCategoryId = null; selectedGuideId = null; selectedGuide = null; selectedPageIndex = 0; listScroll = 0; resetDesc(); return; } if (selectedCategoryId == null || cats.stream().noneMatch(c -> c.getId().equals(selectedCategoryId))) selectedCategoryId = cats.get(0).getId(); List<GuideDefinition> guides = guidesForSelectedCategory(); if (guides.isEmpty()) { selectedGuideId = null; selectedGuide = null; selectedPageIndex = 0; listScroll = 0; resetDesc(); return; } if (selectedGuideId == null || guides.stream().noneMatch(g -> g.getId().equals(selectedGuideId))) selectedGuideId = guides.get(0).getId(); selectedGuide = guides.stream().filter(g -> g.getId().equals(selectedGuideId)).findFirst().orElse(guides.get(0)); selectedGuideId = selectedGuide.getId(); selectedPageIndex = Math.max(0, Math.min(selectedPageIndex, selectedGuide.getPageCount() - 1)); }
    List<GuideCategory> visibleCategories() { List<GuideCategory> out = new ArrayList<>(); for (GuideCategory cat : GuideRegistry.getAllCategories()) if (GuideRegistry.getAll().stream().anyMatch(g -> isVisibleGuide(g) && g.getCategory().getId().equals(cat.getId()))) out.add(cat); out.sort(Comparator.comparingInt(GuideCategory::getSortOrder).thenComparing(c -> c.getId().toString())); return out; }
    List<GuideDefinition> guidesForSelectedCategory() { List<GuideDefinition> out = new ArrayList<>(); if (selectedCategoryId == null) return out; for (GuideDefinition guide : GuideRegistry.getAll()) if (isVisibleGuide(guide) && guide.getCategory().getId().equals(selectedCategoryId)) out.add(guide); out.sort(Comparator.comparingInt(GuideDefinition::getSortOrder).thenComparing(g -> g.getId().toString())); return out; }
    boolean isVisibleGuide(GuideDefinition guide) { return guide != null && !guide.isHidden() && ClientGuideCache.INSTANCE.isUnlocked(guide.getId()) && guide.getCategory() != null; }
    void selectCategory(ResourceLocation id) { if (Objects.equals(selectedCategoryId, id)) return; selectedCategoryId = id; selectedGuideId = null; selectedPageIndex = 0; listScroll = 0; resetDesc(); rebuildSelection(); refreshMediaBinding(); }
    void selectGuide(ResourceLocation id) { if (Objects.equals(selectedGuideId, id)) return; selectedGuideId = id; selectedPageIndex = 0; resetDesc(); rebuildSelection(); refreshMediaBinding(); }
    void selectPrevCategory() { List<GuideCategory> cats = visibleCategories(); for (int i = 0; i < cats.size(); i++) if (cats.get(i).getId().equals(selectedCategoryId) && i > 0) { selectCategory(cats.get(i - 1).getId()); return; } }
    void selectNextCategory() { List<GuideCategory> cats = visibleCategories(); for (int i = 0; i < cats.size(); i++) if (cats.get(i).getId().equals(selectedCategoryId) && i < cats.size() - 1) { selectCategory(cats.get(i + 1).getId()); return; } }
    void selectPrevGuide() { List<GuideDefinition> guides = guidesForSelectedCategory(); for (int i = 0; i < guides.size(); i++) if (guides.get(i).getId().equals(selectedGuideId) && i > 0) { selectGuide(guides.get(i - 1).getId()); return; } }
    void selectNextGuide() { List<GuideDefinition> guides = guidesForSelectedCategory(); for (int i = 0; i < guides.size(); i++) if (guides.get(i).getId().equals(selectedGuideId) && i < guides.size() - 1) { selectGuide(guides.get(i + 1).getId()); return; } }
    void prevPage() { if (selectedGuide != null && selectedPageIndex > 0) { selectedPageIndex--; resetDesc(); refreshMediaBinding(); } }
    void nextPage() { if (selectedGuide != null && selectedPageIndex < selectedGuide.getPageCount() - 1) { selectedPageIndex++; resetDesc(); refreshMediaBinding(); } }
    void adjustListScroll(int delta) { listScroll = Math.max(0, listScroll + delta); }
    void adjustDescScroll(double delta) { int max = Math.max(0, contentPanel.descriptionLineCount() * GuideScreenLayout.TEXT_LINE_H - contentPanel.descriptionRect()[3]); descTargetScroll = clamp(descTargetScroll + delta, 0d, max); }
    void resetDesc() { descScroll = 0d; descTargetScroll = 0d; }
    void refreshMediaBinding() { GuideMediaDefinition m = currentMedia(); if (m != null && m.getType() == GuideMediaType.PONDER && m.getSceneId() != null) ponderPanel.bind(m.getSceneId(), getThemeColor(), m.isAutoplay()); else ponderPanel.unbind(); }
    GuideDefinition getSelectedGuide() { return selectedGuide; }
    GuideMediaDefinition currentMedia() { return selectedGuide == null ? null : selectedGuide.getPage(selectedPageIndex).getMedia(); }
    GuidePageView currentPageView() { return selectedGuide == null ? null : new GuidePageView(selectedGuide, selectedGuide.getPage(selectedPageIndex), selectedPageIndex); }
    List<FormattedCharSequence> descriptionLines(int width) { GuidePageView view = currentPageView(); return view == null ? List.of() : font.split(view.page().getDescriptionText().resolve(null, null), width); }
    EmbeddedPonderScenePanel ponderPanel() { return ponderPanel; }
    int getThemeColor() { return selectedGuide != null ? selectedGuide.getCategory().getThemeColor() : 0x4FC3F7; }
    ResourceLocation getSelectedCategoryId() { return selectedCategoryId; }
    ResourceLocation getSelectedGuideId() { return selectedGuideId; }
    int getListScroll() { return listScroll; }
    double getDescScroll() { return descScroll; }
    void setDescScroll(double v) { descScroll = v; }
    void setDescTargetScroll(double v) { descTargetScroll = v; }
    double getDescTargetScroll() { return descTargetScroll; }
    boolean hasAnyVisibleGuide() { return !visibleCategories().isEmpty(); }
    int[] tabsRect() { return new int[]{16, 34, width - 32, 24}; }
    int[] listRect() { return new int[]{16, 66, 220, height - 84}; }
    int[] contentRect() { return new int[]{248, 66, width - 264, height - 84}; }
    private int clampPage(int p) { return selectedGuide == null ? 0 : Math.max(0, Math.min(p, selectedGuide.getPageCount() - 1)); }
    private static double clamp(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }
    record GuidePageView(GuideDefinition guide, org.arcadia.arc_quest.guide.api.GuidePageDefinition page, int pageIndex) {}
}

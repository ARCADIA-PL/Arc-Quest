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
import org.arcadia.arc_quest.client.hud.component.HudCursorManager;
import org.arcadia.arc_quest.client.hud.component.HudRect;
import org.arcadia.arc_quest.client.compat.jecharacters.JustEnoughCharactersCompat;
import org.arcadia.arc_quest.client.hud.ponder.EmbeddedPonderScenePanel;
import org.arcadia.arc_quest.client.hud.StyledTextUtil;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;
import org.arcadia.arc_quest.client.config.ArcQuestTextSettingsButton;
import org.arcadia.arc_quest.client.config.ArcQuestTextTarget;
import org.arcadia.arc_quest.config.ArcQuestTextConfig;
import org.arcadia.arc_quest.guide.api.GuideCategory;
import org.arcadia.arc_quest.guide.api.GuideDefinition;
import org.arcadia.arc_quest.guide.api.GuideMediaDefinition;
import org.arcadia.arc_quest.guide.api.GuideMediaType;
import org.arcadia.arc_quest.guide.network.ClientGuideCache;
import org.arcadia.arc_quest.guide.network.C2SUpdateGuideProgressPacket;
import org.arcadia.arc_quest.guide.registry.GuideRegistry;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class GuideListScreen extends Screen {
    private final ArcQuestTextSettingsButton textSettingsButton =
            new ArcQuestTextSettingsButton(ArcQuestTextTarget.GUIDE);
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
    private String guideSearchQuery = "";
    private int searchCursor;
    private boolean searchFocused;
    @Nullable
    private Screen parentScreen;
    private boolean triggeredParentClose;
    private boolean triggeredParentReopen;

    public GuideListScreen() {
        this(null, null);
    }

    private GuideListScreen(@Nullable Screen parentScreen, @Nullable ResourceLocation initialCategoryId) {
        super(Component.translatable("gui.arc_quest.guide_list.title"));
        this.parentScreen = parentScreen;
        selectedCategoryId = initialCategoryId;
    }

    public float getUiScale() {
        if (minecraft == null) return 1.0f;
        double guiScale = minecraft.getWindow().getGuiScale();
        if (guiScale == 0) guiScale = 1.0;
        float scale = (float) (3.0 / guiScale) * (float) ArcQuestTextConfig.guideScale();
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

    public static void openFromJournal(QuestJournalScreen journalScreen, @Nullable ResourceLocation categoryId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) mc.setScreen(new GuideListScreen(journalScreen, categoryId));
    }

    @Override
    protected void init() {
        super.init();
        transitionAlpha = 0f;
        isClosing = false;
        triggeredParentClose = false;
        triggeredParentReopen = false;
        lastRenderTime = 0;
        searchCursor = guideSearchQuery.length();
        searchFocused = false;
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
        HudCursorManager.reset();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchFocused && !isClosing) {
            if (keyCode == 256) {
                searchFocused = false;
                return true;
            }
            if (keyCode == 259) {
                deleteSearchCharacter(-1);
                return true;
            }
            if (keyCode == 261) {
                deleteSearchCharacter(1);
                return true;
            }
            if (keyCode == 263) {
                searchCursor = previousSearchCursor();
                return true;
            }
            if (keyCode == 262) {
                searchCursor = nextSearchCursor();
                return true;
            }
            if (keyCode == 268) {
                searchCursor = 0;
                return true;
            }
            if (keyCode == 269) {
                searchCursor = guideSearchQuery.length();
                return true;
            }
            // Keep focus while typing so the guide-list shortcut does not close the screen.
            return true;
        }
        if (keyCode == 256 || minecraft.options.keyInventory.matches(keyCode, scanCode)
                || ClientEventHandler.KEY_OPEN_GUIDE_LIST.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        if (contentPanel != null && contentPanel.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!searchFocused || isClosing || Character.isISOControl(codePoint)) return false;
        if (guideSearchQuery.length() >= 80) return true;
        guideSearchQuery = guideSearchQuery.substring(0, searchCursor)
                + codePoint + guideSearchQuery.substring(searchCursor);
        searchCursor += Character.charCount(codePoint);
        refreshSearchResults();
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        float uiScale = getUiScale();
        double smx = mx / uiScale, smy = my / uiScale;
        int sw = getScaledWidth(), sh = getScaledHeight();
        if (!isClosing && button == 0 && searchBounds(sw).contains(smx, smy)) {
            searchFocused = true;
            searchCursor = guideSearchQuery.length();
            return true;
        }
        if (button == 0) searchFocused = false;
        int textSettingsX = GuideConstants.LIST_MARGIN;
        if (!isClosing && textSettingsButton.mouseClickedAt(
                this, smx, smy, button, textSettingsX)) return true;
        if (isClosing || button != 0) return super.mouseClicked(mx, my, button);

        float slideOffset = (1f - getEaseProgress()) * 200f;
        int listX = GuideConstants.LIST_MARGIN - (int) slideOffset;
        int listY = GuideConstants.TAB_TOP + GuideConstants.TAB_HEIGHT + 6, listH = sh - 20 - listY;
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

        int listY = GuideConstants.TAB_TOP + GuideConstants.TAB_HEIGHT + 6, listH = sh - 20 - listY;
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
    public boolean mouseScrolled(double mx, double my, double delta) {
        float uiScale = getUiScale();
        double smx = mx / uiScale, smy = my / uiScale;
        int sw = getScaledWidth(), sh = getScaledHeight();
        if (isClosing) return false;

        float slideOffset = (1f - getEaseProgress()) * 200f;
        int listX = GuideConstants.LIST_MARGIN - (int) slideOffset;
        int listY = GuideConstants.TAB_TOP + GuideConstants.TAB_HEIGHT + 6, listH = sh - 20 - listY;
        int detailX = GuideConstants.LIST_MARGIN + GuideConstants.LIST_WIDTH + GuideConstants.DETAIL_MARGIN + (int) slideOffset;
        int detailW = sw - detailX - GuideConstants.DETAIL_MARGIN;

        if (tabs.mouseScrolled(smx, smy, delta, listX, detailW + GuideConstants.LIST_WIDTH + GuideConstants.DETAIL_MARGIN)) return true;
        if (listPanel.mouseScrolled(smx, smy, delta, listX, listY, GuideConstants.LIST_WIDTH, listH)) return true;
        if (contentPanel.mouseScrolled(smx, smy, delta, detailX, listY, detailW, listH)) return true;

        return super.mouseScrolled(mx, my, delta);
    }

    private float getEaseProgress() {
        return isClosing ? HudAnimUtil.easeInCubic(transitionAlpha) : HudAnimUtil.easeOutCubic(transitionAlpha);
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        HudCursorManager.beginFrame();
        float uiScale = getUiScale();
        int smx = (int) (mouseX / uiScale), smy = (int) (mouseY / uiScale);
        int sw = getScaledWidth(), sh = getScaledHeight();

        long now = Util.getMillis();
        if (lastRenderTime == 0) lastRenderTime = now;
        dt = (now - lastRenderTime) / 1000f;
        lastRenderTime = now;
        if (dt > 0.1f) dt = 0.1f;

        transitionAlpha = HudAnimUtil.lerp(transitionAlpha, isClosing ? 0f : 1f, isClosing ? 0.2f : 0.12f, dt);
        renderParentScreen(g, partialTick);
        if (isClosing && transitionAlpha <= 0.01f) {
            HudCursorManager.apply();
            if (minecraft != null) {
                Screen restoreScreen = parentScreen;
                parentScreen = null;
                minecraft.setScreen(restoreScreen);
            }
            HudCursorManager.apply();
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
            int titleY = GuideConstants.TITLE_TOP;
            g.pose().translate(sw / 2f, titleY, 0);
            float titleScale = 0.95f + 0.05f * easeProgress;
            g.pose().scale(titleScale, titleScale, 1f);
            g.pose().translate(-sw / 2f, -titleY, 0);
            g.drawCenteredString(font, title, sw / 2, titleY,
                    HudAnimUtil.withAlpha(0xFFFFFF, safeAlpha));
            g.pose().popPose();
        }

        renderSearchBox(g, sw, smx, smy, safeAlpha);

        int listX = GuideConstants.LIST_MARGIN - (int) slideOffset;
        int listY = GuideConstants.TAB_TOP + GuideConstants.TAB_HEIGHT + 6;
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
        if (!guideSearchQuery.isBlank() && guidesForSelectedCategory().isEmpty() && safeAlpha > 8) {
            g.drawCenteredString(font, Component.translatable("gui.arc_quest.guide_list.search_empty"),
                    listX + GuideConstants.LIST_WIDTH / 2, listY + listH / 2,
                    HudAnimUtil.withAlpha(0x89939E, (int) (220 * effectiveAlpha)));
        }

        // 渲染内容面板外框 (移除主题色，替换为极简灰 0x333333)
        HudAnimUtil.drawFrame(g, detailX, listY, detailW, listH,
                HudAnimUtil.withAlpha(0x000000, (int) (0x44 * effectiveAlpha)),
                HudAnimUtil.withAlpha(0x333333, safeAlpha));
        contentPanel.render(g, detailX, listY, detailW, listH, smx, smy, currentThemeColor, dt);

        if (!isClosing) {
            int textSettingsX = GuideConstants.LIST_MARGIN;
            textSettingsButton.renderAt(g, font, textSettingsX,
                    smx, smy, currentThemeColor);
        }
        g.pose().popPose();
        HudCursorManager.apply();
    }

    void rebuildSelection() {
        rebuildSelection(true);
    }

    private void rebuildSelection(boolean scrollListToSelection) {
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
        if (scrollListToSelection) listPanel.scrollToSelected();
    }

    List<GuideCategory> visibleCategories() {
        return visibleCategoriesSnapshot(guideSearchQuery);
    }

    public static List<GuideCategory> visibleCategoriesSnapshot() {
        return visibleCategoriesSnapshot("");
    }

    private static List<GuideCategory> visibleCategoriesSnapshot(String searchQuery) {
        List<GuideCategory> out = new ArrayList<>();
        for (GuideCategory cat : GuideRegistry.getAllCategories())
            if (GuideRegistry.getAll().stream().anyMatch(g -> isVisibleGuideSnapshot(g)
                    && g.getCategory().getId().equals(cat.getId())
                    && matchesSearch(g, searchQuery)))
                out.add(cat);
        out.sort(Comparator.comparingInt(GuideCategory::getSortOrder).thenComparing(c -> c.getId().toString()));
        return out;
    }

    List<GuideDefinition> guidesForSelectedCategory() {
        List<GuideDefinition> out = new ArrayList<>();
        if (selectedCategoryId == null) return out;
        for (GuideDefinition guide : GuideRegistry.getAll())
            if (isVisibleGuide(guide) && guide.getCategory().getId().equals(selectedCategoryId)
                    && matchesSearch(guide, guideSearchQuery)) out.add(guide);
        out.sort(Comparator.comparingInt(GuideDefinition::getSortOrder).thenComparing(g -> g.getId().toString()));
        return out;
    }

    boolean isVisibleGuide(GuideDefinition guide) {
        return isVisibleGuideSnapshot(guide);
    }

    private static boolean matchesSearch(GuideDefinition guide, String query) {
        if (query == null || query.isBlank()) return true;
        if (fuzzyMatches(guide.getTitle().getString(), query)
                || fuzzyMatches(guide.getSummary().getString(), query)
                || fuzzyMatches(guide.getId().toString(), query)
                || fuzzyMatches(guide.getCategory().getDisplayName().getString(), query)) {
            return true;
        }
        return guide.getPages().stream().anyMatch(page -> fuzzyMatches(
                page.getDescriptionText().resolve(null, null).getString(), query));
    }

    private static boolean fuzzyMatches(String text, String query) {
        if (JustEnoughCharactersCompat.matches(text, query)) return true;
        String haystack = text.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        String needle = query.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        if (needle.isEmpty()) return true;
        if (haystack.contains(needle)) return true;

        int[] haystackCodePoints = haystack.codePoints().toArray();
        int[] needleCodePoints = needle.codePoints().toArray();
        int queryIndex = 0;
        for (int codePoint : haystackCodePoints) {
            if (queryIndex < needleCodePoints.length && codePoint == needleCodePoints[queryIndex]) {
                queryIndex++;
            }
        }
        return queryIndex == needleCodePoints.length;
    }

    private HudRect searchBounds(int scaledWidth) {
        int searchWidth = Math.min(GuideConstants.SEARCH_BOX_WIDTH,
                Math.max(120, scaledWidth - GuideConstants.LIST_MARGIN * 2));
        return new HudRect((scaledWidth - searchWidth) / 2,
                GuideConstants.SEARCH_BOX_TOP, searchWidth, GuideConstants.SEARCH_BOX_HEIGHT);
    }

    private void renderSearchBox(GuiGraphics graphics, int scaledWidth,
                                 int mouseX, int mouseY, int safeAlpha) {
        HudRect bounds = searchBounds(scaledWidth);
        boolean hovered = bounds.contains(mouseX, mouseY);
        HudCursorManager.requestPointer(hovered);
        int borderColor = searchFocused || hovered ? currentThemeColor : 0x65707C;
        int borderAlpha = (int) ((searchFocused || hovered ? 210 : 145) * effectiveAlpha);
        graphics.fill(bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + bounds.height(),
                HudAnimUtil.withAlpha(0x080C10, (int) (190 * effectiveAlpha)));
        HudAnimUtil.drawFrame(graphics, bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                HudAnimUtil.withAlpha(0x000000, (int) (35 * effectiveAlpha)),
                HudAnimUtil.withAlpha(borderColor, borderAlpha));

        boolean empty = guideSearchQuery.isEmpty();
        Component text = empty
                ? Component.translatable("gui.arc_quest.guide_list.search")
                : Component.literal(guideSearchQuery);
        int textColor = empty
                ? HudAnimUtil.withAlpha(0x89939E, (int) (180 * effectiveAlpha))
                : HudAnimUtil.withAlpha(0xFFFFFF, safeAlpha);
        enableScissor(graphics, bounds.x() + 5, bounds.y() + 2,
                bounds.x() + bounds.width() - 5, bounds.y() + bounds.height() - 2);
        graphics.drawString(font, StyledTextUtil.fitSingleLine(font, text, bounds.width() - 12),
                bounds.x() + 6, bounds.y() + 6, textColor, false);
        if (searchFocused && (Util.getMillis() / 500L) % 2L == 0L) {
            int cursorX = bounds.x() + 6 + Math.min(bounds.width() - 12,
                    font.width(Component.literal(guideSearchQuery.substring(0, searchCursor))));
            graphics.fill(cursorX, bounds.y() + 4, cursorX + 1, bounds.y() + bounds.height() - 4,
                    HudAnimUtil.withAlpha(currentThemeColor, safeAlpha));
        }
        graphics.disableScissor();
    }

    private void deleteSearchCharacter(int direction) {
        if (direction < 0 && searchCursor > 0) {
            int previous = searchQueryOffset(-1);
            guideSearchQuery = guideSearchQuery.substring(0, previous)
                    + guideSearchQuery.substring(searchCursor);
            searchCursor = previous;
            refreshSearchResults();
        } else if (direction > 0 && searchCursor < guideSearchQuery.length()) {
            int next = searchQueryOffset(1);
            guideSearchQuery = guideSearchQuery.substring(0, searchCursor)
                    + guideSearchQuery.substring(next);
            refreshSearchResults();
        }
    }

    private void refreshSearchResults() {
        ResourceLocation previousGuideId = selectedGuideId;
        rebuildSelection(false);
        if (!Objects.equals(previousGuideId, selectedGuideId)) {
            contentPanel.resetState();
            refreshMediaBinding();
        }
    }

    private int searchQueryOffset(int direction) {
        return guideSearchQuery.offsetByCodePoints(searchCursor, direction);
    }

    private int previousSearchCursor() {
        return searchCursor > 0 ? searchQueryOffset(-1) : 0;
    }

    private int nextSearchCursor() {
        return searchCursor < guideSearchQuery.length() ? searchQueryOffset(1) : guideSearchQuery.length();
    }

    boolean shouldRenderOpaqueItems() {
        return effectiveAlpha >= 0.38f;
    }

    private static boolean isVisibleGuideSnapshot(GuideDefinition guide) {
        return guide != null && !guide.isHidden()
                && ClientGuideCache.INSTANCE.isUnlocked(guide.getId())
                && guide.getCategory() != null;
    }

    private void renderParentScreen(GuiGraphics graphics, float partialTick) {
        if (parentScreen == null) return;
        if (parentScreen instanceof QuestJournalScreen journalScreen) {
            if (!triggeredParentClose && !isClosing) {
                journalScreen.onClose();
                triggeredParentClose = true;
            }
            if (isClosing && !triggeredParentReopen) {
                journalScreen.triggerEntranceAnimation();
                triggeredParentReopen = true;
            }
        }
        parentScreen.render(graphics, -999, -999, partialTick);
    }

    boolean hasUnreadGuide(GuideCategory category) {
        if (category == null) return false;
        for (GuideDefinition guide : GuideRegistry.getAll()) {
            if (isVisibleGuide(guide)
                    && guide.getCategory().getId().equals(category.getId())
                    && !ClientGuideCache.INSTANCE.isSeen(guide.getId())) {
                return true;
            }
        }
        return false;
    }

    void selectCategory(ResourceLocation id) {
        if (Objects.equals(selectedCategoryId, id)) return;
        selectedCategoryId = id; selectedGuideId = null; selectedPageIndex = 0;
        contentPanel.resetState(); listPanel.resetState(); rebuildSelection(); refreshMediaBinding(); playClick();
    }

    void selectGuide(ResourceLocation id) {
        if (Objects.equals(selectedGuideId, id)) {
            completeSelectedGuideIfFinished();
            return;
        }
        selectedGuideId = id;
        selectedPageIndex = ClientGuideCache.INSTANCE.getProgress(id);
        contentPanel.resetState(); rebuildSelection(false); refreshMediaBinding(); playClick();
        completeSelectedGuideIfFinished();
    }

    public void prevPage() {
        if (selectedGuide != null && selectedPageIndex > 0) {
            selectedPageIndex--; saveProgress(); contentPanel.resetState(); refreshMediaBinding(); playClick();
        }
    }
    public void nextPage() {
        if (selectedGuide != null && selectedPageIndex < selectedGuide.getPageCount() - 1) {
            selectedPageIndex++; saveProgress(); contentPanel.resetState(); refreshMediaBinding(); playClick();
            completeSelectedGuideIfFinished();
        }
    }

    private void completeSelectedGuideIfFinished() {
        GuideCompletionClient.completeIfFinalPage(selectedGuide, selectedGuideId, selectedPageIndex);
    }

    private void saveProgress() {
        if (selectedGuideId == null) return;
        ClientGuideCache.INSTANCE.applyLocalProgress(selectedGuideId, selectedPageIndex);
        ArcQuestNetwork.sendGuideProgress(new C2SUpdateGuideProgressPacket(selectedGuideId, selectedPageIndex));
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

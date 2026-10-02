package org.arcadia.arc_quest.client.hud.quest.journal;

import net.minecraft.Util;
import org.arcadia.arc_quest.client.hud.quest.icon.ObjectiveIconSession;
import org.arcadia.arc_quest.client.hud.quest.icon.portrait.EntityPortraits;
import org.arcadia.arc_quest.client.hud.quest.journal.component.JournalTooltipRequest;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.arcadia.arc_quest.client.events.ClientEventHandler;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.QuestHudOverlay;
import org.arcadia.arc_quest.client.hud.component.HudCursorManager;
import org.arcadia.arc_quest.client.hud.guide.GuideListScreen;
import org.arcadia.arc_quest.client.config.ArcQuestTextSettingsButton;
import org.arcadia.arc_quest.client.config.ArcQuestTextTarget;
import org.arcadia.arc_quest.client.config.ArcQuestModSettingsButton;
import org.arcadia.arc_quest.client.config.ArcQuestTrackerToggleButton;
import org.arcadia.arc_quest.client.hud.quest.history.CollectionHistoryPanel;
import org.arcadia.arc_quest.client.hud.quest.history.QuestHistoryPanel;
import org.arcadia.arc_quest.client.hud.quest.journal.detail.JournalDetailPanel;
import org.arcadia.arc_quest.client.hud.quest.journal.component.JournalScreenLayout;
import org.arcadia.arc_quest.client.hud.quest.journal.component.JournalTooltipRenderer;
import org.arcadia.arc_quest.client.hud.quest.journal.history.QuestChangeHistoryPanel;
import org.arcadia.arc_quest.client.hud.quest.journal.history.QuestChangeHistoryStore;
import org.arcadia.arc_quest.client.hud.quest.journal.history.QuestChangeNotificationManager;
import org.arcadia.arc_quest.client.hud.quest.offer.QuestOfferPanel;
import org.arcadia.arc_quest.client.hud.quest.ponder.QuestIntelPanel;
import org.arcadia.arc_quest.client.hud.quest.splash.QuestSplashRenderer;
import org.arcadia.arc_quest.client.hud.quest.story.QuestStoryPanel;
import org.arcadia.arc_quest.config.ArcQuestConfig;
import org.arcadia.arc_quest.config.ArcQuestTextConfig;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.api.QuestState;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.jetbrains.annotations.NotNull;
import org.arcadia.arc_quest.client.compat.jei.screen.JeiQueryReturn;
import org.arcadia.arc_quest.client.compat.jei.screen.JeiScreenIngredients;
import org.arcadia.arc_quest.client.compat.jei.screen.JeiScreenSuspension;
import org.arcadia.arc_quest.client.data.sync.ClientDatapackContentReceiver;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class QuestJournalScreen extends Screen implements JeiQueryReturn {
    private final JeiScreenSuspension jeiSuspension = new JeiScreenSuspension();
    private Object jeiQueryConnection;
    private long jeiQueryEpoch = -1;
    private final ArcQuestTextSettingsButton textSettingsButton =
            new ArcQuestTextSettingsButton(ArcQuestTextTarget.JOURNAL);
    private final ArcQuestModSettingsButton modSettingsButton =
            new ArcQuestModSettingsButton();
    private final ArcQuestTrackerToggleButton trackerToggleButton = new ArcQuestTrackerToggleButton();

    private static final float TIP_HOVER_DELAY = 0.05f;
    private final JournalTabPanel tabPanel;
    private final JournalListPanel listPanel;
    private final JournalDetailPanel detailPanel;
    private final QuestChangeHistoryPanel changeHistoryPanel;
    private final List<JournalTypes.QuestListEntry> currentEntries = new ArrayList<>();

    private JournalTypes.Tab currentTab = JournalTypes.Tab.ACTIVE;
    private boolean showingChangeLog = false;
    private int selectedIndex = -1;

    private float transitionAlpha = 0f;
    private boolean isClosing = false;
    private float suspendAlpha = 1.0f;
    private float effectiveAlpha = 0f;
    private float dt = 0f;
    private long lastRenderTime = 0;
    private int currentThemeColor = 0xFFFFFF;
    private boolean collectionDetailWasOpen;
    private boolean journalSessionOpen;
    private boolean renderingJournalBackground;
    private long selectionContentEpoch = Long.MIN_VALUE;

    private final ObjectiveIconSession objectiveIcons = new ObjectiveIconSession();
    private JournalTooltipRequest hoveredObjectiveTooltip;
    private JournalTooltipRequest activeObjectiveTooltip;
    private ItemStack hoveredRewardTooltip = null;
    private List<Component> hoveredCustomTooltip = null;
    private ItemStack activeTooltipStack = null;
    private List<Component> activeCustomTooltip = null;
    private float tooltipHoverTimer = 0f;
    private float tooltipTipAlpha = 0f;
    private float animTipX = 0, animTipY = 0, animTipW = 0, animTipH = 0;

    public QuestJournalScreen() {
        super(Component.translatable("gui.arc_quest.journal.title"));
        tabPanel = new JournalTabPanel(this);
        listPanel = new JournalListPanel(this);
        detailPanel = new JournalDetailPanel(this);
        changeHistoryPanel = new QuestChangeHistoryPanel(this);
    }

    public boolean isShowingChangeLog() { return showingChangeLog && ArcQuestConfig.isQuestHistoryTabEnabled(); }

    public void setShowingChangeLog(boolean showing) {
        showingChangeLog = showing && ArcQuestConfig.isQuestHistoryTabEnabled();
        if (showing) {
            selectedIndex = -1;
            changeHistoryPanel.reset();
        }
    }

    public void triggerEntranceAnimation() {
        transitionAlpha = 0f;
        isClosing = false;
        suspendAlpha = 1.0f;
        lastRenderTime = 0;
        tooltipHoverTimer = 0f;
        tooltipTipAlpha = 0f;
        hoveredObjectiveTooltip = null;
        hoveredRewardTooltip = null;
        hoveredCustomTooltip = null;
        activeTooltipStack = null;
        animTipW = 0f;
    }

    public float getUiScale() {
        if (minecraft == null) return 1.0f;
        double guiScale = minecraft.getWindow().getGuiScale();
        if (guiScale == 0) guiScale = 1.0;
        float scale = (float) (2.5 / guiScale) * (float) ArcQuestTextConfig.journalScale();
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
        JeiScreenIngredients.enableScissor(this, g, (int) (x * s), (int) (y * s), (int) (x2 * s), (int) (y2 * s));
    }

    public void disableScissor(GuiGraphics graphics) {
        JeiScreenIngredients.disableScissor(this, graphics);
    }

    @Override
    protected void init() {
        super.init();
        if (jeiSuspension.resume()) {
            if (minecraft.getConnection() != jeiQueryConnection
                    || ClientDatapackContentReceiver.INSTANCE.appliedEpoch() != jeiQueryEpoch) clearTransientPanels();
            jeiQueryConnection = null;
            lastRenderTime = 0;
            rebuildEntries();
            return;
        }
        clearTransientPanels();
        QuestChangeHistoryStore.INSTANCE.ensureLoaded();
        lastRenderTime = 0;

        String trackedQuestId = QuestHudOverlay.INSTANCE.getTrackedQuestId();
        if (trackedQuestId != null && !trackedQuestId.isEmpty()) {
            if (ClientQuestCache.INSTANCE.isQuestCompleted(trackedQuestId)) {
                currentTab = JournalTypes.Tab.COMPLETED;
            } else if (ClientQuestCache.INSTANCE.isQuestFailed(trackedQuestId)) {
                currentTab = JournalTypes.Tab.FAILED;
            }
        }
        rebuildEntries();
        if (!journalSessionOpen) {
            detailPanel.collectionRenderer.resetBrowserOnOpen();
            journalSessionOpen = true;
        }
    }

    public void rebuildEntries() {
        rebuildEntries(true);
    }

    private void rebuildEntries(boolean preserveSelection) {
        JournalTypes.QuestListEntry previousEntry = selectedIndex >= 0 && selectedIndex < currentEntries.size()
                ? currentEntries.get(selectedIndex)
                : null;
        String fallbackQuestId = preserveSelection ? resolveTargetQuestForOpen() : null;

        currentEntries.clear();
        switch (currentTab) {
            case ACTIVE -> {
                for (Map.Entry<String, QuestRuntimeData> e : ClientQuestCache.INSTANCE.getAllActiveQuests().entrySet()) {
                    ResourceLocation questRl = ResourceLocation.tryParse(e.getKey());
                    QuestDefinition def = questRl != null ? QuestRegistry.get(questRl) : null;
                    currentEntries.add(new JournalTypes.QuestListEntry(e.getKey(), ClientQuestCache.INSTANCE.getQuestDisplayComponent(e.getKey()), QuestState.ACTIVE, def));
                }
            }
            case COMPLETED -> {
                for (String id : ClientQuestCache.INSTANCE.getCompletedQuests()) {
                    ResourceLocation questRl = ResourceLocation.tryParse(id);
                    QuestDefinition def = questRl != null ? QuestRegistry.get(questRl) : null;
                    currentEntries.add(new JournalTypes.QuestListEntry(id, ClientQuestCache.INSTANCE.getQuestDisplayComponent(id), QuestState.COMPLETED, def));
                }
            }
            case FAILED -> {
                for (String id : ClientQuestCache.INSTANCE.getFailedQuests()) {
                    ResourceLocation questRl = ResourceLocation.tryParse(id);
                    QuestDefinition def = questRl != null ? QuestRegistry.get(questRl) : null;
                    currentEntries.add(new JournalTypes.QuestListEntry(id, ClientQuestCache.INSTANCE.getQuestDisplayComponent(id), QuestState.FAILED, def));
                }
            }
        }
        JournalQuestOrder.sortByDefinition(currentEntries);

        long contentEpoch = ClientDatapackContentReceiver.INSTANCE.appliedEpoch();
        JournalSelectionResolver.Result selection = JournalSelectionResolver.resolve(
                currentEntries, previousEntry, fallbackQuestId, preserveSelection, contentEpoch == selectionContentEpoch);
        selectionContentEpoch = contentEpoch;
        selectedIndex = selection.selectedIndex();
        if (selection.contextChanged()) {
            listPanel.resetState();
            detailPanel.resetState();
        } else {
            listPanel.refreshEntries();
            if (previousEntry != null && selectedIndex >= 0
                    && previousEntry.def() != currentEntries.get(selectedIndex).def()) detailPanel.refreshDefinition();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (detailPanel.collectionRenderer.imageOpen()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || minecraft.options.keyInventory.matches(keyCode, scanCode))
                detailPanel.collectionRenderer.closeImage();
            return true;
        }
        if (QuestIntelPanel.isActive()) {
            if (keyCode == 256 || minecraft.options.keyInventory.matches(keyCode, scanCode)) { QuestIntelPanel.dismiss(); return true; }
            return true;
        }
        if (QuestOfferPanel.isActive()) { QuestOfferPanel.keyPressed(keyCode); return true; }
        if (CollectionHistoryPanel.isActive()) { CollectionHistoryPanel.keyPressed(keyCode); return true; }
        if (QuestHistoryPanel.isActive()) { QuestHistoryPanel.keyPressed(keyCode); return true; }
        if (QuestStoryPanel.isActive()) { QuestStoryPanel.keyPressed(keyCode); return true; }
        if (detailPanel.collectionRenderer.detailVisible()) {
            if (!isClosing && detailPanel.collectionRenderer.detailOpen())
                detailPanel.collectionRenderer.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        if (!canInteractWithJournalBackground()) return true;
        if (detailPanel.collectionRenderer.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (ClientEventHandler.KEY_OPEN_JOURNAL.matches(keyCode, scanCode)) {
            onClose(); return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB && canInteractWithObjectiveIcons()
                && objectiveIcons.focusNext((modifiers & GLFW.GLFW_MOD_SHIFT) != 0)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!canInteractWithJournalBackground()) return true;
        if (canInteractWithObjectiveIcons() && detailPanel.collectionRenderer.charTyped(codePoint, modifiers)) return true;
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void onClose() {
        QuestChangeHistoryStore.INSTANCE.flush();
        if (!isClosing) isClosing = true;
    }

    @Override
    public void removed() {
        objectiveIcons.suspend();
        QuestChangeHistoryStore.INSTANCE.flush();
        if (!jeiSuspension.removed()) {
            journalSessionOpen = false;
            clearTransientPanels();
            detailPanel.collectionRenderer.closeDetail();
        }
        HudCursorManager.reset();
        super.removed();
    }

    private void clearTransientPanels() {
        objectiveIcons.clear();
        activeObjectiveTooltip = null;
        hoveredObjectiveTooltip = null;
        QuestIntelPanel.clearClientSession();
        QuestOfferPanel.clearClientSession();
        CollectionHistoryPanel.clearClientSession();
        QuestHistoryPanel.clearClientSession();
        QuestStoryPanel.clearClientSession();
        detailPanel.collectionRenderer.closeImage();
    }

    public boolean canQueryJei() {
        return !isClosing && !detailPanel.collectionRenderer.imageOpen() && !detailPanel.parallelPhaseRenderer.isManipulatingCards()
                && (!detailPanel.collectionRenderer.detailVisible() || detailPanel.collectionRenderer.detailInteractive())
                && !QuestSplashRenderer.isActive() && !QuestIntelPanel.isActive()
                && !CollectionHistoryPanel.isActive() && !QuestStoryPanel.isActive()
                && (!QuestHistoryPanel.isActive() || QuestHistoryPanel.canQueryJei())
                && (!QuestOfferPanel.isActive() || QuestOfferPanel.canQueryJei());
    }
    public boolean canInteractWithJournalBackground() {
        return !isClosing && !detailPanel.collectionRenderer.detailVisible()
                && !detailPanel.collectionRenderer.imageOpen() && !QuestSplashRenderer.isActive()
                && !QuestIntelPanel.isActive() && !QuestOfferPanel.isActive()
                && !CollectionHistoryPanel.isActive() && !QuestHistoryPanel.isActive() && !QuestStoryPanel.isActive();
    }
    public boolean canQueryJeiByKeyboard() { return canQueryJei() && !detailPanel.collectionRenderer.searchFocused(); }
    @Override public void prepareJeiQuery() {
        detailPanel.collectionRenderer.blurSearch();
        objectiveIcons.suspend();
        QuestOfferPanel.suspendIconCycle();
        jeiQueryConnection = minecraft.getConnection();
        jeiQueryEpoch = ClientDatapackContentReceiver.INSTANCE.appliedEpoch();
        jeiSuspension.arm();
    }
    @Override public void cancelJeiQuery() { jeiSuspension.cancel(); jeiQueryConnection = null; }
    @Override public void abandonJeiQuery() { cancelJeiQuery(); clearTransientPanels(); }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        float uiScale = getUiScale();
        double smx = mx / uiScale, smy = my / uiScale;
        if (detailPanel.collectionRenderer.imageClick(smx, smy, button)) return true;
        int settingsX = modSettingsButton.defaultX();
        int textSettingsX = settingsX + modSettingsButton.width(font) + modSettingsButton.gap();
        int trackerX = textSettingsX + textSettingsButton.width(font) + modSettingsButton.gap();
        if (canInteractWithJournalBackground() && modSettingsButton.mouseClickedAt(
                this, smx, smy, button, settingsX)) return true;
        if (canInteractWithJournalBackground() && textSettingsButton.mouseClickedAt(
                this, smx, smy, button, textSettingsX)) return true;
        if (canInteractWithJournalBackground() && trackerToggleButton.mouseClickedAt(font, smx, smy, button, trackerX)) {
            playClick(); return true;
        }
        int sw = getScaledWidth(), sh = getScaledHeight();

        if (QuestIntelPanel.isActive()) { QuestIntelPanel.handleMouseClick(smx, smy, sw, sh); return true; }
        if (QuestOfferPanel.isActive()) { QuestOfferPanel.mouseClicked(smx, smy, button); return true; }
        if (CollectionHistoryPanel.isActive()) { CollectionHistoryPanel.mouseClicked(smx, smy, button); return true; }
        if (QuestHistoryPanel.isActive()) { QuestHistoryPanel.mouseClicked(smx, smy, button); return true; }
        if (QuestStoryPanel.isActive()) { QuestStoryPanel.mouseClicked(smx, smy, button); return true; }

        if (detailPanel.collectionRenderer.detailVisible()) {
            if (!isClosing) detailPanel.collectionRenderer.detailClick(smx, smy, button);
            return true;
        }

        if (!canInteractWithJournalBackground()) return true;
        if (button != 0) return super.mouseClicked(mx, my, button);

        JournalScreenLayout layout = JournalScreenLayout.calculate(sw, sh, getEaseProgress());

        if (tabPanel.mouseClicked(smx, smy, layout.listPanel().x(), layout.rightEdge())) return true;
        if (listPanel.mouseClicked(smx, smy, layout.listPanel().x(), layout.listPanel().y(),
                layout.listPanel().width(), layout.listPanel().height())) return true;

        if (isShowingChangeLog()) {
            if (changeHistoryPanel.mouseClicked(smx, smy, button, layout.detailPanel().x(),
                    layout.detailPanel().y(), layout.detailPanel().width(), layout.detailPanel().height())) return true;
        } else {
            if (detailPanel.mouseClicked(smx, smy, layout.detailPanel().x(), layout.detailPanel().y(),
                    layout.detailPanel().width(), layout.detailPanel().height())) return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dragX, double dragY) {
        if (detailPanel.collectionRenderer.imageOpen() || isClosing) return true;
        float uiScale = getUiScale();
        double smx = mx / uiScale, smy = my / uiScale;
        JournalScreenLayout layout = JournalScreenLayout.calculate(
                getScaledWidth(), getScaledHeight(), getEaseProgress());
        if (QuestIntelPanel.isActive()) { QuestIntelPanel.mouseDragged(smx, smy); return true; }
        if (QuestOfferPanel.isActive()) { QuestOfferPanel.mouseDragged(smx, smy); return true; }
        if (QuestStoryPanel.isActive()) { QuestStoryPanel.mouseDragged(smx, smy); return true; }
        if (CollectionHistoryPanel.isActive()) { CollectionHistoryPanel.mouseDragged(smx, smy); return true; }
        if (QuestHistoryPanel.isActive()) { QuestHistoryPanel.mouseDragged(smx, smy); return true; }

        if (detailPanel.collectionRenderer.detailVisible()) {
            detailPanel.collectionRenderer.detailDrag(smx, smy, button);
            return true;
        }
        if (!canInteractWithJournalBackground()) return true;
        if (detailPanel.collectionRenderer.mouseDraggedAbsolute(smx, smy, button)) return true;

        if (listPanel.mouseDragged(smx, smy, layout.listPanel().y(), layout.listPanel().height())) return true;
        if (!isShowingChangeLog()) {
            if (detailPanel.mouseDragged(smx, smy, layout.detailPanel().y(), layout.detailPanel().height())) return true;
        }
        return super.mouseDragged(mx, my, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (detailPanel.collectionRenderer.imageOpen()) return true;
        float uiScale = getUiScale();
        double smx = mx / uiScale, smy = my / uiScale;
        if (QuestIntelPanel.isActive()) { QuestIntelPanel.mouseReleased(button); return true; }
        if (QuestOfferPanel.isActive()) { QuestOfferPanel.mouseReleased(button); return true; }
        if (QuestStoryPanel.isActive()) { QuestStoryPanel.mouseReleased(button); return true; }
        if (CollectionHistoryPanel.isActive()) { CollectionHistoryPanel.mouseReleased(button); return true; }
        if (QuestHistoryPanel.isActive()) { QuestHistoryPanel.mouseReleased(button); return true; }
        detailPanel.collectionRenderer.mouseReleased(button);
        if (detailPanel.collectionRenderer.detailVisible()) {
            detailPanel.collectionRenderer.detailRelease(button);
            return true;
        }
        listPanel.mouseReleased(button);
        if (!isShowingChangeLog()) detailPanel.mouseReleased(button);
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double delta) {
        if (detailPanel.collectionRenderer.imageOpen()) return true;
        float uiScale = getUiScale();
        double smx = mx / uiScale, smy = my / uiScale;
        int sw = getScaledWidth(), sh = getScaledHeight();

        if (QuestIntelPanel.isActive() || QuestOfferPanel.isActive() || QuestStoryPanel.isActive()) return true;
        if (CollectionHistoryPanel.isActive()) return true;
        if (QuestHistoryPanel.isActive()) { QuestHistoryPanel.mouseScrolled(smx, smy, delta); return true; }
        if (isClosing) return true;

        if (detailPanel.collectionRenderer.detailVisible()) {
            detailPanel.collectionRenderer.detailScroll(smx, smy, delta);
            return true;
        }
        if (!canInteractWithJournalBackground()) return true;

        JournalScreenLayout layout = JournalScreenLayout.calculate(sw, sh, getEaseProgress());

        if (listPanel.mouseScrolled(smx, smy, delta, layout.listPanel().x(), layout.listPanel().y(),
                layout.listPanel().width(), layout.listPanel().height())) return true;

        if (isShowingChangeLog()) {
            if (changeHistoryPanel.mouseScrolled(smx, smy, delta, layout.detailPanel().x(),
                    layout.detailPanel().y(), layout.detailPanel().width(), layout.detailPanel().height())) return true;
        } else {
            if (detailPanel.mouseScrolled(smx, smy, delta, layout.detailPanel().x(),
                    layout.detailPanel().y(), layout.detailPanel().width(), layout.detailPanel().height())) return true;
        }
        return super.mouseScrolled(mx, my, scrollX, delta);
    }

    private float getEaseProgress() {
        return (isClosing ? HudAnimUtil.easeInCubic(transitionAlpha) : HudAnimUtil.easeOutCubic(transitionAlpha)) * HudAnimUtil.easeOutCubic(suspendAlpha);
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.flush();
        EntityPortraits.prepare();
        objectiveIcons.beginFrame();
        HudCursorManager.beginFrame();
        hoveredObjectiveTooltip = null;
        hoveredRewardTooltip = null;
        hoveredCustomTooltip = null;
        float uiScale = getUiScale();
        int smx = (int) (mouseX / uiScale), smy = (int) (mouseY / uiScale);
        int sw = getScaledWidth(), sh = getScaledHeight();

        long now = Util.getMillis();
        if (lastRenderTime == 0) lastRenderTime = now;
        float realDt = (now - lastRenderTime) / 1000f;
        lastRenderTime = now;
        if (realDt > 0.1f) realDt = 0.1f;

        boolean intelActive = QuestIntelPanel.isActive(), offerActive = QuestOfferPanel.isActive(), collectionHistoryActive = CollectionHistoryPanel.isActive(), historyActive = QuestHistoryPanel.isActive(), storyActive = QuestStoryPanel.isActive();

        if (QuestSplashRenderer.isActive()) {
            suspendAlpha = Math.max(0f, suspendAlpha - realDt * 6f);
            dt = 0f;
        } else {
            suspendAlpha = Math.min(1f, suspendAlpha + realDt * 4f);
            dt = (intelActive || storyActive) ? 0f : realDt;
        }

        transitionAlpha = HudAnimUtil.lerp(transitionAlpha, isClosing ? 0f : 1f, isClosing ? 0.2f : 0.12f, realDt);
        if (isClosing && transitionAlpha <= 0.01f) {
            HudCursorManager.apply();
            if (minecraft != null && minecraft.screen == this) minecraft.setScreen(null);
            return;
        }

        effectiveAlpha = transitionAlpha * suspendAlpha;
        // Advance before registering background input, so the final exit frame restores real hits immediately.
        detailPanel.collectionRenderer.advanceDetailTransition(dt);
        JeiScreenIngredients.begin(this, canQueryJei() && !QuestOfferPanel.isActive() && !QuestHistoryPanel.isActive());
        float easeProgress = getEaseProgress();
        int safeAlpha = (int) (255 * effectiveAlpha);

        g.pose().pushPose();
        g.pose().scale(uiScale, uiScale, 1f);

        int bgTint = HudAnimUtil.lerpColor(0x000000, currentThemeColor, 0.05f);
        g.fill(0, 0, sw, sh, HudAnimUtil.withAlpha(bgTint, (int) (180 * effectiveAlpha)));

        if (safeAlpha > 8) {
            g.pose().pushPose();
            g.pose().translate(sw / 2f, 14, 0);
            float titleScale = 0.95f + 0.05f * easeProgress;
            g.pose().scale(titleScale, titleScale, 1f);
            g.pose().translate(-sw / 2f, -14, 0);
            g.drawCenteredString(font, title, sw / 2, 14,
                    HudAnimUtil.withAlpha(0xFFFFFF, safeAlpha));
            g.pose().popPose();
        }

        int theme = getThemeColor();
        JournalScreenLayout layout = JournalScreenLayout.calculate(sw, sh, easeProgress);
        boolean backgroundInteractive = canInteractWithJournalBackground();
        int backgroundMouseX = backgroundInteractive ? smx : -1000;
        int backgroundMouseY = backgroundInteractive ? smy : -1000;
        renderingJournalBackground = true;

        // 渲染顶部 Tabs (包含集成在右侧的 Guide Button)
        tabPanel.render(g, backgroundMouseX, backgroundMouseY, safeAlpha, layout.listPanel().x(), layout.rightEdge(), theme, dt);

        HudAnimUtil.drawFrame(g, layout.listPanel().x(), layout.listPanel().y(),
                layout.listPanel().width(), layout.listPanel().height(),
                HudAnimUtil.withAlpha(0x000000, (int) (0x55 * effectiveAlpha)),
                HudAnimUtil.withAlpha(theme, (int) (0x55 * effectiveAlpha)));
        listPanel.render(g, layout.listPanel().x(), layout.listPanel().y(),
                layout.listPanel().width(), layout.listPanel().height(), backgroundMouseX, backgroundMouseY, theme, dt);

        HudAnimUtil.drawFrame(g, layout.detailPanel().x(), layout.detailPanel().y(),
                layout.detailPanel().width(), layout.detailPanel().height(),
                HudAnimUtil.withAlpha(0x000000, (int) (0x44 * effectiveAlpha)),
                HudAnimUtil.withAlpha(currentThemeColor, (int) (0x55 * effectiveAlpha)));

        if (isShowingChangeLog()) {
            changeHistoryPanel.render(g, layout.detailPanel().x(), layout.detailPanel().y(),
                    layout.detailPanel().width(), layout.detailPanel().height(), backgroundMouseX, backgroundMouseY, dt);
        } else {
            detailPanel.render(g, layout.detailPanel().x(), layout.detailPanel().y(),
                    layout.detailPanel().width(), layout.detailPanel().height(), backgroundMouseX, backgroundMouseY, theme, dt);
        }
        if (effectiveAlpha > .03f && !QuestSplashRenderer.isActive()) {
            int settingsX = modSettingsButton.defaultX();
            int textSettingsX = settingsX + modSettingsButton.width(font) + modSettingsButton.gap();
            int trackerX = textSettingsX + textSettingsButton.width(font) + modSettingsButton.gap();
            modSettingsButton.renderAt(g, font, settingsX,
                    backgroundMouseX, backgroundMouseY, currentThemeColor, effectiveAlpha);
            textSettingsButton.renderAt(g, font, textSettingsX,
                    backgroundMouseX, backgroundMouseY, currentThemeColor, effectiveAlpha);
            trackerToggleButton.renderAt(this, g, font, trackerX,
                    backgroundMouseX, backgroundMouseY, currentThemeColor, effectiveAlpha);
        }
        renderingJournalBackground = false;

        boolean collectionDetailOpen = detailPanel.collectionRenderer.detailVisible();
        if (collectionDetailOpen != collectionDetailWasOpen) {
            activeObjectiveTooltip = null; activeTooltipStack = null; activeCustomTooltip = null;
            tooltipTipAlpha = 0; tooltipHoverTimer = 0;
        }
        collectionDetailWasOpen = collectionDetailOpen;
        if (collectionDetailOpen && !detailPanel.collectionRenderer.detailInteractive()) {
            activeObjectiveTooltip = null; activeTooltipStack = null; activeCustomTooltip = null;
            tooltipTipAlpha = 0; tooltipHoverTimer = 0;
        }
        if (collectionDetailOpen) {
            hoveredObjectiveTooltip = null; hoveredRewardTooltip = null; hoveredCustomTooltip = null;
            detailPanel.collectionRenderer.renderModal(g, sw, sh, smx, smy);
        }
        if (intelActive) QuestIntelPanel.render(g, sw, sh, smx, smy, partialTick);
        if (offerActive) {
            JeiScreenIngredients.modal(this, canQueryJei());
            QuestOfferPanel.render(g, sw, sh, smx, smy, partialTick);
        }
        if (collectionHistoryActive) CollectionHistoryPanel.render(g, smx, smy, partialTick);
        if (historyActive) {
            JeiScreenIngredients.modal(this, canQueryJei() && !offerActive && !intelActive && !collectionHistoryActive);
            hoveredRewardTooltip = null;
            hoveredCustomTooltip = null;
            QuestHistoryPanel.render(g, smx, smy, partialTick);
        }
        if (storyActive) QuestStoryPanel.render(g, sw, sh, smx, smy, partialTick);

        if (detailPanel.collectionRenderer.imageOpen()) {
            hoveredObjectiveTooltip = null; hoveredRewardTooltip = null; hoveredCustomTooltip = null;
            activeObjectiveTooltip = null; activeTooltipStack = null; activeCustomTooltip = null;
            tooltipTipAlpha = 0;
            detailPanel.collectionRenderer.renderImage(g, smx, smy);
        }

        objectiveIcons.endFrame();
        updateAndRenderTooltip(g, smx, smy);
        g.pose().popPose();
        HudCursorManager.apply();
    }

    public void requestPointerCursor() {
        HudCursorManager.requestPointer();
    }

    private void updateAndRenderTooltip(GuiGraphics g, int mouseX, int mouseY) {
        if (hoveredObjectiveTooltip != null && canInteractWithObjectiveIcons()) {
            if (activeObjectiveTooltip == null || !activeObjectiveTooltip.identity().equals(hoveredObjectiveTooltip.identity())) {
                tooltipHoverTimer = 0f;
            }
            activeObjectiveTooltip = hoveredObjectiveTooltip;
            activeCustomTooltip = null;
            activeTooltipStack = null;
            tooltipHoverTimer += dt;
            float target = tooltipHoverTimer >= TIP_HOVER_DELAY ? 1f : 0f;
            tooltipTipAlpha += (target - tooltipTipAlpha) * Math.min(1f, dt * 15f);
            if (tooltipTipAlpha > 0.02f) {
                renderObjectiveTooltip(g, mouseX, mouseY);
            }
            return;
        }
        boolean hasCustom = hoveredCustomTooltip != null && !hoveredCustomTooltip.isEmpty();
        boolean hasItem = hoveredRewardTooltip != null;
        boolean isHoveringValid = (hasCustom || hasItem) && !QuestIntelPanel.isActive()
                && !QuestOfferPanel.isActive() && !CollectionHistoryPanel.isActive() && !QuestStoryPanel.isActive();

        if (isHoveringValid) {
            activeObjectiveTooltip = null;
            if (hasCustom) {
                activeCustomTooltip = hoveredCustomTooltip;
                activeTooltipStack = null;
                tooltipHoverTimer += dt;
            } else {
                activeCustomTooltip = null;
                if (activeTooltipStack == null || !ItemStack.matches(activeTooltipStack, hoveredRewardTooltip)) {
                    if (tooltipTipAlpha > 0.5f) {
                        tooltipHoverTimer = TIP_HOVER_DELAY;
                        activeTooltipStack = hoveredRewardTooltip;
                    } else tooltipHoverTimer += dt;
                } else tooltipHoverTimer += dt;
                if (tooltipHoverTimer >= TIP_HOVER_DELAY) activeTooltipStack = hoveredRewardTooltip;
            }
        } else {
            tooltipHoverTimer = 0f;
            activeCustomTooltip = null;
        }

        float targetTipAlpha = (isHoveringValid && tooltipHoverTimer >= TIP_HOVER_DELAY && !isClosing) ? 1f : 0f;
        tooltipTipAlpha += (targetTipAlpha - tooltipTipAlpha) * Math.min(1f, dt * 15f);

        if (tooltipTipAlpha > 0.02f) {
            if (activeObjectiveTooltip != null)
                renderObjectiveTooltip(g, mouseX, mouseY);
            else if (activeCustomTooltip != null && !activeCustomTooltip.isEmpty())
                renderTooltipLines(g, activeCustomTooltip, mouseX, mouseY);
            else if (activeTooltipStack != null) renderTooltip(g, activeTooltipStack, mouseX, mouseY);
        } else {
            animTipW = 0;
            activeObjectiveTooltip = null;
            activeTooltipStack = null;
            activeCustomTooltip = null;
        }
    }

    private void renderObjectiveTooltip(GuiGraphics g, int mouseX, int mouseY) {
        ItemStack stack = activeObjectiveTooltip.stack();
        var focused = objectiveIcons.focusedTarget();
        if (focused != null) {
            mouseX = (int) (focused.x() / getUiScale());
            mouseY = (int) (focused.y() / getUiScale());
        }
        renderTooltipLayout(g, JournalTooltipRenderer.measureBounded(font, activeObjectiveTooltip.lines(), !stack.isEmpty(),
                Math.max(40, Math.min(280, getScaledWidth() - 4))), stack, mouseX, mouseY);
    }

    public void renderTooltip(GuiGraphics g, ItemStack stack, int mouseX, int mouseY) {
        if (minecraft == null || minecraft.player == null) return;
        List<Component> lines = stack.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(minecraft.level), minecraft.player, minecraft.options.advancedItemTooltips ? TooltipFlag.Default.ADVANCED : TooltipFlag.Default.NORMAL);
        if (lines.isEmpty()) return;
        renderTooltipLayout(g, JournalTooltipRenderer.measureWithItemIcon(font, lines), stack, mouseX, mouseY);
    }

    public void renderTooltipLines(GuiGraphics g, List<Component> tooltipLines, int mouseX, int mouseY) {
        if (tooltipLines == null || tooltipLines.isEmpty()) return;
        renderTooltipLayout(g, buildTooltipLayoutNoCache(tooltipLines), ItemStack.EMPTY, mouseX, mouseY);
    }

    private JournalTooltipRenderer.Layout buildTooltipLayoutNoCache(List<Component> lines) {
        return JournalTooltipRenderer.measure(font, lines);
    }

    private void renderTooltipLayout(GuiGraphics g, JournalTooltipRenderer.Layout layout, ItemStack iconStack,
                                     int mouseX, int mouseY) {
        if (layout == null || layout.lines().isEmpty()) return;
        int targetW = layout.width(), targetH = layout.height(), targetX = mouseX + 12, targetY = mouseY - 12;

        int sw = getScaledWidth(), sh = getScaledHeight();
        if (targetX + targetW > sw) targetX = mouseX - targetW - 8;
        targetX = Math.max(2, Math.min(targetX, Math.max(2, sw - targetW - 2)));
        if (targetY + targetH > sh) targetY = sh - targetH - 2;
        if (targetY < 0) targetY = 2;

        if (animTipW == 0 || Math.abs(animTipW - targetW) > 50) {
            animTipX = targetX; animTipY = targetY; animTipW = targetW; animTipH = targetH;
        } else {
            float morphSpeed = 18f;
            animTipX += (targetX - animTipX) * Math.min(1f, dt * morphSpeed);
            animTipY += (targetY - animTipY) * Math.min(1f, dt * morphSpeed);
            animTipW += (targetW - animTipW) * Math.min(1f, dt * morphSpeed);
            animTipH += (targetH - animTipH) * Math.min(1f, dt * morphSpeed);
        }

        float scale = isClosing ? HudAnimUtil.easeInCubic(tooltipTipAlpha) : (tooltipTipAlpha == 0 ? 1f : HudAnimUtil.easeOutCubic(tooltipTipAlpha));
        if (scale < 0.01f) return;

        int drawX = (int) animTipX, drawY = (int) animTipY, drawW = (int) animTipW, drawH = (int) animTipH;
        float finalTipAlpha = (tooltipTipAlpha == 0 ? 1f : tooltipTipAlpha) * effectiveAlpha;
        g.pose().pushPose();
        g.pose().translate(0, 0, 5000);
        float centerX = drawX + drawW / 2f, centerY = drawY + drawH / 2f;
        g.pose().translate(centerX, centerY, 0);
        g.pose().scale(scale, scale, 1f);
        g.pose().translate(-centerX, -centerY, 0);

        JournalTooltipRenderer.drawFrame(g, drawX, drawY, drawW, drawH, currentThemeColor, finalTipAlpha);
        enableScissor(g, drawX, drawY, drawX + drawW, drawY + drawH);
        if (iconStack.isEmpty()) {
            JournalTooltipRenderer.drawText(g, font, layout.lines(), drawX, drawY, finalTipAlpha);
        } else {
            JournalTooltipRenderer.drawItemTooltipText(g, font, layout.lines(), drawX, drawY, finalTipAlpha);
            JournalTooltipRenderer.drawItemIcon(g, iconStack, layout, drawX, drawY, finalTipAlpha);
        }
        disableScissor(g);
        g.pose().popPose();
    }

    public Font getFont() { return font; }
    public float getEffectiveAlpha() { return effectiveAlpha; }
    public float getDt() { return dt; }
    public int getThemeColor() { return currentTab == JournalTypes.Tab.ACTIVE ? JournalConstants.THEME_ACTIVE : currentTab == JournalTypes.Tab.COMPLETED ? JournalConstants.THEME_COMPLETED : JournalConstants.THEME_FAILED; }
    public JournalTypes.Tab getCurrentTab() { return currentTab; }
    public void setCurrentTab(JournalTypes.Tab tab) {
        currentTab = tab;
        rebuildEntries(false);
    }
    public List<JournalTypes.QuestListEntry> getCurrentEntries() { return currentEntries; }
    public int getSelectedIndex() { return selectedIndex; }
    @Nullable public String getSelectedQuestId() { return (selectedIndex >= 0 && selectedIndex < currentEntries.size()) ? currentEntries.get(selectedIndex).questId() : null; }

    public void onEntrySelected(int idx) {
        if (isShowingChangeLog() && selectedIndex == idx) { selectedIndex = -1; playClick(); return; }
        boolean markedRead = false;
        if (idx >= 0 && idx < currentEntries.size()) {
            String questId = currentEntries.get(idx).questId();
            markedRead = acknowledgeQuestChanges(questId);
        }
        if (selectedIndex != idx) {
            selectedIndex = idx; detailPanel.resetState();
            playClick();
        } else if (markedRead) {
            playClick();
        }
    }

    public boolean acknowledgeQuestChanges(@Nullable String questId) {
        if (questId == null || questId.isEmpty()) return false;
        boolean wasUnread = QuestChangeNotificationManager.INSTANCE.hasUnread(questId);
        QuestChangeNotificationManager.INSTANCE.markRead(questId);
        return wasUnread;
    }

    private String resolveTargetQuestForOpen() {
        String trackedQuestId = QuestHudOverlay.INSTANCE.getTrackedQuestId();
        if (trackedQuestId == null || trackedQuestId.isEmpty()) return null;
        if (ClientQuestCache.INSTANCE.isQuestActive(trackedQuestId)) return trackedQuestId;
        if (ClientQuestCache.INSTANCE.isQuestCompleted(trackedQuestId)) return trackedQuestId;
        if (ClientQuestCache.INSTANCE.isQuestFailed(trackedQuestId)) return trackedQuestId;
        return null;
    }

    public void playClick() { if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F)); }
    public void setHoveredRewardTooltip(ItemStack stack) {
        if (renderingJournalBackground && !canInteractWithJournalBackground()) return;
        hoveredObjectiveTooltip = null; hoveredRewardTooltip = stack;
    }
    public ObjectiveIconSession getObjectiveIcons() { return objectiveIcons; }
    public boolean canInteractWithObjectiveIcons() {
        return canQueryJei() && !QuestOfferPanel.isActive() && !QuestHistoryPanel.isActive()
                && (!renderingJournalBackground || canInteractWithJournalBackground());
    }
    public void requestTooltip(JournalTooltipRequest request) {
        if (renderingJournalBackground && !canInteractWithJournalBackground()) return;
        hoveredObjectiveTooltip = request;
        hoveredRewardTooltip = null;
        hoveredCustomTooltip = null;
    }
    @Override public void mouseMoved(double x, double y) {
        objectiveIcons.clearFocus();
        super.mouseMoved(x, y);
    }
    public void setHoveredCustomTooltip(List<Component> lines) {
        if (renderingJournalBackground && !canInteractWithJournalBackground()) return;
        hoveredObjectiveTooltip = null; hoveredCustomTooltip = lines;
    }
    public int getCurrentThemeColor() { return currentThemeColor; }
    public void setCurrentThemeColor(int color) { currentThemeColor = color; }
    public JournalDetailPanel getDetailPanel() { return detailPanel; }
    public QuestChangeHistoryPanel getChangeHistoryPanel() { return changeHistoryPanel; }

}

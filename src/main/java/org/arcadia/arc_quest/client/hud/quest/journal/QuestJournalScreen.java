package org.arcadia.arc_quest.client.hud.quest.journal;

import net.minecraft.Util;
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
import org.arcadia.arc_quest.client.hud.guide.GuideListScreen;
import org.arcadia.arc_quest.client.hud.quest.history.CollectionHistoryPanel;
import org.arcadia.arc_quest.client.hud.quest.history.QuestHistoryPanel;
import org.arcadia.arc_quest.client.hud.quest.journal.detail.JournalDetailPanel;
import org.arcadia.arc_quest.client.hud.quest.journal.history.QuestChangeHistoryPanel;
import org.arcadia.arc_quest.client.hud.quest.journal.history.QuestChangeHistoryStore;
import org.arcadia.arc_quest.client.hud.quest.journal.history.QuestChangeNotificationManager;
import org.arcadia.arc_quest.client.hud.quest.offer.QuestOfferPanel;
import org.arcadia.arc_quest.client.hud.quest.ponder.QuestIntelPanel;
import org.arcadia.arc_quest.client.hud.quest.splash.QuestSplashRenderer;
import org.arcadia.arc_quest.client.hud.quest.story.QuestStoryPanel;
import org.arcadia.arc_quest.config.ArcQuestConfig;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.api.QuestState;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.arcadia.arc_quest.client.hud.HudRenderUtil.drawCyberneticEdge;

public class QuestJournalScreen extends Screen {

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

    private ItemStack hoveredRewardTooltip = null;
    private List<Component> hoveredCustomTooltip = null;
    private ItemStack activeTooltipStack = null;
    private List<Component> activeCustomTooltip = null;
    private float tooltipHoverTimer = 0f;
    private float tooltipTipAlpha = 0f;
    private float animTipX = 0, animTipY = 0, animTipW = 0, animTipH = 0;
    private boolean pointerCursorRequested = false;
    private boolean pointerCursorApplied = false;
    private long pointerCursorHandle = 0L;

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
        hoveredRewardTooltip = null;
        hoveredCustomTooltip = null;
        activeTooltipStack = null;
        animTipW = 0f;
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

    @Override
    protected void init() {
        super.init();
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
    }

    public void rebuildEntries() {
        String targetQuestId = resolveTargetQuestForOpen();
        List<String> lastActivePhases = null;
        boolean hadLiveSelectionBeforeRebuild = false;
        if (selectedIndex >= 0 && selectedIndex < currentEntries.size()) {
            hadLiveSelectionBeforeRebuild = true;
            targetQuestId = currentEntries.get(selectedIndex).questId();
            var rt = ClientQuestCache.INSTANCE.getActiveQuest(targetQuestId);
            if (rt != null) lastActivePhases = new ArrayList<>(rt.getActivePhaseIds());
        }

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

        if (targetQuestId != null) {
            for (int i = 0; i < currentEntries.size(); i++) {
                if (currentEntries.get(i).questId().equals(targetQuestId)) {
                    selectedIndex = i;
                    if (!hadLiveSelectionBeforeRebuild) {
                        listPanel.resetState();
                        detailPanel.resetState();
                    }
                    return;
                }
            }
        }
        selectedIndex = currentEntries.isEmpty() ? -1 : 0;
        listPanel.resetState();
        detailPanel.resetState();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (QuestIntelPanel.isActive()) {
            if (keyCode == 256 || minecraft.options.keyInventory.matches(keyCode, scanCode)) { QuestIntelPanel.dismiss(); return true; }
            return true;
        }
        if (QuestOfferPanel.isActive()) { QuestOfferPanel.keyPressed(keyCode); return true; }
        if (CollectionHistoryPanel.isActive()) { CollectionHistoryPanel.keyPressed(keyCode); return true; }
        if (QuestHistoryPanel.isActive()) { QuestHistoryPanel.keyPressed(keyCode); return true; }
        if (QuestStoryPanel.isActive()) { QuestStoryPanel.keyPressed(keyCode); return true; }
        if (ClientEventHandler.KEY_OPEN_JOURNAL.matches(keyCode, scanCode)) {
            onClose(); return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        QuestChangeHistoryStore.INSTANCE.flush();
        if (!isClosing) isClosing = true;
    }

    @Override
    public void removed() {
        QuestChangeHistoryStore.INSTANCE.flush();
        clearTransientPanels();
        releasePointerCursor();
        super.removed();
    }

    private void clearTransientPanels() {
        QuestIntelPanel.clearClientSession();
        QuestOfferPanel.clearClientSession();
        CollectionHistoryPanel.clearClientSession();
        QuestHistoryPanel.clearClientSession();
        QuestStoryPanel.clearClientSession();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        float uiScale = getUiScale();
        double smx = mx / uiScale, smy = my / uiScale;
        int sw = getScaledWidth(), sh = getScaledHeight();

        if (QuestIntelPanel.isActive()) { QuestIntelPanel.handleMouseClick(smx, smy, sw, sh); return true; }
        if (QuestOfferPanel.isActive()) { QuestOfferPanel.mouseClicked(smx, smy, button); return true; }
        if (CollectionHistoryPanel.isActive()) { CollectionHistoryPanel.mouseClicked(smx, smy, button); return true; }
        if (QuestHistoryPanel.isActive()) { QuestHistoryPanel.mouseClicked(smx, smy, button); return true; }
        if (QuestStoryPanel.isActive()) { QuestStoryPanel.mouseClicked(smx, smy, button); return true; }

        if (isClosing || button != 0) return super.mouseClicked(mx, my, button);

        float slideOffset = (1f - getEaseProgress()) * 200f;
        int listX = JournalConstants.LIST_MARGIN - (int) slideOffset;
        int listY = 38 + JournalConstants.TAB_HEIGHT + 6, listH = sh - 20 - listY;
        int detailX = JournalConstants.LIST_MARGIN + JournalConstants.LIST_WIDTH + JournalConstants.DETAIL_MARGIN + (int) slideOffset;
        int detailW = sw - detailX - JournalConstants.DETAIL_MARGIN;

        if (tabPanel.mouseClicked(smx, smy, listX, detailX + detailW)) return true;
        if (listPanel.mouseClicked(smx, smy, listX, listY, JournalConstants.LIST_WIDTH, listH)) return true;

        if (isShowingChangeLog()) {
            if (changeHistoryPanel.mouseClicked(smx, smy, button, detailX, listY, detailW, listH)) return true;
        } else {
            if (detailPanel.mouseClicked(smx, smy, detailX, listY, detailW, listH)) return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dragX, double dragY) {
        float uiScale = getUiScale();
        double smx = mx / uiScale, smy = my / uiScale;
        int sh = getScaledHeight();
        if (QuestIntelPanel.isActive()) { QuestIntelPanel.mouseDragged(smx, smy); return true; }
        if (QuestOfferPanel.isActive()) { QuestOfferPanel.mouseDragged(smx, smy); return true; }
        if (QuestStoryPanel.isActive()) { QuestStoryPanel.mouseDragged(smx, smy); return true; }
        if (CollectionHistoryPanel.isActive()) { CollectionHistoryPanel.mouseDragged(smx, smy); return true; }
        if (QuestHistoryPanel.isActive()) { QuestHistoryPanel.mouseDragged(smx, smy); return true; }

        int listY = 38 + JournalConstants.TAB_HEIGHT + 6, listH = sh - 20 - listY;
        if (listPanel.mouseDragged(smx, smy, listY, listH)) return true;
        if (!isShowingChangeLog()) {
            if (detailPanel.mouseDragged(smx, smy, listY, listH)) return true;
        }
        return super.mouseDragged(mx, my, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        float uiScale = getUiScale();
        double smx = mx / uiScale, smy = my / uiScale;
        if (QuestIntelPanel.isActive()) { QuestIntelPanel.mouseReleased(button); return true; }
        if (QuestOfferPanel.isActive()) { QuestOfferPanel.mouseReleased(button); return true; }
        if (QuestStoryPanel.isActive()) { QuestStoryPanel.mouseReleased(button); return true; }
        if (CollectionHistoryPanel.isActive()) { CollectionHistoryPanel.mouseReleased(button); return true; }
        if (QuestHistoryPanel.isActive()) { QuestHistoryPanel.mouseReleased(button); return true; }
        listPanel.mouseReleased(button);
        if (!isShowingChangeLog()) detailPanel.mouseReleased(button);
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        double delta = scrollY;
        float uiScale = getUiScale();
        double smx = mx / uiScale, smy = my / uiScale;
        int sw = getScaledWidth(), sh = getScaledHeight();

        if (QuestIntelPanel.isActive() || QuestOfferPanel.isActive() || QuestStoryPanel.isActive()) return true;
        if (CollectionHistoryPanel.isActive()) return true;
        if (QuestHistoryPanel.isActive()) { QuestHistoryPanel.mouseScrolled(smx, smy, delta); return true; }
        if (isClosing) return false;

        float slideOffset = (1f - getEaseProgress()) * 200f;
        int listX = JournalConstants.LIST_MARGIN - (int) slideOffset;
        int listY = 38 + JournalConstants.TAB_HEIGHT + 6, listH = sh - 20 - listY;
        int detailX = JournalConstants.LIST_MARGIN + JournalConstants.LIST_WIDTH + JournalConstants.DETAIL_MARGIN + (int) slideOffset;
        int detailW = sw - detailX - JournalConstants.DETAIL_MARGIN;

        if (listPanel.mouseScrolled(smx, smy, delta, listX, listY, JournalConstants.LIST_WIDTH, listH)) return true;

        if (isShowingChangeLog()) {
            if (changeHistoryPanel.mouseScrolled(smx, smy, delta, detailX, listY, detailW, listH)) return true;
        } else {
            if (detailPanel.mouseScrolled(smx, smy, delta, detailX, listY, detailW, listH)) return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    private float getEaseProgress() {
        return (isClosing ? HudAnimUtil.easeInCubic(transitionAlpha) : HudAnimUtil.easeOutCubic(transitionAlpha)) * HudAnimUtil.easeOutCubic(suspendAlpha);
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        pointerCursorRequested = false;
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
            applyRequestedCursor();
            if (minecraft != null && minecraft.screen == this) minecraft.setScreen(null);
            return;
        }

        effectiveAlpha = transitionAlpha * suspendAlpha;
        float easeProgress = getEaseProgress();
        float slideOffset = (1f - easeProgress) * 200f;
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
            g.drawCenteredString(font, title, sw / 2, 14, HudAnimUtil.withAlpha(0xFFFFFF, safeAlpha));
            g.pose().popPose();
        }

        int theme = getThemeColor();
        int listX = JournalConstants.LIST_MARGIN - (int) slideOffset;
        int listY = 38 + JournalConstants.TAB_HEIGHT + 6;
        int listH = sh - 20 - listY;
        int detailX = JournalConstants.LIST_MARGIN + JournalConstants.LIST_WIDTH + JournalConstants.DETAIL_MARGIN + (int) slideOffset;
        int detailW = sw - detailX - JournalConstants.DETAIL_MARGIN;

        // 渲染顶部 Tabs (包含集成在右侧的 Guide Button)
        tabPanel.render(g, smx, smy, safeAlpha, listX, detailX + detailW, theme, dt);

        HudAnimUtil.drawFrame(g, listX, listY, JournalConstants.LIST_WIDTH, listH, HudAnimUtil.withAlpha(0x000000, (int) (0x55 * effectiveAlpha)), HudAnimUtil.withAlpha(theme, (int) (0x55 * effectiveAlpha)));
        listPanel.render(g, listX, listY, JournalConstants.LIST_WIDTH, listH, smx, smy, theme, dt);

        HudAnimUtil.drawFrame(g, detailX, listY, detailW, listH, HudAnimUtil.withAlpha(0x000000, (int) (0x44 * effectiveAlpha)), HudAnimUtil.withAlpha(currentThemeColor, (int) (0x55 * effectiveAlpha)));

        if (isShowingChangeLog()) {
            changeHistoryPanel.render(g, detailX, listY, detailW, listH, smx, smy, dt);
        } else {
            detailPanel.render(g, detailX, listY, detailW, listH, smx, smy, theme, dt);
        }

        if (intelActive) QuestIntelPanel.render(g, sw, sh, smx, smy, partialTick);
        if (offerActive) QuestOfferPanel.render(g, sw, sh, smx, smy, partialTick);
        if (collectionHistoryActive) CollectionHistoryPanel.render(g, smx, smy, partialTick);
        if (historyActive) {
            hoveredRewardTooltip = null;
            hoveredCustomTooltip = null;
            QuestHistoryPanel.render(g, smx, smy, partialTick);
        }
        if (storyActive) QuestStoryPanel.render(g, sw, sh, smx, smy, partialTick);

        updateAndRenderTooltip(g, smx, smy);
        g.pose().popPose();
        applyRequestedCursor();
    }

    public void requestPointerCursor() {
        pointerCursorRequested = true;
    }

    private void applyRequestedCursor() {
        if (minecraft == null || pointerCursorRequested == pointerCursorApplied) return;
        if (pointerCursorRequested) {
            if (pointerCursorHandle == 0L) {
                pointerCursorHandle = GLFW.glfwCreateStandardCursor(GLFW.GLFW_HAND_CURSOR);
            }
            GLFW.glfwSetCursor(minecraft.getWindow().getWindow(), pointerCursorHandle);
        } else {
            GLFW.glfwSetCursor(minecraft.getWindow().getWindow(), 0L);
        }
        pointerCursorApplied = pointerCursorRequested;
    }

    private void releasePointerCursor() {
        if (minecraft != null && pointerCursorApplied) {
            GLFW.glfwSetCursor(minecraft.getWindow().getWindow(), 0L);
        }
        if (pointerCursorHandle != 0L) {
            GLFW.glfwDestroyCursor(pointerCursorHandle);
            pointerCursorHandle = 0L;
        }
        pointerCursorRequested = false;
        pointerCursorApplied = false;
    }

    private void updateAndRenderTooltip(GuiGraphics g, int mouseX, int mouseY) {
        boolean hasCustom = hoveredCustomTooltip != null && !hoveredCustomTooltip.isEmpty();
        boolean hasItem = hoveredRewardTooltip != null;
        boolean isHoveringValid = (hasCustom || hasItem) && !QuestIntelPanel.isActive()
                && !QuestOfferPanel.isActive() && !CollectionHistoryPanel.isActive() && !QuestStoryPanel.isActive();

        if (isHoveringValid) {
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
            if (activeCustomTooltip != null && !activeCustomTooltip.isEmpty())
                renderTooltipLines(g, activeCustomTooltip, mouseX, mouseY);
            else if (activeTooltipStack != null) renderTooltip(g, activeTooltipStack, mouseX, mouseY);
        } else {
            animTipW = 0;
            activeTooltipStack = null;
            activeCustomTooltip = null;
        }
    }

    public void renderTooltip(GuiGraphics g, ItemStack stack, int mouseX, int mouseY) {
        if (minecraft == null || minecraft.player == null) return;
        List<Component> lines = stack.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.EMPTY, minecraft.player, minecraft.options.advancedItemTooltips ? TooltipFlag.Default.ADVANCED : TooltipFlag.Default.NORMAL);
        if (lines.isEmpty()) return;
        renderTooltipLayout(g, buildTooltipLayoutNoCache(lines), mouseX, mouseY);
    }

    public void renderTooltipLines(GuiGraphics g, List<Component> tooltipLines, int mouseX, int mouseY) {
        if (tooltipLines == null || tooltipLines.isEmpty()) return;
        renderTooltipLayout(g, buildTooltipLayoutNoCache(tooltipLines), mouseX, mouseY);
    }

    private TooltipLayoutCache buildTooltipLayoutNoCache(List<Component> lines) {
        TooltipLayoutCache layout = new TooltipLayoutCache();
        layout.lines = lines == null ? List.of() : lines;
        int padding = 6, cyberEdgeWidth = 3;
        for (Component line : layout.lines) {
            int lineWidth = font.width(line);
            if (lineWidth > layout.textMaxWidth) layout.textMaxWidth = lineWidth;
        }
        layout.targetW = layout.textMaxWidth + padding * 2 + cyberEdgeWidth + 2;
        layout.targetH = layout.lines.size() * font.lineHeight + padding * 2;
        return layout;
    }

    private void renderTooltipLayout(GuiGraphics g, TooltipLayoutCache layout, int mouseX, int mouseY) {
        if (layout == null || layout.lines.isEmpty()) return;
        int padding = 6, cyberEdgeWidth = 3;
        int targetW = layout.targetW, targetH = layout.targetH, targetX = mouseX + 12, targetY = mouseY - 12;

        int sw = getScaledWidth(), sh = getScaledHeight();
        if (targetX + targetW > sw) targetX = mouseX - targetW - 8;
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
        int bgAlpha = (int) (0xD0 * finalTipAlpha), borderAlpha = (int) (0x66 * finalTipAlpha), edgeAlpha = (int) (255 * finalTipAlpha);

        g.pose().pushPose();
        g.pose().translate(0, 0, 5000);
        float centerX = drawX + drawW / 2f, centerY = drawY + drawH / 2f;
        g.pose().translate(centerX, centerY, 0);
        g.pose().scale(scale, scale, 1f);
        g.pose().translate(-centerX, -centerY, 0);

        g.fill(drawX + cyberEdgeWidth, drawY, drawX + drawW, drawY + drawH, HudAnimUtil.withAlpha(0x000000, bgAlpha));
        g.fill(drawX + cyberEdgeWidth, drawY, drawX + drawW, drawY + 1, HudAnimUtil.withAlpha(0xCCCCCC, borderAlpha));
        g.fill(drawX + cyberEdgeWidth, drawY + drawH - 1, drawX + drawW, drawY + drawH, HudAnimUtil.withAlpha(0xCCCCCC, borderAlpha));
        g.fill(drawX + drawW - 1, drawY, drawX + drawW, drawY + drawH, HudAnimUtil.withAlpha(0xCCCCCC, borderAlpha));

        drawCyberneticEdge(g, drawX, drawY, drawH, currentThemeColor, edgeAlpha);
        enableScissor(g, drawX, drawY, drawX + drawW, drawY + drawH);

        int textX = drawX + cyberEdgeWidth + padding + 1, textY = drawY + padding;
        for (Component line : layout.lines) {
            g.drawString(font, line, textX, textY, HudAnimUtil.withAlpha(0xFFFFFF, edgeAlpha), true);
            textY += font.lineHeight;
        }
        g.disableScissor();
        g.pose().popPose();
    }

    public Font getFont() { return font; }
    public float getEffectiveAlpha() { return effectiveAlpha; }
    public float getDt() { return dt; }
    public int getThemeColor() { return currentTab == JournalTypes.Tab.ACTIVE ? JournalConstants.THEME_ACTIVE : currentTab == JournalTypes.Tab.COMPLETED ? JournalConstants.THEME_COMPLETED : JournalConstants.THEME_FAILED; }
    public JournalTypes.Tab getCurrentTab() { return currentTab; }
    public void setCurrentTab(JournalTypes.Tab tab) { currentTab = tab; rebuildEntries(); }
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
    public void setHoveredRewardTooltip(ItemStack stack) { hoveredRewardTooltip = stack; }
    public void setHoveredCustomTooltip(List<Component> lines) { hoveredCustomTooltip = lines; }
    public int getCurrentThemeColor() { return currentThemeColor; }
    public void setCurrentThemeColor(int color) { currentThemeColor = color; }
    public JournalDetailPanel getDetailPanel() { return detailPanel; }
    public QuestChangeHistoryPanel getChangeHistoryPanel() { return changeHistoryPanel; }

    private static class TooltipLayoutCache {
        List<Component> lines = List.of();
        int textMaxWidth = 0;
        int targetW = 0;
        int targetH = 0;
    }
}

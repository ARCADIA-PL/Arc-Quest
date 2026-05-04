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
import org.arcadia.arc_quest.mutil.animation.ArcAnimClock;
import org.arcadia.arc_quest.mutil.theme.ArcDrawUtil;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.QuestArcHudController;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.panel.collection.ArcQuestCollectionHistoryManager;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.panel.history.ArcQuestHistoryPanelElement;
import org.arcadia.arc_quest.client.hud.quest.journal.detail.JournalDetailPanel;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.panel.offer.ArcQuestOfferPanelElement;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.panel.intel.ArcQuestIntelPanelElement;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.splash.ArcQuestSplashManager;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.panel.story.ArcQuestStoryPanelElement;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.api.QuestState;
import org.arcadia.arc_quest.quest.capability.QuestRuntimeData;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;



public class QuestJournalScreen extends Screen {

    private static final float TIP_HOVER_DELAY = 0.05f;
    private final JournalTabPanel tabPanel;
    private final JournalListPanel listPanel;
    private final JournalDetailPanel detailPanel;
    private ArcQuestJournalRoot arcRoot;
    private final List<JournalTypes.QuestListEntry> currentEntries = new ArrayList<>();
    private JournalTypes.Tab currentTab = JournalTypes.Tab.ACTIVE;
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

    public QuestJournalScreen() {
        super(Component.translatable("gui.arc_quest.journal.title"));
        this.tabPanel = new JournalTabPanel(this);
        this.listPanel = new JournalListPanel(this);
        this.detailPanel = new JournalDetailPanel(this);
    }

    public void triggerEntranceAnimation() {
        this.transitionAlpha = 0f;
        this.isClosing = false;
        this.suspendAlpha = 1.0f;
        this.lastRenderTime = 0;
        this.tooltipHoverTimer = 0f;
        this.tooltipTipAlpha = 0f;
        this.hoveredRewardTooltip = null;
        this.hoveredCustomTooltip = null;
        this.activeTooltipStack = null;
        this.animTipW = 0f;
    }

    public float getUiScale() {
        if (this.minecraft == null) return 1.0f;
        double guiScale = this.minecraft.getWindow().getGuiScale();
        if (guiScale == 0) guiScale = 1.0;
        float scale = (float) (3.0 / guiScale);
        float sw = this.width / scale, sh = this.height / scale;
        float minW = 480f, minH = 260f;
        if (sw < minW) {
            scale = this.width / minW;
            sh = this.height / scale;
        }
        if (sh < minH) scale = this.height / minH;
        return scale;
    }

    public int getScaledWidth() {
        return (int) (this.width / getUiScale());
    }

    public int getScaledHeight() {
        return (int) (this.height / getUiScale());
    }

    public void enableScissor(GuiGraphics g, int x, int y, int x2, int y2) {
        float s = getUiScale();
        g.enableScissor((int) (x * s), (int) (y * s), (int) (x2 * s), (int) (y2 * s));
    }

    @Override
    protected void init() {
        super.init();
        this.lastRenderTime = 0;
        if (this.minecraft != null && this.arcRoot == null) {
            this.arcRoot = new ArcQuestJournalRoot(this.minecraft, this);
        }
        rebuildEntries();
    }

    public void rebuildEntries() {
        String lastSelectedQuestId = null;
        List<String> lastActivePhases = null;
        if (selectedIndex >= 0 && selectedIndex < currentEntries.size()) {
            lastSelectedQuestId = currentEntries.get(selectedIndex).questId();
            var rt = ClientQuestCache.INSTANCE.getActiveQuest(lastSelectedQuestId);
            if (rt != null) lastActivePhases = new ArrayList<>(rt.getActivePhaseIds());
        }

        currentEntries.clear();
        switch (currentTab) {
            case ACTIVE -> {
                for (Map.Entry<String, QuestRuntimeData> e : ClientQuestCache.INSTANCE.getAllActiveQuests().entrySet()) {
                    ResourceLocation questRl = ResourceLocation.tryParse(e.getKey());
                    QuestDefinition def = questRl != null ? QuestRegistry.get(questRl) : null;
                    currentEntries.add(new JournalTypes.QuestListEntry(e.getKey(), ClientQuestCache.INSTANCE.getQuestDisplayName(e.getKey()), QuestState.ACTIVE, def));
                }
            }
            case COMPLETED -> {
                for (String id : ClientQuestCache.INSTANCE.getCompletedQuests()) {
                    ResourceLocation questRl = ResourceLocation.tryParse(id);
                    QuestDefinition def = questRl != null ? QuestRegistry.get(questRl) : null;
                    currentEntries.add(new JournalTypes.QuestListEntry(id, ClientQuestCache.INSTANCE.getQuestDisplayName(id), QuestState.COMPLETED, def));
                }
            }
            case FAILED -> {
                for (String id : ClientQuestCache.INSTANCE.getFailedQuests()) {
                    ResourceLocation questRl = ResourceLocation.tryParse(id);
                    QuestDefinition def = questRl != null ? QuestRegistry.get(questRl) : null;
                    currentEntries.add(new JournalTypes.QuestListEntry(id, ClientQuestCache.INSTANCE.getQuestDisplayName(id), QuestState.FAILED, def));
                }
            }
        }

        if (lastSelectedQuestId != null) {
            for (int i = 0; i < currentEntries.size(); i++) {
                if (currentEntries.get(i).questId().equals(lastSelectedQuestId)) {
                    var rt = ClientQuestCache.INSTANCE.getActiveQuest(lastSelectedQuestId);
                    List<String> currentPhases = rt != null ? new ArrayList<>(rt.getActivePhaseIds()) : null;
                    boolean phasesChanged = false;
                    if (lastActivePhases == null && currentPhases != null) phasesChanged = true;
                    else if (lastActivePhases != null && currentPhases == null) phasesChanged = true;
                    else if (lastActivePhases != null && currentPhases != null) {
                        if (lastActivePhases.size() != currentPhases.size() || !lastActivePhases.containsAll(currentPhases))
                            phasesChanged = true;
                    }
                    if (!phasesChanged) {
                        selectedIndex = i;
                        return;
                    }
                    break;
                }
            }
        }
        selectedIndex = currentEntries.isEmpty() ? -1 : 0;
        listPanel.resetState();
        detailPanel.resetState();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ArcQuestIntelPanelElement.isActive()) {
            if (keyCode == 256 || minecraft.options.keyInventory.matches(keyCode, scanCode)) {
                ArcQuestIntelPanelElement.dismiss();
                return true;
            }
            return true;
        }
        if (ArcQuestOfferPanelElement.isActive()) {
            ArcQuestOfferPanelElement.keyPressed(keyCode);
            return true;
        }
        if (ArcQuestHistoryPanelElement.isActive()) {
            ArcQuestHistoryPanelElement.keyPressed(keyCode);
            return true;
        }
        if (ArcQuestCollectionHistoryManager.isActive()) {
            ArcQuestCollectionHistoryManager.keyPressed(keyCode);
            return true;
        }
        if (ArcQuestStoryPanelElement.isActive()) {
            ArcQuestStoryPanelElement.keyPressed(keyCode);
            return true;
        }
        if (ClientEventHandler.KEY_OPEN_JOURNAL.matches(keyCode, scanCode)) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (!isClosing) isClosing = true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        float uiScale = getUiScale();
        double smx = mx / uiScale, smy = my / uiScale;
        int sw = getScaledWidth(), sh = getScaledHeight();

        if (ArcQuestIntelPanelElement.isActive()) {
            ArcQuestIntelPanelElement.handleMouseClick(smx, smy, sw, sh);
            return true;
        }
        if (ArcQuestOfferPanelElement.isActive()) {
            ArcQuestOfferPanelElement.mouseClicked(smx, smy, button);
            return true;
        }
        if (ArcQuestHistoryPanelElement.isActive()) {
            ArcQuestHistoryPanelElement.mouseClicked(smx, smy, button);
            return true;
        }
        if (ArcQuestCollectionHistoryManager.isActive()) return true;
        if (ArcQuestStoryPanelElement.isActive()) {
            ArcQuestStoryPanelElement.mouseClicked(smx, smy, button);
            return true;
        }

        if (isClosing || button != 0) return super.mouseClicked(mx, my, button);

        ensureArcRoot();
        if (arcRoot.mouseClicked(mx, my, button)) return true;
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dragX, double dragY) {
        float uiScale = getUiScale();
        double smx = mx / uiScale, smy = my / uiScale;
        int sh = getScaledHeight();
        if (ArcQuestIntelPanelElement.isActive() || ArcQuestOfferPanelElement.isActive() || ArcQuestStoryPanelElement.isActive() || ArcQuestCollectionHistoryManager.isActive()) return true;
        if (ArcQuestHistoryPanelElement.isActive()) {
            ArcQuestHistoryPanelElement.mouseDragged(smx, smy);
            return true;
        }

        ensureArcRoot();
        if (arcRoot.mouseDragged(mx, my, button, dragX, dragY)) return true;
        return super.mouseDragged(mx, my, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (ArcQuestIntelPanelElement.isActive() || ArcQuestOfferPanelElement.isActive() || ArcQuestStoryPanelElement.isActive() || ArcQuestCollectionHistoryManager.isActive()) return true;
        if (ArcQuestHistoryPanelElement.isActive()) {
            ArcQuestHistoryPanelElement.mouseReleased(button);
            return true;
        }
        ensureArcRoot();
        arcRoot.mouseReleased(mx, my, button);
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        float uiScale = getUiScale();
        double smx = mx / uiScale, smy = my / uiScale;
        int sw = getScaledWidth(), sh = getScaledHeight();

        if (ArcQuestIntelPanelElement.isActive() || ArcQuestOfferPanelElement.isActive() || ArcQuestStoryPanelElement.isActive() || ArcQuestCollectionHistoryManager.isActive()) return true;
        if (ArcQuestHistoryPanelElement.isActive()) {
            ArcQuestHistoryPanelElement.mouseScrolled(smx, smy, delta);
            return true;
        }
        if (isClosing) return false;

        ensureArcRoot();
        if (arcRoot.mouseScrolled(mx, my, delta)) return true;
        return super.mouseScrolled(mx, my, delta);
    }

    public boolean handleJournalMouseClicked(double smx, double smy, int button) {
        if (isClosing || button != 0) return false;
        int sw = getScaledWidth();
        int sh = getScaledHeight();
        float slideOffset = (1f - getEaseProgress()) * 200f;
        int listX = JournalConstants.LIST_MARGIN - (int) slideOffset;
        int listY = 38 + JournalConstants.TAB_HEIGHT + 6;
        int listH = sh - 20 - listY;
        int detailX = JournalConstants.LIST_MARGIN + JournalConstants.LIST_WIDTH + JournalConstants.DETAIL_MARGIN + (int) slideOffset;
        int detailW = sw - detailX - JournalConstants.DETAIL_MARGIN;
        if (detailPanel.mouseClicked(smx, smy, detailX, listY, detailW, listH)) return true;
        if (listPanel.mouseClicked(smx, smy, listX, listY, JournalConstants.LIST_WIDTH, listH)) return true;
        return tabPanel.mouseClicked(smx, smy, listX);
    }

    public boolean handleJournalMouseDragged(double smx, double smy, int button, double dragX, double dragY) {
        int sh = getScaledHeight();
        int listY = 38 + JournalConstants.TAB_HEIGHT + 6;
        int listH = sh - 20 - listY;
        if (listPanel.mouseDragged(smx, smy, listY, listH)) return true;
        return detailPanel.mouseDragged(smx, smy, listY, listH);
    }

    public void handleJournalMouseReleased(double smx, double smy, int button) {
        listPanel.mouseReleased(button);
        detailPanel.mouseReleased(button);
    }

    public boolean handleJournalMouseScrolled(double smx, double smy, double delta) {
        if (isClosing) return false;
        int sw = getScaledWidth();
        int sh = getScaledHeight();
        float slideOffset = (1f - getEaseProgress()) * 200f;
        int listX = JournalConstants.LIST_MARGIN - (int) slideOffset;
        int listY = 38 + JournalConstants.TAB_HEIGHT + 6;
        int listH = sh - 20 - listY;
        int detailX = JournalConstants.LIST_MARGIN + JournalConstants.LIST_WIDTH + JournalConstants.DETAIL_MARGIN + (int) slideOffset;
        int detailW = sw - detailX - JournalConstants.DETAIL_MARGIN;
        if (listPanel.mouseScrolled(smx, smy, delta, listX, listY, JournalConstants.LIST_WIDTH, listH)) return true;
        return detailPanel.mouseScrolled(smx, smy, delta, detailX, listY, detailW, listH);
    }

    private float getEaseProgress() {
        return (isClosing ? ArcAnimClock.easeInCubic(transitionAlpha) : ArcAnimClock.easeOutCubic(transitionAlpha)) * ArcAnimClock.easeOutCubic(suspendAlpha);
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.hoveredRewardTooltip = null;
        this.hoveredCustomTooltip = null;
        ensureArcRoot();
        tickJournalAnimation(computeFrameDelta());
        if (!shouldRenderJournalBody()) return;

        arcRoot.setUiScale(getUiScale());
        arcRoot.drawScaledRoot(g, partialTick);
    }

    private void ensureArcRoot() {
        if (this.minecraft != null && this.arcRoot == null) {
            this.arcRoot = new ArcQuestJournalRoot(this.minecraft, this);
        }
    }

    private float computeFrameDelta() {
        long now = Util.getMillis();
        if (lastRenderTime == 0) lastRenderTime = now;
        float realDt = (now - lastRenderTime) / 1000f;
        lastRenderTime = now;
        return Math.min(realDt, 0.1f);
    }

    public void tickJournalAnimation(float realDt) {
        boolean intelActive = ArcQuestIntelPanelElement.isActive();
        boolean storyActive = ArcQuestStoryPanelElement.isActive();

        if (ArcQuestSplashManager.isActive()) {
            suspendAlpha = Math.max(0f, suspendAlpha - realDt * 6f);
            dt = 0f;
        } else {
            suspendAlpha = Math.min(1f, suspendAlpha + realDt * 4f);
            dt = (intelActive || storyActive) ? 0f : realDt;
        }

        transitionAlpha = ArcAnimClock.lerp(transitionAlpha, isClosing ? 0f : 1f, isClosing ? 0.2f : 0.12f, realDt);
        if (isClosing && transitionAlpha <= 0.01f && minecraft != null && minecraft.screen == this) {
            minecraft.setScreen(null);
        }
        effectiveAlpha = transitionAlpha * suspendAlpha;
    }

    public boolean shouldRenderJournalBody() {
        return !(isClosing && transitionAlpha <= 0.01f);
    }

    public float getEaseProgressForRender() {
        return getEaseProgress();
    }

    public float getJournalSlideOffset() {
        return (1f - getEaseProgress()) * 200f;
    }

    public boolean isJournalClosing() {
        return isClosing;
    }

    public void updateAndRenderJournalTooltip(GuiGraphics g, int mouseX, int mouseY) {
        boolean hasCustom = hoveredCustomTooltip != null && !hoveredCustomTooltip.isEmpty();
        boolean hasItem = hoveredRewardTooltip != null;
        boolean isHoveringValid = (hasCustom || hasItem) && !ArcQuestIntelPanelElement.isActive() && !ArcQuestOfferPanelElement.isActive() && !ArcQuestHistoryPanelElement.isActive() && !ArcQuestStoryPanelElement.isActive();

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
            // 纯 2D 自定义文本框
            if (activeCustomTooltip != null && !activeCustomTooltip.isEmpty())
                renderTooltipLines(g, activeCustomTooltip, mouseX, mouseY);
                // 3D 原生物品提示框
            else if (activeTooltipStack != null) renderTooltip(g, activeTooltipStack, mouseX, mouseY);
        } else {
            animTipW = 0;
            activeTooltipStack = null;
            activeCustomTooltip = null;
        }
    }

    public void renderTooltip(GuiGraphics g, ItemStack stack, int mouseX, int mouseY) {
        if (this.minecraft == null || this.minecraft.player == null) return;
        List<Component> lines = stack.getTooltipLines(this.minecraft.player, this.minecraft.options.advancedItemTooltips ? TooltipFlag.Default.ADVANCED : TooltipFlag.Default.NORMAL);
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
            animTipX = targetX;
            animTipY = targetY;
            animTipW = targetW;
            animTipH = targetH;
        } else {
            float morphSpeed = 18f;
            animTipX += (targetX - animTipX) * Math.min(1f, dt * morphSpeed);
            animTipY += (targetY - animTipY) * Math.min(1f, dt * morphSpeed);
            animTipW += (targetW - animTipW) * Math.min(1f, dt * morphSpeed);
            animTipH += (targetH - animTipH) * Math.min(1f, dt * morphSpeed);
        }

        float scale = isClosing ? ArcAnimClock.easeInCubic(tooltipTipAlpha) : (tooltipTipAlpha == 0 ? 1f : ArcAnimClock.easeOutCubic(tooltipTipAlpha));
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

        g.fill(drawX + cyberEdgeWidth, drawY, drawX + drawW, drawY + drawH, ArcDrawUtil.withAlpha(0x000000, bgAlpha));
        g.fill(drawX + cyberEdgeWidth, drawY, drawX + drawW, drawY + 1, ArcDrawUtil.withAlpha(0xCCCCCC, borderAlpha));
        g.fill(drawX + cyberEdgeWidth, drawY + drawH - 1, drawX + drawW, drawY + drawH, ArcDrawUtil.withAlpha(0xCCCCCC, borderAlpha));
        g.fill(drawX + drawW - 1, drawY, drawX + drawW, drawY + drawH, ArcDrawUtil.withAlpha(0xCCCCCC, borderAlpha));

        ArcDrawUtil.drawCyberneticEdge(g, drawX, drawY, drawH, currentThemeColor, edgeAlpha, 3);
        enableScissor(g, drawX, drawY, drawX + drawW, drawY + drawH);

        int textX = drawX + cyberEdgeWidth + padding + 1, textY = drawY + padding;
        for (Component line : layout.lines) {
            g.drawString(font, line, textX, textY, ArcDrawUtil.withAlpha(0xFFFFFF, edgeAlpha), true);
            textY += font.lineHeight;
        }
        g.disableScissor();
        g.pose().popPose();
    }

    public Font getFont() {
        return this.font;
    }

    public Component getTitleComponent() {
        return this.title;
    }

    public JournalTabPanel getTabPanel() {
        return tabPanel;
    }

    public JournalListPanel getListPanel() {
        return listPanel;
    }

    public JournalDetailPanel getDetailPanel() {
        return detailPanel;
    }

    public float getEffectiveAlpha() {
        return this.effectiveAlpha;
    }

    public float getDt() {
        return this.dt;
    }

    public int getThemeColor() {
        return currentTab == JournalTypes.Tab.ACTIVE ? JournalConstants.THEME_ACTIVE : currentTab == JournalTypes.Tab.COMPLETED ? JournalConstants.THEME_COMPLETED : JournalConstants.THEME_FAILED;
    }

    public JournalTypes.Tab getCurrentTab() {
        return currentTab;
    }

    public void setCurrentTab(JournalTypes.Tab tab) {
        this.currentTab = tab;
        rebuildEntries();
    }

    public List<JournalTypes.QuestListEntry> getCurrentEntries() {
        return currentEntries;
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    public void onEntrySelected(int idx) {
        if (this.selectedIndex != idx) {
            this.selectedIndex = idx;
            this.detailPanel.resetState();
            playClick();
        }
    }

    public void playClick() {
        if (minecraft != null)
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    public void setHoveredRewardTooltip(ItemStack stack) {
        this.hoveredRewardTooltip = stack;
    }

    public void setHoveredCustomTooltip(List<Component> lines) {
        this.hoveredCustomTooltip = lines;
    }

    public int getCurrentThemeColor() {
        return this.currentThemeColor;
    }

    public void setCurrentThemeColor(int color) {
        this.currentThemeColor = color;
    }

    private static class TooltipLayoutCache {
        List<Component> lines = List.of();
        int textMaxWidth = 0;
        int targetW = 0;
        int targetH = 0;
    }
}
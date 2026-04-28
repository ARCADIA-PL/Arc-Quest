package org.com.arc_quest.client.gui.quest.journal;

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
import org.com.arc_quest.client.events.ClientEventHandler;
import org.com.arc_quest.client.gui.HudAnimUtil;
import org.com.arc_quest.client.gui.quest.journal.detail.JournalDetailPanel;
import org.com.arc_quest.client.gui.quest.offer.QuestOfferPanel;
import org.com.arc_quest.client.gui.render.QuestIntelPanel;
import org.com.arc_quest.client.gui.render.QuestSplashRenderer;
import org.com.arc_quest.quest.api.QuestDefinition;
import org.com.arc_quest.quest.api.QuestState;
import org.com.arc_quest.quest.capability.QuestRuntimeData;
import org.com.arc_quest.quest.network.ClientQuestCache;
import org.com.arc_quest.quest.registry.QuestRegistry;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.com.arc_quest.client.gui.HudRenderUtil.drawCyberneticEdge;

public class QuestJournalScreen extends Screen {

    private final JournalTabPanel tabPanel;
    private final JournalListPanel listPanel;
    private final JournalDetailPanel detailPanel;

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
    private ItemStack activeTooltipStack = null;
    private float tooltipHoverTimer = 0f;
    private float tooltipTipAlpha = 0f;
    private float animTipX = 0, animTipY = 0, animTipW = 0, animTipH = 0;
    private static final float TIP_HOVER_DELAY = 0.05f;

    public QuestJournalScreen() {
        super(Component.translatable("gui.arc_quest.journal.title"));
        this.tabPanel = new JournalTabPanel(this);
        this.listPanel = new JournalListPanel(this);
        this.detailPanel = new JournalDetailPanel(this);
    }

    @Override
    protected void init() {
        super.init();
        this.lastRenderTime = 0;
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
                        if (lastActivePhases.size() != currentPhases.size() || !lastActivePhases.containsAll(currentPhases)) {
                            phasesChanged = true;
                        }
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
        // 全面阻断键盘事件透传
        if (QuestIntelPanel.isActive()) {
            if (keyCode == 256 || minecraft.options.keyInventory.matches(keyCode, scanCode)) { QuestIntelPanel.dismiss(); return true; }
            return true;
        }
        if (QuestOfferPanel.isActive()) {
            QuestOfferPanel.keyPressed(keyCode);
            return true;
        }
        if (ClientEventHandler.KEY_OPEN_JOURNAL.matches(keyCode, scanCode)) { this.onClose(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() { if (!isClosing) isClosing = true; }
    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // 全面阻断鼠标点击透传
        if (QuestIntelPanel.isActive()) {
            QuestIntelPanel.handleMouseClick(mx, my, this.width, this.height);
            return true;
        }
        if (QuestOfferPanel.isActive()) {
            QuestOfferPanel.mouseClicked(mx, my, button);
            return true;
        }

        if (isClosing || button != 0) return super.mouseClicked(mx, my, button);

        float slideOffset = (1f - getEaseProgress()) * 200f;
        int listX = JournalConstants.LIST_MARGIN - (int) slideOffset;
        int listY = 38 + JournalConstants.TAB_HEIGHT + 6;
        int listH = this.height - 20 - listY;
        int detailX = JournalConstants.LIST_MARGIN + JournalConstants.LIST_WIDTH + JournalConstants.DETAIL_MARGIN + (int) slideOffset;
        int detailW = this.width - detailX - JournalConstants.DETAIL_MARGIN;

        if (detailPanel.mouseClicked(mx, my, detailX, listY, detailW, listH)) return true;
        if (listPanel.mouseClicked(mx, my, listX, listY, JournalConstants.LIST_WIDTH, listH)) return true;
        if (tabPanel.mouseClicked(mx, my, listX)) return true;

        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dragX, double dragY) {
        // 全面阻断拖拽透传
        if (QuestIntelPanel.isActive() || QuestOfferPanel.isActive()) return true;

        int listY = 38 + JournalConstants.TAB_HEIGHT + 6;
        int listH = this.height - 20 - listY;
        if (listPanel.mouseDragged(mx, my, listY, listH)) return true;
        if (detailPanel.mouseDragged(mx, my, listY, listH)) return true;
        return super.mouseDragged(mx, my, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        // 全面阻断释放透传
        if (QuestIntelPanel.isActive() || QuestOfferPanel.isActive()) return true;

        listPanel.mouseReleased(button);
        detailPanel.mouseReleased(button);
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        // 全面阻断滚轮透传
        if (QuestIntelPanel.isActive() || QuestOfferPanel.isActive()) return true;

        if (isClosing) return false;

        float slideOffset = (1f - getEaseProgress()) * 200f;
        int listX = JournalConstants.LIST_MARGIN - (int) slideOffset;
        int listY = 38 + JournalConstants.TAB_HEIGHT + 6;
        int listH = this.height - 20 - listY;
        int detailX = JournalConstants.LIST_MARGIN + JournalConstants.LIST_WIDTH + JournalConstants.DETAIL_MARGIN + (int) slideOffset;
        int detailW = this.width - detailX - JournalConstants.DETAIL_MARGIN;

        if (listPanel.mouseScrolled(mx, my, delta, listX, listY, JournalConstants.LIST_WIDTH, listH)) return true;
        if (detailPanel.mouseScrolled(mx, my, delta, detailX, listY, detailW, listH)) return true;

        return super.mouseScrolled(mx, my, delta);
    }

    private float getEaseProgress() {
        return (isClosing ? HudAnimUtil.easeInCubic(transitionAlpha) : HudAnimUtil.easeOutCubic(transitionAlpha)) * HudAnimUtil.easeOutCubic(suspendAlpha);
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.hoveredRewardTooltip = null;

        long now = Util.getMillis();
        if (lastRenderTime == 0) lastRenderTime = now;
        float realDt = (now - lastRenderTime) / 1000f;
        lastRenderTime = now;
        if (realDt > 0.1f) realDt = 0.1f;

        boolean intelActive = QuestIntelPanel.isActive();
        boolean offerActive = QuestOfferPanel.isActive();

        if (QuestSplashRenderer.isActive()) {
            suspendAlpha = Math.max(0f, suspendAlpha - realDt * 6f);
            dt = 0f;
        } else {
            suspendAlpha = Math.min(1f, suspendAlpha + realDt * 4f);
            dt = intelActive ? 0f : realDt;
        }

        transitionAlpha = HudAnimUtil.lerp(transitionAlpha, isClosing ? 0f : 1f, isClosing ? 0.2f : 0.12f, realDt);
        if (isClosing && transitionAlpha <= 0.01f) {
            if (minecraft != null) minecraft.setScreen(null);
            return;
        }

        effectiveAlpha = transitionAlpha * suspendAlpha;
        float easeProgress = getEaseProgress();
        float slideOffset = (1f - easeProgress) * 200f;
        int safeAlpha = (int) (255 * effectiveAlpha);

        int bgTint = HudAnimUtil.lerpColor(0x000000, currentThemeColor, 0.05f);
        g.fill(0, 0, this.width, this.height, HudAnimUtil.withAlpha(bgTint, (int) (180 * effectiveAlpha)));

        if (safeAlpha > 8) {
            g.pose().pushPose(); g.pose().translate(this.width / 2f, 14, 0);
            float titleScale = 0.95f + 0.05f * easeProgress;
            g.pose().scale(titleScale, titleScale, 1f); g.pose().translate(-this.width / 2f, -14, 0);
            g.drawCenteredString(font, this.title, this.width / 2, 14, HudAnimUtil.withAlpha(0xFFFFFF, safeAlpha));
            g.pose().popPose();
        }

        int theme = getThemeColor();
        tabPanel.render(g, mouseX, mouseY, safeAlpha, slideOffset, theme, dt);

        int listX = JournalConstants.LIST_MARGIN - (int) slideOffset;
        int listY = 38 + JournalConstants.TAB_HEIGHT + 6;
        int listH = this.height - 20 - listY;

        HudAnimUtil.drawFrame(g, listX, listY, JournalConstants.LIST_WIDTH, listH,
                HudAnimUtil.withAlpha(0x000000, (int) (0x55 * effectiveAlpha)),
                HudAnimUtil.withAlpha(theme, (int) (0x55 * effectiveAlpha)));
        listPanel.render(g, listX, listY, JournalConstants.LIST_WIDTH, listH, mouseX, mouseY, theme, dt);

        int detailX = JournalConstants.LIST_MARGIN + JournalConstants.LIST_WIDTH + JournalConstants.DETAIL_MARGIN + (int) slideOffset;
        int detailW = this.width - detailX - JournalConstants.DETAIL_MARGIN;

        HudAnimUtil.drawFrame(g, detailX, listY, detailW, listH,
                HudAnimUtil.withAlpha(0x000000, (int) (0x44 * effectiveAlpha)),
                HudAnimUtil.withAlpha(currentThemeColor, (int) (0x55 * effectiveAlpha)));
        detailPanel.render(g, detailX, listY, detailW, listH, mouseX, mouseY, theme, dt);

        updateAndRenderTooltip(g, mouseX, mouseY);

        if (intelActive) {
            QuestIntelPanel.render(g, this.width, this.height, partialTick);
        }

        if (offerActive) {
            QuestOfferPanel.render(g, mouseX, mouseY, partialTick);
        }
    }

    private void updateAndRenderTooltip(GuiGraphics g, int mouseX, int mouseY) {
        // 核心修复：模态面板开启时，彻底屏蔽底层 Tooltip 渲染
        boolean isHoveringValid = hoveredRewardTooltip != null && !QuestIntelPanel.isActive() && !QuestOfferPanel.isActive();

        if (isHoveringValid) {
            if (activeTooltipStack == null || !ItemStack.matches(activeTooltipStack, hoveredRewardTooltip)) {
                if (tooltipTipAlpha > 0.5f) { tooltipHoverTimer = TIP_HOVER_DELAY; activeTooltipStack = hoveredRewardTooltip; }
                else tooltipHoverTimer += dt;
            } else tooltipHoverTimer += dt;
            if (tooltipHoverTimer >= TIP_HOVER_DELAY) activeTooltipStack = hoveredRewardTooltip;
        } else tooltipHoverTimer = 0f;

        float targetTipAlpha = (isHoveringValid && tooltipHoverTimer >= TIP_HOVER_DELAY && !isClosing) ? 1f : 0f;
        tooltipTipAlpha += (targetTipAlpha - tooltipTipAlpha) * Math.min(1f, dt * 15f);

        if (tooltipTipAlpha > 0.02f && activeTooltipStack != null) {
            renderTooltip(g, activeTooltipStack, mouseX, mouseY);
        } else {
            animTipW = 0;
            activeTooltipStack = null;
        }
    }

    public void renderTooltip(GuiGraphics g, ItemStack stack, int mouseX, int mouseY) {
        if (this.minecraft == null || this.minecraft.player == null) return;
        List<Component> tooltipLines = stack.getTooltipLines(this.minecraft.player, this.minecraft.options.advancedItemTooltips ? TooltipFlag.Default.ADVANCED : TooltipFlag.Default.NORMAL);
        if (tooltipLines.isEmpty()) return;

        int padding = 6, cyberEdgeWidth = 3, textMaxWidth = 0;
        for (Component line : tooltipLines) { int lineWidth = font.width(line); if (lineWidth > textMaxWidth) textMaxWidth = lineWidth; }

        int targetW = textMaxWidth + padding * 2 + cyberEdgeWidth + 2;
        int targetH = tooltipLines.size() * font.lineHeight + padding * 2;
        int targetX = mouseX + 12, targetY = mouseY - 12;

        if (targetX + targetW > this.width) targetX = mouseX - targetW - 8;
        if (targetY + targetH > this.height) targetY = this.height - targetH - 2;
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

        float scale = isClosing ? HudAnimUtil.easeInCubic(tooltipTipAlpha) : HudAnimUtil.easeOutCubic(tooltipTipAlpha);
        if (scale < 0.01f) return;

        int drawX = (int) animTipX, drawY = (int) animTipY, drawW = (int) animTipW, drawH = (int) animTipH;
        float finalTipAlpha = tooltipTipAlpha * effectiveAlpha;
        int bgAlpha = (int) (0xD0 * finalTipAlpha), borderAlpha = (int) (0x66 * finalTipAlpha), edgeAlpha = (int) (255 * finalTipAlpha);

        g.pose().pushPose(); g.pose().translate(0, 0, 400);
        float centerX = drawX + drawW / 2f, centerY = drawY + drawH / 2f;
        g.pose().translate(centerX, centerY, 0); g.pose().scale(scale, scale, 1f); g.pose().translate(-centerX, -centerY, 0);

        g.fill(drawX + cyberEdgeWidth, drawY, drawX + drawW, drawY + drawH, HudAnimUtil.withAlpha(0x000000, bgAlpha));
        g.fill(drawX + cyberEdgeWidth, drawY, drawX + drawW, drawY + 1, HudAnimUtil.withAlpha(0xCCCCCC, borderAlpha));
        g.fill(drawX + cyberEdgeWidth, drawY + drawH - 1, drawX + drawW, drawY + drawH, HudAnimUtil.withAlpha(0xCCCCCC, borderAlpha));
        g.fill(drawX + drawW - 1, drawY, drawX + drawW, drawY + drawH, HudAnimUtil.withAlpha(0xCCCCCC, borderAlpha));

        drawCyberneticEdge(g, drawX, drawY, drawH, currentThemeColor, edgeAlpha);
        g.enableScissor(drawX, drawY, drawX + drawW, drawY + drawH);

        int textX = drawX + cyberEdgeWidth + padding + 1, textY = drawY + padding;
        for (Component line : tooltipLines) {
            g.drawString(font, line, textX, textY, HudAnimUtil.withAlpha(0xFFFFFF, edgeAlpha), true);
            textY += font.lineHeight;
        }

        g.disableScissor();
        g.pose().popPose();
    }

    public Font getFont() { return this.font; }
    public float getEffectiveAlpha() { return this.effectiveAlpha; }
    public float getDt() { return this.dt; }
    public int getThemeColor() { return currentTab == JournalTypes.Tab.ACTIVE ? JournalConstants.THEME_ACTIVE : currentTab == JournalTypes.Tab.COMPLETED ? JournalConstants.THEME_COMPLETED : JournalConstants.THEME_FAILED; }
    public JournalTypes.Tab getCurrentTab() { return currentTab; }
    public void setCurrentTab(JournalTypes.Tab tab) { this.currentTab = tab; rebuildEntries(); }
    public List<JournalTypes.QuestListEntry> getCurrentEntries() { return currentEntries; }
    public int getSelectedIndex() { return selectedIndex; }
    public void onEntrySelected(int idx) { if (this.selectedIndex != idx) { this.selectedIndex = idx; this.detailPanel.resetState(); playClick(); } }
    public void playClick() { if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F)); }
    public void setHoveredRewardTooltip(ItemStack stack) { this.hoveredRewardTooltip = stack; }
    public void setCurrentThemeColor(int color) { this.currentThemeColor = color; }
    public int getCurrentThemeColor() { return this.currentThemeColor; }
    public void executeNetworkAction(Runnable action) { if (minecraft != null) minecraft.execute(action); }
}
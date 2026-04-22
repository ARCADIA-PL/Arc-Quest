package org.com.arc_quest.client.gui.quest;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import org.com.arc_quest.client.events.ClientEventHandler;
import org.com.arc_quest.client.gui.HudAnimUtil;
import org.com.arc_quest.client.gui.HudRenderUtil;
import org.com.arc_quest.client.gui.QuestHudOverlay;
import org.com.arc_quest.client.gui.render.QuestIconRenderer;
import org.com.arc_quest.client.gui.render.QuestSplashRenderer;
import org.com.arc_quest.quest.api.*;
import org.com.arc_quest.quest.capability.QuestRuntimeData;
import org.com.arc_quest.quest.network.ArcQuestNetwork;
import org.com.arc_quest.quest.network.C2SRequestQuestActionPacket;
import org.com.arc_quest.quest.network.ClientQuestCache;
import org.com.arc_quest.quest.registry.QuestRegistry;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class QuestJournalScreen extends Screen {

    private static final int THEME_ACTIVE = 0x4FC3F7;
    private static final int THEME_COMPLETED = 0x66FF66;
    private static final int THEME_FAILED = 0xFF6666;

    private static final int LIST_WIDTH = 205;
    private static final int LIST_MARGIN = 16;
    private static final int DETAIL_MARGIN = 12;
    private static final int ENTRY_HEIGHT = 24;
    private static final int TAB_HEIGHT = 22;
    private final List<QuestListEntry> currentEntries = new ArrayList<>();
    private final List<ChoiceButtonRect> currentChoiceButtons = new ArrayList<>();
    private float transitionAlpha = 0f;
    private boolean isClosing = false;
    private long lastRenderTime = 0;
    private float dt = 0f;
    private Tab currentTab = Tab.ACTIVE;
    private float tabSlideAnim = 0f;
    private float tabWidthAnim = 0f;
    // 挂起动画与最终有效透明度
    private float suspendAlpha = 1.0f;
    private float effectiveAlpha = 0f;
    private int selectedIndex = -1;
    private float selectedSlide = -1f;
    private float[] entryHoverAnim = new float[0];
    private double scrollOffset = 0;
    private double targetScroll = 0;
    private boolean isDraggingListScrollbar = false;
    private double dragListYOffset = 0;
    private double detailScrollOffset = 0;
    private double detailTargetScroll = 0;
    private boolean isDraggingDetailScrollbar = false;
    private double dragDetailYOffset = 0;
    private int detailContentHeight = 0;
    private float detailReveal = 0f;
    private float[] detailObjReveal = new float[0];
    private float trackBtnHover = 0f;
    private float abandonBtnHover = 0f;
    private float failedRestartBtnHover = 0f;

    public QuestJournalScreen() {
        super(Component.translatable("gui.arc_quest.journal.title"));
    }

    @Override
    protected void init() {
        super.init();
        this.lastRenderTime = 0;
        rebuildEntries();
        if (!currentEntries.isEmpty()) {
            selectedIndex = 0;
        }
    }

    private void rebuildEntries() {
        currentEntries.clear();
        switch (currentTab) {
            case ACTIVE -> {
                for (Map.Entry<String, QuestRuntimeData> e : ClientQuestCache.INSTANCE.getAllActiveQuests().entrySet()) {
                    String name = ClientQuestCache.INSTANCE.getQuestDisplayName(e.getKey());
                    ResourceLocation questRl = ResourceLocation.tryParse(e.getKey());
                    QuestDefinition def = questRl != null ? QuestRegistry.get(questRl) : null;
                    currentEntries.add(new QuestListEntry(e.getKey(), name, QuestState.ACTIVE, def));
                }
            }
            case COMPLETED -> {
                for (String id : ClientQuestCache.INSTANCE.getCompletedQuests()) {
                    String name = ClientQuestCache.INSTANCE.getQuestDisplayName(id);
                    ResourceLocation questRl = ResourceLocation.tryParse(id);
                    QuestDefinition def = questRl != null ? QuestRegistry.get(questRl) : null;
                    currentEntries.add(new QuestListEntry(id, name, QuestState.COMPLETED, def));
                }
            }
            case FAILED -> {
                for (String id : ClientQuestCache.INSTANCE.getFailedQuests()) {
                    String name = ClientQuestCache.INSTANCE.getQuestDisplayName(id);
                    ResourceLocation questRl = ResourceLocation.tryParse(id);
                    QuestDefinition def = questRl != null ? QuestRegistry.get(questRl) : null;
                    currentEntries.add(new QuestListEntry(id, name, QuestState.FAILED, def));
                }
            }
        }
        selectedIndex = currentEntries.isEmpty() ? -1 : 0;
        entryHoverAnim = new float[currentEntries.size()];
        selectedSlide = selectedIndex;
        targetScroll = 0;
        scrollOffset = 0;
        resetDetailState();
    }

    private void resetDetailState() {
        detailReveal = 0f;
        detailObjReveal = new float[0];
        detailTargetScroll = 0;
        detailScrollOffset = 0;
        currentChoiceButtons.clear();
    }

    private float lerp(float c, float t, float s) {
        return HudAnimUtil.lerp(c, t, s, dt);
    }

    private float step(float c, float t, float s) {
        return HudAnimUtil.step(c, t, s, dt);
    }

    private int themeColor() {
        return currentTab == Tab.ACTIVE ? THEME_ACTIVE : currentTab == Tab.COMPLETED ? THEME_COMPLETED : THEME_FAILED;
    }

    private String getTabLabel(Tab tab) {
        return Component.translatable(switch (tab) {
            case ACTIVE -> "arc_quest.gui.journal.tab.active";
            case COMPLETED -> "arc_quest.gui.journal.tab.completed";
            case FAILED -> "arc_quest.gui.journal.tab.failed";
        }).getString();
    }

    private boolean shouldShowBranchChoices(QuestDefinition def, QuestRuntimeData runtime) {
        if (def == null || runtime == null) return false;
        // 使用缓存层获取当前阶段
        PhaseDefinition currentPhase = ClientQuestCache.INSTANCE.getCurrentPhase(runtime.getQuestId());
        if (currentPhase == null || !currentPhase.hasChoices()) return false;
        int[] progress = runtime.getAllProgress();
        for (int i = 0; i < currentPhase.getObjectives().size(); i++) {
            if (i >= progress.length || progress[i] < currentPhase.getObjectives().get(i).getRequiredCount())
                return false;
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
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
    public boolean mouseClicked(double mx, double my, int button) {
        if (isClosing || button != 0) return super.mouseClicked(mx, my, button);

        float easeProgress = getEaseProgress();
        float slideOffset = (1f - easeProgress) * 200f;

        int listX = LIST_MARGIN - (int) slideOffset;
        int listY = 38 + TAB_HEIGHT + 6;
        int listBottom = this.height - 20;
        int listH = listBottom - listY;

        int detailX = LIST_MARGIN + LIST_WIDTH + DETAIL_MARGIN + (int) slideOffset;
        int detailW = this.width - detailX - DETAIL_MARGIN;
        int detailY = listY;
        int detailH = listH;

        int maxListScroll = Math.max(0, currentEntries.size() * ENTRY_HEIGHT - listH);
        int listScrollbarX = listX + LIST_WIDTH - 6;
        if (maxListScroll > 0 && mx >= listScrollbarX && mx <= listScrollbarX + 6 && my >= listY && my <= listBottom) {
            isDraggingListScrollbar = true;
            int thumbH = Math.max(16, (int) (((float) listH / (currentEntries.size() * ENTRY_HEIGHT)) * listH));
            int thumbY = listY + (int) ((scrollOffset / maxListScroll) * (listH - thumbH));
            if (my >= thumbY && my <= thumbY + thumbH) {
                dragListYOffset = my - thumbY;
            } else {
                dragListYOffset = thumbH / 2.0;
                updateListScrollFromMouse(my, listY, listH, maxListScroll);
            }
            return true;
        }

        int scrollAreaH = detailH - 40;
        int maxDetailScroll = Math.max(0, detailContentHeight - scrollAreaH);
        int detailScrollbarX = detailX + detailW - 6;
        if (maxDetailScroll > 0 && mx >= detailScrollbarX && mx <= detailScrollbarX + 6 && my >= detailY && my <= detailY + scrollAreaH) {
            isDraggingDetailScrollbar = true;
            int thumbH = Math.max(16, (int) (((float) scrollAreaH / detailContentHeight) * scrollAreaH));
            int thumbY = detailY + (int) ((detailScrollOffset / maxDetailScroll) * (scrollAreaH - thumbH));
            if (my >= thumbY && my <= thumbY + thumbH) {
                dragDetailYOffset = my - thumbY;
            } else {
                dragDetailYOffset = thumbH / 2.0;
                updateDetailScrollFromMouse(my, detailY, scrollAreaH, maxDetailScroll);
            }
            return true;
        }

        int tabY = 38, tabBaseX = LIST_MARGIN - (int) slideOffset;
        for (Tab tab : Tab.values()) {
            int tw = font.width(getTabLabel(tab)) + 16;
            if (mx >= tabBaseX && mx <= tabBaseX + tw && my >= tabY && my <= tabY + TAB_HEIGHT) {
                if (currentTab != tab) {
                    currentTab = tab;
                    rebuildEntries();
                    playClick();
                }
                return true;
            }
            tabBaseX += tw + 4;
        }

        int btnW = 90, btnH = 20, btnY = detailY + detailH - btnH - 8;
        int trackX = detailX + detailW - btnW - 8, abanX = trackX - btnW - 12;

        if (currentTab == Tab.ACTIVE && selectedIndex >= 0) {
            if (mx >= trackX && mx <= trackX + btnW && my >= btnY && my <= btnY + btnH) {
                QuestHudOverlay.INSTANCE.setTrackedQuest(currentEntries.get(selectedIndex).questId());
                playClick();
                return true;
            }
            if (mx >= abanX && mx <= abanX + btnW && my >= btnY && my <= btnY + btnH) {
                ArcQuestNetwork.sendQuestAction(C2SRequestQuestActionPacket.abandon(currentEntries.get(selectedIndex).questId()));
                playClick();
                return true;
            }
        }
        if (currentTab == Tab.FAILED && selectedIndex >= 0) {
            int restartBtnW = 120, restartBtnX = detailX + detailW - restartBtnW - 8;
            if (mx >= restartBtnX && mx <= restartBtnX + restartBtnW && my >= btnY && my <= btnY + btnH) {
                String qid = currentEntries.get(selectedIndex).questId();
                ArcQuestNetwork.sendQuestAction(C2SRequestQuestActionPacket.abandon(qid));
                if (minecraft != null)
                    minecraft.execute(() -> ArcQuestNetwork.sendQuestAction(C2SRequestQuestActionPacket.accept(qid)));
                playClick();
                return true;
            }
        }

        if (currentTab == Tab.ACTIVE && !currentChoiceButtons.isEmpty()) {
            for (ChoiceButtonRect rect : currentChoiceButtons) {
                if (mx >= rect.x && mx <= rect.x + rect.w && my >= rect.y && my <= rect.y + rect.h) {
                    if (my >= detailY && my <= detailY + scrollAreaH) {
                        ArcQuestNetwork.sendQuestAction(C2SRequestQuestActionPacket.choose(currentEntries.get(selectedIndex).questId(), rect.choiceIndex));
                        QuestHudOverlay.INSTANCE.clearBranchChoiceToast();
                        playClick();
                        return true;
                    }
                }
            }
        }

        if (mx >= listX && mx <= listX + LIST_WIDTH - 6 && my >= listY && my <= listBottom) {
            double relY = my - listY + scrollOffset;
            int idx = (int) (relY / ENTRY_HEIGHT);
            if (idx >= 0 && idx < currentEntries.size()) {
                if (selectedIndex != idx) {
                    selectedIndex = idx;
                    resetDetailState();
                    playClick();
                }
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dragX, double dragY) {
        if (isDraggingListScrollbar) {
            int listH = this.height - 20 - (38 + TAB_HEIGHT + 6);
            updateListScrollFromMouse(my, 38 + TAB_HEIGHT + 6, listH, Math.max(0, currentEntries.size() * ENTRY_HEIGHT - listH));
            return true;
        }
        if (isDraggingDetailScrollbar) {
            int detailY = 38 + TAB_HEIGHT + 6;
            int scrollAreaH = (this.height - 20 - detailY) - 40;
            updateDetailScrollFromMouse(my, detailY, scrollAreaH, Math.max(0, detailContentHeight - scrollAreaH));
            return true;
        }
        return super.mouseDragged(mx, my, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0) {
            isDraggingListScrollbar = false;
            isDraggingDetailScrollbar = false;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (isClosing) return false;
        float slideOffset = (1f - getEaseProgress()) * 200f;
        int listX = LIST_MARGIN - (int) slideOffset;
        int listY = 38 + TAB_HEIGHT + 6;
        int listBottom = this.height - 20;

        int detailX = LIST_MARGIN + LIST_WIDTH + DETAIL_MARGIN + (int) slideOffset;
        int detailW = this.width - detailX - DETAIL_MARGIN;

        if (mx >= listX && mx <= listX + LIST_WIDTH && my >= listY && my <= listBottom) {
            targetScroll -= delta * ENTRY_HEIGHT;
            clampScrolls();
            return true;
        }
        if (mx >= detailX && mx <= detailX + detailW && my >= listY && my <= listBottom) {
            detailTargetScroll -= delta * 25.0;
            clampScrolls();
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    private void updateListScrollFromMouse(double my, int y0, int viewH, int maxScroll) {
        if (maxScroll <= 0) return;
        int thumbH = Math.max(16, (int) (((float) viewH / (currentEntries.size() * ENTRY_HEIGHT)) * viewH));
        targetScroll = Math.max(0.0, Math.min(1.0, (my - y0 - dragListYOffset) / (viewH - thumbH))) * maxScroll;
    }

    private void updateDetailScrollFromMouse(double my, int y0, int viewH, int maxScroll) {
        if (maxScroll <= 0) return;
        int thumbH = Math.max(16, (int) (((float) viewH / detailContentHeight) * viewH));
        detailTargetScroll = Math.max(0.0, Math.min(1.0, (my - y0 - dragDetailYOffset) / (viewH - thumbH))) * maxScroll;
    }

    private void clampScrolls() {
        int listH = this.height - 20 - (38 + TAB_HEIGHT + 6);
        targetScroll = Math.max(0, Math.min(targetScroll, Math.max(0, currentEntries.size() * ENTRY_HEIGHT - listH)));
        detailTargetScroll = Math.max(0, Math.min(detailTargetScroll, Math.max(0, detailContentHeight - (listH - 40))));
    }

    private void playClick() {
        if (minecraft != null)
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private float getEaseProgress() {
        return (isClosing ? HudAnimUtil.easeInCubic(transitionAlpha) : HudAnimUtil.easeOutCubic(transitionAlpha))
                * HudAnimUtil.easeOutCubic(suspendAlpha);
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long now = Util.getMillis();
        if (lastRenderTime == 0) lastRenderTime = now;
        float realDt = (now - lastRenderTime) / 1000f;
        lastRenderTime = now;
        if (realDt > 0.1f) realDt = 0.1f;

        if (QuestSplashRenderer.isActive()) {
            suspendAlpha = Math.max(0f, suspendAlpha - realDt * 6f);
            dt = 0f;
        } else {
            suspendAlpha = Math.min(1f, suspendAlpha + realDt * 4f);
            dt = realDt;
        }

        transitionAlpha = lerp(transitionAlpha, isClosing ? 0f : 1f, isClosing ? 0.2f : 0.12f);
        if (isClosing && transitionAlpha <= 0.01f) {
            if (minecraft != null) minecraft.setScreen(null);
            return;
        }

        // 结合界面开启/关闭动画与立绘避让动画，得到最终渲染Alpha
        effectiveAlpha = transitionAlpha * suspendAlpha;

        clampScrolls();
        scrollOffset += Math.abs(targetScroll - scrollOffset) > 0.5 ? (targetScroll - scrollOffset) * Math.min(1.0, dt * 14.0) : (targetScroll - scrollOffset);
        detailScrollOffset += Math.abs(detailTargetScroll - detailScrollOffset) > 0.5 ? (detailTargetScroll - detailScrollOffset) * Math.min(1.0, dt * 14.0) : (detailTargetScroll - detailScrollOffset);

        float easeProgress = getEaseProgress();
        float slideOffset = (1f - easeProgress) * 200f;

        g.fill(0, 0, this.width, this.height, ((int) (160 * effectiveAlpha) << 24));
        int safeAlpha = (int) (255 * effectiveAlpha);

        if (safeAlpha > 8) {
            g.pose().pushPose();
            g.pose().translate(this.width / 2f, 14, 0);
            float titleScale = 0.95f + 0.05f * easeProgress;
            g.pose().scale(titleScale, titleScale, 1f);
            g.pose().translate(-this.width / 2f, -14, 0);
            g.drawCenteredString(font, this.title, this.width / 2, 14, HudAnimUtil.withAlpha(0xFFFFFF, safeAlpha));
            g.pose().popPose();
        }

        renderTabs(g, mouseX, mouseY, safeAlpha, slideOffset, themeColor());

        int listX = LIST_MARGIN - (int) slideOffset;
        int listY = 38 + TAB_HEIGHT + 6;
        int listH = this.height - 20 - listY;

        HudAnimUtil.drawFrame(g, listX, listY, LIST_WIDTH, listH, HudAnimUtil.withAlpha(0x000000, (int) (0x55 * effectiveAlpha)), HudAnimUtil.withAlpha(0xFFFFFF, (int) (0x44 * effectiveAlpha)));
        renderQuestList(g, listX, listY, LIST_WIDTH, listH, mouseX, mouseY, themeColor());

        int detailX = LIST_MARGIN + LIST_WIDTH + DETAIL_MARGIN + (int) slideOffset;
        int detailW = this.width - detailX - DETAIL_MARGIN;

        HudAnimUtil.drawFrame(g, detailX, listY, detailW, listH, HudAnimUtil.withAlpha(0x000000, (int) (0x55 * effectiveAlpha)), HudAnimUtil.withAlpha(0xFFFFFF, (int) (0x44 * effectiveAlpha)));
        renderDetailMatrixEngine(g, detailX, listY, detailW, listH, mouseX, mouseY, themeColor());
    }

    private void renderTabs(GuiGraphics g, int mx, int my, int safeAlpha, float slide, int theme) {
        int tabY = 38, tabBaseX = LIST_MARGIN - (int) slide;
        float targetTabX = 0, currentTabX = tabBaseX, targetTabW = 0;

        for (Tab tab : Tab.values()) {
            int tw = font.width(getTabLabel(tab)) + 16;
            if (tab == currentTab) {
                targetTabX = currentTabX;
                targetTabW = tw;
            }
            currentTabX += tw + 4;
        }

        if (tabWidthAnim <= 0.1f) {
            tabSlideAnim = targetTabX;
            tabWidthAnim = targetTabW;
        }
        float lerpFactor = Math.min(1.0f, dt * 15f);
        tabSlideAnim += (targetTabX - tabSlideAnim) * lerpFactor;
        tabWidthAnim += (targetTabW - tabWidthAnim) * lerpFactor;

        currentTabX = tabBaseX;
        for (Tab tab : Tab.values()) {
            String label = getTabLabel(tab);
            int tw = font.width(label) + 16;
            boolean hovered = mx >= currentTabX && mx <= currentTabX + tw && my >= tabY && my <= tabY + TAB_HEIGHT;
            int textColor = (tab == currentTab) ? HudAnimUtil.withAlpha(0xFFFFFF, safeAlpha) : hovered ? HudAnimUtil.withAlpha(0xDDDDDD, safeAlpha) : HudAnimUtil.withAlpha(0x888888, safeAlpha);
            if (safeAlpha > 8)
                g.drawString(font, label, (int) currentTabX + 8, tabY + (TAB_HEIGHT - font.lineHeight) / 2, textColor, true);
            currentTabX += tw + 4;
        }

        if ((int) (255 * effectiveAlpha) > 8) {
            g.fill((int) tabSlideAnim, tabY + TAB_HEIGHT - 2, (int) (tabSlideAnim + tabWidthAnim), tabY + TAB_HEIGHT, HudAnimUtil.withAlpha(theme, (int) (255 * effectiveAlpha)));
        }
    }

    private void renderQuestList(GuiGraphics g, int x, int y, int w, int h, int mx, int my, int theme) {
        g.enableScissor(x, y, x + w - 6, y + h);

        if (selectedIndex >= 0) {
            if (selectedSlide < 0) selectedSlide = selectedIndex;
            selectedSlide = lerp(selectedSlide, selectedIndex, 0.25f);
            int hlY = (int) (y + 2 - scrollOffset + selectedSlide * ENTRY_HEIGHT);

            int entryTheme = theme;
            if (selectedIndex < currentEntries.size() && currentEntries.get(selectedIndex).def != null) {
                int defTheme = currentEntries.get(selectedIndex).def.getThemeColor();
                if (defTheme != 0xFFFFFFFF) entryTheme = defTheme;
            }

            g.fill(x + 2, hlY, x + w - 8, hlY + ENTRY_HEIGHT - 2, HudAnimUtil.withAlpha(0xFFFFFF, (int) (0x44 * effectiveAlpha)));
            g.fill(x + 2, hlY, x + 5, hlY + ENTRY_HEIGHT - 2, HudAnimUtil.withAlpha(entryTheme, (int) (0xFF * effectiveAlpha)));
        }

        for (int i = 0; i < currentEntries.size(); i++) {
            QuestListEntry entry = currentEntries.get(i);
            int entryY = (int) (y + 2 - scrollOffset + i * ENTRY_HEIGHT);
            if (entryY + ENTRY_HEIGHT < y || entryY > y + h) {
                if (i < entryHoverAnim.length) entryHoverAnim[i] = 0f;
                continue;
            }

            boolean hovered = mx >= x && mx <= x + w - 8 && my >= entryY && my <= entryY + ENTRY_HEIGHT && my >= y && my <= y + h;
            if (i < entryHoverAnim.length) entryHoverAnim[i] = step(entryHoverAnim[i], hovered ? 1f : 0f, 8f);
            float eHover = HudAnimUtil.easeOutCubic(i < entryHoverAnim.length ? entryHoverAnim[i] : 0f);

            if (i != selectedIndex && eHover > 0.01f) {
                g.fill(x + 2, entryY, x + w - 8, entryY + ENTRY_HEIGHT - 2, HudAnimUtil.withAlpha(0xFFFFFF, (int) (eHover * 0x22 * effectiveAlpha)));
            }

            if (effectiveAlpha > 0.05f) {
                int baseGray = (int) (0xAA + 0x55 * eHover);
                int nameColor = (i == selectedIndex) ? HudAnimUtil.withAlpha(0xFFFFFF, (int) (255 * effectiveAlpha)) : HudAnimUtil.withAlpha((baseGray << 16) | (baseGray << 8) | baseGray, (int) (255 * effectiveAlpha));

                int textOffsetX = 10;
                if (entry.def != null) {
                    entry.def.getVisualConfig().getIcon(IconPosition.QUEST_LIST).ifPresent(icon -> {
                        QuestIconRenderer.renderIcon(g, icon, x + 10, entryY + (ENTRY_HEIGHT - 12) / 2, 12, 12);
                    });
                    if (entry.def.getVisualConfig().getIcon(IconPosition.QUEST_LIST).isPresent()) {
                        textOffsetX = 26;
                    }
                }

                // 智能响应式排版
                int maxDrawWidth = w - textOffsetX - 16;
                String displayName = entry.displayName();
                int textW = font.width(displayName);
                float baseScale = 1f;

                if (textW > maxDrawWidth) {
                    baseScale = Math.max(0.75f, (float) maxDrawWidth / textW);
                    if (font.width(displayName) * baseScale > maxDrawWidth) {
                        int allowedW = (int) (maxDrawWidth / 0.75f) - font.width("...");
                        displayName = font.plainSubstrByWidth(displayName, allowedW) + "...";
                    }
                }

                float finalScale = baseScale * (1f + 0.03f * eHover);

                g.pose().pushPose();
                float textY = entryY + (ENTRY_HEIGHT - font.lineHeight * baseScale) / 2f + 1;
                g.pose().translate(x + textOffsetX, textY, 0);
                g.pose().scale(finalScale, finalScale, 1f);
                g.drawString(font, displayName, 0, 0, nameColor, true);
                g.pose().popPose();
            }
        }
        g.disableScissor();
        renderScrollbar(g, x + w - 6, y + 2, h - 4, currentEntries.size() * ENTRY_HEIGHT, scrollOffset, Math.max(0, currentEntries.size() * ENTRY_HEIGHT - h), isDraggingListScrollbar);
    }

    private void renderDetailMatrixEngine(GuiGraphics g, int x, int y, int w, int h, int mx, int my, int theme) {
        if (selectedIndex < 0 || selectedIndex >= currentEntries.size()) {
            renderEmptyDetail(g, x, y, w, h);
            return;
        }

        QuestListEntry entry = currentEntries.get(selectedIndex);
        QuestDefinition def = entry.def;
        if (def == null) return;

        // 使用缓存层获取主题色
        int activeTheme = ClientQuestCache.INSTANCE.getQuestThemeColor(entry.questId(), theme);

        detailReveal = lerp(detailReveal, 1f, 0.15f);
        float dAlpha = effectiveAlpha * HudAnimUtil.easeOutCubic(Math.min(1f, detailReveal));
        int safeA = (int) (255 * dAlpha);
        if (safeA <= 8) return;

        int scrollAreaY = y;
        int scrollAreaH = h - 40;
        int scrollAreaW = w - 8;

        g.enableScissor(x, scrollAreaY, x + w - 8, scrollAreaY + scrollAreaH);

        def.getSplashConfig(SplashType.QUEST_DETAIL).ifPresent(asset -> {
            RenderSystem.enableBlend();
            float watermarkAlpha = 0.15f * dAlpha;
            int rw = (int) (w * 0.7f);
            int rh = rw;
            int rx = x + w / 2 - rw / 2 + (int) ((1f - detailReveal) * 50f);
            int ry = scrollAreaY + scrollAreaH / 2 - rh / 2;

            RenderSystem.setShaderColor(1f, 1f, 1f, watermarkAlpha);
            QuestIconRenderer.renderIcon(g, asset, rx, ry, rw, rh);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        });

        g.pose().pushPose();
        g.pose().translate(x + 12, scrollAreaY + 12 - detailScrollOffset, 0);

        int localY = 0;

        int titleIconOffset = 0;
        if (def.getVisualConfig().getIcon(IconPosition.QUEST_TITLE).isPresent()) {
            int finalLocalY = localY;
            def.getVisualConfig().getIcon(IconPosition.QUEST_TITLE).ifPresent(icon -> {
                RenderSystem.setShaderColor(1f, 1f, 1f, dAlpha);
                QuestIconRenderer.renderIcon(g, icon, 0, finalLocalY - 2, 16, 16);
                RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            });
            titleIconOffset = 22;
        }

        g.pose().pushPose();
        g.pose().translate(titleIconOffset, localY, 0);
        g.pose().scale(1.2f, 1.2f, 1f);
        g.drawString(font, def.getDisplayName().getString(), 0, 0, HudAnimUtil.withAlpha(0xFFFFFF, safeA), true);
        g.pose().popPose();
        localY += 18;

        if (!def.getDescription().getString().isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(0, localY, 0);
            g.pose().scale(0.85f, 0.85f, 1f);
            // 使用共享工具方法
            List<String> descLines = HudRenderUtil.wrapText(def.getDescription().getString(), (int) ((scrollAreaW - 24) / 0.85f), font);
            for (String line : descLines) {
                g.drawString(font, line, 0, 0, HudAnimUtil.withAlpha(0xAAAAAA, safeA), false);
                g.pose().translate(0, font.lineHeight + 1, 0);
            }
            g.pose().popPose();
            localY += descLines.size() * (int) (font.lineHeight * 0.85f + 1) + 8;
        }

        g.fill(0, localY, scrollAreaW - 24, localY + 1, HudAnimUtil.withAlpha(activeTheme, (int) (120 * dAlpha)));
        localY += 10;

        QuestRuntimeData runtime = ClientQuestCache.INSTANCE.getActiveQuest(entry.questId());

        if (entry.state() == QuestState.ACTIVE && runtime != null) {
            // 使用缓存层获取当前阶段定义
            PhaseDefinition phase = ClientQuestCache.INSTANCE.getCurrentPhase(entry.questId());
            if (phase != null) {
                g.pose().pushPose();
                g.pose().translate(0, localY, 0);
                g.pose().scale(0.8f, 0.8f, 1f);
                String phaseName = phase.getDisplayName() != null && !phase.getDisplayName().getString().isEmpty()
                        ? phase.getDisplayName().getString()
                        : phase.getPhaseId();
                g.drawString(font, Component.translatable("arc_quest.gui.journal.section.current_phase", phaseName).getString(), 0, 0, HudAnimUtil.withAlpha(activeTheme, safeA), true);
                g.pose().popPose();
                localY += 14;

                List<ObjectiveEntry> objs = phase.getObjectives();
                if (detailObjReveal.length != objs.size()) detailObjReveal = new float[objs.size()];
                
                for (int i = 0; i < objs.size(); i++) {
                    detailObjReveal[i] = lerp(detailObjReveal[i], 1f, 0.1f + i * 0.03f);
                    float oAlpha = dAlpha * HudAnimUtil.easeOutCubic(Math.min(1f, detailObjReveal[i]));
                    int oA = (int) (255 * oAlpha);
                    if (oA <= 4) {
                        localY += 22;
                        continue;
                    }
                
                    int objX = (int) ((1f - HudAnimUtil.easeOutCubic(Math.min(1f, detailObjReveal[i]))) * 25f);
                    int progress = runtime.getObjectiveProgress(i), required = objs.get(i).getRequiredCount();
                    boolean complete = progress >= required;
                
                    String objText = (complete ? Component.translatable("arc_quest.gui.journal.label.objective_complete_prefix").getString() : Component.translatable("arc_quest.gui.journal.label.objective_active_prefix").getString()) + objs.get(i).getDisplayText().getString();
                
                    // 使用共享工具方法
                    List<String> wrappedObjLines = HudRenderUtil.wrapText(objText, scrollAreaW - 40 - objX, font);
                    for (String line : wrappedObjLines) {
                        g.drawString(font, line, objX, localY, HudAnimUtil.withAlpha(complete ? 0x88FF88 : 0xDDDDDD, oA), true);
                        localY += font.lineHeight + 1;
                    }

                    int barW = scrollAreaW - 40 - objX;
                    HudAnimUtil.drawProgressBarGlow(g, objX, localY, barW, 3, required > 0 ? (float) progress / required : 0f, HudAnimUtil.withAlpha(0xFFFFFF, (int) (0x30 * oAlpha)), HudAnimUtil.withAlpha(complete ? 0x66FF66 : activeTheme, (int) (0xCC * oAlpha)), HudAnimUtil.withAlpha(0xFFFFFF, (int) (0xFF * oAlpha)));

                    g.pose().pushPose();
                    g.pose().translate(objX + barW + 4, localY - 1, 0);
                    g.pose().scale(0.7f, 0.7f, 1f);
                    g.drawString(font, progress + " / " + required, 0, 0, HudAnimUtil.withAlpha(0x999999, oA), false);
                    g.pose().popPose();
                    localY += 12;
                }

                localY += 6;
                g.fill(0, localY, scrollAreaW - 24, localY + 1, HudAnimUtil.withAlpha(activeTheme, (int) (80 * dAlpha)));
                localY += 10;

                g.pose().pushPose();
                g.pose().translate(0, localY, 0);
                g.pose().scale(0.8f, 0.8f, 1f);
                g.drawString(font, Component.translatable("arc_quest.gui.journal.section.completed_phases").getString(), 0, 0, HudAnimUtil.withAlpha(0xAAAAAA, safeA), true);
                g.pose().popPose();
                localY += 14;

                int completedCount = 0;
                for (String phaseId : def.getPhaseIds()) {
                    if (phaseId.equals(runtime.getCurrentPhaseId())) break;
                    // 使用缓存层获取阶段名称
                    String completedPhaseName = ClientQuestCache.INSTANCE.getPhaseDisplayName(entry.questId(), phaseId);
                    g.pose().pushPose();
                    g.pose().translate(8, localY, 0);
                    g.pose().scale(0.75f, 0.75f, 1f);
                    g.drawString(font, "§a✔ " + completedPhaseName, 0, 0, HudAnimUtil.withAlpha(0x88FF88, (int) (200 * dAlpha)), false);
                    g.pose().popPose();
                    localY += 12;
                    completedCount++;
                }
                if (completedCount == 0) {
                    g.pose().pushPose();
                    g.pose().translate(8, localY, 0);
                    g.pose().scale(0.75f, 0.75f, 1f);
                    g.drawString(font, Component.translatable("arc_quest.gui.journal.label.no_phases_completed").getString(), 0, 0, HudAnimUtil.withAlpha(0x666666, safeA), false);
                    g.pose().popPose();
                    localY += 12;
                }

                if (shouldShowBranchChoices(def, runtime)) {
                    localY += 8;
                    g.fill(0, localY, scrollAreaW - 24, localY + 1, HudAnimUtil.withAlpha(activeTheme, (int) (80 * dAlpha)));
                    localY += 10;

                    g.pose().pushPose();
                    g.pose().translate(0, localY, 0);
                    g.pose().scale(0.8f, 0.8f, 1f);
                    g.drawString(font, Component.translatable("arc_quest.gui.journal.section.choose_path").getString(), 0, 0, HudAnimUtil.withAlpha(0xFFCC66, safeA), true);
                    g.pose().popPose();
                    localY += 14;

                    currentChoiceButtons.clear();
                    // 使用缓存层获取当前阶段的选项
                    List<ChoiceOption> choices = ClientQuestCache.INSTANCE.getCurrentPhase(entry.questId()).getChoices();
                    for (int i = 0; i < choices.size(); i++) {
                        ChoiceOption choice = choices.get(i);
                        boolean isVisible = true;
                        if (choice.getVisibleCondition() != null) {
                            // 使用缓存层提供的便捷方法
                            isVisible = choice.getVisibleCondition().testClient(
                                    ClientQuestCache.INSTANCE.getCompletedQuestsAsRL(),
                                    ClientQuestCache.INSTANCE.getAllFlags(),
                                    ClientQuestCache.INSTANCE.getAllVariables()
                            );
                        }
                        if (!isVisible) continue;

                        int btnW = scrollAreaW - 24, btnH = 22;
                        int absX = x + 12, absY = scrollAreaY + 12 - (int) detailScrollOffset + localY;
                        currentChoiceButtons.add(new ChoiceButtonRect(absX, absY, btnW, btnH, i));

                        boolean isHovered = mx >= absX && mx <= absX + btnW && my >= absY && my <= absY + btnH && my >= scrollAreaY && my <= scrollAreaY + scrollAreaH;

                        int borderColor = isHovered ? activeTheme : 0x666666;
                        int textColor = isHovered ? activeTheme : 0xCCCCCC;

                        g.fill(0, localY, btnW, localY + btnH, HudAnimUtil.withAlpha(0xFFFFFF, (int) ((isHovered ? 0x22 : 0x11) * dAlpha)));
                        g.fill(0, localY, btnW, localY + 1, HudAnimUtil.withAlpha(borderColor, (int) (200 * dAlpha)));
                        g.fill(0, localY + btnH - 1, btnW, localY + btnH, HudAnimUtil.withAlpha(borderColor, (int) (200 * dAlpha)));
                        g.fill(0, localY, 1, localY + btnH, HudAnimUtil.withAlpha(borderColor, (int) (200 * dAlpha)));
                        g.fill(btnW - 1, localY, btnW, localY + btnH, HudAnimUtil.withAlpha(borderColor, (int) (200 * dAlpha)));

                        float textScale = 0.8f;
                        float textH = font.lineHeight * textScale;
                        float textYOffset = (btnH - textH) / 2f;

                        g.pose().pushPose();
                        g.pose().translate(8, localY + textYOffset + 1, 0);
                        g.pose().scale(textScale, textScale, 1f);

                        String safeChoiceText = font.plainSubstrByWidth((i + 1) + ". " + choice.getDisplayText().getString(), (int) ((btnW - 16) / textScale));

                        g.drawString(font, safeChoiceText, 0, 0, HudAnimUtil.withAlpha(textColor, safeA), false);
                        g.pose().popPose();

                        localY += btnH + 5;
                    }
                }
            }
        } else if (entry.state() == QuestState.COMPLETED) {
            g.drawString(font, Component.translatable("arc_quest.gui.journal.label.quest_completed").getString(), 0, localY, HudAnimUtil.withAlpha(0x88FF88, safeA), true);
            localY += 16;
        } else if (entry.state() == QuestState.FAILED) {
            g.drawString(font, Component.translatable("arc_quest.gui.journal.label.quest_failed").getString(), 0, localY, HudAnimUtil.withAlpha(0xFF6666, safeA), true);
            localY += 16;
        }

        detailContentHeight = localY + 12;
        g.pose().popPose();
        g.disableScissor();

        renderScrollbar(g, x + w - 6, scrollAreaY + 2, scrollAreaH - 4, detailContentHeight, detailScrollOffset, Math.max(0, detailContentHeight - scrollAreaH), isDraggingDetailScrollbar);

        int btnW = 90, btnH = 20, btnY = y + h - btnH - 8, trackX = x + w - btnW - 8, abanX = trackX - btnW - 12;
        if (currentTab == Tab.ACTIVE && runtime != null) {
            boolean tHover = mx >= trackX && mx <= trackX + btnW && my >= btnY && my <= btnY + btnH;
            trackBtnHover = step(trackBtnHover, tHover ? 1f : 0f, 8f);
            drawButton(g, trackX, btnY, btnW, btnH, entry.questId().equals(QuestHudOverlay.INSTANCE.getTrackedQuestId()) ? Component.translatable("arc_quest.gui.journal.button.tracked").getString() : Component.translatable("arc_quest.gui.journal.button.track").getString(), activeTheme, HudAnimUtil.easeOutCubic(trackBtnHover), tHover);

            boolean aHover = mx >= abanX && mx <= abanX + btnW && my >= btnY && my <= btnY + btnH;
            abandonBtnHover = step(abandonBtnHover, aHover ? 1f : 0f, 8f);
            drawButton(g, abanX, btnY, btnW, btnH, Component.translatable("arc_quest.gui.journal.button.abandon").getString(), 0xFF4444, HudAnimUtil.easeOutCubic(abandonBtnHover), aHover);
        } else if (currentTab == Tab.FAILED && entry.state() == QuestState.FAILED) {
            int restartBtnW = 120, restartBtnX = x + w - restartBtnW - 8;
            boolean rHover = mx >= restartBtnX && mx <= restartBtnX + restartBtnW && my >= btnY && my <= btnY + btnH;
            failedRestartBtnHover = step(failedRestartBtnHover, rHover ? 1f : 0f, 8f);
            drawButton(g, restartBtnX, btnY, restartBtnW, btnH, Component.translatable("arc_quest.gui.journal.button.restart").getString(), activeTheme, HudAnimUtil.easeOutCubic(failedRestartBtnHover), rHover);
        }
    }

    private void renderScrollbar(GuiGraphics g, int x, int y, int viewH, int contentH, double currentScroll, int maxScroll, boolean isDragging) {
        if (maxScroll <= 0) return;
        int thumbH = Math.max(16, (int) (((float) viewH / contentH) * viewH));
        int thumbY = y + (int) ((currentScroll / maxScroll) * (viewH - thumbH));
        g.fill(x, y, x + 4, y + viewH, HudAnimUtil.withAlpha(0x000000, (int) (40 * effectiveAlpha)));
        g.fill(x, thumbY, x + 4, thumbY + thumbH, HudAnimUtil.withAlpha(0xFFFFFF, (int) ((isDragging ? 180 : 120) * effectiveAlpha)));
    }

    private void renderEmptyDetail(GuiGraphics g, int x, int y, int w, int h) {
        if (effectiveAlpha > 0.05f) {
            g.drawCenteredString(font, Component.translatable("arc_quest.gui.journal.label.select_quest").getString(), x + w / 2, y + h / 2, HudAnimUtil.withAlpha(0x666666, (int) (120 * effectiveAlpha)));
        }
    }

    private void drawButton(GuiGraphics g, int x, int y, int w, int h, String text, int themeColor, float hoverEase, boolean hovered) {
        int bgAlpha = (int) ((0x33 + 0x44 * hoverEase) * effectiveAlpha);
        int borderAlpha = (int) ((0x66 + 0x99 * hoverEase) * effectiveAlpha);
        int borderRgb = hovered ? (themeColor & 0xFFFFFF) : 0xCCCCCC;
        g.fill(x, y, x + w, y + h, HudAnimUtil.withAlpha(0x000000, bgAlpha));
        g.fill(x, y, x + w, y + 1, HudAnimUtil.withAlpha(borderRgb, borderAlpha));
        g.fill(x, y + h - 1, x + w, y + h, HudAnimUtil.withAlpha(borderRgb, borderAlpha));
        g.fill(x, y, x + 1, y + h, HudAnimUtil.withAlpha(borderRgb, borderAlpha));
        g.fill(x + w - 1, y, x + w, y + h, HudAnimUtil.withAlpha(borderRgb, borderAlpha));
        if (effectiveAlpha > 0.05f) {
            g.pose().pushPose();
            g.pose().translate(x + w / 2f, y + (h - font.lineHeight) / 2f + 1, 0);
            g.pose().scale(0.85f, 0.85f, 1f);
            g.drawCenteredString(font, text, 0, 0, HudAnimUtil.withAlpha(0xFFFFFF, (int) (255 * effectiveAlpha)));
            g.pose().popPose();
        }
    }


    private enum Tab {ACTIVE, COMPLETED, FAILED}

    private static class ChoiceButtonRect {
        int x, y, w, h, choiceIndex;

        ChoiceButtonRect(int x, int y, int w, int h, int idx) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.choiceIndex = idx;
        }
    }

    private record QuestListEntry(String questId, String displayName, QuestState state, QuestDefinition def) {
    }
}
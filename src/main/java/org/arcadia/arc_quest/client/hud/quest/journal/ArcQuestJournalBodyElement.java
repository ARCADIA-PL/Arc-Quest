package org.arcadia.arc_quest.client.hud.quest.journal;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.mutil.animation.ArcAnimClock;
import org.arcadia.arc_quest.mutil.theme.ArcDrawUtil;
import org.arcadia.arc_quest.mutil.text.ArcTextLayoutUtil;
import org.arcadia.arc_quest.mutil.core.ArcGuiElement;
import org.arcadia.arc_quest.client.hud.quest.QuestIconRenderer;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.panel.history.ArcQuestHistoryPanelElement;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.panel.offer.ArcQuestOfferPanelElement;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.panel.intel.ArcQuestIntelPanelElement;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.panel.story.ArcQuestStoryPanelElement;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.capability.QuestRuntimeData;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;

import java.util.ArrayList;
import java.util.List;

public class ArcQuestJournalBodyElement extends ArcGuiElement {





    private final QuestJournalScreen screen;
    private final ArcQuestJournalHeaderElement headerElement;
    private final String questCompletedText = Component.translatable("arc_quest.gui.journal.label.quest_completed").getString();
    private final String questFailedText = Component.translatable("arc_quest.gui.journal.label.quest_failed").getString();
    private final String selectQuestText = Component.translatable("arc_quest.gui.journal.label.select_quest").getString();
    private double detailScrollOffset = 0, detailTargetScroll = 0, dragDetailYOffset = 0;
    private boolean isDraggingDetailScrollbar = false;
    private int detailContentHeight = 0;
    private float detailReveal = 0f;

    public ArcQuestJournalBodyElement(QuestJournalScreen screen) {
        super(0, 0, 0, 0);
        this.screen = screen;
        this.headerElement = new ArcQuestJournalHeaderElement(screen);





    }

    public double getDetailScrollOffset() {
        return detailScrollOffset;
    }

    public void resetState() {
        detailReveal = 0f;
        detailTargetScroll = 0;
        detailScrollOffset = 0;
        headerElement.resetState();
        screen.resetJournalPhaseState();

    }

    public void render(GuiGraphics g, int x, int y, int w, int h, int mx, int my, int theme, float dt) {
        clampScroll(h - 40);
        detailScrollOffset += Math.abs(detailTargetScroll - detailScrollOffset) > 0.5 ? (detailTargetScroll - detailScrollOffset) * Math.min(1.0, dt * 14.0) : (detailTargetScroll - detailScrollOffset);
        int selectedIndex = screen.getSelectedIndex();
        if (selectedIndex < 0 || selectedIndex >= screen.getCurrentEntries().size()) {
            renderEmptyDetail(g, x, y, w, h);
            return;
        }

        JournalTypes.QuestListEntry entry = screen.getCurrentEntries().get(selectedIndex);
        QuestDefinition def = entry.def();
        if (def == null) return;
        int activeTheme = ClientQuestCache.INSTANCE.getQuestThemeColor(entry.questId(), theme);
        screen.setCurrentThemeColor(activeTheme);

        detailReveal = ArcAnimClock.lerp(detailReveal, 1f, 0.15f, dt);
        float dAlpha = screen.getEffectiveAlpha() * ArcAnimClock.easeOutCubic(Math.min(1f, detailReveal));
        int safeA = (int) (255 * dAlpha);
        if (safeA <= 8) return;

        int scrollAreaY = y, scrollAreaH = h - 40, scrollAreaW = w - 8;
        screen.enableScissor(g, x, scrollAreaY, x + w - 8, scrollAreaY + scrollAreaH);

        def.getSplashConfig(SplashType.QUEST_DETAIL).ifPresent(asset -> {
            RenderSystem.enableBlend();
            float watermarkAlpha = 0.15f * dAlpha;
            int rw = (int) (w * 0.7f), rh = rw;
            int rx = x + w / 2 - rw / 2 + (int) ((1f - detailReveal) * 50f), ry = scrollAreaY + scrollAreaH / 2 - rh / 2;
            RenderSystem.setShaderColor(1f, 1f, 1f, watermarkAlpha);
            QuestIconRenderer.renderIcon(g, asset, rx, ry, rw, rh);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        });

        g.pose().pushPose();
        g.pose().translate(x + 12, scrollAreaY + 12 - detailScrollOffset, 0);

        QuestRuntimeData runtime = ClientQuestCache.INSTANCE.getActiveQuest(entry.questId());
        int localY = headerElement.render(g, entry, def, runtime, x, scrollAreaY, scrollAreaW, scrollAreaH, mx, my, activeTheme, dAlpha, safeA, 0, detailScrollOffset, dt);

        g.fill(0, localY, scrollAreaW - 24, localY + 1, ArcDrawUtil.withAlpha(activeTheme, (int) (120 * dAlpha)));
        localY += 10;
        String selectedPhaseIdForRewards = null;

        if (entry.state() == QuestState.ACTIVE && runtime != null) {
            List<String> activePhaseIds = new ArrayList<>();
            for (String pid : def.getPhaseIds()) if (runtime.isPhaseActive(pid)) activePhaseIds.add(pid);
            if (activePhaseIds.isEmpty()) activePhaseIds.addAll(runtime.getActivePhaseIds());

            if (def.isCollectionQuest()) {
                localY = screen.renderJournalCollection(g, entry, def, runtime, localY, safeA, activeTheme);
                selectedPhaseIdForRewards = !activePhaseIds.isEmpty() ? activePhaseIds.get(0) : null;
            } else if (activePhaseIds.isEmpty()) {
                g.drawString(screen.getFont(), "No active phase.", 0, localY, ArcDrawUtil.withAlpha(0x888888, safeA), false);
                localY += 16;
            } else if (activePhaseIds.size() == 1) {
                selectedPhaseIdForRewards = activePhaseIds.get(0);
                localY = screen.renderJournalSinglePhase(g, entry, def, runtime, activePhaseIds.get(0), x, scrollAreaY, scrollAreaW, scrollAreaH, mx, my, dt, activeTheme, dAlpha, safeA, localY);
            } else {
                localY = screen.renderJournalParallelPhase(g, entry, def, runtime, activePhaseIds, x, scrollAreaY, scrollAreaW, scrollAreaH, mx, my, dt, activeTheme, dAlpha, safeA, localY);
                selectedPhaseIdForRewards = screen.getSelectedJournalParallelPhaseId();
            }
        } else if (entry.state() == QuestState.COMPLETED) {
            g.drawString(screen.getFont(), questCompletedText, 0, localY, ArcDrawUtil.withAlpha(0x88FF88, safeA), false);
            localY += 16;
        } else if (entry.state() == QuestState.FAILED) {
            g.drawString(screen.getFont(), questFailedText, 0, localY, ArcDrawUtil.withAlpha(0xFF6666, safeA), false);
            localY += 16;
        }

        localY = screen.renderJournalRewards(g, def, selectedPhaseIdForRewards, x, scrollAreaY, scrollAreaW, scrollAreaH, mx, my, activeTheme, dAlpha, safeA, localY, dt);

        detailContentHeight = localY + 12;
        g.pose().popPose();
        g.disableScissor();
        renderScrollbar(g, x + w - 6, scrollAreaY + 2, scrollAreaH - 4, detailContentHeight, Math.max(0, detailContentHeight - scrollAreaH));

    }

    private void renderEmptyDetail(GuiGraphics g, int x, int y, int w, int h) {
        if (screen.getEffectiveAlpha() > 0.05f)
            g.drawCenteredString(screen.getFont(), selectQuestText, x + w / 2, y + h / 2, ArcDrawUtil.withAlpha(0x666666, (int) (120 * screen.getEffectiveAlpha())));
    }

    private void renderScrollbar(GuiGraphics g, int x, int y, int viewH, int contentH, int maxScroll) {
        if (maxScroll <= 0) return;
        int thumbH = Math.max(16, (int) (((float) viewH / contentH) * viewH)), thumbY = y + (int) ((detailScrollOffset / maxScroll) * (viewH - thumbH));
        g.fill(x, y, x + 4, y + viewH, ArcDrawUtil.withAlpha(0x000000, (int) (40 * screen.getEffectiveAlpha())));
        g.fill(x, thumbY, x + 4, thumbY + thumbH, ArcDrawUtil.withAlpha(0xFFFFFF, (int) ((isDraggingDetailScrollbar ? 180 : 120) * screen.getEffectiveAlpha())));
    }

    public boolean mouseClicked(double mx, double my, int x, int y, int w, int h) {
        int scrollAreaH = h - 40, maxDetailScroll = Math.max(0, detailContentHeight - scrollAreaH);
        boolean panelsActive = ArcQuestIntelPanelElement.isActive() || ArcQuestOfferPanelElement.isActive() || ArcQuestHistoryPanelElement.isActive() || ArcQuestStoryPanelElement.isActive();
        if (!panelsActive && screen.mouseClickedJournalRewards(mx, my)) return true;
        if (!panelsActive && maxDetailScroll > 0 && mx >= x + w - 6 && mx <= x + w && my >= y && my <= y + scrollAreaH) {
            isDraggingDetailScrollbar = true;
            int thumbH = Math.max(16, (int) (((float) scrollAreaH / detailContentHeight) * scrollAreaH)), thumbY = y + (int) ((detailScrollOffset / maxDetailScroll) * (scrollAreaH - thumbH));
            if (my >= thumbY && my <= thumbY + thumbH) dragDetailYOffset = my - thumbY;
            else {
                dragDetailYOffset = thumbH / 2.0;
                updateScrollFromMouse(my, y, scrollAreaH, maxDetailScroll);
            }
            return true;
        }

        if (!panelsActive && entryIsCollectionActive() && screen.mouseClickedJournalCollection(mx - (x + 12), my - (y + 12 - detailScrollOffset)))
            return true;
        if (!panelsActive && headerElement.mouseClicked(mx, my, y, scrollAreaH)) return true;
        if (screen.getSelectedIndex() >= 0 && screen.getSelectedIndex() < screen.getCurrentEntries().size()) {
            JournalTypes.QuestListEntry entry = screen.getCurrentEntries().get(screen.getSelectedIndex());
            QuestRuntimeData runtime = ClientQuestCache.INSTANCE.getActiveQuest(entry.questId());
            if (entry.state() == QuestState.ACTIVE && runtime != null) {
                List<String> activePhaseIds = new ArrayList<>();
                for (String pid : entry.def().getPhaseIds()) if (runtime.isPhaseActive(pid)) activePhaseIds.add(pid);
                if (activePhaseIds.isEmpty()) activePhaseIds.addAll(runtime.getActivePhaseIds());
                if (activePhaseIds.size() == 1) {
                    if (screen.mouseClickedJournalSinglePhase(mx, my, x, y, w, h)) return true;
                } else if (activePhaseIds.size() > 1) {
                    if (screen.mouseClickedJournalParallelPhase(mx, my, x, y, w, h)) return true;
                }
            }
        }
        return false;
    }

    public boolean mouseDragged(double mx, double my, int y, int h) {
        if (screen.mouseDraggedJournalRewards(mx, my)) return true;
        if (isDraggingDetailScrollbar) {
            updateScrollFromMouse(my, y, h - 40, Math.max(0, detailContentHeight - (h - 40)));
            return true;
        }
        return screen.mouseDraggedJournalParallelPhase(mx, my);
    }

    public boolean mouseReleased(int button) {
        if (screen.mouseReleasedJournalRewards(button)) return true;
        if (button == 0) {
            isDraggingDetailScrollbar = false;
            screen.mouseReleasedJournalParallelPhase();
        }
        return isDraggingDetailScrollbar;
    }

    public boolean mouseScrolled(double mx, double my, double delta, int x, int y, int w, int h) {
        if (screen.mouseScrolledJournalRewards(mx, my, delta)) return true;
        if (screen.mouseScrolledJournalParallelPhase(mx, my, delta, x, y, h - 40)) return true;
        if (mx >= x && mx <= x + w && my >= y && my <= y + h) {
            detailTargetScroll -= delta * 25.0;
            clampScroll(h - 40);
            return true;
        }
        return false;
    }

    public void clampScroll(int scrollAreaH) {
        detailTargetScroll = Math.max(0, Math.min(detailTargetScroll, Math.max(0, detailContentHeight - scrollAreaH)));
    }

    private void updateScrollFromMouse(double my, int y0, int viewH, int maxScroll) {
        if (maxScroll <= 0) return;
        int thumbH = Math.max(16, (int) (((float) viewH / detailContentHeight) * viewH));
        detailTargetScroll = Math.max(0.0, Math.min(1.0, (my - y0 - dragDetailYOffset) / (viewH - thumbH))) * maxScroll;
    }

    private boolean entryIsCollectionActive() {
        if (screen.getSelectedIndex() < 0 || screen.getSelectedIndex() >= screen.getCurrentEntries().size())
            return false;
        JournalTypes.QuestListEntry entry = screen.getCurrentEntries().get(screen.getSelectedIndex());
        return entry.def() != null && entry.def().isCollectionQuest() && entry.state() == QuestState.ACTIVE;
    }

}
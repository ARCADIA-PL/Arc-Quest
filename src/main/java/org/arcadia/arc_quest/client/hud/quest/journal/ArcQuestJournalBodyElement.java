package org.arcadia.arc_quest.client.hud.quest.journal;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.mutil.animation.ArcAnimClock;
import org.arcadia.arc_quest.mutil.theme.ArcDrawUtil;
import org.arcadia.arc_quest.mutil.core.ArcGuiElement;
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
    private final ArcQuestJournalDetailScrollbarElement scrollbarElement;
    private final ArcQuestJournalWatermarkElement watermarkElement;
    private final String questCompletedText = Component.translatable("arc_quest.gui.journal.label.quest_completed").getString();
    private final String questFailedText = Component.translatable("arc_quest.gui.journal.label.quest_failed").getString();
    private final String selectQuestText = Component.translatable("arc_quest.gui.journal.label.select_quest").getString();
    private int detailContentHeight = 0;
    private float detailReveal = 0f;

    public ArcQuestJournalBodyElement(QuestJournalScreen screen) {
        super(0, 0, 0, 0);
        this.screen = screen;
        this.headerElement = new ArcQuestJournalHeaderElement(screen);
        this.scrollbarElement = new ArcQuestJournalDetailScrollbarElement(screen);
        this.watermarkElement = new ArcQuestJournalWatermarkElement();





    }

    public double getDetailScrollOffset() {
        return scrollbarElement.getScrollOffset();
    }

    public void resetState() {
        detailReveal = 0f;
        scrollbarElement.resetState();
        headerElement.resetState();
        screen.resetJournalPhaseState();

    }

    public void render(GuiGraphics g, int x, int y, int w, int h, int mx, int my, int theme, float dt) {
        int scrollAreaHForTick = h - 40;
        scrollbarElement.tick(scrollAreaHForTick, detailContentHeight, dt);
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

        watermarkElement.render(g, def, x, scrollAreaY, w, scrollAreaH, detailReveal, dAlpha);

        g.pose().pushPose();
        double detailScrollOffset = scrollbarElement.getScrollOffset();
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
        scrollbarElement.render(g, x + w - 6, scrollAreaY + 2, scrollAreaH - 4, detailContentHeight);

    }

    private void renderEmptyDetail(GuiGraphics g, int x, int y, int w, int h) {
        if (screen.getEffectiveAlpha() > 0.05f)
            g.drawCenteredString(screen.getFont(), selectQuestText, x + w / 2, y + h / 2, ArcDrawUtil.withAlpha(0x666666, (int) (120 * screen.getEffectiveAlpha())));
    }

    public boolean mouseClicked(double mx, double my, int x, int y, int w, int h) {
        int scrollAreaH = h - 40;
        double detailScrollOffset = scrollbarElement.getScrollOffset();
        boolean panelsActive = ArcQuestIntelPanelElement.isActive() || ArcQuestOfferPanelElement.isActive() || ArcQuestHistoryPanelElement.isActive() || ArcQuestStoryPanelElement.isActive();
        if (!panelsActive && screen.mouseClickedJournalRewards(mx, my)) return true;
        if (!panelsActive && scrollbarElement.mouseClicked(mx, my, x, y, w, scrollAreaH, detailContentHeight)) return true;

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
        if (scrollbarElement.mouseDragged(my, y, h - 40, detailContentHeight)) return true;
        return screen.mouseDraggedJournalParallelPhase(mx, my);
    }

    public boolean mouseReleased(int button) {
        if (screen.mouseReleasedJournalRewards(button)) return true;
        boolean scrollbarReleased = scrollbarElement.mouseReleased(button);
        if (button == 0) screen.mouseReleasedJournalParallelPhase();
        return scrollbarReleased;
    }

    public boolean mouseScrolled(double mx, double my, double delta, int x, int y, int w, int h) {
        if (screen.mouseScrolledJournalRewards(mx, my, delta)) return true;
        if (screen.mouseScrolledJournalParallelPhase(mx, my, delta, x, y, h - 40)) return true;
        if (mx >= x && mx <= x + w && my >= y && my <= y + h) {
            scrollbarElement.mouseScrolled(delta, h - 40, detailContentHeight);
            return true;
        }
        return false;
    }

    private boolean entryIsCollectionActive() {
        if (screen.getSelectedIndex() < 0 || screen.getSelectedIndex() >= screen.getCurrentEntries().size())
            return false;
        JournalTypes.QuestListEntry entry = screen.getCurrentEntries().get(screen.getSelectedIndex());
        return entry.def() != null && entry.def().isCollectionQuest() && entry.state() == QuestState.ACTIVE;
    }

}
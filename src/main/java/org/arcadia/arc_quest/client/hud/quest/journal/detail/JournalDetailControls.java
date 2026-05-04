package org.arcadia.arc_quest.client.hud.quest.journal.detail;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.mutil.animation.ArcAnimClock;
import org.arcadia.arc_quest.mutil.theme.ArcDrawUtil;
import org.arcadia.arc_quest.client.hud.QuestHudOverlay;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.panel.history.ArcQuestHistoryPanelElement;
import org.arcadia.arc_quest.client.hud.quest.journal.JournalTypes;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.panel.offer.ArcQuestOfferPanelElement;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.panel.intel.ArcQuestIntelPanelElement;
import org.arcadia.arc_quest.client.hud.quest.arcmutil.panel.story.ArcQuestStoryPanelElement;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.api.QuestState;
import org.arcadia.arc_quest.quest.capability.QuestRuntimeData;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.quest.network.C2SRequestQuestActionPacket;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;

public class JournalDetailControls {
    private final QuestJournalScreen screen;
    private final JournalDetailPanel parent;

    private float trackBtnHover = 0f;
    private float abandonBtnHover = 0f;
    private float failedRestartBtnHover = 0f;
    private float chapterShopBtnHover = 0f;

    public JournalDetailControls(QuestJournalScreen screen, JournalDetailPanel parent) {
        this.screen = screen;
        this.parent = parent;
    }

    private boolean shouldShowShop(QuestDefinition def) {
        return def != null && def.hasChapterShop() && (screen.getCurrentTab() == JournalTypes.Tab.ACTIVE || (screen.getCurrentTab() == JournalTypes.Tab.COMPLETED && def.isChapterShopPersistent()));
    }

    private boolean shouldShowActiveBtns(QuestRuntimeData runtime) {
        return screen.getCurrentTab() == JournalTypes.Tab.ACTIVE && runtime != null;
    }

    private boolean shouldShowFailedBtns(JournalTypes.QuestListEntry entry) {
        return screen.getCurrentTab() == JournalTypes.Tab.FAILED && entry.state() == QuestState.FAILED;
    }

    public void render(GuiGraphics g, JournalTypes.QuestListEntry entry, QuestDefinition def, QuestRuntimeData runtime, int x, int y, int w, int h, int mx, int my, float dt, int activeTheme) {
        int btnH = 20, btnY = y + h - btnH - 8;
        boolean active = ArcQuestIntelPanelElement.isActive() || ArcQuestOfferPanelElement.isActive() || ArcQuestHistoryPanelElement.isActive() || ArcQuestStoryPanelElement.isActive();

        boolean bShop = shouldShowShop(def);
        boolean bActive = shouldShowActiveBtns(runtime);
        boolean bFailed = shouldShowFailedBtns(entry);

        int btnCount = (bShop ? 1 : 0) + (bActive ? 2 : 0) + (bFailed ? 1 : 0);
        if (btnCount == 0) return;

        int btnW = Math.min(110, (w - 8 * (btnCount + 1)) / btnCount);

        int trackX = x + w - 8 - btnW;
        int abanX = trackX - 8 - btnW;
        int restartX = trackX;
        int shopBtnX = x + 8;

        if (bShop) {
            boolean shopHover = !active && mx >= shopBtnX && mx <= shopBtnX + btnW && my >= btnY && my <= btnY + btnH;
            chapterShopBtnHover = ArcAnimClock.step(chapterShopBtnHover, shopHover ? 1f : 0f, 8f, dt);
            JournalDetailPanel.drawCyberButton(g, screen, shopBtnX, btnY, btnW, btnH, Component.translatable("arc_quest.gui.journal.button.chapter_shop").getString(), activeTheme, ArcAnimClock.easeOutCubic(chapterShopBtnHover), shopHover);
        }

        if (bActive) {
            boolean tHover = !active && mx >= trackX && mx <= trackX + btnW && my >= btnY && my <= btnY + btnH;
            trackBtnHover = ArcAnimClock.step(trackBtnHover, tHover ? 1f : 0f, 8f, dt);
            JournalDetailPanel.drawCyberButton(g, screen, trackX, btnY, btnW, btnH, entry.questId().equals(QuestHudOverlay.INSTANCE.getTrackedQuestId()) ? Component.translatable("arc_quest.gui.journal.button.tracked").getString() : Component.translatable("arc_quest.gui.journal.button.track").getString(), activeTheme, ArcAnimClock.easeOutCubic(trackBtnHover), tHover);

            boolean aHover = !active && mx >= abanX && mx <= abanX + btnW && my >= btnY && my <= btnY + btnH;
            abandonBtnHover = ArcAnimClock.step(abandonBtnHover, aHover ? 1f : 0f, 8f, dt);
            JournalDetailPanel.drawCyberButton(g, screen, abanX, btnY, btnW, btnH, Component.translatable("arc_quest.gui.journal.button.abandon").getString(), 0xFF4444, ArcAnimClock.easeOutCubic(abandonBtnHover), aHover);
        } else if (bFailed) {
            boolean rHover = !active && mx >= restartX && mx <= restartX + btnW && my >= btnY && my <= btnY + btnH;
            failedRestartBtnHover = ArcAnimClock.step(failedRestartBtnHover, rHover ? 1f : 0f, 8f, dt);
            JournalDetailPanel.drawCyberButton(g, screen, restartX, btnY, btnW, btnH, Component.translatable("arc_quest.gui.journal.button.restart").getString(), activeTheme, ArcAnimClock.easeOutCubic(failedRestartBtnHover), rHover);
        }
    }

    public boolean mouseClicked(double mx, double my, int x, int y, int w, int h) {
        if (ArcQuestIntelPanelElement.isActive() || ArcQuestOfferPanelElement.isActive() || ArcQuestHistoryPanelElement.isActive() || ArcQuestStoryPanelElement.isActive())
            return false;
        if (screen.getSelectedIndex() < 0 || screen.getSelectedIndex() >= screen.getCurrentEntries().size())
            return false;

        JournalTypes.QuestListEntry entry = screen.getCurrentEntries().get(screen.getSelectedIndex());
        QuestDefinition def = entry.def();
        QuestRuntimeData runtime = ClientQuestCache.INSTANCE.getActiveQuest(entry.questId());

        int btnH = 20, btnY = y + h - btnH - 8;

        boolean bShop = shouldShowShop(def);
        boolean bActive = shouldShowActiveBtns(runtime);
        boolean bFailed = shouldShowFailedBtns(entry);

        int btnCount = (bShop ? 1 : 0) + (bActive ? 2 : 0) + (bFailed ? 1 : 0);
        if (btnCount == 0) return false;

        int btnW = Math.min(110, (w - 8 * (btnCount + 1)) / btnCount);
        int trackX = x + w - 8 - btnW;
        int abanX = trackX - 8 - btnW;
        int restartX = trackX;
        int shopBtnX = x + 8;

        if (bShop && mx >= shopBtnX && mx <= shopBtnX + btnW && my >= btnY && my <= btnY + btnH) {
            ArcQuestNetwork.sendQuestAction(C2SRequestQuestActionPacket.openChapterShop(entry.questId()));
            screen.playClick();
            return true;
        }

        if (bActive) {
            if (mx >= trackX && mx <= trackX + btnW && my >= btnY && my <= btnY + btnH) {
                QuestHudOverlay.INSTANCE.setTrackedQuest(entry.questId());
                screen.playClick();
                return true;
            }
            if (mx >= abanX && mx <= abanX + btnW && my >= btnY && my <= btnY + btnH) {
                ArcQuestNetwork.sendQuestAction(C2SRequestQuestActionPacket.abandon(entry.questId()));
                screen.playClick();
                return true;
            }
        }

        if (bFailed && mx >= restartX && mx <= restartX + btnW && my >= btnY && my <= btnY + btnH) {
            ArcQuestNetwork.sendQuestAction(C2SRequestQuestActionPacket.accept(entry.questId()));
            screen.playClick();
            return true;
        }

        return false;
    }
}
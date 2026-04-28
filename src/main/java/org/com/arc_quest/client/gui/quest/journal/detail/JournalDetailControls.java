package org.com.arc_quest.client.gui.quest.journal.detail;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.com.arc_quest.client.gui.HudAnimUtil;
import org.com.arc_quest.client.gui.QuestHudOverlay;
import org.com.arc_quest.client.gui.quest.journal.JournalTypes;
import org.com.arc_quest.client.gui.quest.journal.QuestJournalScreen;
import org.com.arc_quest.client.gui.quest.offer.QuestOfferPanel;
import org.com.arc_quest.client.gui.render.QuestIntelPanel;
import org.com.arc_quest.quest.api.QuestDefinition;
import org.com.arc_quest.quest.api.QuestState;
import org.com.arc_quest.quest.capability.QuestRuntimeData;
import org.com.arc_quest.quest.network.ArcQuestNetwork;
import org.com.arc_quest.quest.network.C2SRequestQuestActionPacket;

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

    public void render(GuiGraphics g, JournalTypes.QuestListEntry entry, QuestDefinition def, QuestRuntimeData runtime, int x, int y, int w, int h, int mx, int my, float dt, int activeTheme) {
        int btnH = 20, btnY = y + h - btnH - 8;

        boolean active = QuestIntelPanel.isActive() || QuestOfferPanel.isActive();

        if (def.hasChapterShop() && (screen.getCurrentTab() == JournalTypes.Tab.ACTIVE || (screen.getCurrentTab() == JournalTypes.Tab.COMPLETED && def.isChapterShopPersistent()))) {
            int shopBtnW = Math.min(110, w - 16), shopBtnX = x + w - shopBtnW - 8, shopBtnY = btnY - btnH - 6;
            boolean shopHover = !active && mx >= shopBtnX && mx <= shopBtnX + shopBtnW && my >= shopBtnY && my <= shopBtnY + btnH;
            chapterShopBtnHover = HudAnimUtil.step(chapterShopBtnHover, shopHover ? 1f : 0f, 8f, dt);
            JournalDetailPanel.drawCyberButton(g, screen, shopBtnX, shopBtnY, shopBtnW, btnH, Component.translatable("arc_quest.gui.journal.button.chapter_shop").getString(), activeTheme, HudAnimUtil.easeOutCubic(chapterShopBtnHover), shopHover);
        }

        if (screen.getCurrentTab() == JournalTypes.Tab.ACTIVE && runtime != null) {
            int btnW = Math.min(90, (w - 24) / 2), trackX = x + w - btnW - 8, abanX = trackX - 8 - btnW;
            boolean tHover = !active && mx >= trackX && mx <= trackX + btnW && my >= btnY && my <= btnY + btnH;
            trackBtnHover = HudAnimUtil.step(trackBtnHover, tHover ? 1f : 0f, 8f, dt);
            JournalDetailPanel.drawCyberButton(g, screen, trackX, btnY, btnW, btnH, entry.questId().equals(QuestHudOverlay.INSTANCE.getTrackedQuestId()) ? Component.translatable("arc_quest.gui.journal.button.tracked").getString() : Component.translatable("arc_quest.gui.journal.button.track").getString(), activeTheme, HudAnimUtil.easeOutCubic(trackBtnHover), tHover);

            boolean aHover = !active && mx >= abanX && mx <= abanX + btnW && my >= btnY && my <= btnY + btnH;
            abandonBtnHover = HudAnimUtil.step(abandonBtnHover, aHover ? 1f : 0f, 8f, dt);
            JournalDetailPanel.drawCyberButton(g, screen, abanX, btnY, btnW, btnH, Component.translatable("arc_quest.gui.journal.button.abandon").getString(), 0xFF4444, HudAnimUtil.easeOutCubic(abandonBtnHover), aHover);
        } else if (screen.getCurrentTab() == JournalTypes.Tab.FAILED && entry.state() == QuestState.FAILED) {
            int restartBtnW = Math.min(120, w - 16), restartBtnX = x + w - restartBtnW - 8;
            boolean rHover = !active && mx >= restartBtnX && mx <= restartBtnX + restartBtnW && my >= btnY && my <= btnY + btnH;
            failedRestartBtnHover = HudAnimUtil.step(failedRestartBtnHover, rHover ? 1f : 0f, 8f, dt);
            JournalDetailPanel.drawCyberButton(g, screen, restartBtnX, btnY, restartBtnW, btnH, Component.translatable("arc_quest.gui.journal.button.restart").getString(), activeTheme, HudAnimUtil.easeOutCubic(failedRestartBtnHover), rHover);
        }
    }

    public boolean mouseClicked(double mx, double my, int x, int y, int w, int h) {
        if (QuestIntelPanel.isActive()) return false;
        if (QuestOfferPanel.isActive()) return false;

        if (screen.getSelectedIndex() < 0 || screen.getSelectedIndex() >= screen.getCurrentEntries().size()) return false;
        JournalTypes.QuestListEntry entry = screen.getCurrentEntries().get(screen.getSelectedIndex());
        int btnH = 20, btnY = y + h - btnH - 8;

        if (screen.getCurrentTab() == JournalTypes.Tab.ACTIVE) {
            int btnW = Math.min(90, (w - 24) / 2), trackX = x + w - btnW - 8, abanX = trackX - 8 - btnW;
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

        if (screen.getCurrentTab() == JournalTypes.Tab.FAILED && entry.state() == QuestState.FAILED) {
            int restartBtnW = Math.min(120, w - 16), restartBtnX = x + w - restartBtnW - 8;
            if (mx >= restartBtnX && mx <= restartBtnX + restartBtnW && my >= btnY && my <= btnY + btnH) {
                ArcQuestNetwork.sendQuestAction(C2SRequestQuestActionPacket.accept(entry.questId()));
                screen.playClick();
                return true;
            }
        }
        return false;
    }
}

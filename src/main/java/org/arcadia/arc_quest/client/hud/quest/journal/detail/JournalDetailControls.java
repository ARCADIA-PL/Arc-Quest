package org.arcadia.arc_quest.client.hud.quest.journal.detail;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.QuestHudOverlay;
import org.arcadia.arc_quest.client.hud.quest.journal.JournalTypes;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;
import org.arcadia.arc_quest.client.hud.quest.offer.QuestOfferPanel;
import org.arcadia.arc_quest.client.hud.quest.ponder.QuestIntelPanel;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.api.QuestState;
import org.arcadia.arc_quest.quest.capability.QuestRuntimeData;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.quest.network.C2SRequestQuestActionPacket;

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
            int shopBtnW = Math.min(110, w - 16);
            int shopBtnX = x + 8;
            int shopBtnY = btnY; // 【架构师修复】：去除多余的减法，完美和右侧追踪按钮处于同一水平线！
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
        if (QuestIntelPanel.isActive() || QuestOfferPanel.isActive()) return false;
        if (screen.getSelectedIndex() < 0 || screen.getSelectedIndex() >= screen.getCurrentEntries().size()) return false;

        JournalTypes.QuestListEntry entry = screen.getCurrentEntries().get(screen.getSelectedIndex());
        int btnH = 20, btnY = y + h - btnH - 8;
        QuestDefinition def = entry.def();

        if (def != null && def.hasChapterShop()
                && (screen.getCurrentTab() == JournalTypes.Tab.ACTIVE
                || (screen.getCurrentTab() == JournalTypes.Tab.COMPLETED && def.isChapterShopPersistent()))) {

            int shopBtnW = Math.min(110, w - 16);
            int shopBtnX = x + 8;
            int shopBtnY = btnY; // 【架构师修复】：点击判定框也一并对齐

            if (mx >= shopBtnX && mx <= shopBtnX + shopBtnW && my >= shopBtnY && my <= shopBtnY + btnH) {
                ArcQuestNetwork.sendQuestAction(C2SRequestQuestActionPacket.openChapterShop(entry.questId()));
                screen.playClick();
                return true;
            }
        }

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
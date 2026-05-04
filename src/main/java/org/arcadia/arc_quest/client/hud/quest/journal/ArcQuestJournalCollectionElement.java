package org.arcadia.arc_quest.client.hud.quest.journal;

import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.mutil.theme.ArcDrawUtil;
import org.arcadia.arc_quest.mutil.core.ArcGuiElement;
import org.arcadia.arc_quest.mutil.input.ArcCyberButtonElement;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.capability.QuestRuntimeData;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.quest.network.C2SClaimCollectionRewardPacket;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;

import java.util.ArrayList;
import java.util.List;

public final class ArcQuestJournalCollectionElement extends ArcGuiElement {

    private final QuestJournalScreen screen;
    private final List<ClaimButton> claimButtons = new ArrayList<>();

    public ArcQuestJournalCollectionElement(QuestJournalScreen screen) {
        super(0, 0, 0, 0);
        this.screen = screen;
    }

    public int render(GuiGraphics g,
                      JournalTypes.QuestListEntry entry,
                      QuestDefinition def,
                      QuestRuntimeData runtime,
                      int localY,
                      int safeA,
                      int activeTheme) {
        claimButtons.clear();
        int completed = ClientQuestCache.INSTANCE.getCollectionCompletedEntryCount(entry.questId());
        int total = ClientQuestCache.INSTANCE.getCollectionTotalEntryCount(entry.questId());
        int discovered = ClientQuestCache.INSTANCE.getCollectionDiscoveredEntryCount(entry.questId());

        g.drawString(screen.getFont(), "Collection Progress: " + completed + "/" + total, 0, localY, ArcDrawUtil.withAlpha(activeTheme, safeA), false);
        localY += 12;
        g.drawString(screen.getFont(), "Discovered: " + discovered + "/" + total, 0, localY, ArcDrawUtil.withAlpha(0xAAAAAA, safeA), false);
        localY += 16;

        List<String> rows = new ArrayList<>();
        for (String pid : def.getPhaseIds()) {
            if (runtime.isPhaseActive(pid) || runtime.isPhaseCompleted(pid) || ClientQuestCache.INSTANCE.isCollectionEntryDiscovered(entry.questId(), pid))
                rows.add(pid);
        }

        if (rows.isEmpty()) {
            g.drawString(screen.getFont(), "No collection entries visible.", 0, localY, ArcDrawUtil.withAlpha(0x888888, safeA), false);
            return localY + 16;
        }

        for (String pid : rows) {
            PhaseDefinition phase = def.getPhase(pid);
            if (phase == null) continue;
            CollectionEntryConfig entryConfig = phase.getCollectionEntryConfig();
            if (entryConfig == null) continue;
            int count = ClientQuestCache.INSTANCE.getCollectionEntryCount(entry.questId(), pid);
            int target = Math.max(1, entryConfig.getCompletionTarget());
            String name = phase.getDisplayName().getString();
            if (name == null || name.isEmpty()) name = pid;
            int color = runtime.isPhaseCompleted(pid) ? 0x88FF88 : 0xFFFFFF;
            g.drawString(screen.getFont(), name + "  [" + count + "/" + target + "]", 0, localY, ArcDrawUtil.withAlpha(color, safeA), false);
            localY += 12;
        }

        localY += 4;
        localY = renderManualRewards(g, entry.questId(), def, localY, safeA, activeTheme);
        return localY + 4;
    }

    private int renderManualRewards(GuiGraphics g, String questId, QuestDefinition def, int localY, int safeA, int activeTheme) {
        List<RewardRow> rewardRows = collectManualRewardRows(questId, def);
        if (rewardRows.isEmpty()) return localY;

        g.drawString(screen.getFont(), "Manual Rewards", 0, localY, ArcDrawUtil.withAlpha(0xFFD166, safeA), false);
        localY += 14;

        for (RewardRow row : rewardRows) {
            int textColor = switch (row.state()) {
                case CLAIMED -> 0x88FF88;
                case CLAIMABLE -> 0xFFFFFF;
                case LOCKED -> 0x888888;
            };
            g.drawString(screen.getFont(), row.label(), 0, localY, ArcDrawUtil.withAlpha(textColor, safeA), false);

            int bx = 150, by = localY - 2, bw = 56, bh = 12;
            String buttonText = switch (row.state()) {
                case CLAIMED -> "Claimed";
                case CLAIMABLE -> "Claim";
                case LOCKED -> "Locked";
            };
            ArcCyberButtonElement.renderCyber(g, screen.getFont(), bx, by, bw, bh, buttonText, 0f, row.state() == RewardState.LOCKED, safeA, screen.getEffectiveAlpha(), activeTheme);
            if (row.state() == RewardState.CLAIMABLE)
                claimButtons.add(new ClaimButton(bx, by, bw, bh, row.rewardNodeId()));
            localY += 14;
        }
        return localY + 2;
    }

    private List<RewardRow> collectManualRewardRows(String questId, QuestDefinition def) {
        List<RewardRow> rows = new ArrayList<>();
        CollectionQuestConfig config = def.getCollectionConfig();
        if (config == null || !config.isAllowManualRewardClaim()) return rows;

        for (CollectionRewardNode node : config.getQuestRewardNodes())
            addManualRewardRow(rows, questId, "Quest Reward", node);
        for (CollectionCategoryDefinition category : config.getCategories()) {
            String categoryName = category.getDisplayNameText().resolve(null, null).getString();
            if (categoryName == null || categoryName.isEmpty()) categoryName = category.getCategoryId();
            for (CollectionRewardNode node : category.getRewardNodes())
                addManualRewardRow(rows, questId, "Category: " + categoryName, node);
        }
        for (String phaseId : def.getPhaseIds()) {
            PhaseDefinition phase = def.getPhase(phaseId);
            if (phase == null || phase.getCollectionEntryConfig() == null) continue;
            String phaseName = phase.getDisplayName().getString();
            if (phaseName == null || phaseName.isEmpty()) phaseName = phaseId;
            for (CollectionRewardNode node : phase.getCollectionEntryConfig().getRewardNodes())
                addManualRewardRow(rows, questId, "Entry: " + phaseName, node);
        }
        return rows;
    }

    private void addManualRewardRow(List<RewardRow> rows, String questId, String ownerLabel, CollectionRewardNode node) {
        if (node == null || node.getGrantMode() != EntryRewardGrantMode.MANUAL) return;
        RewardState state = resolveRewardState(questId, node.getRewardNodeId());
        rows.add(new RewardRow(ownerLabel + " · " + node.getRewardNodeId(), node.getRewardNodeId(), state));
    }

    private RewardState resolveRewardState(String questId, String rewardNodeId) {
        if (ClientQuestCache.INSTANCE.isCollectionRewardClaimed(questId, rewardNodeId)) return RewardState.CLAIMED;
        if (ClientQuestCache.INSTANCE.isCollectionRewardUnlocked(questId, rewardNodeId)) return RewardState.CLAIMABLE;
        return RewardState.LOCKED;
    }

    public boolean mouseClicked(double mx, double my) {
        for (ClaimButton btn : claimButtons) {
            if (mx >= btn.x() && mx <= btn.x() + btn.w() && my >= btn.y() && my <= btn.y() + btn.h()) {
                ArcQuestNetwork.sendClaimCollectionReward(C2SClaimCollectionRewardPacket.of(getSelectedQuestId(), btn.rewardNodeId()));
                screen.playClick();
                return true;
            }
        }
        return false;
    }

    private String getSelectedQuestId() {
        int idx = screen.getSelectedIndex();
        if (idx < 0 || idx >= screen.getCurrentEntries().size()) return "";
        return screen.getCurrentEntries().get(idx).questId();
    }

    private enum RewardState {LOCKED, CLAIMABLE, CLAIMED}

    private record ClaimButton(int x, int y, int w, int h, String rewardNodeId) {
    }

    private record RewardRow(String label, String rewardNodeId, RewardState state) {
    }
}

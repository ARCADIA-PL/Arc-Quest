package org.arcadia.arc_quest.client.hud.quest.journal.detail;

import org.arcadia.arc_quest.client.hud.component.HudRect;

/** Stationary identity, tabs and rewards keep their position while the selected view scrolls. */
public record CollectionDetailWorkspace(HudRect identity, HudRect tabs, HudRect viewport,
                                        HudRect rewards) {
    public static final int REWARD_HEIGHT = 60;

    public static CollectionDetailWorkspace measure(HudRect panel, boolean hasRewards) {
        int inset = Math.min(14, Math.max(1, panel.width() / 8));
        int width = Math.max(1, panel.width() - inset * 2);
        HudRect identity = new HudRect(panel.x() + inset, panel.y() + 8, width, 34);
        HudRect tabs = new HudRect(identity.x(), identity.bottom() + 5, width, 18);
        int bottom = panel.bottom() - 8;
        int rewardHeight = hasRewards ? Math.min(REWARD_HEIGHT,
                Math.max(1, bottom - tabs.bottom() - 30)) : 0;
        HudRect rewards = new HudRect(identity.x(), bottom - rewardHeight, width, rewardHeight);
        HudRect viewport = new HudRect(identity.x(), tabs.bottom() + 8,
                Math.max(1, width - CollectionJournalLayout.SCROLLBAR_GUTTER),
                Math.max(1, rewards.y() - tabs.bottom() - 8 - (hasRewards ? 6 : 0)));
        return new CollectionDetailWorkspace(identity, tabs, viewport, rewards);
    }
}

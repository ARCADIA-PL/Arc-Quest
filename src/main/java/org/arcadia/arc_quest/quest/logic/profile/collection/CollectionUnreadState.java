package org.arcadia.arc_quest.quest.logic.profile.collection;

import org.arcadia.arc_quest.quest.data.CollectionBindingProgress;
import org.arcadia.arc_quest.quest.data.CollectionEntryRecord;

/** Read state belongs to permanent knowledge, independently of acquisition or quest tracking. */
public final class CollectionUnreadState {
    private CollectionUnreadState() {}

    public static boolean isUnread(CollectionBindingProgress row, CollectionEntryRecord record) {
        return row.visible() && row.revealed() && record.isRevealed()
                && (!record.isSeen() || row.content().stream().anyMatch(block -> !record.isSeen(block.blockId())));
    }
}

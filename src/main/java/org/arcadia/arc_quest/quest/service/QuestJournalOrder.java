package org.arcadia.arc_quest.quest.service;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.Arc_Quest;

public final class QuestJournalOrder {

    private QuestJournalOrder() {
    }

    public static ResourceLocation categoryGroupId(ResourceLocation categoryId) {
        return ResourceLocation.fromNamespaceAndPath(
                Arc_Quest.MOD_ID,
                "journal_category/" + categoryId.getNamespace() + "/" + categoryId.getPath()
        );
    }
}

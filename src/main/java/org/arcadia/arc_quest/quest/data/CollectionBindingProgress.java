package org.arcadia.arc_quest.quest.data;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.api.CollectionContentBlock;
import org.arcadia.arc_quest.quest.api.CollectionEntryRewardProgress;
import java.util.List;
import net.minecraft.network.chat.Component;

public record CollectionBindingProgress(String bindingId, ResourceLocation entryId,
                                        boolean visible, boolean revealed, boolean discovered,
                                        boolean researchComplete, boolean complete,
                                        List<CollectionRequirementProgress> requirements,
                                        List<CollectionContentBlock> content,
                                        List<CollectionEntryRewardProgress> entryRewards,
                                        Component publicClue) {
    public CollectionBindingProgress {
        requirements = List.copyOf(requirements);
        content = List.copyOf(content);
        entryRewards = List.copyOf(entryRewards);
        publicClue = publicClue == null ? Component.empty() : publicClue.copy();
    }
    public CollectionBindingProgress(String bindingId, ResourceLocation entryId, boolean visible, boolean revealed,
            boolean discovered, boolean researchComplete, boolean complete, List<CollectionRequirementProgress> requirements,
            List<CollectionContentBlock> content, List<CollectionEntryRewardProgress> entryRewards) {
        this(bindingId, entryId, visible, revealed, discovered, researchComplete, complete, requirements, content, entryRewards, Component.empty());
    }
    public CollectionBindingProgress(String bindingId, ResourceLocation entryId, boolean visible, boolean revealed,
            boolean discovered, boolean researchComplete, boolean complete, List<CollectionRequirementProgress> requirements,
            List<CollectionContentBlock> content) {
        this(bindingId, entryId, visible, revealed, discovered, researchComplete, complete, requirements, content, List.of());
    }
    public boolean hasPublicClue() { return visible && !publicClue.getString().isBlank(); }
}

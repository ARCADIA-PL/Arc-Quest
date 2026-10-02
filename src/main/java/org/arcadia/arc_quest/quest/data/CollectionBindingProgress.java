package org.arcadia.arc_quest.quest.data;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.api.CollectionContentBlock;
import java.util.List;

public record CollectionBindingProgress(String bindingId, ResourceLocation entryId,
                                        boolean visible, boolean revealed, boolean discovered,
                                        boolean researchComplete, boolean complete,
                                        List<CollectionRequirementProgress> requirements,
                                        List<CollectionContentBlock> content) {
    public CollectionBindingProgress {
        requirements = List.copyOf(requirements);
        content = List.copyOf(content);
    }
}

package org.arcadia.arc_quest.quest.data;

import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.quest.api.ObjectiveEntry;
import org.jetbrains.annotations.Nullable;

public record CollectionRequirementProgress(String requirementId, Component label,
                                            @Nullable ObjectiveEntry objective, int objectiveIndex,
                                            int current, int target, boolean complete, boolean optional) { }

package org.arcadia.arc_quest.quest.api;

import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.quest.capability.CollectionRuntimeData;
import org.arcadia.arc_quest.quest.capability.IQuestCapability;
import org.arcadia.arc_quest.quest.capability.QuestRuntimeData;

import javax.annotation.Nullable;
import java.util.Objects;

public final class CollectionRuleContext {

    private final ServerPlayer player;
    private final QuestDefinition questDefinition;
    private final QuestRuntimeData questRuntimeData;
    private final CollectionRuntimeData collectionRuntimeData;
    private final IQuestCapability capability;
    @Nullable
    private final String categoryId;

    public CollectionRuleContext(ServerPlayer player,
                                 QuestDefinition questDefinition,
                                 QuestRuntimeData questRuntimeData,
                                 CollectionRuntimeData collectionRuntimeData,
                                 IQuestCapability capability,
                                 @Nullable String categoryId) {
        this.player = Objects.requireNonNull(player);
        this.questDefinition = Objects.requireNonNull(questDefinition);
        this.questRuntimeData = Objects.requireNonNull(questRuntimeData);
        this.collectionRuntimeData = Objects.requireNonNull(collectionRuntimeData);
        this.capability = Objects.requireNonNull(capability);
        this.categoryId = categoryId;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public QuestDefinition getQuestDefinition() {
        return questDefinition;
    }

    public QuestRuntimeData getQuestRuntimeData() {
        return questRuntimeData;
    }

    public CollectionRuntimeData getCollectionRuntimeData() {
        return collectionRuntimeData;
    }

    public IQuestCapability getCapability() {
        return capability;
    }

    @Nullable
    public String getCategoryId() {
        return categoryId;
    }
}

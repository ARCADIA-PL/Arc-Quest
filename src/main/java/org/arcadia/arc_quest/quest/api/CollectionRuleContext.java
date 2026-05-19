package org.arcadia.arc_quest.quest.api;

import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.quest.capability.CollectionRuntimeData;
import org.arcadia.arc_quest.capability.ArcQuestPlayer;
import org.arcadia.arc_quest.quest.capability.QuestRuntimeData;

import javax.annotation.Nullable;
import java.util.Objects;

public final class CollectionRuleContext {

    private final ServerPlayer player;
    private final QuestDefinition questDefinition;
    private final QuestRuntimeData questRuntimeData;
    private final CollectionRuntimeData collectionRuntimeData;
    private final ArcQuestPlayer playerData;
    @Nullable
    private final String categoryId;

    public CollectionRuleContext(ServerPlayer player,
                                 QuestDefinition questDefinition,
                                 QuestRuntimeData questRuntimeData,
                                 CollectionRuntimeData collectionRuntimeData,
                                 ArcQuestPlayer playerData,
                                 @Nullable String categoryId) {
        this.player = Objects.requireNonNull(player);
        this.questDefinition = Objects.requireNonNull(questDefinition);
        this.questRuntimeData = Objects.requireNonNull(questRuntimeData);
        this.collectionRuntimeData = Objects.requireNonNull(collectionRuntimeData);
        this.playerData = Objects.requireNonNull(playerData);
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

    public ArcQuestPlayer getPlayerData() {
        return playerData;
    }

    /** @deprecated use {@link #getPlayerData()} */
    @Deprecated
    public ArcQuestPlayer getCapability() {
        return getPlayerData();
    }

    @Nullable
    public String getCategoryId() {
        return categoryId;
    }
}

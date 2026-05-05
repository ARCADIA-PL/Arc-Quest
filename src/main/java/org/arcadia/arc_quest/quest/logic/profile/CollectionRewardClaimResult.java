package org.arcadia.arc_quest.quest.logic.profile;

import org.arcadia.arc_quest.quest.network.QuestRejectCodeDictionary;

import javax.annotation.Nullable;

public final class CollectionRewardClaimResult {

    public enum Status {
        OK,
        INVALID_REWARD_ID,
        NO_COLLECTION_CONFIG,
        NO_COLLECTION_DATA,
        NOT_UNLOCKED,
        ALREADY_CLAIMED,
        NOT_MANUAL_REWARD,
        REWARD_NODE_NOT_FOUND
    }

    private final Status status;
    @Nullable
    private final String rewardNodeId;
    private final boolean changed;

    private CollectionRewardClaimResult(Status status, @Nullable String rewardNodeId, boolean changed) {
        this.status = status;
        this.rewardNodeId = rewardNodeId;
        this.changed = changed;
    }

    public static CollectionRewardClaimResult ok(String rewardNodeId) {
        return new CollectionRewardClaimResult(Status.OK, rewardNodeId, true);
    }

    public static CollectionRewardClaimResult rejected(Status status, @Nullable String rewardNodeId) {
        return new CollectionRewardClaimResult(status, rewardNodeId, false);
    }

    public Status getStatus() {
        return status;
    }

    @Nullable
    public String getRewardNodeId() {
        return rewardNodeId;
    }

    public boolean isChanged() {
        return changed;
    }

    public boolean isOk() {
        return status == Status.OK;
    }

    public QuestRejectCodeDictionary.Code toRejectCode() {
        return switch (status) {
            case OK -> QuestRejectCodeDictionary.Code.OK;
            case NO_COLLECTION_CONFIG, NO_COLLECTION_DATA -> QuestRejectCodeDictionary.Code.NOT_ACTIVE;
            case INVALID_REWARD_ID, NOT_UNLOCKED, ALREADY_CLAIMED, NOT_MANUAL_REWARD, REWARD_NODE_NOT_FOUND -> QuestRejectCodeDictionary.Code.UNKNOWN;
        };
    }
}

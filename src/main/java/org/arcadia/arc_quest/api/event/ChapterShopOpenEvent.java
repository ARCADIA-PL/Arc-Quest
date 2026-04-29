package org.arcadia.arc_quest.api.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;
import org.arcadia.arc_quest.quest.network.QuestRejectCodeDictionary;

public class ChapterShopOpenEvent extends Event {
    private final ServerPlayer player;
    private final String questId;
    private final String shopId;
    private final QuestRejectCodeDictionary.Code code;

    public ChapterShopOpenEvent(ServerPlayer player, String questId, String shopId, QuestRejectCodeDictionary.Code code) {
        this.player = player;
        this.questId = questId;
        this.shopId = shopId;
        this.code = code;
    }

    public ServerPlayer getPlayer() { return player; }
    public String getQuestId() { return questId; }
    public String getShopId() { return shopId; }
    public QuestRejectCodeDictionary.Code getCode() { return code; }
    public boolean isSuccess() { return code == QuestRejectCodeDictionary.Code.OK; }
}

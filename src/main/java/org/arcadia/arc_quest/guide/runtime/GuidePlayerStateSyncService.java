package org.arcadia.arc_quest.guide.runtime;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.arcadia.arc_quest.guide.network.S2CSyncGuideStatePacket;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;

import java.util.ArrayList;
import java.util.List;

public final class GuidePlayerStateSyncService {

    private GuidePlayerStateSyncService() {
    }

    public static void sync(ServerPlayer player) {
        ArcQuestPlayer data = ArcQuestPlayerManager.getOrCreate(player);
        sync(player, data);
    }

    public static void sync(ServerPlayer player, ArcQuestPlayer data) {
        List<ResourceLocation> unlocked = new ArrayList<>(data.getUnlockedGuides());
        List<ResourceLocation> seen = new ArrayList<>(data.getSeenGuides());
        PacketDistributor.sendToPlayer(player, new S2CSyncGuideStatePacket(unlocked, seen));
    }
}

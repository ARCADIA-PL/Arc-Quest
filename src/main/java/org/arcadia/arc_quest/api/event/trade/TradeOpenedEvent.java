package org.arcadia.arc_quest.api.event.trade;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.eventbus.api.Event;

import javax.annotation.Nullable;

/**
 * 商店打开事件。
 * <p>
 * 当玩家打开商店时触发（服务端）。
 * </p>
 *
 * @since 1.0.0
 */
public class TradeOpenedEvent extends Event {

    private final ServerPlayer player;
    private final String shopId;
    
    /**
     * 关联的 NPC 实体（可能为 null，如果是无 NPC 的商店）。
     */
    @Nullable
    private final Entity npc;

    public TradeOpenedEvent(ServerPlayer player, String shopId, @Nullable Entity npc) {
        this.player = player;
        this.shopId = shopId;
        this.npc = npc;
    }

    /**
     * 获取玩家。
     */
    public ServerPlayer getPlayer() {
        return player;
    }

    /**
     * 获取商店 ID。
     */
    public String getShopId() {
        return shopId;
    }
    
    /**
     * 获取关联的 NPC 实体。
     * <p>
     * 如果商店是通过对话打开的，则返回 NPC 实体；
     * 如果是直接打开的商店，则返回 null。
     * </p>
     * 
     * @return NPC 实体，可能为 null
     */
    @Nullable
    public Entity getNpc() {
        return npc;
    }
    
    /**
     * 检查是否有 NPC 实体。
     * 
     * @return true 如果有关联的 NPC
     */
    public boolean hasNpc() {
        return npc != null;
    }
}

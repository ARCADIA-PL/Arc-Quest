package org.arcadia.arc_quest.quest.event;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;

/** Server-confirmed positive inventory delta, including creative-slot and container transfers. */
public final class PlayerInventoryAcquiredEvent extends PlayerEvent {
    private final ResourceLocation itemId;
    private final int amount;

    public PlayerInventoryAcquiredEvent(ServerPlayer player, ResourceLocation itemId, int amount) {
        super(player);
        this.itemId = itemId;
        this.amount = amount;
    }

    public ResourceLocation getItemId() { return itemId; }
    public int getAmount() { return amount; }
}

package org.arcadia.arc_quest.api.event.quest;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.Event;

/** Cancel quest kill credit without cancelling the entity's actual death or other death listeners. */
public final class QuestKillCreditEvent extends Event implements ICancellableEvent {
    private final ServerPlayer player;
    private final LivingEntity killed;
    private final DamageSource source;

    public QuestKillCreditEvent(ServerPlayer player, LivingEntity killed, DamageSource source) {
        this.player = player;
        this.killed = killed;
        this.source = source;
    }

    public ServerPlayer getPlayer() { return player; }
    public LivingEntity getKilled() { return killed; }
    public DamageSource getSource() { return source; }
}

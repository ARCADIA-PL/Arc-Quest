package org.arcadia.arc_quest.api.event.quest;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;

/** Cancel quest kill credit without cancelling the entity's actual death or other death listeners. */
@Cancelable
public final class QuestKillCreditEvent extends Event {
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

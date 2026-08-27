package com.example.arcqaddon;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import org.arcadia.arc_quest.api.event.registry.ArcQuestRegistrationEvent;
import org.arcadia.arc_quest.dialogue.api.IEntityDialogueExtension;
import org.arcadia.arc_quest.npc.NpcBinding;

public final class ExampleVillagerDialogueExtension
        implements IEntityDialogueExtension<Villager> {

    private static final String ROLE_KEY = "ExampleArcQDialogueRole";
    private static final String FOREMAN_ROLE = "foreman";

    public static void register(ArcQuestRegistrationEvent.Npc event) {
        event.registerExtension(new ExampleVillagerDialogueExtension());
    }

    @Override
    public EntityType<Villager> getEntityType() {
        return EntityType.VILLAGER;
    }

    @Override
    public boolean canInteractWith(Player player, Villager entity) {
        return entity.isAlive()
                && !entity.isBaby()
                && FOREMAN_ROLE.equals(entity.getPersistentData().getString(ROLE_KEY));
    }

    @Override
    public String getDialogueTreeId(ServerPlayer player, Villager entity,
                                    InteractionHand hand, NpcBinding npcBinding) {
        return ExampleDialogueContent.FOREMAN_DIALOGUE_ID.toString();
    }

    @Override
    public int maxTalkDistance() {
        return 6;
    }

    @Override
    public net.minecraft.world.InteractionResult shouldCancelInteract(Player player, Villager entity) {
        return canInteractWith(player, entity)
                ? net.minecraft.world.InteractionResult.SUCCESS
                : net.minecraft.world.InteractionResult.PASS;
    }
}

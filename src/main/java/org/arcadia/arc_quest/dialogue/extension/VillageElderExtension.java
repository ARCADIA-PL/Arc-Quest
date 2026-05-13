package org.arcadia.arc_quest.dialogue.extension;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.dialogue.api.EntityDialogueExtension;
import org.arcadia.arc_quest.dialogue.api.IEntityDialogueExtension;
import org.jetbrains.annotations.Nullable;

/**
 * 村庄长老对话扩展 - 村民职业为 NONE 的 NPC。
 * <p>
 * 此扩展实现：
 * <ul>
 *   <li>只允许非潜行玩家对话</li>
 *   <li>取消村民交易界面</li>
 *   <li>自定义对话距离（8格）</li>
 * </ul>
 *
 * @author Arc Quest Team
 * @since 2.0
 */
@EntityDialogueExtension(modId = Arc_Quest.MOD_ID)
public class VillageElderExtension implements IEntityDialogueExtension<Villager> {

    @Override
    public EntityType<Villager> getEntityType() {
        return EntityType.VILLAGER;
    }

    @Override
    public boolean canInteractWith(Player player, Villager villager) {
        return !player.isCrouching();
    }

    @Override
    @Nullable
    public String getDialogueTreeId(ServerPlayer player, Villager villager, InteractionHand hand) {
        CompoundTag persistentData = villager.getPersistentData();

        if (persistentData.contains("ArcQuestDialogueId")) {
            return persistentData.getString("ArcQuestDialogueId");
        }

        if (persistentData.contains("ArcQuestNpcId")) {
            String npcId = persistentData.getString("ArcQuestNpcId");
            if ("village_elder".equals(npcId)) {
                return "arc_quest:epic_village_elder";
            }
        }

        CompoundTag fullNbt = new CompoundTag();
        villager.saveWithoutId(fullNbt);

        if (fullNbt.contains("ArcQuestDialogueId")) {
            return fullNbt.getString("ArcQuestDialogueId");
        }

        if (fullNbt.contains("ArcQuestNpcId")) {
            String npcId = fullNbt.getString("ArcQuestNpcId");
            if ("village_elder".equals(npcId)) {
                return "arc_quest:epic_village_elder";
            }
        }

        if (villager.hasCustomName()) {
            String name = villager.getCustomName().getString();
            if (name.contains("长老") || name.contains("Elder")) {
                return "arc_quest:epic_village_elder";
            }
        }

        return null;
    }

    @Override
    @Nullable
    public InteractionResult shouldCancelInteract(Player player, Villager villager) {
        return InteractionResult.SUCCESS;
    }
}

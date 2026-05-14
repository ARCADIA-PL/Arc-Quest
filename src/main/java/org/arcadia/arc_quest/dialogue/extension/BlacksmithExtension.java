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
import org.arcadia.arc_quest.npc.NpcBinding;
import org.jetbrains.annotations.Nullable;

/**
 * 铁匠对话扩展 - 村民职业为 ARMORER 的 NPC。
 * <p>
 * 此扩展实现：
 * <ul>
 *   <li>只允许非潜行玩家对话</li>
 *   <li>取消村民交易界面</li>
 *   <li>根据职业自动选择对话树</li>
 * </ul>
 *
 * @author Arc Quest Team
 * @since 2.0
 */
@EntityDialogueExtension(modId = Arc_Quest.MOD_ID)
public class BlacksmithExtension implements IEntityDialogueExtension<Villager> {

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
    public String getDialogueTreeId(ServerPlayer player, Villager villager, InteractionHand hand, NpcBinding npcBinding) {
        CompoundTag persistentData = villager.getPersistentData();

        if (persistentData.contains("ArcQuestDialogueId")) {
            return npcBinding.bindDialogueForNpc(
                    "arc_quest:blacksmith_nbt_dialogue_id",
                    persistentData.getString("ArcQuestDialogueId"));
        }

        if (persistentData.contains("ArcQuestNpcId")) {
            String npcId = persistentData.getString("ArcQuestNpcId");
            if ("blacksmith".equals(npcId)) {
                return npcBinding.bindDialogueForNpc(
                        "arc_quest:blacksmith_npc_id",
                        "arc_quest:epic_blacksmith");
            }
        }

        CompoundTag fullNbt = new CompoundTag();
        villager.saveWithoutId(fullNbt);

        if (fullNbt.contains("ArcQuestDialogueId")) {
            return npcBinding.bindDialogueForNpc(
                    "arc_quest:blacksmith_full_nbt_dialogue_id",
                    fullNbt.getString("ArcQuestDialogueId"));
        }

        if (fullNbt.contains("ArcQuestNpcId")) {
            String npcId = fullNbt.getString("ArcQuestNpcId");
            if ("blacksmith".equals(npcId)) {
                return npcBinding.bindDialogueForNpc(
                        "arc_quest:blacksmith_full_nbt_npc_id",
                        "arc_quest:epic_blacksmith");
            }
        }

        if (villager.hasCustomName()) {
            String name = villager.getCustomName().getString();
            if (name.contains("铁匠") || name.contains("Blacksmith")) {
                return npcBinding.bindDialogueForNpc(
                        "arc_quest:blacksmith_custom_name",
                        "arc_quest:epic_blacksmith");
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

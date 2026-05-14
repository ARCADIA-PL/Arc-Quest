package org.arcadia.arc_quest.dialogue.extension;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.dialogue.api.EntityDialogueExtension;
import org.arcadia.arc_quest.dialogue.api.IEntityDialogueExtension;
import org.arcadia.arc_quest.dialogue.api.ProgressScope;
import org.arcadia.arc_quest.npc.NpcBinding;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * 商人对话扩展 - 流浪商人。
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
public class MysteriousTraderExtension implements IEntityDialogueExtension<WanderingTrader> {

    @Override
    public EntityType<WanderingTrader> getEntityType() {
        return EntityType.WANDERING_TRADER;
    }

    @Override
    public boolean canInteractWith(Player player, WanderingTrader villager) {
        return !player.isCrouching();
    }

    @Override
    @Nullable
    public String getDialogueTreeId(ServerPlayer player, WanderingTrader wanderingTrader, InteractionHand hand, NpcBinding npcBinding) {
        CompoundTag persistentData = wanderingTrader.getPersistentData();

        if (persistentData.contains("ArcQuestDialogueId")) {
            return npcBinding.bindDialogueForNpc(
                    "arc_quest:mysterious_trader_nbt_dialogue_id",
                    persistentData.getString("ArcQuestDialogueId"));
        }

        if (persistentData.contains("ArcQuestNpcId")) {
            String npcId = persistentData.getString("ArcQuestNpcId");
            if ("mysterious_merchant".equals(npcId)) {
                return npcBinding.bindDialogueForNpc(
                        "arc_quest:mysterious_trader_npc_id",
                        "arc_quest:epic_mysterious_merchant");
            }
        }

        CompoundTag fullNbt = new CompoundTag();
        wanderingTrader.saveWithoutId(fullNbt);

        if (fullNbt.contains("ArcQuestDialogueId")) {
            return npcBinding.bindDialogueForNpc(
                    "arc_quest:mysterious_trader_full_nbt_dialogue_id",
                    fullNbt.getString("ArcQuestDialogueId"));
        }

        if (fullNbt.contains("ArcQuestNpcId")) {
            String npcId = fullNbt.getString("ArcQuestNpcId");
            if ("mysterious_merchant".equals(npcId)) {
                return npcBinding.bindDialogueForNpc(
                        "arc_quest:mysterious_trader_full_nbt_npc_id",
                        "arc_quest:epic_mysterious_merchant");
            }
        }

        if (wanderingTrader.hasCustomName()) {
            String name = Objects.requireNonNull(wanderingTrader.getCustomName()).getString();
            if (name.contains("神秘人") || name.contains("MysteriousMerchant")) {
                return npcBinding.bindDialogueForNpc(
                        "arc_quest:mysterious_trader_custom_name",
                        "arc_quest:epic_mysterious_merchant");
            }
        }

        return null;
    }

    @Override
    public ProgressScope getProgressScope() {
        return ProgressScope.INSTANCE;
    }

    @Override
    @Nullable
    public InteractionResult shouldCancelInteract(Player player, WanderingTrader villager) {
        return InteractionResult.SUCCESS;
    }
}

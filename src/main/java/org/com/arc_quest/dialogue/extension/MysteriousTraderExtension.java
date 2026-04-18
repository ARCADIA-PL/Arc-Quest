package org.com.arc_quest.dialogue.extension;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import org.com.arc_quest.Arc_quest;
import org.com.arc_quest.dialogue.api.EntityDialogueExtension;
import org.com.arc_quest.dialogue.api.IEntityDialogueExtension;
import org.com.arc_quest.dialogue.api.ProgressScope;
import org.jetbrains.annotations.Nullable;

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
@EntityDialogueExtension(modId = Arc_quest.MOD_ID)
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
    public String getDialogueTreeId(ServerPlayer player, WanderingTrader wanderingTrader, InteractionHand hand) {
        // 1. 优先检查 PersistentData
        var persistentData = wanderingTrader.getPersistentData();
        if (persistentData.contains("ArcQuestNpcId")) {
            String npcId = persistentData.getString("ArcQuestNpcId");
            if ("mysterious_merchant".equals(npcId)) {
                return "epic_mysterious_merchant";
            }
        }

        // 2. 尝试从根 NBT 读取 ArcQuestNpcId
        try {
            CompoundTag fullNbt = new CompoundTag();
            wanderingTrader.save(fullNbt);
            if (fullNbt.contains("ArcQuestNpcId")) {
                String npcId = fullNbt.getString("ArcQuestNpcId");
                if ("mysterious_merchant".equals(npcId)) {
                    return "epic_mysterious_merchant";
                }
            }
        } catch (Exception e) {
            Arc_quest.LOGGER.debug("[TraderExtension] Failed to read root NBT: {}", e.getMessage());
        }

        // 3. 通过自定义名称识别（最简单）
        if (wanderingTrader.hasCustomName()) {
            String name = wanderingTrader.getCustomName().getString();
            if (name.contains("神秘人") || name.contains("MysteriousMerchant")) {
                return "epic_mysterious_merchant";
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

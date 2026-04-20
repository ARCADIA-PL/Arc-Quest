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
@EntityDialogueExtension(modId = Arc_quest.MOD_ID)
public class TraderExtension implements IEntityDialogueExtension<WanderingTrader> {

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
            if ("merchant".equals(npcId)) {
                return "arc_quest:epic_merchant";
            }
            if ("wandering_trader".equals(npcId)) {
                return "arc_quest:epic_wandering_trader";
            }
        }

        // 2. 尝试从根 NBT 读取 ArcQuestNpcId
        try {
            CompoundTag fullNbt = new CompoundTag();
            wanderingTrader.save(fullNbt);
            if (fullNbt.contains("ArcQuestNpcId")) {
                String npcId = fullNbt.getString("ArcQuestNpcId");
                if ("merchant".equals(npcId)) {
                    return "arc_quest:epic_merchant";
                }
                if ("wandering_trader".equals(npcId)) {
                    return "arc_quest:epic_wandering_trader";
                }
            }
        } catch (Exception e) {
            Arc_quest.LOGGER.debug("[TraderExtension] Failed to read root NBT: {}", e.getMessage());
        }

        // 3. 通过自定义名称识别（最简单）
        if (wanderingTrader.hasCustomName()) {
            String name = Objects.requireNonNull(wanderingTrader.getCustomName()).getString();
            if (name.contains("商人") || name.contains("Merchant")) {
                return "arc_quest:epic_merchant";
            }
            if (name.contains("行商") || name.contains("WanderingTrader")) {
                return "arc_quest:epic_wandering_trader";
            }
        }

        return null;
    }

    @Override
    @Nullable
    public InteractionResult shouldCancelInteract(Player player, WanderingTrader villager) {
        return InteractionResult.SUCCESS;
    }
}

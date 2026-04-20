package org.com.arc_quest.dialogue.extension;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import org.com.arc_quest.Arc_quest;
import org.com.arc_quest.dialogue.api.EntityDialogueExtension;
import org.com.arc_quest.dialogue.api.IEntityDialogueExtension;
import org.com.arc_quest.dialogue.api.ProgressScope;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

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
@EntityDialogueExtension(modId = Arc_quest.MOD_ID)
public class GuardExtension implements IEntityDialogueExtension<Villager> {

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
        // 1. 优先检查 PersistentData
        if (villager.getPersistentData().contains("ArcQuestNpcId")) {
            String npcId = villager.getPersistentData().getString("ArcQuestNpcId");
            if ("village_guard".equals(npcId)) {
                return "arc_quest:epic_village_guard";
            }
        }

        // 2. 尝试从根 NBT 读取 ArcQuestNpcId
        CompoundTag fullNbt = new CompoundTag();
        villager.save(fullNbt);
        if (fullNbt.contains("ArcQuestNpcId")) {
            String npcId = fullNbt.getString("ArcQuestNpcId");
            if ("village_guard".equals(npcId)) {
                return "arc_quest:epic_village_guard";
            }
        }

        // 3. 通过自定义名称识别
        if (villager.hasCustomName()) {
            String name = Objects.requireNonNull(villager.getCustomName()).getString();
            if (name.contains("村庄守卫") || name.contains("Village Guard")) {
                return "arc_quest:epic_village_guard";
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
    public InteractionResult shouldCancelInteract(Player player, Villager villager) {
        return InteractionResult.SUCCESS;
    }
}

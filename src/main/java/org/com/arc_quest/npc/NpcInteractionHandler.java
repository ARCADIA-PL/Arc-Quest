package org.com.arc_quest.npc;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.com.arc_quest.Arc_quest;
import org.com.arc_quest.dialogue.api.DialogueTree;
import org.com.arc_quest.dialogue.registry.DialogueRegistry;
import org.com.arc_quest.dialogue.runtime.DialogueSessionManager;
import org.slf4j.Logger;

/**
 * NPC 右键交互钩子。
 * <p>
 * 检测方式（优先级从高到低）：
 * <ol>
 *   <li>实体 NBT 标签 {@code ArcQuestNpcId} → 查询 DialogueRegistry 绑定</li>
 *   <li>实体 NBT 标签 {@code ArcQuestDialogueId} → 直接指定对话树</li>
 *   <li>实体的 CustomName → 作为 NPC ID 查询绑定</li>
 * </ol>
 * <p>
 * 用法：给任意实体加 NBT 标签即可变成任务 NPC。
 * <pre>
 * /data merge entity @e[type=minecraft:villager,limit=1] {ArcQuestNpcId:"elder_001"}
 * </pre>
 */
@Mod.EventBusSubscriber(modid = Arc_quest.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class NpcInteractionHandler {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TAG_NPC_ID = "ArcQuestNpcId";
    private static final String TAG_DIALOGUE_ID = "ArcQuestDialogueId";

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        Entity target = event.getTarget();
        if (!(target instanceof LivingEntity)) return;

        // 如果玩家已在对话中，忽略
        if (DialogueSessionManager.INSTANCE.isInDialogue(player)) return;

        String dialogueId = resolveDialogueId(target);
        if (dialogueId == null) return;

        DialogueTree tree = DialogueRegistry.INSTANCE.get(dialogueId);
        if (tree == null) {
            LOGGER.debug("[NPC] Dialogue '{}' not found for entity {}.",
                    dialogueId, target.getName().getString());
            return;
        }

        // 开始对话
        DialogueSessionManager.INSTANCE.startDialogue(player, tree);
        event.setCanceled(true); // 阻止原版交互
        LOGGER.debug("[NPC] Player '{}' started dialogue '{}' with '{}'.",
                player.getName().getString(), dialogueId, target.getName().getString());
    }

    private static String resolveDialogueId(Entity entity) {
        CompoundTag persistentData = entity.getPersistentData();

        // 方式 1：直接指定对话 ID
        if (persistentData.contains(TAG_DIALOGUE_ID)) {
            return persistentData.getString(TAG_DIALOGUE_ID);
        }

        // 方式 2：通过 NPC ID 查询绑定
        if (persistentData.contains(TAG_NPC_ID)) {
            String npcId = persistentData.getString(TAG_NPC_ID);
            return DialogueRegistry.INSTANCE.getDialogueForNpc(npcId);
        }

        // 方式 3：使用 CustomName 作为 NPC ID
        if (entity.hasCustomName()) {
            String customName = entity.getCustomName().getString();
            String binding = DialogueRegistry.INSTANCE.getDialogueForNpc(customName);
            if (binding != null) return binding;
        }

        return null;
    }
}
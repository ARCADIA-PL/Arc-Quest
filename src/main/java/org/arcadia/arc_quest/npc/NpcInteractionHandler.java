package org.arcadia.arc_quest.npc;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.dialogue.api.DialogueTree;
import org.arcadia.arc_quest.dialogue.capability.DialogueNpcPatch;
import org.arcadia.arc_quest.dialogue.registry.DialogueRegistry;
import org.arcadia.arc_quest.dialogue.registry.EntityDialogueExtensionManager;
import org.arcadia.arc_quest.dialogue.runtime.DialogueSessionManager;
import org.arcadia.arc_quest.npc.runtime.NpcBindingRegistry;
import org.slf4j.Logger;

/**
 * NPC 右键交互钩子 —— 无代码扩展的实体类型兜底。
 * <p>
 * 对话 ID 解析统一走 {@link NpcBindingRegistry}，
 * 此处理器仅在实体类型无 {@code IEntityDialogueExtension} 时生效。
 */
@Mod.EventBusSubscriber(modid = Arc_Quest.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class NpcInteractionHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        Entity target = event.getTarget();
        if (!(target instanceof LivingEntity)) return;

        if (DialogueSessionManager.INSTANCE.isInDialogue(player)) return;

        if (EntityDialogueExtensionManager.INSTANCE.hasExtensionsForEntityType(target.getType())) {
            return;
        }

        String dialogueId = NpcBindingRegistry.INSTANCE.resolveDialogueId(target, player);
        if (dialogueId == null) return;

        DialogueTree tree = DialogueRegistry.INSTANCE.get(dialogueId);
        if (tree == null) {
            LOGGER.debug("[NPC] Dialogue '{}' not found for entity {}.",
                    dialogueId, target.getName().getString());
            return;
        }

        ensureDialogueNpcPatch(target, player);

        DialogueSessionManager.INSTANCE.startDialogue(player, tree);
        event.setCanceled(true);
        LOGGER.debug("[NPC] Player '{}' started dialogue '{}' with '{}'.",
                player.getName().getString(), dialogueId, target.getName().getString());
    }

    private static void ensureDialogueNpcPatch(Entity entity, ServerPlayer player) {
        entity.getCapability(DialogueNpcPatch.CAPABILITY).ifPresent(patch -> {
            patch.setConversing(player);
        });
    }
}
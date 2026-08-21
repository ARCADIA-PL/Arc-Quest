package org.arcadia.arc_quest.dialogue.data;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.api.event.dialogue.DialogueEndedEvent;
import org.arcadia.arc_quest.api.event.registry.ArcQuestRegistrationEvent;
import org.arcadia.arc_quest.dialogue.api.DialogueContext;
import org.arcadia.arc_quest.dialogue.api.DialogueTree;
import org.arcadia.arc_quest.dialogue.api.IEntityDialogueExtension;
import org.arcadia.arc_quest.dialogue.api.IDialogueNpc;
import org.arcadia.arc_quest.dialogue.registry.DialogueRegistry;
import org.arcadia.arc_quest.dialogue.registry.EntityDialogueExtensionManager;
import org.arcadia.arc_quest.dialogue.runtime.DialogueSession;
import org.arcadia.arc_quest.dialogue.runtime.DialogueSessionManager;
import org.arcadia.arc_quest.dialogue.util.AnnotatedInstanceUtil;
import org.arcadia.arc_quest.npc.NpcBinding;
import org.arcadia.arc_quest.npc.runtime.NpcBindingRegistry;
import org.arcadia.arc_quest.npc.runtime.NpcResolution;
import org.arcadia.arc_quest.npc.spec.NpcInteractionPolicy;
import org.arcadia.arc_quest.npc.spec.NpcSpec;
import org.slf4j.Logger;

import java.util.List;
import javax.annotation.Nullable;

/**
 * 实体对话扩展系统 - 处理扩展注册、实体交互和 tick 更新。
 * <p>
 * 取代了原有的简单 Capability 附加逻辑，提供完整的扩展系统支持。
 *
 * @author Arc Quest Team
 * @since 2.0
 */
@EventBusSubscriber(modid = Arc_Quest.MOD_ID)
public class EntityDialogueExtensionHandler {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation CAP_ID = ResourceLocation.fromNamespaceAndPath(
            Arc_Quest.MOD_ID, "dialogue_npc_patch");

    /**
     * 处理玩家与实体的交互事件。
     * <p>
     * 统一入口：代码扩展优先，无匹配时 fallback 到数据包注册表。
     * 行为控制（取消交互、注视、移动等）由匹配到的扩展或 NpcSpec 决定。
     */
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        Entity target = event.getTarget();
        if (!(target instanceof LivingEntity)) return;

        if (DialogueSessionManager.INSTANCE.isInDialogue(player)) return;

        boolean hasExtensions = EntityDialogueExtensionManager.INSTANCE.hasExtensionsForEntityType(target.getType());
        NpcResolution datapackResolution = null;

        boolean canInteract = true;
        if (hasExtensions) {
            for (var extension : EntityDialogueExtensionManager.INSTANCE.getExtensionsForEntityType(target.getType())) {
                @SuppressWarnings("unchecked")
                IEntityDialogueExtension<Entity> ext = (IEntityDialogueExtension<Entity>) extension;
                if (!ext.canInteractWith(player, target)) {
                    canInteract = false;
                    break;
                }
            }
        } else {
            datapackResolution = NpcBindingRegistry.INSTANCE.resolve(target, player);
            canInteract = datapackResolution.matched();
        }
        if (!canInteract) return;

        NpcBinding npcBinding = new NpcBinding();
        String dialogueId = null;
        IEntityDialogueExtension<Entity> matchedExt = null;
        NpcInteractionPolicy interactionPolicy = NpcInteractionPolicy.PARALLEL_PRIVATE;

        if (hasExtensions) {
            for (var extension : EntityDialogueExtensionManager.INSTANCE.getExtensionsForEntityType(target.getType())) {
                @SuppressWarnings("unchecked")
                IEntityDialogueExtension<Entity> ext = (IEntityDialogueExtension<Entity>) extension;
                dialogueId = ext.getDialogueTreeId(player, target, event.getHand(), npcBinding);
                if (dialogueId != null) {
                    matchedExt = ext;
                    interactionPolicy = ext.interactionPolicy(player, target);
                    break;
                }
            }

            for (NpcBinding.Entry entry : npcBinding.getEntries()) {
                NpcBindingRegistry.INSTANCE.registerCodeBindingId(entry.bindingId());
            }
        }

        if (dialogueId == null) {
            if (datapackResolution == null) {
                datapackResolution = NpcBindingRegistry.INSTANCE.resolve(target, player);
            }
            dialogueId = datapackResolution.dialogueId();
            if (datapackResolution.npcSpec() != null && datapackResolution.npcSpec().interactionPolicy != null) {
                interactionPolicy = datapackResolution.npcSpec().interactionPolicy;
            }
        }

        if (dialogueId == null) return;

        DialogueTree tree = DialogueRegistry.INSTANCE.get(dialogueId);
        if (tree == null) {
            LOGGER.warn("[EntityDialogueExtension] Dialogue '{}' not found for entity {}",
                    dialogueId, target.getName().getString());
            return;
        }

        DialogueContext ctx = new DialogueContext();
        ctx.put("npcName", target.getDisplayName().getString());
        ctx.put("defaultNpc", target.getDisplayName().getString());

        DialogueSession session = DialogueSessionManager.INSTANCE.startDialogue(
                player, target, tree.dialogueId(), ctx, interactionPolicy);
        if (session == null) {
            LOGGER.warn("[EntityDialogueExtension] Failed to start resolved dialogue: player={}, entityRef={}, dialogueId={}",
                    player.getUUID(), datapackResolution != null ? datapackResolution.entityRef() : target.getUUID(),
                    dialogueId);
            return;
        }

        if (matchedExt != null) {
            matchedExt.onDialogueStart(player, target, session);

            InteractionResult cancelResult = matchedExt.shouldCancelInteract(player, target);
            if (cancelResult != null) {
                event.setCancellationResult(cancelResult);
                event.setCanceled(true);
            }
        } else {
            NpcSpec npcSpec = datapackResolution != null ? datapackResolution.npcSpec() : null;
            if (npcSpec != null && npcSpec.cancelVanillaInteract) {
                event.setCancellationResult(InteractionResult.SUCCESS);
                event.setCanceled(true);
            }
        }

        for (NpcBinding.Entry entry : npcBinding.getEntries()) {
            LOGGER.debug("[EntityDialogueExtension] Binding hit: {} -> {}", entry.bindingId(), entry.dialogueId());
        }

        List<NpcBinding.Entry> bindingEntries = npcBinding.getEntries();
        if (bindingEntries.isEmpty()) {
            LOGGER.info("[EntityDialogueExtension] Dialogue started. tree={}, source=datapack_registry", dialogueId);
        } else {
            LOGGER.info("[EntityDialogueExtension] Dialogue started. tree={}, bindings={}",
                    dialogueId,
                    bindingEntries.stream()
                            .map(e -> e.bindingId() + "->" + e.dialogueId())
                            .toList());
        }

        LOGGER.debug("[EntityDialogueExtension] Player '{}' started dialogue '{}' with '{}'",
                player.getName().getString(), dialogueId, target.getName().getString());
    }

    @SubscribeEvent
    public static void onDialogueEnded(DialogueEndedEvent event) {
        Entity npc = event.getNpc();
        if (npc == null) return;

        if (EntityDialogueExtensionManager.INSTANCE.hasExtensionsForEntityType(npc.getType())) {
            return;
        }

        NpcSpec npcSpec = resolveNpcSpec(npc, event.getPlayer());
        if (npcSpec != null) {
            executeCommands(npcSpec.onDialogueEndCommands, event.getPlayer());
        }
    }

    /**
     * Tick 事件 - 按实体更新对话中的 NPC（更稳定的时序）
     */
    @SubscribeEvent
    public static void onLivingTick(EntityTickEvent.Pre event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity() instanceof LivingEntity livingEntity)) return;

        List<ServerPlayer> participants = DialogueNpcStateManager.getParticipants(livingEntity);
        if (participants.isEmpty()) return;

        for (ServerPlayer participant : List.copyOf(participants)) {
            if (!participant.isAlive()
                    || !participant.level().dimension().equals(livingEntity.level().dimension())) {
                DialogueSessionManager.INSTANCE.endDialogue(participant);
                continue;
            }
            DialogueSessionManager.INSTANCE.heartbeat(participant);
            checkDistance(livingEntity, participant);
        }

        DialogueNpcStateManager.State state = DialogueNpcStateManager.get(livingEntity);
        if (state == null || state.conversingPlayer() == null) return;
        ServerPlayer serverPlayer = (ServerPlayer) state.conversingPlayer();

        controlNpcBehavior(livingEntity, serverPlayer);
        callExtensionOnTick(livingEntity, serverPlayer);
    }

    /**
     * 检查距离，超过最大距离则终止对话
     */
    private static void checkDistance(LivingEntity entity, ServerPlayer player) {
        double maxDist = entity instanceof IDialogueNpc npc
                ? npc.getMaxDialogueDistance()
                : 5.0;

        if (EntityDialogueExtensionManager.INSTANCE.hasExtensionsForEntityType(entity.getType())) {
            var extensions = EntityDialogueExtensionManager.INSTANCE.getExtensionsForEntityType(entity.getType());
            for (var ext : extensions) {
                maxDist = ext.maxTalkDistance();
                break;
            }
        } else {
            NpcSpec npcSpec = resolveNpcSpec(entity, player);
            if (npcSpec != null) {
                maxDist = npcSpec.dialogueDistance;
            }
        }

        if (entity.distanceTo(player) > maxDist + 2.0) {
            DialogueSessionManager.INSTANCE.endDialogue(player);
            DialogueNpcStateManager.clear(entity, player);
        }
    }

    /**
     * 控制 NPC 行为（注视玩家、停止移动）
     */
    @SuppressWarnings("unchecked")
    private static void controlNpcBehavior(LivingEntity entity, ServerPlayer player) {
        if (!(entity instanceof Mob mob)) return;

        DialogueNpcStateManager.State state = DialogueNpcStateManager.get(entity);
        if (state == null || state.conversingPlayer() == null) return;

        boolean lookAt = true;
        boolean stopMoving = true;

        if (EntityDialogueExtensionManager.INSTANCE.hasExtensionsForEntityType(entity.getType())) {
            var extensions = EntityDialogueExtensionManager.INSTANCE.getExtensionsForEntityType(entity.getType());
            for (var ext : extensions) {
                IEntityDialogueExtension<LivingEntity> livingExt = (IEntityDialogueExtension<LivingEntity>) ext;
                lookAt = livingExt.shouldLookAtPlayer(player, entity);
                stopMoving = livingExt.shouldStopMoving(player, entity);
                break;
            }
        } else {
            NpcSpec npcSpec = resolveNpcSpec(entity, player);
            if (npcSpec != null) {
                lookAt = npcSpec.shouldLookAtPlayer;
                stopMoving = npcSpec.shouldStopMoving;
            }
        }

        if (lookAt) {
            mob.getLookControl().setLookAt(player);
        }
        if (stopMoving) {
            mob.getNavigation().stop();
        }
    }

    /**
     * 调用扩展的 onTalkingTick 方法
     */
    @SuppressWarnings("unchecked")
    private static void callExtensionOnTick(LivingEntity entity, ServerPlayer player) {
        if (!EntityDialogueExtensionManager.INSTANCE.hasExtensionsForEntityType(entity.getType())) return;
        var extensions = EntityDialogueExtensionManager.INSTANCE.getExtensionsForEntityType(entity.getType());
        for (var ext : extensions) {
            IEntityDialogueExtension<LivingEntity> livingExt = (IEntityDialogueExtension<LivingEntity>) ext;
            livingExt.onTalkingTick(player, entity);
            break;
        }
    }

    /**
     * 确保实体有 DialogueNpcPatch 并设置对话状态
     */
    private static void executeCommands(List<String> commands, ServerPlayer player) {
        if (commands == null || commands.isEmpty()) return;
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        for (String cmd : commands) {
            if (cmd == null || cmd.isBlank()) continue;
            String resolved = cmd.replace("@p", player.getName().getString());
            server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack().withEntity(player), resolved);
        }
    }

    @Nullable
    private static NpcSpec resolveNpcSpec(Entity entity, ServerPlayer player) {
        return NpcBindingRegistry.INSTANCE.resolveSpec(entity, player);
    }

    @EventBusSubscriber(modid = Arc_Quest.MOD_ID)
    public static class ModBusEvents {

        /**
         * 模组加载完成时扫描并注册所有扩展
         */
        @SubscribeEvent
        public static void onNpcRegistration(ArcQuestRegistrationEvent.Npc event) {
            List<IEntityDialogueExtension<?>> extensions = AnnotatedInstanceUtil.getModEntityExtensions();

            if (!extensions.isEmpty()) {
                EntityDialogueExtensionManager.INSTANCE.registerAll(extensions);
                LOGGER.info("[EntityDialogueExtension] Auto-registered {} extensions via annotation scanning",
                        extensions.size());
            } else {
                LOGGER.debug("[EntityDialogueExtension] No annotated extensions found. Use manual registration.");
            }
        }
    }

    @SubscribeEvent
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        for (ServerPlayer participant : DialogueNpcStateManager.getParticipants(event.getEntity())) {
            DialogueSessionManager.INSTANCE.endDialogue(participant);
        }
        DialogueNpcStateManager.clear(event.getEntity());
    }
}

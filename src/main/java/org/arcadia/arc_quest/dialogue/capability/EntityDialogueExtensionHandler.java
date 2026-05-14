package org.arcadia.arc_quest.dialogue.capability;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.api.event.dialogue.DialogueEndedEvent;
import org.arcadia.arc_quest.dialogue.api.DialogueContext;
import org.arcadia.arc_quest.dialogue.api.DialogueTree;
import org.arcadia.arc_quest.dialogue.api.IEntityDialogueExtension;
import org.arcadia.arc_quest.dialogue.registry.DialogueRegistry;
import org.arcadia.arc_quest.dialogue.registry.EntityDialogueExtensionManager;
import org.arcadia.arc_quest.dialogue.runtime.DialogueSession;
import org.arcadia.arc_quest.dialogue.runtime.DialogueSessionManager;
import org.arcadia.arc_quest.dialogue.util.AnnotatedInstanceUtil;
import org.arcadia.arc_quest.npc.NpcBinding;
import org.arcadia.arc_quest.npc.runtime.NpcBindingRegistry;
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
@Mod.EventBusSubscriber(modid = Arc_Quest.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class EntityDialogueExtensionHandler {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation CAP_ID = ResourceLocation.fromNamespaceAndPath(
            Arc_Quest.MOD_ID, "dialogue_npc_patch");

    /**
     * 附着 Capability 到实体
     */
    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof LivingEntity) {
            if (!event.getObject().getCapability(DialogueNpcPatch.CAPABILITY).isPresent()) {
                event.addCapability(CAP_ID, new DialogueNpcPatchProvider(event.getObject()));
            }
        }
    }

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
            NpcSpec npcSpec = resolveNpcSpec(target, player);
            if (npcSpec != null && npcSpec.interactCondition != null && !npcSpec.interactCondition.isAlways()) {
                canInteract = false;
            }
        }
        if (!canInteract) return;

        NpcBinding npcBinding = new NpcBinding();
        String dialogueId = null;
        IEntityDialogueExtension<Entity> matchedExt = null;

        if (hasExtensions) {
            for (var extension : EntityDialogueExtensionManager.INSTANCE.getExtensionsForEntityType(target.getType())) {
                @SuppressWarnings("unchecked")
                IEntityDialogueExtension<Entity> ext = (IEntityDialogueExtension<Entity>) extension;
                dialogueId = ext.getDialogueTreeId(player, target, event.getHand(), npcBinding);
                if (dialogueId != null) {
                    matchedExt = ext;
                    break;
                }
            }

            for (NpcBinding.Entry entry : npcBinding.getEntries()) {
                NpcBindingRegistry.INSTANCE.registerCodeBindingId(entry.bindingId());
            }
        }

        if (dialogueId == null) {
            dialogueId = NpcBindingRegistry.INSTANCE.resolveDialogueId(target, player);
        }

        if (dialogueId == null) return;

        DialogueTree tree = DialogueRegistry.INSTANCE.get(dialogueId);
        if (tree == null) {
            LOGGER.warn("[EntityDialogueExtension] Dialogue '{}' not found for entity {}",
                    dialogueId, target.getName().getString());
            return;
        }

        ensureDialogueNpcPatch(target, player);

        DialogueContext ctx = new DialogueContext();
        ctx.put("npcName", target.getDisplayName().getString());
        ctx.put("defaultNpc", target.getDisplayName().getString());

        DialogueSession session = DialogueSessionManager.INSTANCE.startDialogue(
                player, target, tree.dialogueId(), ctx);

        if (matchedExt != null) {
            matchedExt.onDialogueStart(player, target, session);

            InteractionResult cancelResult = matchedExt.shouldCancelInteract(player, target);
            if (cancelResult != null) {
                event.setCancellationResult(cancelResult);
                event.setCanceled(true);
            }
        } else {
            NpcSpec npcSpec = resolveNpcSpec(target, player);
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
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity().level().isClientSide()) return;

        LivingEntity livingEntity = event.getEntity();
        livingEntity.getCapability(DialogueNpcPatch.CAPABILITY).ifPresent(patch -> {
            if (!patch.isConversing()) return;

            var player = patch.getConversingPlayer();
            if (!(player instanceof ServerPlayer serverPlayer)) return;

            // 检查距离
            checkDistance(patch, livingEntity, serverPlayer);
            if (!patch.isConversing()) return;

            // 控制 NPC 行为（注视/停止移动）
            controlNpcBehavior(patch, livingEntity, serverPlayer);

            // 调用扩展的 onTalkingTick
            callExtensionOnTick(livingEntity, serverPlayer);

            // 调用 patch.tick()
            patch.tick();
        });
    }

    /**
     * 检查距离，超过最大距离则终止对话
     */
    private static void checkDistance(DialogueNpcPatch patch, LivingEntity entity, ServerPlayer player) {
        double maxDist = 5.0;

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
            patch.clearConversing();
        }
    }

    /**
     * 控制 NPC 行为（注视玩家、停止移动）
     */
    @SuppressWarnings("unchecked")
    private static void controlNpcBehavior(DialogueNpcPatch patch, LivingEntity entity, ServerPlayer player) {
        if (!(entity instanceof Mob mob)) return;
        if (!patch.isConversing()) return;

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
    private static void ensureDialogueNpcPatch(Entity entity, ServerPlayer player) {
        entity.getCapability(DialogueNpcPatch.CAPABILITY).ifPresent(patch -> {
            patch.setConversing(player);
        });
    }

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

    @Mod.EventBusSubscriber(modid = Arc_Quest.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModBusEvents {

        @SubscribeEvent
        public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
            event.register(DialogueNpcPatch.class);
        }

        /**
         * 模组加载完成时扫描并注册所有扩展
         */
        @SubscribeEvent
        public static void onCommonSetup(FMLCommonSetupEvent event) {
            event.enqueueWork(() -> {
                List<IEntityDialogueExtension<?>> extensions = AnnotatedInstanceUtil.getModEntityExtensions();

                if (!extensions.isEmpty()) {
                    EntityDialogueExtensionManager.INSTANCE.registerAll(extensions);
                    LOGGER.info("[EntityDialogueExtension] Auto-registered {} extensions via annotation scanning",
                            extensions.size());
                } else {
                    LOGGER.debug("[EntityDialogueExtension] No annotated extensions found. Use manual registration.");
                }
            });
        }
    }
}

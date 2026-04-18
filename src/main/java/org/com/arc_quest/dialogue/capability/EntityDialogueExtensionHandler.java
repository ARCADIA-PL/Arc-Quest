package org.com.arc_quest.dialogue.capability;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.com.arc_quest.Arc_quest;
import org.com.arc_quest.dialogue.api.DialogueContext;
import org.com.arc_quest.dialogue.api.DialogueTree;
import org.com.arc_quest.dialogue.api.IEntityDialogueExtension;
import org.com.arc_quest.dialogue.registry.DialogueRegistry;
import org.com.arc_quest.dialogue.registry.EntityDialogueExtensionManager;
import org.com.arc_quest.dialogue.runtime.DialogueSession;
import org.com.arc_quest.dialogue.runtime.DialogueSessionManager;
import org.com.arc_quest.dialogue.util.AnnotatedInstanceUtil;
import org.slf4j.Logger;

import java.util.List;

/**
 * 实体对话扩展系统 - 处理扩展注册、实体交互和 tick 更新。
 * <p>
 * 取代了原有的简单 Capability 附加逻辑，提供完整的扩展系统支持。
 *
 * @author Arc Quest Team
 * @since 2.0
 */
@Mod.EventBusSubscriber(modid = Arc_quest.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class EntityDialogueExtensionHandler {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation CAP_ID = ResourceLocation.fromNamespaceAndPath(
            Arc_quest.MOD_ID, "dialogue_npc_patch");

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
     * 处理玩家与实体的交互事件
     */
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        Entity target = event.getTarget();
        if (!(target instanceof LivingEntity)) return;

        if (DialogueSessionManager.INSTANCE.isInDialogue(player)) return;

        if (!EntityDialogueExtensionManager.INSTANCE.hasExtensionsForEntityType(target.getType())) {
            return; // 没有扩展，使用原有的 NBT 绑定方式
        }

        // 执行扩展的交互逻辑
        EntityDialogueExtensionManager.INSTANCE.runIfExtensionExists(player, target, extension -> {
            @SuppressWarnings("unchecked")
            IEntityDialogueExtension<Entity> ext = (IEntityDialogueExtension<Entity>) extension;

            String dialogueId = ext.getDialogueTreeId(player, target, event.getHand());
            if (dialogueId == null) {
                return;
            }

            DialogueTree tree = DialogueRegistry.INSTANCE.get(dialogueId);
            if (tree == null) {
                LOGGER.warn("[EntityDialogueExtension] Dialogue '{}' not found for entity {}",
                        dialogueId, target.getName().getString());
                return;
            }

            ensureDialogueNpcPatch(target, player);

            DialogueSession session = DialogueSessionManager.INSTANCE.startDialogue(
                    player, target, tree.dialogueId(), new DialogueContext());

            ext.onDialogueStart(player, target, session);

            InteractionResult cancelResult = ext.shouldCancelInteract(player, target);
            if (cancelResult != null) {
                event.setCancellationResult(cancelResult);
                event.setCanceled(true);
            }

            LOGGER.debug("[EntityDialogueExtension] Player '{}' started dialogue '{}' with '{}' via extension",
                    player.getName().getString(), dialogueId, target.getName().getString());
        });
    }

    /**
     * Tick 事件 - 更新所有正在对话的 NPC
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.level instanceof ServerLevel serverLevel)) return;

        // 遍历所有 LivingEntity
        for (var entity : serverLevel.getAllEntities()) {
            if (entity instanceof LivingEntity livingEntity) {
                livingEntity.getCapability(DialogueNpcPatch.CAPABILITY).ifPresent(patch -> {
                    if (!patch.isConversing()) return;

                    var player = patch.getConversingPlayer();
                    if (!(player instanceof ServerPlayer serverPlayer)) return;

                    // 检查距离
                    checkDistance(patch, livingEntity, serverPlayer);

                    // 控制 NPC 行为（注视/停止移动）
                    controlNpcBehavior(patch, livingEntity, serverPlayer);

                    // 调用扩展的 onTalkingTick
                    callExtensionOnTick(livingEntity, serverPlayer);

                    // 调用 patch.tick()
                    patch.tick();
                });
            }
        }
    }

    /**
     * 检查距离，超过最大距离则终止对话
     */
    private static void checkDistance(DialogueNpcPatch patch, LivingEntity entity, ServerPlayer player) {
        if (EntityDialogueExtensionManager.INSTANCE.hasExtensionsForEntityType(entity.getType())) {
            var extensions = EntityDialogueExtensionManager.INSTANCE.getExtensionsForEntityType(entity.getType());
            for (var ext : extensions) {
                int maxDist = ext.maxTalkDistance();
                if (entity.distanceTo(player) > maxDist + 2.0) {
                    DialogueSessionManager.INSTANCE.endDialogue(player);
                    patch.clearConversing();
                    return;
                }
            }
        }
    }

    /**
     * 控制 NPC 行为（注视玩家、停止移动）
     */
    @SuppressWarnings("unchecked")
    private static void controlNpcBehavior(DialogueNpcPatch patch, LivingEntity entity, ServerPlayer player) {
        if (!(entity instanceof Mob mob)) return;

        if (EntityDialogueExtensionManager.INSTANCE.hasExtensionsForEntityType(entity.getType())) {
            var extensions = EntityDialogueExtensionManager.INSTANCE.getExtensionsForEntityType(entity.getType());
            for (var ext : extensions) {
                IEntityDialogueExtension<LivingEntity> livingExt = (IEntityDialogueExtension<LivingEntity>) ext;
                if (livingExt.canInteractWith(player, entity)) {
                    if (livingExt.shouldLookAtPlayer(player, entity)) {
                        mob.getLookControl().setLookAt(player, 60.0F, 60.0F);
                    }
                    if (livingExt.shouldStopMoving(player, entity)) {
                        mob.getNavigation().stop();
                    }
                    break;
                }
            }
        } else {
            patch.tick();
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
            if (livingExt.canInteractWith(player, entity)) {
                livingExt.onTalkingTick(player, entity);
                break;
            }
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

    @Mod.EventBusSubscriber(modid = Arc_quest.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModBusEvents {

        @SubscribeEvent
        public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
            event.register(DialogueNpcPatch.class);
        }

        /**
         * 模组加载完成时扫描并注册所有扩展
         */
        @SubscribeEvent
        public static void onCommonSetup(net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event) {
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

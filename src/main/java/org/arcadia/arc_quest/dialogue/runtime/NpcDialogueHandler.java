package org.arcadia.arc_quest.dialogue.runtime;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.dialogue.api.DialogueContext;
import org.arcadia.arc_quest.dialogue.api.IDialogueNpc;
import org.arcadia.arc_quest.dialogue.capability.DialogueNpcPatch;
import org.arcadia.arc_quest.dialogue.capability.DialogueNpcPatchProvider;
import org.arcadia.arc_quest.dialogue.registry.DialogueRegistry;
import org.slf4j.Logger;

/**
 * NPC 对话事件处理器 —— 将 Forge 事件连接到对话系统。
 * <p>
 * 处理：
 * <ul>
 *   <li>玩家右键实体 → 检查 {@link IDialogueNpc} → 触发对话</li>
 *   <li>实体 Tick → {@link DialogueNpcPatch} 状态更新（注视/停步/距离断开）</li>
 *   <li>Capability 注册和附加</li>
 * </ul>
 * <p>
 * 注册方式：在 {@code Arc_quest} 主类构造器中：
 * <pre>{@code
 * MinecraftForge.EVENT_BUS.register(NpcDialogueHandler.class);
 * }</pre>
 */
public final class NpcDialogueHandler {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation CAP_ID =
            ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "dialogue_npc_patch");

    private NpcDialogueHandler() {
    }

    // ═══════════════════════════════════════════════════════
    //  玩家交互 → 对话触发
    // ═══════════════════════════════════════════════════════

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        // 仅服务端、主手
        if (event.getLevel().isClientSide()) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        Entity target = event.getTarget();
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        // ── 优先级1：实体实现了 IDialogueNpc ──
        if (target instanceof IDialogueNpc npc) {
            if (npc.canDialogueWith(player)) {
                npc.startDialogueWith(serverPlayer);
                event.setCancellationResult(InteractionResult.SUCCESS);
                event.setCanceled(true);
            }
            return;
        }

        // ── 优先级2：DialogueRegistry 中的实体类型绑定 ──
        String dialogueId = DialogueRegistry.INSTANCE.getDialogueForEntity(target, serverPlayer);
        if (dialogueId != null) {
            DialogueContext context = new DialogueContext()
                    .put("playerName", player.getName().getString())
                    .put("npcName", target.getDisplayName().getString());
            DialogueSessionManager.INSTANCE.startDialogue(
                    serverPlayer, target, dialogueId, context);
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    // ═══════════════════════════════════════════════════════
    //  实体 Tick → Capability 更新
    // ═══════════════════════════════════════════════════════

    @SubscribeEvent
    public static void onEntityTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity() instanceof IDialogueNpc)) return;

        DialogueNpcPatch patch = DialogueNpcPatch.get(event.getEntity());
        if (patch != null) {
            patch.tick();
        }
    }

    // ═══════════════════════════════════════════════════════
    //  Capability 注册与附加
    // ═══════════════════════════════════════════════════════

    @SubscribeEvent
    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.register(DialogueNpcPatch.class);
    }

    @SubscribeEvent
    public static void onAttachEntityCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof IDialogueNpc) {
            event.addCapability(CAP_ID, new DialogueNpcPatchProvider(event.getObject()));
        }
    }
}
package org.com.arc_quest.dialogue.capability;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import org.com.arc_quest.dialogue.api.IDialogueNpc;
import org.com.arc_quest.dialogue.runtime.DialogueSessionManager;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * NPC 实体的对话状态 Capability 数据。
 * <p>
 * 挂载在所有实现 {@link IDialogueNpc} 的实体上，用于追踪：
 * <ul>
 *   <li>当前正在对话的玩家</li>
 *   <li>对话距离超限自动断开</li>
 *   <li>NPC 行为控制（注视/停步）</li>
 * </ul>
 */
public class DialogueNpcPatch {

    public static final Capability<DialogueNpcPatch> CAPABILITY =
            CapabilityManager.get(new CapabilityToken<>() {});

    private static final Logger LOGGER = LogUtils.getLogger();

    private final Entity owner;

    @Nullable
    private Player conversingPlayer;

    @Nullable
    private UUID conversingPlayerUUID;

    public DialogueNpcPatch(Entity owner) {
        this.owner = owner;
    }

    // ═══════════════════════════════════════════════════════
    //  状态管理
    // ═══════════════════════════════════════════════════════

    public void setConversing(@Nullable Player player) {
        this.conversingPlayer = player;
        this.conversingPlayerUUID = player != null ? player.getUUID() : null;
    }

    public void clearConversing() {
        this.conversingPlayer = null;
        this.conversingPlayerUUID = null;
    }

    public boolean isConversing() {
        return conversingPlayer != null && conversingPlayer.isAlive();
    }

    @Nullable
    public Player getConversingPlayer() {
        return conversingPlayer;
    }

    public Entity getOwner() {
        return owner;
    }

    // ═══════════════════════════════════════════════════════
    //  每 Tick 更新
    // ═══════════════════════════════════════════════════════

    /**
     * 每 tick 调用。处理：
     * <ol>
     *   <li>玩家离线/死亡 → 清除对话状态</li>
     *   <li>距离超限 → 断开对话并通知客户端</li>
     *   <li>NPC 行为控制（注视玩家、停止移动）</li>
     * </ol>
     */
    public void tick() {
        if (conversingPlayer == null) {
            // 尝试通过 UUID 恢复（理论上不常发生）
            if (conversingPlayerUUID != null) {
                Player found = owner.level().getPlayerByUUID(conversingPlayerUUID);
                if (found != null && found.isAlive()) {
                    conversingPlayer = found;
                } else {
                    clearConversing();
                }
            }
            return;
        }

        // 检查玩家是否仍然有效
        if (!conversingPlayer.isAlive() || conversingPlayer.isRemoved()) {
            clearConversing();
            return;
        }

        // 获取 NPC 接口
        if (!(owner instanceof IDialogueNpc npc)) {
            return;
        }

        // 距离检查（给 2 格容差，避免抖动断开）
        double maxDist = npc.getMaxDialogueDistance() + 2.0;
        if (conversingPlayer.distanceTo(owner) > maxDist) {
            LOGGER.debug("[DialogueNpcPatch] Player {} moved too far from NPC, ending dialogue.",
                    conversingPlayer.getName().getString());
            if (conversingPlayer instanceof ServerPlayer sp) {
                DialogueSessionManager.INSTANCE.endDialogue(sp);
            }
            clearConversing();
            return;
        }

        // NPC 行为控制
        if (owner instanceof Mob mob) {
            if (npc.shouldLookAtPlayer()) {
                mob.getLookControl().setLookAt(
                        conversingPlayer,
                        30.0F,  // maxYawDelta
                        30.0F   // maxPitchDelta
                );
            }
            if (npc.shouldStopMoving()) {
                mob.getNavigation().stop();
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    //  静态便捷方法
    // ═══════════════════════════════════════════════════════

    /**
     * 获取实体的 DialogueNpcPatch。
     *
     * @return patch 实例，若实体未附加 capability 则返回 null
     */
    @Nullable
    public static DialogueNpcPatch get(Entity entity) {
        return entity.getCapability(CAPABILITY).orElse(null);
    }

    // ═══════════════════════════════════════════════════════
    //  NBT 持久化
    // ═══════════════════════════════════════════════════════

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        // 对话状态不持久化（重载后NPC不应还锁定在对话中）
        return tag;
    }

    public void load(CompoundTag tag) {
        // 对话状态不持久化
        clearConversing();
    }
}
package org.arcadia.arc_quest.dialogue.capability;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import org.arcadia.arc_quest.dialogue.api.IDialogueNpc;
import org.arcadia.arc_quest.dialogue.runtime.DialogueSessionManager;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.UUID;

public class DialogueNpcPatch {

    public static final Capability<DialogueNpcPatch> CAPABILITY =
            CapabilityManager.get(new CapabilityToken<>() {
            });

    private static final Logger LOGGER = LogUtils.getLogger();

    private final Entity owner;

    @Nullable
    private Player conversingPlayer;

    @Nullable
    private UUID conversingPlayerUUID;

    public DialogueNpcPatch(Entity owner) {
        this.owner = owner;
    }

    /**
     * 获取实体的 DialogueNpcPatch。
     *
     * @return patch 实例，若实体未附加 capability 则返回 null
     */
    public static @NotNull DialogueNpcPatch get(Entity entity) {
        return entity.getCapability(CAPABILITY).orElse(null);
    }

    public void clearConversing() {
        conversingPlayer = null;
        conversingPlayerUUID = null;
    }

    public boolean isConversing() {
        return conversingPlayer != null && conversingPlayer.isAlive();
    }

    public void setConversing(@Nullable Player player) {
        conversingPlayer = player;
        conversingPlayerUUID = player != null ? player.getUUID() : null;
    }

    @Nullable
    public Player getConversingPlayer() {
        return conversingPlayer;
    }

    public Entity getOwner() {
        return owner;
    }

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

        if (!conversingPlayer.isAlive() || conversingPlayer.isRemoved()) {
            clearConversing();
            return;
        }

        if (!(owner instanceof IDialogueNpc npc)) {
            return;
        }

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

        if (owner instanceof Mob mob) {
            if (npc.shouldLookAtPlayer()) {
                mob.getLookControl().setLookAt(
                        conversingPlayer,
                        30.0F,
                        30.0F
                );
            }
            if (npc.shouldStopMoving()) {
                mob.getNavigation().stop();
            }
        }
    }

    public CompoundTag save() {
        return new CompoundTag();
    }

    public void load(CompoundTag tag) {
        clearConversing();
    }
}
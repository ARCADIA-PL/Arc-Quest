package org.com.arc_quest.dialogue.registry;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.com.arc_quest.dialogue.runtime.DialogueSession;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自定义对话动作处理器注册表。
 * <p>
 * 配合 {@link org.com.arc_quest.dialogue.api.DialogueAction.Custom} 使用，
 * 允许外部模组注册自己的对话动作处理逻辑。
 *
 * <h3>注册（在 FMLCommonSetupEvent 中）</h3>
 * <pre>{@code
 * DialogueActionTypes.register(
 *     new ResourceLocation("mymod", "give_exp"),
 *     (player, session, data) -> {
 *         int amount = data.getInt("amount");
 *         player.giveExperiencePoints(amount);
 *     }
 * );
 * }</pre>
 *
 * <h3>在对话中使用</h3>
 * <pre>{@code
 * CompoundTag data = new CompoundTag();
 * data.putInt("amount", 100);
 * builder.choice("获得经验", c -> c
 *     .action(new DialogueAction.Custom(
 *         new ResourceLocation("mymod", "give_exp"), data))
 *     .close());
 * }</pre>
 */
public final class DialogueActionTypes {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<ResourceLocation, ActionHandler> HANDLERS = new ConcurrentHashMap<>();

    private DialogueActionTypes() {
    }

    /**
     * 注册自定义动作处理器。
     *
     * @param typeId  动作类型 ID（例如 {@code "mymod:give_coins"}）
     * @param handler 处理器实现
     */
    public static void register(ResourceLocation typeId, ActionHandler handler) {
        ActionHandler prev = HANDLERS.put(typeId, handler);
        if (prev != null) {
            LOGGER.warn("[DialogueActionTypes] Overwriting registered action type: {}", typeId);
        }
        LOGGER.debug("[DialogueActionTypes] Registered action type: {}", typeId);
    }

    /**
     * 执行自定义动作。由 {@link DialogueSession} 内部调用。
     */
    public static void execute(ResourceLocation typeId, ServerPlayer player,
                               @Nullable DialogueSession session, CompoundTag data) {
        ActionHandler handler = HANDLERS.get(typeId);
        if (handler != null) {
            try {
                handler.execute(player, session, data);
            } catch (Exception e) {
                LOGGER.error("[DialogueActionTypes] Error executing custom action '{}': {}",
                        typeId, e.getMessage(), e);
            }
        } else {
            LOGGER.error("[DialogueActionTypes] No handler registered for action type: {}", typeId);
        }
    }

    /**
     * 检查指定类型是否已注册。
     */
    public static boolean isRegistered(ResourceLocation typeId) {
        return HANDLERS.containsKey(typeId);
    }

    /**
     * 获取所有已注册的类型 ID（只读视图）。
     */
    public static Set<ResourceLocation> getAllTypes() {
        return Collections.unmodifiableSet(HANDLERS.keySet());
    }

    /**
     * 动作处理器函数接口。
     */
    @FunctionalInterface
    public interface ActionHandler {
        /**
         * @param player  触发动作的玩家
         * @param session 当前对话会话（可获取上下文、对话树等信息）
         * @param data    动作携带的自定义 NBT 数据
         */
        void execute(ServerPlayer player, @Nullable DialogueSession session, CompoundTag data);
    }
}
package org.arcadia.arc_quest.dialogue.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import org.arcadia.arc_quest.dialogue.runtime.DialogueSession;
import org.jetbrains.annotations.Nullable;

/**
 * 实体对话扩展接口 - 允许为特定实体类型添加自定义对话行为。
 * <p>
 * 实现此接口的类需要使用 {@link EntityDialogueExtension} 注解标记，
 * 系统会自动扫描并注册。
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li>自定义交互条件检查</li>
 *   <li>对话开始/结束回调</li>
 *   <li>动态对话树选择</li>
 *   <li>NPC 行为控制（注视、移动）</li>
 *   <li>取消原交互（如村民交易）</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * @EntityDialogueExtension(modId = "arc_quest")
 * public class VillagerDialogueExtension implements IEntityDialogueExtension<Villager> {
 *
 *     @Override
 *     public EntityType<Villager> getEntityType() {
 *         return EntityType.VILLAGER;
 *     }
 *
 *     @Override
 *     public boolean canInteractWith(Player player, Villager villager) {
 *         // 只允许非潜行玩家对话
 *         return !player.isCrouching();
 *     }
 *
 *     @Override
 *     public void onDialogueStart(ServerPlayer player, Villager villager, DialogueTree tree) {
 *         // 根据职业动态选择对话树
 *         String profession = villager.getVillagerData().getProfession().toString();
 *         // ... 自定义逻辑
 *     }
 *
 *     @Override
 *     @Nullable
 *     public InteractionResult shouldCancelInteract(Player player, Villager villager) {
 *         // 取消村民的交易界面
 *         return InteractionResult.SUCCESS;
 *     }
 * }
 * }</pre>
 *
 * @param <T> 要扩展的实体类型
 * @author Arc Quest Team
 * @since 2.0
 */
public interface IEntityDialogueExtension<T extends Entity> {

    /**
     * 返回此扩展适用的实体类型。
     *
     * @return 实体类型
     */
    EntityType<T> getEntityType();

    /**
     * 判断是否可以与玩家发起对话。
     *
     * @param player 尝试交互的玩家
     * @param entity 目标实体
     * @return true 允许对话，false 禁止对话
     */
    boolean canInteractWith(Player player, T entity);

    /**
     * 获取对话树 ID。
     * <p>
     * 可以根据实体状态、玩家状态等动态返回不同的对话树。
     *
     * @param player 服务端玩家
     * @param entity 目标实体
     * @param hand   交互手
     * @return 对话树 ID，返回 null 则不打开对话
     */
    @Nullable
    String getDialogueTreeId(ServerPlayer player, T entity, InteractionHand hand);

    /**
     * 对话开始前的预处理（服务端）。
     * <p>
     * 可用于：
     * <ul>
     *   <li>修改对话会话数据</li>
     *   <li>记录对话历史</li>
     *   <li>触发自定义事件</li>
     * </ul>
     *
     * @param player  服务端玩家
     * @param entity  目标实体
     * @param session 对话会话
     */
    default void onDialogueStart(ServerPlayer player, T entity, DialogueSession session) {
    }

    /**
     * 对话结束后的处理（服务端）。
     * <p>
     * 可用于：
     * <ul>
     *   <li>给予奖励</li>
     *   <li>更新任务进度</li>
     *   <li>播放音效</li>
     * </ul>
     *
     * @param player 服务端玩家
     * @param entity 目标实体
     */
    default void onDialogueEnd(ServerPlayer player, T entity) {
    }

    /**
     * 是否取消原交互（如村民交易界面）。
     *
     * @param player 玩家
     * @param entity 目标实体
     * @return InteractionResult 取消原交互并返回此结果，null 不取消
     */
    @Nullable
    default InteractionResult shouldCancelInteract(Player player, T entity) {
        return null; // 默认不取消
    }

    /**
     * 最大对话距离（超过则终止对话）。
     *
     * @return 最大距离（格）
     */
    default int maxTalkDistance() {
        return 5;
    }

    /**
     * 对话时是否注视玩家。
     *
     * @param player 玩家
     * @param entity 目标实体
     * @return true 注视玩家，false 不控制
     */
    default boolean shouldLookAtPlayer(ServerPlayer player, T entity) {
        return true;
    }

    /**
     * 对话时是否停止移动。
     *
     * @param player 玩家
     * @param entity 目标实体
     * @return true 停止移动，false 不控制
     */
    default boolean shouldStopMoving(ServerPlayer player, T entity) {
        return true;
    }

    /**
     * 对话期间的每 tick 更新（服务端）。
     * <p>
     * 可用于：
     * <ul>
     *   <li>实时更新 NPC 状态</li>
     *   <li>检测特殊条件</li>
     *   <li>触发动画或音效</li>
     * </ul>
     *
     * @param player 玩家
     * @param entity 目标实体
     */
    default void onTalkingTick(ServerPlayer player, T entity) {
    }

    /**
     * 获取对话进度作用域。
     * <p>
     * 控制对话历史记录的范围：
     * <ul>
     *   <li>{@link ProgressScope#DIALOGUE_TREE} - 同对话树的所有 NPC 共享进度（默认）</li>
     *   <li>{@link ProgressScope#INSTANCE} - 每个 NPC 实例独立进度</li>
     *   <li>{@link ProgressScope#CUSTOM} - 自定义命名空间（通过 {@link #getProgressNamespace()}）</li>
     * </ul>
     *
     * @return 进度作用域
     */
    default ProgressScope getProgressScope() {
        return ProgressScope.DIALOGUE_TREE; // 默认共享
    }

    /**
     * 获取自定义进度命名空间（仅当 {@link #getProgressScope()} 返回 CUSTOM 时有效）。
     * <p>
     * 可用于按阵营、类型等分组共享进度。
     *
     * @param entity 目标实体
     * @return 命名空间字符串，返回 null 则使用对话树 ID
     */
    @Nullable
    default String getProgressNamespace(T entity) {
        return null;
    }

    /**
     * 准备发送到客户端的数据。
     * <p>
     * 可用于传递实体状态、任务进度等信息到客户端。
     *
     * @param player 服务端玩家
     * @param entity 目标实体
     * @param hand   交互手
     * @return 附加数据（可为空）
     */
    @Nullable
    default Object getClientData(ServerPlayer player, T entity, InteractionHand hand) {
        return null;
    }
}

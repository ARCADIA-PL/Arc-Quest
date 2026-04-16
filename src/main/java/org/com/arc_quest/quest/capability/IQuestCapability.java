package org.com.arc_quest.quest.capability;

import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;

/**
 * 玩家任务数据 Capability 接口。
 * <p>
 * 设计原则：
 * <ul>
 *   <li>所有写操作仅在服务端调用</li>
 *   <li>客户端通过网络同步获得只读镜像（{@link org.com.arc_quest.quest.network.ClientQuestCache}）</li>
 *   <li>Flag / Variable 系统是全局的（跨任务共享）</li>
 * </ul>
 */
public interface IQuestCapability {

    // ═══════════════════════════════════════════════════════
    //  任务生命周期
    // ═══════════════════════════════════════════════════════

    /** 添加一个活跃任务。 */
    void addActiveQuest(QuestRuntimeData data);

    /** 移除活跃任务（放弃时调用）。 */
    void removeActiveQuest(String questId);

    /** 将任务标记为已完成并从活跃列表移除。 */
    void markCompleted(String questId);

    /** 将任务标记为失败并从活跃列表移除。 */
    void markFailed(String questId);

    /** 获取活跃任务运行时数据（可变引用，服务端直接修改）。 */
    @Nullable
    QuestRuntimeData getActiveQuest(String questId);

    /** 返回所有活跃任务的不可变视图。 */
    Map<String, QuestRuntimeData> getAllActiveQuests();

    /** 已完成的任务 ID 集合。 */
    Set<String> getCompletedQuests();

    /** 已失败的任务 ID 集合。 */
    Set<String> getFailedQuests();

    /** 任务是否正在进行中。 */
    boolean isQuestActive(String questId);

    /** 任务是否已完成。 */
    boolean isQuestCompleted(String questId);

    /** 任务是否已失败。 */
    boolean isQuestFailed(String questId);

    // ═══════════════════════════════════════════════════════
    //  全局 Flag 系统
    // ═══════════════════════════════════════════════════════

    void setFlag(String flag);
    boolean hasFlag(String flag);
    void removeFlag(String flag);
    Set<String> getAllFlags();

    // ═══════════════════════════════════════════════════════
    //  全局 Variable 系统
    // ═══════════════════════════════════════════════════════

    int getVariable(String key);
    void setVariable(String key, int value);
    void incrementVariable(String key, int amount);
    Map<String, Integer> getAllVariables();

    // ═══════════════════════════════════════════════════════
    //  序列化
    // ═══════════════════════════════════════════════════════

    CompoundTag serializeNBT();
    void deserializeNBT(CompoundTag tag);

    /** 从另一个 Capability 复制全部数据（死亡克隆时使用）。 */
    void copyFrom(IQuestCapability other);
}
package org.arcadia.arc_quest.client.ponder;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.quest.util.IntelSceneIdHelper;

/**
 * Arc Quest Ponder 场景 ID 生成工具。
 *
 * <p>统一管理命名规则，避免各处硬编码字符串，保证一致性。
 *
 * <p><b>sceneId 命名约定：</b>
 * <pre>
 *   arc_quest:quest_phase/{ns}__{questPath}/{ns}__{phasePath}
 *   arc_quest:dialogue/{ns}__{dialoguePath}
 *   arc_quest:trade/{ns}__{shopPath}
 *   arc_quest:tutorial/{ns}__{topicPath}
 * </pre>
 *
 * <p><b>命名空间编码规则：</b>
 * <ul>
 *   <li>命名空间与 path 之间用 {@code __}（双下划线）连接，保留来源信息</li>
 *   <li>来自本模组（{@code arc_quest:xxx}）→ 省略前缀，直接用 path</li>
 *   <li>来自附属模组（{@code addon:xxx}）→ 编码为 {@code addon__xxx}</li>
 *   <li>纯 path 字符串（无 {@code :}）→ 直接使用，无前缀</li>
 * </ul>
 *
 * <p><b>示例：</b>
 * <pre>
 *   questPhaseId("arc_quest:epic_prologue", "arc_quest:defend_village")
 *     → arc_quest:quest_phase/epic_prologue/defend_village
 *
 *   questPhaseId("addon:prologue",          "addon:phase_1")
 *     → arc_quest:quest_phase/addon__prologue/addon__phase_1
 *
 *   questPhaseId("epic_prologue",           "defend_village")
 *     → arc_quest:quest_phase/epic_prologue/defend_village
 * </pre>
 */
public final class ArcQuestPonderHelper {

    private static final String MOD = Arc_Quest.MOD_ID;
    /**
     * 命名空间与 path 之间的分隔符，选用双下划线避免与常规命名冲突。
     */
    private static final String NS_SEP = "__";

    private ArcQuestPonderHelper() {
    }

    /**
     * 将任意形式的 ID 字符串编码为合法的 RL path 段。
     *
     * <ul>
     *   <li>{@code "defend_village"}       → {@code "defend_village"}（纯 path，直接使用）</li>
     *   <li>{@code "arc_quest:defend_village"} → {@code "defend_village"}（本模组，省略前缀）</li>
     *   <li>{@code "addon:defend_village"} → {@code "addon__defend_village"}（附属模组，保留命名空间）</li>
     * </ul>
     */
    static String encodePath(String idOrPath) {
        int colon = idOrPath.indexOf(':');
        if (colon < 0) {
            // 纯 path 字符串，无命名空间
            return idOrPath;
        }
        String namespace = idOrPath.substring(0, colon);
        String path = idOrPath.substring(colon + 1);
        if (MOD.equals(namespace)) {
            // 本模组命名空间，省略前缀保持简洁
            return path;
        }
        // 附属模组：编码为 "namespace__path"
        return namespace + NS_SEP + path;
    }

    /**
     * 生成任务阶段情报场景 ID。
     *
     * @param questId 任务 ID，支持三种形式：
     *                {@code "epic_prologue"} /
     *                {@code "arc_quest:epic_prologue"} /
     *                {@code "addon:prologue"}
     * @param phaseId 阶段 ID，同上
     * @return {@code arc_quest:quest_phase/{encodedQuestId}/{encodedPhaseId}}
     */
    public static ResourceLocation questPhaseId(String questId, String phaseId) {
        return IntelSceneIdHelper.questPhaseId(questId, phaseId);
    }

    /**
     * 生成对话场景 ID。
     *
     * @param dialogueId 对话树 ID，支持带或不带命名空间
     * @return {@code arc_quest:dialogue/{encodedDialogueId}}
     */
    public static ResourceLocation dialogueId(String dialogueId) {
        return ResourceLocation.fromNamespaceAndPath(MOD, "dialogue/" + encodePath(dialogueId));
    }

    /**
     * 生成交易商店介绍场景 ID。
     *
     * @param shopId 商店 ID，支持带或不带命名空间
     * @return {@code arc_quest:trade/{encodedShopId}}
     */
    public static ResourceLocation tradeId(String shopId) {
        return ResourceLocation.fromNamespaceAndPath(MOD, "trade/" + encodePath(shopId));
    }

    /**
     * 生成通用教程场景 ID。
     *
     * @param topicId 教程主题 ID，支持带或不带命名空间
     * @return {@code arc_quest:tutorial/{encodedTopicId}}
     */
    public static ResourceLocation tutorialId(String topicId) {
        return ResourceLocation.fromNamespaceAndPath(MOD, "tutorial/" + encodePath(topicId));
    }

    /**
     * 生成推荐的结构文件名（不含 .nbt），用于 {@code addStoryBoard}。
     *
     * <p>文件放置路径：{@code assets/arc_quest/ponder/<返回值>.nbt}
     *
     * <p><b>示例：</b>
     * <pre>
     *   questPhaseStructure("arc_quest:epic_prologue", "arc_quest:defend_village")
     *     → "epic_prologue_defend_village"
     *
     *   questPhaseStructure("addon:prologue", "addon:phase_1")
     *     → "addon__prologue_addon__phase_1"
     * </pre>
     *
     * <p>注意：附属模组通常应把结构文件放在自己的资源包中并传入自定义 structureName，
     * 此方法仅提供 {@code arc_quest} 命名空间下的默认命名建议。
     */
    public static String questPhaseStructure(String questId, String phaseId) {
        return encodePath(questId) + "_" + encodePath(phaseId);
    }
}

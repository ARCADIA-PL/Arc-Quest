package org.arcadia.arc_quest.api;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.dialogue.api.DialogueTree;
import org.arcadia.arc_quest.dialogue.api.IEntityDialogueExtension;
import org.arcadia.arc_quest.dialogue.registry.DialogueRegistry;
import org.arcadia.arc_quest.dialogue.registry.EntityDialogueExtensionManager;
import org.arcadia.arc_quest.guide.api.GuideCategory;
import org.arcadia.arc_quest.guide.api.GuideDefinition;
import org.arcadia.arc_quest.guide.registry.GuideCategoryRegistry;
import org.arcadia.arc_quest.guide.registry.GuideRegistry;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.trade.api.TradeShopDefinition;
import org.arcadia.arc_quest.trade.gacha.api.GachaShopDefinition;
import org.arcadia.arc_quest.trade.gacha.registry.GachaRegistry;
import org.arcadia.arc_quest.trade.registry.TradeRegistry;
import org.jetbrains.annotations.Nullable;

/**
 * Arc Quest 公共 API 入口。
 * <p>
 * 附属模组应通过此类访问所有功能，避免直接依赖内部实现。
 * </p>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 注册任务
 * ArcQuestAPI.registerQuest(
 *     QuestBuilder.create("my_mod:my_quest")
 *         .displayName(Component.literal("My Quest"))
 *         .build()
 * );
 *
 * // 注册对话树
 * ArcQuestAPI.registerDialogueTree(
 *     DialogueTreeBuilder.create("my_dialogue")
 *         .npc("NPC Name")
 *         .build()
 * );
 *
 * // 注册商店
 * ArcQuestAPI.registerTradeShop(
 *     TradeShopBuilder.create("my_shop")
 *         .displayName(Component.literal("My Shop"))
 *         .build()
 * );
 * }</pre>
 *
 * @since 1.0.0
 */
public final class ArcQuestAPI {

    private ArcQuestAPI() {
    }

    // ════════════════════════════════════════
    //  任务系统 API
    // ════════════════════════════════════════

    /**
     * 注册任务定义。
     *
     * @param definition 任务定义
     * @throws IllegalStateException 如果注册表已冻结或 ID 重复
     */
    public static void registerQuest(QuestDefinition definition) {
        QuestRegistry.register(definition);
    }

    /**
     * 获取任务定义。
     *
     * @param id 任务 ID
     * @return 任务定义，不存在则返回 null
     */
    @Nullable
    public static QuestDefinition getQuest(ResourceLocation id) {
        return QuestRegistry.get(id);
    }

    /**
     * 获取任务定义（不存在则抛出异常）。
     *
     * @param id 任务 ID
     * @return 任务定义
     * @throws java.util.NoSuchElementException 如果任务不存在
     */
    public static QuestDefinition getQuestOrThrow(ResourceLocation id) {
        return QuestRegistry.getOrThrow(id);
    }

    /**
     * 检查任务是否存在。
     *
     * @param id 任务 ID
     * @return true 如果任务存在
     */
    public static boolean hasQuest(ResourceLocation id) {
        return QuestRegistry.get(id) != null;
    }

    // ════════════════════════════════════════
    //  Guide 系统 API
    // ════════════════════════════════════════

    /**
     * 注册 Guide 定义。
     *
     * @param definition Guide 定义
     * @throws IllegalStateException 如果注册表已冻结或 ID 重复
     */
    public static void registerGuide(GuideDefinition definition) {
        GuideRegistry.register(definition);
    }

    /**
     * 注册 Guide 分类。
     *
     * @param category Guide 分类
     * @throws IllegalStateException 如果注册表已冻结或 ID 重复
     */
    public static void registerGuideCategory(GuideCategory category) {
        GuideCategoryRegistry.register(category);
    }

    /**
     * 获取 Guide 定义。
     *
     * @param id Guide ID
     * @return Guide 定义，不存在则返回 null
     */
    @Nullable
    public static GuideDefinition getGuide(ResourceLocation id) {
        return GuideRegistry.get(id);
    }

    /**
     * 检查 Guide 是否存在。
     *
     * @param id Guide ID
     * @return true 如果 Guide 存在
     */
    public static boolean hasGuide(ResourceLocation id) {
        return GuideRegistry.get(id) != null;
    }

    // ════════════════════════════════════════
    //  对话系统 API
    // ════════════════════════════════════════

    /**
     * 注册对话树。
     *
     * @param tree 对话树定义
     * @throws IllegalStateException 如果 ID 重复
     */
    public static void registerDialogueTree(DialogueTree tree) {
        DialogueRegistry.INSTANCE.register(tree);
    }

    /**
     * 获取对话树。
     *
     * @param dialogueId 对话树 ID
     * @return 对话树，不存在则返回 null
     */
    @Nullable
    public static DialogueTree getDialogueTree(String dialogueId) {
        return DialogueRegistry.INSTANCE.get(dialogueId);
    }

    /**
     * 检查对话树是否存在。
     *
     * @param dialogueId 对话树 ID
     * @return true 如果对话树存在
     */
    public static boolean hasDialogueTree(String dialogueId) {
        return DialogueRegistry.INSTANCE.get(dialogueId) != null;
    }

    /**
     * 注册 NPC 对话扩展。
     *
     * @param extension 对话扩展实现
     */
    public static void registerDialogueExtension(IEntityDialogueExtension<?> extension) {
        EntityDialogueExtensionManager.INSTANCE.register(extension);
    }

    // ════════════════════════════════════════
    //  交易系统 API
    // ════════════════════════════════════════

    /**
     * 注册商店定义。
     *
     * @param shop 商店定义
     * @throws IllegalStateException 如果 ID 重复
     */
    public static void registerTradeShop(TradeShopDefinition shop) {
        TradeRegistry.register(shop);
    }

    /**
     * 获取商店定义。
     *
     * @param shopId 商店 ID
     * @return 商店定义，不存在则返回 null
     */
    @Nullable
    public static TradeShopDefinition getTradeShop(String shopId) {
        return TradeRegistry.get(shopId);
    }

    /**
     * 检查商店是否存在。
     *
     * @param shopId 商店 ID
     * @return true 如果商店存在
     */
    public static boolean hasTradeShop(String shopId) {
        return TradeRegistry.get(shopId) != null;
    }
    // ════════════════════════════════════════
    //  抽奖系统 API
    // ════════════════════════════════════════

    /**
     * 注册抽奖商店定义。
     *
     * @param shop 抽奖商店定义
     * @throws IllegalStateException 如果 ID 重复
     */
    public static void registerGachaShop(GachaShopDefinition shop) {
        GachaRegistry.register(shop);
    }

    /**
     * 获取抽奖商店定义。
     *
     * @param shopId 抽奖商店 ID
     * @return 抽奖商店定义，不存在则返回 null
     */
    @Nullable
    public static GachaShopDefinition getGachaShop(String shopId) {
        return GachaRegistry.get(shopId);
    }

    /**
     * 检查抽奖商店是否存在。
     *
     * @param shopId 抽奖商店 ID
     * @return true 如果抽奖商店存在
     */
    public static boolean hasGachaShop(String shopId) {
        return GachaRegistry.get(shopId) != null;
    }
}

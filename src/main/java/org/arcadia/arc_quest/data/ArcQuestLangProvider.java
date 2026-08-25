package org.arcadia.arc_quest.data;

import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.LanguageProvider;
import org.arcadia.arc_quest.Arc_Quest;

/**
 * Arc Quest 多语言数据生成器抽象基类。
 * <p>
 * 翻译键命名规范：{@code arc_quest.{category}.{sub_category}.{key}}
 * <p>
 * 子类只需实现 {@link #addTranslations()} 并调用下列快捷方法即可自动
 * 生成对应 locale 的 {@code assets/arc_quest/lang/{locale}.json}。
 */
public abstract class ArcQuestLangProvider extends LanguageProvider {

    protected final String locale;

    /**
     * @param output DataGen 输出
     * @param locale 语言标识符，如 {@code "en_us"} 或 {@code "zh_cn"}
     */
    protected ArcQuestLangProvider(PackOutput output, String locale) {
        super(output, Arc_Quest.MOD_ID, locale);
        this.locale = locale;
    }

    // ════════════════════════════════════════════════════════
    //  任务系统（对应 Quest构建器 API）
    // ════════════════════════════════════════════════════════

    /**
     * 添加任务的显示名称和描述。
     *
     * @param questPath   任务路径（不含命名空间，如 {@code "epic_prologue"}）
     * @param title       任务标题
     * @param description 任务描述（支持 {@code \n\n} 换行）
     */
    protected void addQuest(String questPath, String title, String description) {
        add("arc_quest.quest." + questPath + ".title", title);
        add("arc_quest.quest." + questPath + ".desc", description);
    }

    /**
     * 添加阶段的显示名称。
     *
     * @param questPath 任务路径
     * @param phaseId   阶段ID（如 {@code "gather_wood"}）
     * @param phaseName 阶段名称
     */
    protected void addPhase(String questPath, String phaseId, String phaseName) {
        add("arc_quest.phase." + questPath + "." + phaseId, phaseName);
    }

    /**
     * 添加目标的显示文本。
     *
     * @param questPath      任务路径
     * @param phaseId        阶段ID
     * @param objectiveIndex 目标索引（从 0 开始）
     * @param objectiveText  目标文本
     */
    protected void addObjective(String questPath, String phaseId,
                                int objectiveIndex, String objectiveText) {
        add("arc_quest.objective." + questPath + "." + phaseId + "." + objectiveIndex,
                objectiveText);
    }

    /**
     * 添加任务阶段的分支选项文本。
     *
     * @param questPath   任务路径
     * @param phaseId     所属阶段ID
     * @param choiceIndex 选项索引（从 0 开始）
     * @param choiceText  选项文本
     */
    protected void addQuestChoice(String questPath, String phaseId,
                                  int choiceIndex, String choiceText) {
        add("arc_quest.choice." + questPath + "." + phaseId + "." + choiceIndex,
                choiceText);
    }

    // ════════════════════════════════════════════════════════
    //  对话系统（对应 DialogueNode API）
    // ════════════════════════════════════════════════════════

    /**
     * 添加对话节点的文本。
     *
     * @param treeId  对话树ID
     * @param nodeId  节点ID
     * @param speaker 说话者名称
     * @param text    对话内容（支持 §格式码 和 {@code %player%} 变量）
     */
    protected void addDialogueNode(String treeId, String nodeId,
                                   String speaker, String text) {
        add("arc_quest.dialogue." + treeId + ".node." + nodeId + ".speaker", speaker);
        add("arc_quest.dialogue." + treeId + ".node." + nodeId + ".text", text);
    }

    /**
     * 添加对话选项的文本。
     *
     * @param treeId      对话树ID
     * @param nodeId      父节点ID
     * @param choiceIndex 选项索引（从 0 开始）
     * @param choiceText  选项文本
     */
    protected void addDialogueChoice(String treeId, String nodeId,
                                     int choiceIndex, String choiceText) {
        add("arc_quest.dialogue." + treeId + ".choice." + nodeId + "." + choiceIndex,
                choiceText);
    }

    // ════════════════════════════════════════════════════════
    //  GUI 界面（对应 QuestJournalScreen 等）
    // ════════════════════════════════════════════════════════

    /**
     * 添加 GUI 按钮文本。
     *
     * @param screenName 界面名称（如 {@code "journal"}, {@code "dialogue"}）
     * @param buttonId   按钮ID（如 {@code "track"}, {@code "abandon"}）
     * @param buttonText 按钮显示文本
     */
    protected void addGuiButton(String screenName, String buttonId, String buttonText) {
        add("arc_quest.gui." + screenName + ".button." + buttonId, buttonText);
    }

    /**
     * 添加 GUI 章节/分组标题。
     *
     * @param screenName   界面名称
     * @param sectionId    章节ID
     * @param sectionTitle 章节标题
     */
    protected void addGuiSection(String screenName, String sectionId,
                                 String sectionTitle) {
        add("arc_quest.gui." + screenName + ".section." + sectionId, sectionTitle);
    }

    /**
     * 添加 GUI 标签/杂项文本。
     *
     * @param screenName 界面名称
     * @param labelId    标签ID
     * @param labelText  文本
     */
    protected void addGuiLabel(String screenName, String labelId, String labelText) {
        add("arc_quest.gui." + screenName + ".label." + labelId, labelText);
    }

    /**
     * 添加 GUI Tab 页签文本。
     *
     * @param screenName 界面名称
     * @param tabId      Tab ID
     * @param tabText    Tab 显示文本
     */
    protected void addGuiTab(String screenName, String tabId, String tabText) {
        add("arc_quest.gui." + screenName + ".tab." + tabId, tabText);
    }

    /**
     * 添加交易界面的通用文本。
     *
     * @param category 分类（如 {@code "btn"}, {@code "status"}, {@code "tooltip"}, {@code "error"}）
     * @param key      键ID
     * @param text     显示文本（支持占位符）
     */
    protected void addTradeGuiText(String category, String key, String text) {
        add("arc_quest.gui.trade." + category + "." + key, text);
    }

    /**
     * 添加 Toast 提示文本。
     *
     * @param toastType Toast 类型
     * @param message   提示消息（支持 {@code %s} 占位符）
     */
    protected void addToast(String toastType, String message) {
        add("arc_quest.toast." + toastType, message);
    }

    /**
     * 添加 Toast 子分类文本。
     *
     * @param category 子分类（如 {@code "branch"}）
     * @param key      键ID
     * @param text     显示文本
     */
    protected void addToastText(String category, String key, String text) {
        add("arc_quest.toast." + category + "." + key, text);
    }

    // ════════════════════════════════════════════════════════
    //  命令反馈（对应 ArcQuestCommands）
    // ════════════════════════════════════════════════════════

    /**
     * 添加命令成功/失败反馈。
     *
     * @param commandName 命令名称（如 {@code "give"}, {@code "reset"}）
     * @param messageType 消息类型（如 {@code "success"}, {@code "error.not_active"}）
     * @param message     反馈文本（支持 {@code %1$s}, {@code %2$s} 位置占位符）
     */
    protected void addCommandFeedback(String commandName, String messageType,
                                      String message) {
        add("arc_quest.command." + commandName + "." + messageType, message);
    }

    // ════════════════════════════════════════════════════════
    //  HUD 覆盖层（对应 QuestHudOverlay）
    // ════════════════════════════════════════════════════════

    /**
     * 添加 HUD 显示文本。
     *
     * @param context 上下文（如 {@code "new_phase"}, {@code "phase_prefix"}）
     * @param text    显示文本（支持 {@code %s} 占位符）
     */
    protected void addHudText(String context, String text) {
        add("arc_quest.hud." + context, text);
    }

    // ════════════════════════════════════════════════════════
    //  交易系统（对应 Trade API）
    // ════════════════════════════════════════════════════════

    /**
     * 添加交易屏幕标题。
     *
     * @param screenType 屏幕类型（如 {@code "screen"}, {@code "quick"}）
     * @param title      标题文本
     */
    protected void addTradeScreenTitle(String screenType, String title) {
        add("arc_quest.trade." + screenType + ".title", title);
    }

    /**
     * 添加交易界面通用标签。
     *
     * @param labelId 标签ID（如 {@code "subtitle"}, {@code "closed"}, {@code "buy"}）
     * @param text    标签文本
     */
    protected void addTradeLabel(String labelId, String text) {
        add("arc_quest.trade." + labelId, text);
    }

    /**
     * 添加交易分类名称。
     *
     * @param categoryId 分类ID（如 {@code "all"}, {@code "weapons"}）
     * @param name       分类名称
     */
    protected void addTradeCategory(String categoryId, String name) {
        add("arc_quest.trade.category." + categoryId, name);
    }

    /**
     * 添加交易物描述模板。
     *
     * @param offerType 交易物类型（如 {@code "item"}, {@code "effect"}, {@code "flag"}）
     * @param template  描述模板（支持占位符）
     */
    protected void addTradeOfferTemplate(String offerType, String template) {
        add("arc_quest.trade." + offerType, template);
    }

    /**
     * 添加交易反馈消息。
     *
     * @param feedbackType 反馈类型（如 {@code "success"}, {@code "error.cooldown"}）
     * @param message      反馈消息
     */
    protected void addTradeFeedback(String feedbackType, String message) {
        add("arc_quest.trade.feedback." + feedbackType, message);
    }

    /**
     * 添加交易冷却提示文本。
     *
     * @param cooldownType 冷却类型（如 {@code "game_day"}, {@code "seconds"}）
     * @param text         提示文本（支持占位符）
     */
    protected void addTradeCooldownText(String cooldownType, String text) {
        add("arc_quest.trade.cooldown." + cooldownType, text);
    }

    /**
     * 添加交易商店名称和描述。
     *
     * @param shopId      商店 ID
     * @param name        商店名称
     * @param description 商店描述
     */
    protected void addTradeShop(String shopId, String name, String description) {
        add("arc_quest.trade.shop." + shopId + ".name", name);
        add("arc_quest.trade.shop." + shopId + ".desc", description);
    }

    /**
     * 添加交易项名称和描述。
     *
     * @param entryId     交易项 ID
     * @param name        交易项名称
     * @param description 交易项描述（可选，可为 null）
     */
    protected void addTradeEntry(String entryId, String name, String description) {
        add("arc_quest.trade.entry." + entryId + ".name", name);
        if (description != null) {
            add("arc_quest.trade.entry." + entryId + ".desc", description);
        }
    }

    // ════════════════════════════════════════════════════════
    //  Guide 系统（对应 Guide构建器 API）
    // ════════════════════════════════════════════════════════

    protected void addGuideTitle(String guidePath, String title) {
        add("guide.arc_quest." + guidePath + ".title", title);
    }

    protected void addGuidePageDesc(String guidePath, int pageIndex, String description) {
        add("guide.arc_quest." + guidePath + ".page_" + pageIndex, description);
    }

    protected void addGuideCategory(String categoryPath, String name) {
        add("guide_category.arc_quest." + categoryPath, name);
    }

    protected void addGuiGuideListText(String key, String text) {
        add("gui.arc_quest.guide_list." + key, text);
    }

    protected void addGuideCommandFeedback(String commandName, String messageType, String message) {
        add("arc_quest.command.guide." + commandName + "." + messageType, message);
    }

    // ════════════════════════════════════════════════════════
    //  通用工具
    // ════════════════════════════════════════════════════════

    /**
     * 添加杂项文本。
     *
     * @param key  完整翻译键（如 {@code "arc_quest.misc.loading"}）
     * @param text 文本内容
     */
    protected void addMisc(String key, String text) {
        add(key, text);
    }

    // ════════════════════════════════════════════════════════
    //  Item Tag 翻译（对应物品标签显示名称）
    // ════════════════════════════════════════════════════════

    /**
     * 添加物品标签的显示名称（默认 minecraft 命名空间）。
     * <p>
     * 翻译键格式：{@code tag.item.minecraft.{tagPath}}
     *
     * @param tagPath 标签路径（如 {@code "wool"}, {@code "logs"}, {@code "planks"}）
     * @param tagName 标签显示名称（如 {@code "羊毛"}, {@code "Logs"}, {@code "木板"}）
     */
    protected void addItemTag(String tagPath, String tagName) {
        addItemTag("minecraft", tagPath, tagName);
    }

    /**
     * 添加物品标签的显示名称（可自定义命名空间）。
     * <p>
     * 翻译键格式：{@code tag.item.{namespace}.{tagPath}}
     *
     * @param namespace 命名空间（如 {@code "minecraft"}, {@code "arc_quest"}）
     * @param tagPath   标签路径（使用点分隔，如 {@code "logs"}, {@code "quest.offer_wood"}）
     * @param tagName   标签显示名称
     */
    protected void addItemTag(String namespace, String tagPath, String tagName) {
        String ns = (namespace == null || namespace.isBlank()) ? "minecraft" : namespace;
        add("tag.item." + ns + "." + tagPath, tagName);
    }
}

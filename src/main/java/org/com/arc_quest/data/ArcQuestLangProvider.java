package org.com.arc_quest.data;

import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.LanguageProvider;
import org.com.arc_quest.Arc_quest;

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
        super(output, Arc_quest.MOD_ID, locale);
        this.locale = locale;
    }

    // ════════════════════════════════════════════════════════
    //  任务系统（对应 QuestBuilder API）
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
     * 添加 Toast 提示文本。
     *
     * @param toastType Toast 类型
     * @param message   提示消息（支持 {@code %s} 占位符）
     */
    protected void addToast(String toastType, String message) {
        add("arc_quest.toast." + toastType, message);
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
}
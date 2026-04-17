package org.com.arc_quest.dialogue.builder;

import org.com.arc_quest.dialogue.api.*;
import org.com.arc_quest.dialogue.registry.DialogueRegistry;
import org.com.arc_quest.quest.api.QuestVisualConfig;

import java.util.*;
import java.util.function.Consumer;

/**
 * 流式对话树构建器 —— 让对话编写变得人性化。
 */
public class DialogueTreeBuilder {

    private final String dialogueId;
    private String defaultNpc = "";

    // 自动追踪第一个节点作为起始节点
    private String startNodeId = null;

    // 所有已提交的节点
    private final Map<String, DialogueNode> committedNodes = new LinkedHashMap<>();

    // [新增] 可选的视觉配置
    private QuestVisualConfig visualConfig = null;

    // ── 当前正在构建的节点（延迟提交） ──
    private String curNodeId;
    private String curSpeaker;
    private String curText;
    private String curAutoNextId;
    private int curDelayMs;
    private final List<DialogueChoice> curChoices = new ArrayList<>();
    private boolean hasOpenNode = false;

    // ═══════════════════════════════════════════════════════
    //  构造
    // ═══════════════════════════════════════════════════════

    private DialogueTreeBuilder(String dialogueId) {
        this.dialogueId = Objects.requireNonNull(dialogueId, "dialogueId must not be null");
    }

    public static DialogueTreeBuilder create(String dialogueId) {
        return new DialogueTreeBuilder(dialogueId);
    }

    // ═══════════════════════════════════════════════════════
    //  树级配置
    // ═══════════════════════════════════════════════════════

    /** 设置默认 NPC 名称（所有节点的 fallback speaker）。 */
    public DialogueTreeBuilder npc(String npcName) {
        this.defaultNpc = npcName != null ? npcName : "";
        return this;
    }

    /** 显式指定起始节点 ID（默认为第一个 .node() 调用的节点）。 */
    public DialogueTreeBuilder startNode(String nodeId) {
        this.startNodeId = nodeId;
        return this;
    }

    /**
     * [新增] 设置视觉配置（对话 UI 的外观定制）。
     * <p>
     * 如果不设置，构建时使用 {@code null}（对话树 record 中为 null 即可）。
     */
    public DialogueTreeBuilder visualConfig(QuestVisualConfig config) {
        this.visualConfig = config;
        return this;
    }

    // ═══════════════════════════════════════════════════════
    //  节点构建
    // ═══════════════════════════════════════════════════════

    public DialogueTreeBuilder node(String nodeId) {
        commitCurrentNode();

        this.curNodeId = Objects.requireNonNull(nodeId);
        this.curSpeaker = null;
        this.curText = "";
        this.curAutoNextId = null;
        this.curDelayMs = 0;
        this.curChoices.clear();
        this.hasOpenNode = true;

        if (startNodeId == null) {
            startNodeId = nodeId;
        }

        return this;
    }

    /** 设置当前节点的说话者。 */
    public DialogueTreeBuilder speaker(String speaker) {
        ensureOpenNode();
        this.curSpeaker = speaker;
        return this;
    }

    /** 设置当前节点的对话文本。 */
    public DialogueTreeBuilder say(String text) {
        ensureOpenNode();
        this.curText = text != null ? text : "";
        return this;
    }

    /** 设置当前节点的延迟（毫秒）。 */
    public DialogueTreeBuilder delay(int delayMs) {
        ensureOpenNode();
        this.curDelayMs = Math.max(0, delayMs);
        return this;
    }

    /** 添加一个选项（简单跳转，无动作）。 */
    public DialogueTreeBuilder choice(String text, String nextNodeId) {
        ensureOpenNode();
        curChoices.add(new DialogueChoice(text, nextNodeId, List.of(), List.of()));
        return this;
    }

    /** 添加一个选项（通过 ChoiceBuilder 配置）。 */
    public DialogueTreeBuilder choice(String text, Consumer<ChoiceBuilder> configurator) {
        ensureOpenNode();
        ChoiceBuilder cb = new ChoiceBuilder(text);
        configurator.accept(cb);
        curChoices.add(cb.build());
        return this;
    }

    /** 添加一个带前置条件的选项。 */
    public DialogueTreeBuilder choiceIf(DialogueCondition condition, String text,
                                        Consumer<ChoiceBuilder> configurator) {
        ensureOpenNode();
        ChoiceBuilder cb = new ChoiceBuilder(text);
        cb.onlyIf(condition);
        configurator.accept(cb);
        curChoices.add(cb.build());
        return this;
    }

    /** 将当前节点设为自动跳转节点。 */
    public DialogueTreeBuilder autoNext(String nextNodeId) {
        ensureOpenNode();
        this.curAutoNextId = nextNodeId;
        return this;
    }

    // ═══════════════════════════════════════════════════════
    //  构建
    // ═══════════════════════════════════════════════════════

    public DialogueTree build() {
        commitCurrentNode();

        if (committedNodes.isEmpty()) {
            throw new IllegalStateException("DialogueTree '" + dialogueId + "' has no nodes.");
        }
        if (startNodeId == null || !committedNodes.containsKey(startNodeId)) {
            throw new IllegalStateException("Start node '" + startNodeId
                    + "' not found in dialogue '" + dialogueId + "'.");
        }

        // ★ 修复：传入第5个参数 QuestVisualConfig（可为 null）
        return new DialogueTree(
                dialogueId,
                defaultNpc,
                startNodeId,
                Map.copyOf(committedNodes),
                visualConfig
        );
    }

    public DialogueTree buildAndRegister() {
        DialogueTree tree = build();
        DialogueRegistry.INSTANCE.register(tree);
        return tree;
    }

    // ═══════════════════════════════════════════════════════
    //  内部
    // ═══════════════════════════════════════════════════════

    private void ensureOpenNode() {
        if (!hasOpenNode) {
            throw new IllegalStateException("No open node. Call .node(id) first.");
        }
    }

    private void commitCurrentNode() {
        if (!hasOpenNode) return;

        String speaker = curSpeaker != null ? curSpeaker : "";

        DialogueNode node = new DialogueNode(
                curNodeId,
                speaker,
                curText,
                List.copyOf(curChoices),
                curAutoNextId,
                curDelayMs
        );

        committedNodes.put(curNodeId, node);
        hasOpenNode = false;
    }

    // ═══════════════════════════════════════════════════════
    //  内部类: ChoiceBuilder
    // ═══════════════════════════════════════════════════════

    public static class ChoiceBuilder {

        private final String text;
        private String nextNodeId = null;
        private final List<DialogueCondition> conditions = new ArrayList<>();
        private final List<DialogueAction> actions = new ArrayList<>();

        ChoiceBuilder(String text) {
            this.text = text;
        }

        public ChoiceBuilder goTo(String nodeId) {
            this.nextNodeId = nodeId;
            return this;
        }

        public ChoiceBuilder close() {
            actions.add(new DialogueAction.Close());
            return this;
        }

        public ChoiceBuilder startQuest(String questId) {
            actions.add(new DialogueAction.StartQuest(questId));
            return this;
        }

        public ChoiceBuilder completeQuest(String questId) {
            actions.add(new DialogueAction.CompleteQuest(questId));
            return this;
        }

        public ChoiceBuilder advancePhase(String questId) {
            actions.add(new DialogueAction.AdvancePhase(questId));
            return this;
        }

        public ChoiceBuilder giveXp(int amount) {
            actions.add(new DialogueAction.GiveXp(amount));
            return this;
        }

        public ChoiceBuilder giveItem(String itemId, int count) {
            actions.add(new DialogueAction.GiveItem(itemId, count));
            return this;
        }

        public ChoiceBuilder notifyTalk(String npcId) {
            actions.add(new DialogueAction.NotifyTalk(npcId));
            return this;
        }

        public ChoiceBuilder notifyInteract(String targetId) {
            actions.add(new DialogueAction.NotifyInteract(targetId));
            return this;
        }

        public ChoiceBuilder runCommand(String command) {
            actions.add(new DialogueAction.RunCommand(command));
            return this;
        }

        public ChoiceBuilder setFlag(String flagName) {
            actions.add(new DialogueAction.SetFlag(flagName));
            return this;
        }

        public ChoiceBuilder setVariable(String key, int value) {
            actions.add(new DialogueAction.SetVariable(key, value));
            return this;
        }

        public ChoiceBuilder action(DialogueAction action) {
            actions.add(action);
            return this;
        }

        public ChoiceBuilder onlyIf(DialogueCondition condition) {
            conditions.add(condition);
            return this;
        }

        DialogueChoice build() {
            return new DialogueChoice(
                    text,
                    nextNodeId,
                    List.copyOf(conditions),
                    List.copyOf(actions)
            );
        }
    }
}
package org.com.arc_quest.dialogue.builder;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.com.arc_quest.dialogue.api.*;
import org.com.arc_quest.dialogue.registry.DialogueRegistry;
import org.com.arc_quest.quest.api.QuestVisualConfig;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

/**
 * 流式对话树构建器 —— 让对话编写变得人性化。
 */
public class DialogueTreeBuilder {

    private final String dialogueId;
    private final Map<String, DialogueNode> committedNodes = new LinkedHashMap<>();
    private final List<ConditionalText> curConditionalTexts = new ArrayList<>();
    private final List<DialogueChoice> curChoices = new ArrayList<>();
    private String defaultNpc = "";
    private String startNodeId = null;
    private QuestVisualConfig visualConfig = null;
    private boolean repeatable = true;  // 默认可重复
    private long cooldownSeconds = 0;   // 默认无冷却
    private CooldownType treeCooldownType = CooldownType.NONE;  // 对话树冷却类型
    private String curNodeId;
    private String curSpeaker;
    private String curText;
    private String curAutoNextId;
    private int curDelayMs;
    private boolean hasOpenNode = false;
    private boolean curRepeatable = true;
    private long curCooldownSeconds = 0;
    private CooldownType curCooldownType = CooldownType.NONE;
    private int curResetTimeTicks = 0;  // GAME_TICK 类型的重置时间点

    private DialogueTreeBuilder(String dialogueId) {
        this.dialogueId = Objects.requireNonNull(dialogueId, "dialogueId must not be null");
    }

    public static DialogueTreeBuilder create(String dialogueId) {
        return new DialogueTreeBuilder(dialogueId);
    }

    /**
     * 设置默认 NPC 名称（所有节点的 fallback speaker）。
     */
    public DialogueTreeBuilder npc(String npcName) {
        this.defaultNpc = npcName != null ? npcName : "";
        return this;
    }

    /**
     * 显式指定起始节点 ID（默认为第一个 .node() 调用的节点）。
     */
    public DialogueTreeBuilder startNode(String nodeId) {
        this.startNodeId = nodeId;
        return this;
    }


    public DialogueTreeBuilder visualConfig(QuestVisualConfig config) {
        this.visualConfig = config;
        return this;
    }

    /**
     * 设置为一次性对话（不可重复）。
     */
    public DialogueTreeBuilder oneTime() {
        this.repeatable = false;
        this.cooldownSeconds = 0;
        return this;
    }

    /**
     * 设置对话为可重复，并添加冷却时间（秒）。
     */
    public DialogueTreeBuilder cooldown(long seconds) {
        this.repeatable = true;
        this.cooldownSeconds = Math.max(0, seconds);
        this.treeCooldownType = CooldownType.SECONDS;
        return this;
    }

    /**
     * 设置对话树为游戏日冷却（每天一次）。
     */
    public DialogueTreeBuilder cooldownGameDay() {
        this.repeatable = true;
        this.cooldownSeconds = 1;
        this.treeCooldownType = CooldownType.GAME_DAY;
        return this;
    }

    /**
     * 显式设置是否可重复。
     */
    public DialogueTreeBuilder repeatable(boolean repeatable) {
        this.repeatable = repeatable;
        if (!repeatable) {
            this.cooldownSeconds = 0;
        }
        return this;
    }


    public DialogueTreeBuilder node(String nodeId) {
        commitCurrentNode();

        this.curNodeId = Objects.requireNonNull(nodeId);
        this.curSpeaker = null;
        this.curText = "";
        this.curConditionalTexts.clear();
        this.curAutoNextId = null;
        this.curDelayMs = 0;
        this.curChoices.clear();
        this.hasOpenNode = true;
        this.curRepeatable = true;
        this.curCooldownSeconds = 0;
        this.curCooldownType = CooldownType.NONE;
        this.curResetTimeTicks = 0;

        if (startNodeId == null) {
            startNodeId = nodeId;
        }

        return this;
    }

    /**
     * 设置当前节点的说话者。
     */
    public DialogueTreeBuilder speaker(String speaker) {
        ensureOpenNode();
        this.curSpeaker = speaker;
        return this;
    }

    /**
     * 设置当前节点的对话文本。
     */
    public DialogueTreeBuilder say(String text) {
        ensureOpenNode();
        this.curText = text != null ? text : "";
        return this;
    }

    /**
     * 添加条件文本（根据条件动态显示不同文本）。
     */
    public DialogueTreeBuilder sayIf(DialogueCondition condition, String text) {
        ensureOpenNode();
        curConditionalTexts.add(new ConditionalText(condition, text != null ? text : ""));
        return this;
    }

    /**
     * 添加带优先级的条件文本。
     */
    public DialogueTreeBuilder sayIf(DialogueCondition condition, String text, int priority) {
        ensureOpenNode();
        curConditionalTexts.add(new ConditionalText(condition, text != null ? text : "", priority));
        return this;
    }

    /**
     * 设置当前节点的延迟（毫秒）。
     */
    public DialogueTreeBuilder delay(int delayMs) {
        ensureOpenNode();
        this.curDelayMs = Math.max(0, delayMs);
        return this;
    }

    /**
     * 添加一个选项（简单跳转，无动作）。
     */
    public DialogueTreeBuilder choice(String text, String nextNodeId) {
        ensureOpenNode();
        curChoices.add(new DialogueChoice(text, nextNodeId, List.of(), List.of()));
        return this;
    }

    /**
     * 添加一个带优先级的选项（简单跳转，无动作）。
     */
    public DialogueTreeBuilder choice(String text, String nextNodeId, int priority) {
        ensureOpenNode();
        curChoices.add(new DialogueChoice(text, nextNodeId, List.of(), List.of(), true, 0, CooldownType.NONE, 0, priority));
        return this;
    }

    /**
     * 添加一个选项（通过 ChoiceBuilder 配置）。
     */
    public DialogueTreeBuilder choice(String text, Consumer<ChoiceBuilder> configurator) {
        ensureOpenNode();
        ChoiceBuilder cb = new ChoiceBuilder(text);
        configurator.accept(cb);
        curChoices.add(cb.build());
        return this;
    }

    /**
     * 添加一个带前置条件的选项。
     */
    public DialogueTreeBuilder choiceIf(DialogueCondition condition, String text,
                                        Consumer<ChoiceBuilder> configurator) {
        ensureOpenNode();
        ChoiceBuilder cb = new ChoiceBuilder(text);
        cb.onlyIf(condition);
        configurator.accept(cb);
        curChoices.add(cb.build());
        return this;
    }

    /**
     * 添加一个带前置条件和优先级的选项。
     */
    public DialogueTreeBuilder choiceIf(DialogueCondition condition, String text, String nextNodeId, int priority) {
        ensureOpenNode();
        curChoices.add(new DialogueChoice(text, nextNodeId, List.of(condition), List.of(), true, 0, CooldownType.NONE, 0, priority));
        return this;
    }

    /**
     * 将当前节点设为自动跳转节点。
     */
    public DialogueTreeBuilder autoNext(String nextNodeId) {
        ensureOpenNode();
        this.curAutoNextId = nextNodeId;
        return this;
    }

    /**
     * 设置当前节点为一次性节点（不可重复访问）。
     */
    public DialogueTreeBuilder nodeOneTime() {
        ensureOpenNode();
        this.curRepeatable = false;
        this.curCooldownSeconds = 0;
        return this;
    }

    /**
     * 设置当前节点的冷却时间（秒）。
     */
    public DialogueTreeBuilder nodeCooldown(long seconds) {
        ensureOpenNode();
        this.curRepeatable = true;
        this.curCooldownSeconds = Math.max(0, seconds);
        this.curCooldownType = CooldownType.SECONDS;
        return this;
    }

    /**
     * 设置当前节点为游戏日冷却（每天一次）。
     */
    public DialogueTreeBuilder nodeCooldownGameDay() {
        ensureOpenNode();
        this.curRepeatable = true;
        this.curCooldownSeconds = 1;
        this.curCooldownType = CooldownType.GAME_DAY;
        return this;
    }

    /**
     * 设置当前节点为游戏时间刻冷却，并指定重置时间点。
     */
    public DialogueTreeBuilder nodeCooldownGameTick(int resetTick) {
        ensureOpenNode();
        this.curRepeatable = true;
        this.curCooldownSeconds = 0;  // GAME_TICK 类型不使用秒数
        this.curCooldownType = CooldownType.GAME_TICK;
        this.curResetTimeTicks = resetTick;
        return this;
    }


    public DialogueTree build() {
        commitCurrentNode();

        if (committedNodes.isEmpty()) {
            throw new IllegalStateException("DialogueTree '" + dialogueId + "' has no nodes.");
        }
        if (startNodeId == null || !committedNodes.containsKey(startNodeId)) {
            throw new IllegalStateException("Start node '" + startNodeId
                    + "' not found in dialogue '" + dialogueId + "'.");
        }

        // 构建对话树
        return new DialogueTree(
                dialogueId,
                defaultNpc,
                startNodeId,
                Map.copyOf(committedNodes),
                visualConfig,
                repeatable,
                cooldownSeconds,
                treeCooldownType,  // 对话树冷却类型
                0  // resetTimeTicks（对话树级别暂不支持）
        );
    }

    public DialogueTree buildAndRegister() {
        DialogueTree tree = build();
        DialogueRegistry.INSTANCE.register(tree);
        return tree;
    }


    private void ensureOpenNode() {
        if (!hasOpenNode) {
            throw new IllegalStateException("No open node. Call .node(id) first.");
        }
    }

    private void commitCurrentNode() {
        if (!hasOpenNode) return;

        String speaker = curSpeaker != null ? curSpeaker : "";

        // 序列化条件文本
        Map<String, String> conditionalTextsMap = serializeConditionalTexts();

        DialogueNode node = new DialogueNode(
                curNodeId,
                speaker,
                curText,
                conditionalTextsMap,
                List.copyOf(curChoices),
                curAutoNextId,
                curDelayMs,
                curRepeatable,
                curCooldownSeconds,
                curCooldownType,  // 使用当前冷却类型
                curResetTimeTicks  // 使用当前重置时间点
        );

        committedNodes.put(curNodeId, node);
        hasOpenNode = false;
    }

    /**
     * 序列化条件文本为 Map：条件标识 → 文本
     */
    private Map<String, String> serializeConditionalTexts() {
        if (curConditionalTexts.isEmpty()) {
            return Map.of();
        }

        Map<String, String> map = new LinkedHashMap<>();

        // 添加条件文本
        for (ConditionalText ct : curConditionalTexts) {
            String key = ct.priority + "|" + serializeCondition(ct.condition);
            map.put(key, ct.text);
        }

        return Map.copyOf(map);
    }

    /**
     * 序列化条件为字符串标识
     */
    private String serializeCondition(DialogueCondition condition) {
        if (condition instanceof DialogueCondition.HasQuest q) {
            return "HAS_QUEST:" + q.questId();
        } else if (condition instanceof DialogueCondition.QuestActive q) {
            return "QUEST_ACTIVE:" + q.questId();
        } else if (condition instanceof DialogueCondition.QuestCompleted q) {
            return "QUEST_COMPLETED:" + q.questId();
        } else if (condition instanceof DialogueCondition.QuestPhase q) {
            return "QUEST_PHASE:" + q.questId() + "|" + q.phaseId();
        } else if (condition instanceof DialogueCondition.Not n) {
            return "NOT:" + serializeCondition(n.inner());
        } else if (condition instanceof DialogueCondition.All a) {
            return "ALL:" + a.conditions().size();
        } else if (condition instanceof DialogueCondition.Any any) {
            return "ANY:" + any.conditions().size();
        } else if (condition instanceof DialogueCondition.IsMorning) {
            return "IS_MORNING";
        } else if (condition instanceof DialogueCondition.IsAfternoon) {
            return "IS_AFTERNOON";
        } else if (condition instanceof DialogueCondition.IsNight) {
            return "IS_NIGHT";
        } else if (condition instanceof DialogueCondition.GameTimeInRange g) {
            return "GAME_TIME_IN_RANGE:" + g.startTick() + "|" + g.endTick();
        }
        return "UNKNOWN";
    }

    // ═════════════════════════════════════════════════════==
    //  内部类: ConditionalText
    // ═════════════════════════════════════════════════════==

    /**
     * 条件文本 - 用于 sayIf()
     */
    private static class ConditionalText {
        final DialogueCondition condition;
        final String text;
        final int priority;

        ConditionalText(DialogueCondition condition, String text) {
            this(condition, text, 0);
        }

        ConditionalText(DialogueCondition condition, String text, int priority) {
            this.condition = condition;
            this.text = text;
            this.priority = priority;
        }
    }

    // ═══════════════════════════════════════════════════════
    //  内部类: ChoiceBuilder
    // ═══════════════════════════════════════════════════════

    public static class ChoiceBuilder {

        private final String text;
        private final List<DialogueCondition> conditions = new ArrayList<>();
        private final List<DialogueAction> actions = new ArrayList<>();
        private String nextNodeId = null;
        private boolean repeatable = true;
        private long cooldownSeconds = 0;
        private CooldownType cooldownType = CooldownType.NONE;
        private int resetTimeTicks = 0;
        private int priority = 0;

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

        /**
         * 给予玩家石剑（预设动作）。
         */
        public ChoiceBuilder presetStoneSword() {
            actions.add(new DialogueAction.GiveItem("minecraft:stone_sword", 1));
            return this;
        }

        /**
         * 给予玩家铁制全套装备（预设动作）。
         */
        public ChoiceBuilder presetIronArmorSet() {
            actions.add(new DialogueAction.GiveItem("minecraft:iron_helmet", 1));
            actions.add(new DialogueAction.GiveItem("minecraft:iron_chestplate", 1));
            actions.add(new DialogueAction.GiveItem("minecraft:iron_leggings", 1));
            actions.add(new DialogueAction.GiveItem("minecraft:iron_boots", 1));
            return this;
        }

        /**
         * 给予玩家钻石全套装备（预设动作）。
         */
        public ChoiceBuilder presetDiamondArmorSet() {
            actions.add(new DialogueAction.GiveItem("minecraft:diamond_helmet", 1));
            actions.add(new DialogueAction.GiveItem("minecraft:diamond_chestplate", 1));
            actions.add(new DialogueAction.GiveItem("minecraft:diamond_leggings", 1));
            actions.add(new DialogueAction.GiveItem("minecraft:diamond_boots", 1));
            return this;
        }

        /**
         * 给予玩家下界合金锭（预设动作）。
         */
        public ChoiceBuilder presetNetheriteIngot() {
            actions.add(new DialogueAction.GiveItem("minecraft:netherite_ingot", 1));
            return this;
        }

        /**
         * 给予玩家火把 x32（预设动作）。
         */
        public ChoiceBuilder presetTorches() {
            actions.add(new DialogueAction.GiveItem("minecraft:torch", 32));
            return this;
        }

        /**
         * 给予玩家面包 x4（预设动作）。
         */
        public ChoiceBuilder presetBread() {
            actions.add(new DialogueAction.GiveItem("minecraft:bread", 4));
            return this;
        }

        /**
         * 给予玩家力量效果60秒（预设动作）。
         */
        public ChoiceBuilder presetStrength() {
            actions.add(new DialogueAction.RunCommand("effect give @p minecraft:strength 60 0"));
            return this;
        }

        /**
         * 给予玩家村庄英雄效果30分钟（预设动作）。
         */
        public ChoiceBuilder presetHeroOfVillage() {
            actions.add(new DialogueAction.RunCommand("effect give @p minecraft:hero_of_the_village 1800 0"));
            return this;
        }

        /**
         * 触发与目标对话者的交互事件（预设动作）。
         */
        public ChoiceBuilder presetAddEvents(String targetId) {
            actions.add(new DialogueAction.RunCommand(
                    "execute as @p run arcquest dialogue epic_wandering_trader"
            ));
            return this;
        }

        /**
         * 添加自定义 Lambda 事件处理器。
         */
        public ChoiceBuilder addEvents(BiConsumer<ServerPlayer, Entity> handler) {
            actions.add(new DialogueAction.LambdaAction(handler));
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

        /**
         * 添加自定义 Lambda 条件。
         * <p>
         * 使用示例：
         * <pre>{@code
         * .conditionIf((player, npc) -> {
         *     // 检查玩家是否有钻石
         *     return player.getInventory().contains(new ItemStack(Items.DIAMOND));
         * })
         *
         * // 或者检查 NPC 状态
         * .conditionIf((player, npc) -> {
         *     if (npc instanceof LivingEntity living) {
         *         return living.getHealth() > living.getMaxHealth() / 2;
         *     }
         *     return false;
         * })
         * }</pre>
         *
         * @param predicate 条件判断函数，接收 (player, npc) 参数
         * @return 当前构建器
         * @deprecated v2 已废弃，请使用标准条件
         */
        @Deprecated(forRemoval = true)
        public ChoiceBuilder conditionIf(BiPredicate<ServerPlayer, Entity> predicate) {
            throw new UnsupportedOperationException(
                    "Custom conditions are not supported in v2. Use standard conditions instead.");
        }

        /**
         * 设置为一次性选项（不可重复选择）。
         */
        public ChoiceBuilder oneTime() {
            this.repeatable = false;
            this.cooldownSeconds = 0;
            return this;
        }

        /**
         * 设置选项冷却时间（秒）。
         */
        public ChoiceBuilder cooldown(long seconds) {
            this.repeatable = true;
            this.cooldownSeconds = Math.max(0, seconds);
            this.cooldownType = CooldownType.SECONDS;
            return this;
        }

        /**
         * 设置游戏日冷却。
         */
        public ChoiceBuilder cooldownGameDay() {
            this.repeatable = true;
            this.cooldownSeconds = 1;
            this.cooldownType = CooldownType.GAME_DAY;
            return this;
        }

        /**
         * 设置游戏时间刻冷却，并指定重置时间点。
         * <p>
         * 使用示例：
         * <pre>{@code
         * // 每天早上6点（tick=0）重置
         * .cooldownGameTick(0)
         *
         * // 每天中午12点（tick=6000）重置
         * .cooldownGameTick(6000)
         *
         * // 每天晚上6点（tick=12000）重置
         * .cooldownGameTick(12000)
         * }</pre>
         *
         * @param resetTick 重置时间点（0-23999），0=早上6点，6000=中午12点，12000=晚上6点，18000=凌晨0点
         * @return 当前构建器
         */
        public ChoiceBuilder cooldownGameTick(int resetTick) {
            this.repeatable = true;
            this.cooldownSeconds = 0;  // GAME_TICK 类型不使用秒数
            this.cooldownType = CooldownType.GAME_TICK;
            this.resetTimeTicks = Math.max(0, Math.min(resetTick, 23999));
            return this;
        }

        /**
         * 设置选项优先级。
         */
        public ChoiceBuilder priority(int priority) {
            this.priority = priority;
            return this;
        }

        DialogueChoice build() {
            return new DialogueChoice(
                    text,
                    nextNodeId,
                    List.copyOf(conditions),
                    List.copyOf(actions),
                    repeatable,
                    cooldownSeconds,
                    cooldownType,
                    resetTimeTicks,  //  支持游戏时间刻冷却
                    priority
            );
        }
    }
}
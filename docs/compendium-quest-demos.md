# 图鉴模式 Quest：统一调查范例

2026-10-03。本文对应 `gameplayVersion: 2`。旧版独立 Research 与 Entry 级 Binding 奖励只供已有任务兼容；新版配置、版本工厂与 JSON 详见 [统一调查使用说明](compendium-unified-quest-usage.md)。

## 1. 三个可玩任务

| 任务 ID | 标题 | 用途 |
| --- | --- | --- |
| `arc_quest:field_compendium_demo` | 荒野手册 | 八项 ALL 调查、成果资料、首次奖与本轮报酬、匿名线索、样本消耗 |
| `arc_quest:renewable_survey_demo` | 轮值调查委托 | 六个候选任选三个，本轮行动重新计数、知识保留、达标停止余项 |
| `arc_quest:parallel_expedition_demo` | 营地联合调查 | 准备、真正并行的生物／材料调查、只读取当前 run 的汇合与回报 |

旧 `collection_codex_demo` 不再用于新接取，保留历史定义。三个新任务共享 `arc_quest:codex/*` 的个人发现和成果；任务行动、报酬与 Run ID 分别保存。

```mcfunction
/arcquest quest give @s arc_quest:field_compendium_demo
/arcquest quest give @s arc_quest:renewable_survey_demo
/arcquest quest give @s arc_quest:parallel_expedition_demo
```

任务面板的「图鉴」分类内打开目录。点击条目打开半透明次级详情，选择具体调查；调查页显示本轮要求，档案页显示资料与成果。追踪立即响应点击，0.2 秒悬停仅控制视觉切换。浏览、收藏、图片放大和 JEI 查询不改变调查进度或领取奖励。

物品图标左键查询配方、右键用途，Tooltip 复用奖励栏。返回目录、面板外空白点击和 Esc 可关闭详情，图片放大时先关闭图片；滑条可以拖动。收藏立即改书签，下一次列表刷新再提前排序。

## 2. 荒野手册：唯一调查进度

| 条目 | 发现 | 本轮要求 | 完成后记录 |
| --- | --- | --- | --- |
| 僵尸 | 首次亲手击败 | 击败 3 只＋提交腐肉 2 份 | `anatomy` 解剖记录 |
| 骷髅 | 首次亲手击败 | 击败 2 只 | `combat` 战斗记录 |
| 蜘蛛 | 首次亲手击败 | 击败 1 只＋提交线 2 份 | `samples` 蛛丝样本 |
| 牛 | 右键互动 | 已发现 | 仅收录 |
| 铁锭 | 首次获得 | 用铁粒合成 1 个铁锭 | `preparation` 制备记录 |
| 煤炭 | 首次获得 | 提交 5 份煤炭 | `fuel_samples` 燃料样本 |
| 原木 | 首次获得任意 `minecraft:logs` 成员 | 提交任意原木共 8 份 | `wood_samples` 木材样本 |
| 骨头 | 首次获得 | 已发现 | 仅收录 |

上述数量只由 Binding 引用的 Phase Objectives 计算。成果不维护另一份累计目标；完成简单发现调查不会自动取得高级成果。牛与骨头没有额外研究计数，已发现后便没有未完成要求可追踪。

铁锭的 CRAFT 只统计真实合成事件，熔炉不冒充合成。OFFER 从背包实际扣除；丢弃再捡回不替代样本交付。原木轮换表示任选成员，总共八份即可，不需要每种木材分别提交。候选集合随当前 run 冻结。

蜘蛛发现前使用作者公开线索，不下发真实身份、头像和秘密奖励。公开的僵尸基础档案能打开，但未接触时仍是尚未发现；`content` 是说明和图片，不是自动完成目标。

全部八项达成后手动确认任务。内置任务最终奖励为绿宝石 ×2，可安装 JSON 为 ×1。调查里程碑另保留：本轮完成三项自动奖励煤炭 ×1；完成生物分类四项可手动领取绿宝石 ×1。

## 3. 首次奖与本次报酬

| 来源 | 稳定奖励 ID | 奖励 | 次数 |
| --- | --- | --- | --- |
| 僵尸首次发现 | `zombie_first_record` | 煤炭 ×1，手动 | 玩家＋Entry 一次 |
| 僵尸解剖成果 | `zombie_anatomy` | 铁粒 ×3，手动 | 首次取得 `anatomy` 一次 |
| 荒野手册僵尸实际调查 | `zombie_investigation` | 绿宝石 ×1，手动 | 该 Quest／run／phase／binding 一次 |
| 荒野手册原木实际调查 | `logs_investigation` | 木棍 ×2，自动 | 该实际调查本轮一次 |

本轮报酬在具体 Binding 配置。轮值委托和联合调查没有继承僵尸／原木 Entry 的通用报酬；简单击败两只僵尸也不会取得荒野手册的解剖成果。它们正常支付各自最终任务奖励。

资料底部通过首次／本次奖励标签切换，奖励较多时分页，领取后原位更新，不弹 Toast。公开预告不代表领取资格。往期欠奖按原 Run、原 Phase／Binding 和原金额领取；不能用当前轮代领。物品交付受背包容量限制，待交付不丢到地上，腾出空位后恢复。

首次领取资格保存标准奖励载荷，自定义回调引用原版本工厂。物品交付使用持久日志和与库存同文件的 receipt。任意命令／外部回调执行前记录尝试，无法承诺通用跨崩溃严格一次；异常禁止自动重放，按日志补偿。

## 4. 重复与并行

轮值委托候选：击败僵尸 2 只、骷髅 2 只、蜘蛛 1 只、提交原木 8 份、煤炭 4 份、铁锭 1 份。任意三项达标后结束该轮候选计数，确认获得绿宝石 ×1。重接新轮保留知识，行动和提交重新开始。

内置联合调查：先提交煤炭 ×1，然后同时激活两个阶段。

- 生物：僵尸两只、骷髅两只、牛互动一次，任选二。
- 材料：提交原木八份、提交煤炭四份、合成铁锭一个，任选二。
- 两阶段都完成后回报：提交原木四份和铁锭一个，手动确认获得钻石 ×1。

汇合读取 `phaseCompleteCurrentRun`，不依赖长期 flag。一次事件先冻结接收者、更新目标，再统一结算；刚激活的新阶段不会再次消费同一份合成或位置采样。

JSON 联合调查略有区别：生物第三候选是蜘蛛；材料第三候选是提交铁锭一个；回报只提交原木四份，最终绿宝石 ×1。两套范例共享玩法契约，各自内部共享 Entry，两个命名空间之间知识独立。

## 5. 建议验收路线

1. 新玩家接荒野手册，打开公开僵尸档案：尚未发现、目标 0/3 与 0/2，不存在永久研究 0/5。
2. 首次亲手击败僵尸：建立发现，本轮击败只推进一次；可领煤炭，不能提前领铁粒。
3. 再击败两只并实际提交两份腐肉：记录解剖成果，同时解锁首次铁粒和本次绿宝石，分别领取防重。
4. 接轮值委托，只击败两只僵尸：该候选完成，不授予未取得的解剖成果；再交原木、煤炭完成配额。
5. 满背包领取合法物品奖励：显示待交付，没有地面掉落；存档重登、腾出空位后交付，不重复发放。
6. 暂不领荒野手册调查报酬，正常确认归档；对应详情保留可领取原来源欠奖，详情关闭、刷新和 JEI 返回不闪烁。
7. 正常重接保留知识；执行管理员 reset 再 give 清关联知识、奖励和旧 run，旧库存不导入。真实新行动仍有效。
8. 联合调查两个并行阶段都完成才开放回报；未完成另一分支时不能借旧全局标记进入。

需要材料时可用原版指令准备；动作仍由实际服务端事件检测。

```mcfunction
/time set night
/summon minecraft:zombie
/summon minecraft:skeleton
/summon minecraft:spider
/summon minecraft:cow
/give @s minecraft:iron_nugget 18
/give @s minecraft:crafting_table 1
/give @s minecraft:oak_log 16
/give @s minecraft:coal 8
```

## 6. 作者配置与旧任务

新 Entry 默认 v2；JSON 必须显式写 `gameplayVersion: 2`，省略按 v1 兼容解释。Entry 定义 Discover、Outcome 和 content，Binding 引用 Phase 的稳定 Objective ID，通过 `.recordOutcome(...)` 声明成果来源并通过 `.reward(...)` 配置本轮报酬。实际 Java／JSON 示例、公开线索、持有目标和接取冷却见 [统一调查使用说明](compendium-unified-quest-usage.md)。

Java 任务必须提供稳定不可变版本工厂；新版本保留旧供应器，禁止旧工厂调用已改成新版的方法。数据包保存完整原始作者 JSON；当前 run 保留原规则和 Tag，展示导出器不是可执行快照。

旧 `.research`、`.researchStep`、`.researchReward` 与 Entry.bindingReward 只在 v1 使用。Demo 显式映射旧阈值：僵尸原击败五次映射解剖成果，骷髅原三次、铁锭原两次合成等同理。已完成旧研究可以迁移；未完成 2/5 不变成新调查 2/3。旧欠奖和已领凭证保留，reset 世代阻止旧锁存和旧迁移恢复知识。

## 7. 可安装 JSON 数据包

[collection-demo-pack](examples/collection/collection-demo-pack) 复制到存档 `datapacks` 后执行 `/reload`，使用独立命名空间：

```mcfunction
/arcquest quest give @s arc_quest_examples:field_compendium_demo
/arcquest quest give @s arc_quest_examples:renewable_survey_demo
/arcquest quest give @s arc_quest_examples:parallel_expedition_demo
```

三个 JSON 内联相同 Entry 定义，冲突定义拒绝注册。图片由模组本地资源提供。生成器 [generate-demo-pack.py](examples/collection/generate-demo-pack.py) 只使用 Python 标准库，读取分支 `gradle.properties`：1.20.1 生成 pack_format 15，1.21.1 生成 48。

本轮 `UnifiedCollectionDemoPackTest` 从真实三个 JSON 编译并检查 v2、无独立 Research、调查成果／报酬归属和当前 run 汇合。其他领域、真实服务端、原生 GUI 与平台差异证据见 [验收记录](compendium-quest-verification.md)，自动回归不能代替多人长期负载认证。

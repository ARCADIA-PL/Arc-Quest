---
name: agent-meeting
description: Arc Quest 项目专用的多角色会议式研讨 skill，用于 quest、dialogue、trade、client/hud、datapack spec/compile/reload、Ponder Intel、arc_quest_editor_modular 等链路的复杂方案取舍、跨层评审、实现复盘与性能审计。适用于用户要求开会讨论、召集研讨、对撞、架构评审、代码评审、性能分析等场景。
disable-model-invocation: true
---

# Arc Quest Agent Meeting

用于在 `Arc Quest` 项目中，把复杂问题组织成“先读代码，再多角色交叉质询，最后给出裁决与施工顺序”的结构化研讨。

## 适用范围

仅用于本项目的实际模块与问题，不按通用软件模板空谈。

优先用于以下议题：

- `quest` 任务链路：spec、validate、compile、logic、network、client cache、journal、tracker、toast、history
- `dialogue` 对话链路：builder、model、runtime、registry、reload、文本延迟解析、对话打开商店后的恢复
- `trade` 交易链路：spec、compile、runtime、network、shop UI、与 dialogue 的联动
- `client/hud` 客户端 HUD / journal / tracker / ponder / intel 交互、动画与状态机问题
- `Ponder / Intel` 场景 ID、注册约定、按钮显示与点击、任务阶段 scene 映射
- `arc_quest_editor_modular` 编辑器链路：core、app、renderers、editors、styles、import/export、selection、registry、normalizer、validator
- 热路径、内存、网络同步、reload 一致性、长期运行稳定性

## 前置要求

开始研讨前必须先读取相关代码、规则、调用链与当前上下文。

禁止：
- 凭印象讨论
- 把 UI 表象误判成 UI 自身问题，而不检查状态链路
- 把 spec 兼容、字段兜底、scene id 规范化补在 Screen / Panel
- 把编辑器导入兼容、旧格式兜底、schema 解释散落到 renderer / editor / 点击事件里
- 跳过 `spec -> validate -> compile -> runtime/UI/registry` 任一层就下结论
- 跳过 `normalizer -> state/shape -> validator -> export -> renderer/editor consume` 任一层就下结论

## 会议模式

根据议题选择最合适的模式。

### 模式一：`review`

用于 Arc Quest 纯代码链路评审，尤其适合 Java、编译链、网络、运行时、reload 问题。

参与角色：
- `Spec`：字段语义、序列化边界、旧格式兼容、datapack 结构
- `Compiler`：校验链、规范化、报错质量、编译产物正确性
- `Runtime`：线程边界、状态一致性、生命周期、异常降级、性能
- `Integrate`：network、registry、event、Mixin、UI 消费方波及面

重点检查：
- 新字段是否贯穿 `spec / validate / compile / consume` 全链路
- 是否引入 reload 残留、注册错位、重复刷新、状态回退
- 网络处理是否正确回主线程，客户端镜像是否仍是唯一可信来源
- 是否把本应在 compiler / helper / service 处理的逻辑错误下沉到了 UI 层

### 模式二：`quest`

用于任务系统专项研讨。

参与角色：
- `Spec/Compiler`：任务字段、phase 字段、校验与编译
- `Logic`：服务端推进与规则语义
- `Sync`：客户端缓存、网络同步、追踪状态恢复
- `HUD`：journal detail、tracker、toast、history 的表现与刷新时机

重点检查：
- `ClientQuestCache` 是否仍是客户端唯一可信镜像
- phase 改动是否同时波及推进、同步、detail、tracker、toast、history
- 是否出现重复刷新、双闪、抖动、追踪状态被覆盖、状态回退
- 是否错误依赖无序 Map 首元素表示“当前对象”

### 模式三：`dialogue`

用于对话系统专项研讨。

参与角色：
- `Model`：builder / model 的字段语义与构建边界
- `Runtime`：节点推进、恢复、异常处理、执行语义
- `Registry/Reload`：builtin 与 datapack 注册层、rebuild、reload 后一致性
- `Trade Link`：对话打开商店、关闭商店、`restoreToNode` 恢复流程

重点检查：
- 是否过早 `getString()` 或把 `Component` 提前降级成 `String`
- translatable 文本是否仍保持延迟解析
- builder / model / runtime 任一改动后，registry rebuild 与 reload 语义是否仍正确
- 对话与商店联动时，是否不仅“能打开”，还“能正确返回原节点”

### 模式四：`trade`

用于交易系统专项研讨。

参与角色：
- `Spec`：交易定义结构、字段约束、兼容与编译输入
- `Runtime`：购买流程、条件判定、服务端消费
- `Network/State`：客户端状态刷新、同步与异常恢复
- `Shop UI`：表现层交互、关闭/恢复流程、与对话联动

重点检查：
- 是否把旧格式兼容、字段兜底直接补在商店界面层
- spec 改动是否同步检查 `spec / validate / compile / runtime / UI`
- 打开、关闭、购买后，客户端状态刷新是否稳定一致
- 与 dialogue 联动时，恢复流程是否正确

### 模式五：`hud`

用于客户端 HUD / Journal / Tracker / Intel / Ponder 交互评审。

参与角色：
- `State`：状态源、主线程约束、缓存读取边界
- `Interaction`：点击路径、反馈、禁用态、恢复态
- `Visual`：动画节奏、可读性、信息层级
- `Data Wiring`：按钮显示条件、注册存在性、点击触发链路

重点检查：
- “按钮显示但点击无效”时，先查数据，再查编译，再查注册，再查点击触发
- 动画问题背后是否其实是状态机或数据同步问题
- UI 是否越层解释 spec、文本或 scene id
- 读取任务状态时是否绕过 `ClientQuestCache`

### 模式六：`ponder-intel`

用于 Intel / Ponder 场景链路专项研讨。

参与角色：
- `Spec`：`intelSceneId` 字段语义与注释
- `Compiler`：scene id 规范化、shorthand 处理、兼容行为
- `Registry`：Ponder 场景注册约定与 ID 生成
- `Consumer`：journal / intel 按钮显示、点击触发、场景打开体验

重点检查：
- `intelSceneId` 三种语义是否被显式区分：原生 scene id、任务阶段 shorthand、标准 quest phase scene id
- 是否统一通过 helper 生成与任务阶段绑定的场景 ID，而不是手拼字符串
- spec 注释、compiler、注册、按钮显示与点击触发是否一致
- 问题到底出在数据缺失、编译未生效、注册不存在，还是点击未触发

### 模式七：`editor`

用于 `arc_quest_editor_modular` 模块化编辑器专项研讨。

参与角色：
- `Core`：shape、factory、normalizer、validator、registry、state 的统一语义
- `App`：导入导出、导航、布局、toast、selection 修正、应用工作流编排
- `Renderers`：树、侧栏、状态栏、事件绑定、state -> render -> bind 链路
- `Editors`：中心编辑器、局部表单组件、setter/path 写回、交互一致性

重点检查：
- 是否把兼容、旧格式兜底、导入导出规范化错误地下沉到 renderer / editor / 事件回调
- 数据字段改动是否同步检查 `factories / shape / validators / import-normalizer / export-normalizer / renderers / editors`
- 是否绕过正式 `state`、setter、path 绑定机制，直接深改共享对象
- 所有“按钮显示但行为不对”的问题，是否按 `selection 有效性 -> 事件绑定 -> action handler 改 state -> rerender 后是否被 selection 修正覆盖` 的顺序排查
- 是否出现“界面显示对了，但导出/切换后丢失”，即写到了临时 UI 状态而没写回正式 state
- 导入前是否先 normalize、导出前是否先 validate，跨 quest / npc / dialogue / trade / gacha 的行为是否仍一致

### 模式八：`perf`

用于 Arc Quest 热路径、内存、网络与规模化退化分析。

参与角色：
- `Hotpath`：tick / event / player 级热点路径与复杂度
- `Memory`：对象分配、集合选型、缓存策略、泄漏风险
- `Network`：同步频率、包体大小、双端状态一致性
- `Scale`：`N=100 / 1000 / 5000` 下的退化曲线与瓶颈变化

重点检查：
- 哪些逻辑在每 tick、每事件、每玩家、每任务目标上触发
- 是否有 O(N²)、重复扫描、重复序列化、重复 rebuild
- 缓存是否有明确失效与清理策略
- reload、多玩家、长时间运行下是否发生退化或状态残留

### 模式九：`all`

用于跨层重大议题，需要同时讨论 quest、dialogue、trade、hud、ponder-intel、editor、network、reload。

做法：
- 先按模块列出现有边界与约束
- 再做交叉质询，明确哪个层应负责解释语义
- 最后收敛为统一裁决、施工顺序与最小验证方案

## Arc Quest 固定检查基线

无论使用哪种模式，都优先检查以下基线：

1. 是否违反模块分层边界
2. 是否错误把兼容、规范化、文本解释塞进 UI 层
3. 是否存在主线程 / 服务端线程边界错误
4. 是否漏掉 `validate`、`compile`、`registry`、`runtime` 或客户端缓存同步中的任一环
5. 是否引入 reload 残留、builtin 与 datapack 注册层错位
6. 是否出现重复刷新、双闪、抖动、状态回退
7. 是否依赖无序容器首元素表达业务当前态
8. 是否为了“更优雅”而增加破坏现有内容的风险
9. 编辑器字段改动是否贯穿 `factories / shape / validators / import-normalizer / export-normalizer / renderers / editors`
10. 编辑器是否错误绕过 `state`、registry、setter/path 写回链路
11. 编辑器导入链路是否先 normalize、导出链路是否先 validate
12. 编辑器交互异常是否本质是 selection 修正、dirty 状态或 rerender 覆盖问题

## 会议流程

1. 先读取与议题直接相关的实现、规则、调用链、配置与消费方代码。
2. 判断最合适的会议模式；若是跨层议题，用 `all`，不要误降级成单模块讨论。
3. 每个角色先给出本视角下的约束、风险、反对点。
4. 至少进行一轮交叉质询，主动否定可疑假设。
5. 将争议收敛为明确裁决；如有未决项，单独列出。
6. 输出施工范围、实施顺序、最小验证方式。

## 输出要求

所有结论必须编号，避免泛泛而谈。

输出时至少包含以下部分：

### 1. 议题与模式
- 当前讨论的问题
- 选择的模式
- 为什么选择该模式而不是其他模式

### 2. 参与角色
- 当前参与角色
- 每个角色负责的视角

### 3. 关键争议点
- 明确争议本身
- 写清楚冲突双方立场
- 标出证据来自哪些代码、规则或调用链

### 4. 裁决清单
每条裁决都要包含：
- 编号
- 结论
- 理由
- 波及模块
- 优先级（`P0 / P1 / P2`）

### 5. 施工建议
- 先改什么，后改什么
- 哪些模块需要一起检查
- 哪些地方不能只修表象

### 6. 验证建议
- 最小验证步骤
- 若涉及 Java 改动，通常至少包含一次 `./gradlew compileJava --console=plain --no-daemon`
- 若涉及编辑器改动，至少检查一次导入、编辑、切换、导出、重新导入闭环
- 若涉及 UI / reload / 网络 / 对话恢复 / 场景打开，指出应补看的链路

## 输出模板

```markdown
# {议题} 研讨总结

## 会议模式
- 模式：quest | dialogue | trade | hud | ponder-intel | editor | review | perf | all
- 选择理由：...

## 参与角色
| 角色 | 关注点 |
|------|--------|
| ... | ... |

## 关键争议点
| # | 争议点 | 立场冲突 | 证据 |
|---|--------|----------|------|
| 1 | ... | ... | ... |

## 裁决清单
| # | 裁决 | 理由 | 波及模块 | 优先级 |
|---|------|------|----------|--------|
| 1 | ... | ... | ... | P0 |

## 施工建议
1. ...
2. ...
3. ...

## 验证建议
- ...
- ...
```

## 约束

- 不要把多角色写成无意义的人物对白，核心是暴露约束冲突。
- 不要假装所有角色都同意；如有分歧，必须写清分歧与裁决原因。
- 不要脱离本项目真实结构空谈；判断必须基于已读取到的实现与规则。
- 若信息不足，先补读代码或先澄清关键前提。
- 若讨论直接服务于实施，结尾必须给出最小可执行方案，而不是只给概念性建议。
- 优先保护现有任务、对话、交易内容的可用性与 reload 一致性，同时保持模块化编辑器的导入/导出闭环稳定，而不是追求表面上的“更优雅”。

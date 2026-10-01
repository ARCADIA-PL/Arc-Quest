# ArcQ × JEI 联动

开发基线：Minecraft 1.20.1、Forge 47.4.20、Java 17、JEI 15.56.0.205。
本文件随代码跟踪；`docs/jei-integration-analysis.md` 是前期分析，当前仓库忽略 `docs/`。

## 版本选择与来源边界

当前构建固定 `jei_version=15.56.0.205`，分别使用作者 Maven 的 `common-api`、`forge-api` 与 Forge runtime 制品。该版本是本轮开发与验收基线；`mods.toml` 声明客户端可选依赖范围 `[15.56.0.205,16)`，这个加载范围不代表其中每一个版本均已实测。后续升级需重新运行联动验收。

选择依据为作者 GitHub 的固定提交、正式发布记录及实际制品，避免仅依赖会变化的分支首页：

- [GitHub 提交 37f996f 的 gradle.properties](https://github.com/mezz/JustEnoughItems/blob/37f996f67082e6b37ca3290a7b7963d64b87b294/gradle.properties)声明 JEI 15.56.0、Minecraft 1.20.1、Java 17、Forge 加载器 47；已通过 GitHub Contents API 读取核实。
- [作者 Modrinth 正式发布 9jqubC9n](https://modrinth.com/mod/jei/version/9jqubC9n)明确为 15.56.0.205、Forge、Minecraft 1.20.1、release，发布时间为 2026-08-31T13:57:20Z。
- [作者 Maven 的 Forge 制品](https://maven.blamejared.com/mezz/jei/jei-1.20.1-forge/15.56.0.205/jei-1.20.1-forge-15.56.0.205.jar)与上述发布文件的 SHA-512 一致：`1306fe15c81d54aa9ff8f3d9f04812c0d61ffaaae433c29621f926db8f8e2c898669ccdd99e025849859d38b7a2eac170c466390b467f4dfa77542b5dbdd1678`。
- 实际 JAR 的 `META-INF/mods.toml` 声明版本 15.56.0.205、Forge `[47.0,)` 和 Minecraft `[1.20.1,1.20.2)`，与 ArcQ 的 Forge 47.4.20 / Java 17 开发基线相容；运行兼容性以本文末尾验收记录为准。

用户指定的 [CurseForge 文件筛选页](https://www.curseforge.com/minecraft/mc-mods/jei/files/all?page=1&pageSize=20&version=1.20.1&gameVersionTypeId=1&showAlphaFiles=hide)在本环境直接访问返回 **HTTP 403**，复查仍相同。因此未直接核实其具体文件页、文件编号或发布分类，不宣称该版本已经通过 CurseForge 页面核验；本轮采用以上已核实的作者来源作为替代证据。

## 玩家使用

安装适配 Minecraft 1.20.1 Forge 的 JEI 后，在 ArcQ 支持的真实物品区域使用 JEI 配置中的“查看配方”“查看用途”按键。若修改了 JEI 按键，以修改后的绑定为准。来源页承担查询与导航，不承担购买、提交、抽奖或领奖；这些操作仍需返回原界面并接受服务器校验。

支持区域包括任务日志中的物品目标与阶段/章节奖励、提交面板、任务历史、商店列表/网格中的商品与每项成本、奖池预览中的抽奖成本与可能奖励，以及具有显式关联的指南入口。所有独立物品图标均按 JEI 当前配置响应鼠标查询，默认左键显示配方、右键显示用途；轮播图标查询当前显示的候选物品。只有当帧可见、未被裁剪或上层弹窗遮挡的区域才能查询。搜索输入期间和原界面正在进行受保护事务时按原界面规则禁用查询。购买、提交、拖动和选择等非物品操作区域继续保留原有左键行为。全屏 ArcQ 界面不把 JEI 侧栏覆盖到原控件上。

商店内商品与每个成本物品独立悬停，当前图标平滑放大至约 1.1 倍，使用原商店自绘 Tooltip 显示所选物品名称与数量。多个商品、复合成本和 Tag 候选共用同一显示/查询槽位；拥挤时在原材料区用小箭头或滚轮翻页。抽奖成本位于抽奖按钮上方独立展示，查询不会触发抽奖。交互边界、覆盖清单与本轮验收见 [物品图标交互](JEI_ITEM_INTERACTIONS.md)。

任务要求以用途呈现：COLLECT 是持有/收集要求，CRAFT 是制作进度要求，DELIVER/OFFER 是交付/提交要求。CRAFT 不会重复注册原版或其他模组的合成配方。标签显示的是候选物品，不能解释成全部候选同时需要。任务奖励作为获得来源显示，阶段分支、可选目标、NPC 标识、章节商店等说明只来自真实任务配置。

任务索引只使用玩家已接受、已完成或失败的任务，以及实际到达或已公开的阶段。未接受任务、未走过的分支、`hidden` 目标和未发现的占位/剪影/遮名收集条目不提供真实物品检索。收集里程碑仅在服务器记录为已解锁或已领取时公开物品。分类和条目的当前可见条件仍会过滤来源。

指南不会因为 JEI 查询而解锁。只有已经授予玩家的指南，且作者明确声明了物品或标签关联，才成为 JEI 信息入口。打开后仍使用 ArcQ 原来的指南页面与 Ponder 阅读界面。指南图标属于装饰，不自动成为物品关联。

当前提供五个来源分类：交易、奖池、任务要求、任务奖励、指南。指南关联是物品的用途/主题入口，不作为物品产出来源；任务与指南来源页可以跳回已授权的原始页面。商店与奖池的 JEI 来源页只展示来源标识，不创建远程交易会话，也没有自动填充或代替原界面执行的“配方转移”操作。

普通、简易与章节商店以交易来源显示真实成本和奖励，支持标签候选、NBT 模板、多成本/多奖励、复合项和玩家动态数量。可见性和进入商店条件由服务器计算；冷却、限额与购买资格是快照说明，不保证玩家稍后仍可成交。交易查询不会建立会话或执行购买。动态注册商店沿用同一过滤与展示路径。

抽奖来源按“单次抽奖的一个可能结果”分别显示，不表示列表里的物品会同时获得。条目区分基础权重、当前服务器权重、普通抽奖概率，以及包含当前保底状态的下一抽快照概率。数量范围按真实抽奖行为解释；复合奖励仅在该结果被选中后执行一次。概率受后续状态变化与抽奖事件扩展影响，不承诺实际下一抽结果。等待服务器结果、动画滚动和领奖确认期间禁止查询。等待超过五秒仅提示并请求一次状态同步，保留待确认请求，绝不自动重抽；迟到结果仍可进入原抽奖动画。收到明确失败后恢复预览，正常确认与关闭兜底确认保持单次发送。

## 指南数据包

`itemAssociations` 为可选字段，省略或空数组表示没有关联。每项恰好使用 `item` 或 `tag` 之一，资源 ID 不带 `#`；`pageIndex` 从 0 开始，省略时为 0，必须指向实际存在的页面。

```json
{
  "id": "example:metallurgy",
  "category": "arc_quest:basics",
  "title": {"mode": "literal", "value": "冶金指南"},
  "itemAssociations": [
    {"item": "minecraft:iron_ingot", "pageIndex": 0},
    {"tag": "forge:ingots/copper", "pageIndex": 1}
  ],
  "pages": [
    {"media": {"type": "none"}, "description": {"mode": "literal", "value": "铁锭的用途。"}},
    {"media": {"type": "none"}, "description": {"mode": "literal", "value": "铜锭的用途。"}}
  ]
}
```

关联不会改变 `unlockConditions`、授予来源或 `hidden` 的原有语义。已经通过服务器授权授予的隐藏指南可使用关联；“可满足解锁条件”本身不是授权。不存在的物品、空标签不会伪造占位物品。标签成员按当前内容/标签状态解析。旧 JSON、旧 `GuideDefinition` 构造器和不调用关联 API 的旧附属模组保持无关联行为。

模块化编辑器的 Guide 页面新增 **JEI subjects (itemAssociations)** 编辑区，原始 JSON 编辑区仍可编辑全部字段。导入/导出保留关联，校验器检查互斥项、资源 ID 和页面范围。修改关联后需按现有流程导出并重载数据包。

## Java 指南 API

```java
GuideBuilder.create("example:metallurgy")
    .title("冶金指南")
    .associatedItem(Items.IRON_INGOT, 0)
    .associatedTag(ResourceLocation.parse("forge:ingots/copper"), 1)
    .page(GuidePageBuilder.create().description("铁锭的用途。"))
    .page(GuidePageBuilder.create().description("铜锭的用途。"))
    .buildAndRegister();
```

`associatedItem` 同时接受 `Item` 或 `ResourceLocation`，`associatedTag` 同时接受 `TagKey<Item>` 或 `ResourceLocation`；均显式传入页面。底层只读值为 `GuideItemAssociation(id, tag, pageIndex)`，可使用 `itemAssociation(...)` 添加。注册/构建顺序与既有指南一致，不需要引用 JEI 类。

## 附属模组只读展示适配

公共包 `org.arcadia.arc_quest.integration.jei.api` 不引用 JEI 类，可供客户端或专用服务器安全加载。附属模组在内容初始化阶段使用：

- `JeiDisplayAdapters.registerObjective(typeId, adapter)`：为自定义 `ObjectiveType` 声明真实要求。
- `JeiDisplayAdapters.registerReward(RewardClass.class, adapter)`：为自定义 `IReward` 声明真实奖励。
- `JeiDisplayAdapters.registerOffer(OfferClass.class, adapter)`：为自定义 `ITradeOffer` 声明成本/奖励。

适配器返回 `JeiDisplayAdapters.Presentation(ingredients, notes)`。一个 `JeiIngredient` 内的 `alternatives` 表示 **OR** 候选；多个 ingredient 表示 **AND**。`amount` 为实际数量，堆栈保留用于匹配的 NBT；`consumed` 表示交付时是否消耗。数量不是物品堆栈最大堆叠量，不能按 64 截断。非物品成本/奖励应写入说明，不得借用装饰图标声称产出某物品。

四参数 `JeiIngredient` 构造器默认按物品/标签候选匹配输入，接受同一物品的 NBT 变体。自定义成本若要求与模板 NBT 完全一致，使用第五个参数 `exactNbt=true`；内置模板成本自动采用此规则。输出来源查询遵循 JEI 的配方子类型规则，因此药水、附魔书等子类型不会仅因基础物品相同而混合。

```java
JeiDisplayAdapters.registerReward(MyItemReward.class, (reward, player) ->
    new JeiDisplayAdapters.Presentation(
        List.of(new JeiIngredient(
            List.of(reward.previewStack(player)), reward.previewCount(player), false,
            Component.literal("附属模组奖励"))),
        List.of(Component.literal("由服务器验证后发放"))));
```

上例 `MyItemReward`、`previewStack`、`previewCount` 是附属模组自行提供的只读接口。适配器可以读取服务器玩家状态，但不得消费物品、执行命令、推进任务、解锁内容、发奖或改变抽奖 RNG。不要调用奖励的 `grant` 或交易的执行方法获得预览。对于客户端直接查询，`player` 可能为空；无法安全预览时返回空展示或明确说明，实际玩家动态结果由服务器目录负责。随机结果不得展开成多个确定同时获得的输出。

实现 `JeiCatalogProvider.collect(ServerPlayer, ArcQuestPlayer, Consumer<JeiCatalogEntry>)` 并调用 `JeiCatalogProviders.register(providerId, provider)` 可提供额外业务目录。必须先证明该玩家有权看到内容，再生成条目。Provider ID 以及目录行 ID 必须稳定、无碰撞；`navigationTarget` 与 `navigationDetail` 是标识信息，不能是命令。不要遍历整个注册表后默认公开所有内容。单个 provider 抛异常或产生重复 ID 时，该 provider 的本次输出整体丢弃，避免公开半份授权状态。

导航约定：任务条目的 `navigationTarget` 为任务 ID，`navigationDetail` 为阶段 ID 或空字符串；条目里程碑可指向所属阶段，任务/分类里程碑打开任务上下文。指南条目的 detail 为十进制的零基页面索引。商店和奖池条目使用商店 ID 与条目 ID，不能借助目录直接发起交易。附属模组自定义导航应沿用明确支持的类别与语义。

## 可选依赖与同步

构建只编译引用 JEI API，开发运行时加载 JEI；ArcQ 发布包不内嵌 JEI。JEI 插件、实际 runtime 和查询界面适配放在客户端兼容层，公共展示模型、服务器权威快照与任务历史不依赖 JEI 类。没有 JEI 的客户端与专用服务器仍可加载 ArcQ；没有 JEI runtime 的客户端不会订阅目录。

新增目录数据包后，ArcQ 网络协议从 14 升为 15。联机双方必须使用这一版 ArcQ 构建；是否安装 JEI 仍是客户端的可选项。服务器不需要为了目录投影安装 JEI。

`-PwithoutJei` 从开发运行依赖中移除 JEI，但保留编译 API；`-PjeiServerAudit` 将现有客户端渲染模组改为仅编译依赖，用于专用服务器验收。它们是开发验收开关，不会把 JEI 或验收代码合并进发布包。

目录按已订阅玩家投影，每秒检查变化，除恢复重传外通过内容 hash 仅发送变化的目录。订阅有租约，并在退出时清理。目录采用完整替换快照，包含会话 nonce、内容 epoch、修订号、总长度与分块摘要，防止断线换服、乱序、旧 epoch 和不完整分块混入当前视图。只有全部内容模块成功应用，客户端才提交新的 epoch/hash、发送 Ready 并启用同 epoch 的目录；部分模块失败时继续保留上一提交状态，新的 JEI 目录保持隐藏。已成功应用的单个模块沿用原有行为。

内容应用失败使用 1/2/4 秒退避，最多请求三次重同步，耗尽后明确记录日志。相同失败快照不会被当作已成功应用，也不会重置重试次数；新内容或断线会重置该状态。收到更高 epoch 后，旧头和旧分片不会回写内容。单个 provider 失败只移除其本次输出；整个目录投影或编码失败时发送空目录以撤回旧视图。传输大小上限为 16 MiB；附属模组不应依赖无限目录容量。

自定义动态价格、奖励与可见性必须由只读适配器返回。不能假设客户端本地持有的全部定义等于玩家可以看到的全部目录。数量使用独立整数传输，不能依赖 ItemStack NBT 的 byte 型 Count；物品堆栈和 NBT 使用防御性副本，避免 JEI 修改业务模板。

## 已知任务历史与可见性

`JeiQuestHistory` 使用主世界 `SavedData` 文件 `arc_quest_jei_known_sources`，按玩家 UUID 和任务 ID 记录已公开的阶段、已解锁及已领取的里程碑。该记录仅用于展示证据，绝不作为完成、发奖或交易权限。普通阶段事件和任务状态同步都会捕获证据，终止状态同步仍使用被移出活跃任务表之前的运行数据，因此终结的收集任务不会丢失最后里程碑。

记录只合并稳定 ID，不为每次刷新追加目录副本。目录重建会移除玩家不再持有活跃/完成/失败记录的任务历史，定义已删除的阶段也不会生成条目。当前显式隐藏目标以及收集条件仍会生效。过去未安装此版本的旧存档没有完成阶段历史时，只展示可证实的任务完成奖励；不会推断所有分支都走过。

收集模式当前业务引擎实际发放 `CollectionRewardNode`，不执行普通 `phaseRewards` 或 `completionRewards`。JEI 遵循这一业务事实；如果作者需要收集奖励，应配置条目、分类或任务里程碑，而非仅填普通奖励字段。

## 验证记录

最新物品图标交互验收见 [2026-10-01 交互验收](JEI_ITEM_INTERACTIONS.md)。以下表格保留初始 JEI 联动基线的实际测试输出与发布包检查，不代表当前 JAR 哈希或最新测试数量。测试失败的早期日志不作为通过证据；全量计数不再叠加分项测试数。

| 验收项 | 最终结果与证据 |
| --- | --- |
| Java 全量测试 | **366 passed，0 failed，0 errors，0 skipped**；`build/test-results/test`、`build/jei-final-build.log` |
| 指南编辑器与文档往返 | **20 passed，0 failed**；`build/jei-editor-verification.log` |
| 数据生成 | 英文、中文资源生成成功；`build/jei-final-tests-data.log` |
| 安装 JEI 的真实客户端 | **PASS WITH_JEI**；`build/jei-client-audit-final.log` |
| 不安装 JEI 的真实客户端 | **PASS WITHOUT_JEI**；真实登录、内容同步、原生任务日志渲染、目录未启用；`build/jei-client-nojei.log` |
| 专用服务器，带 JEI 依赖 | **All 5 required tests passed**；`build/jei-server-verification-3.log` |
| 专用服务器，不带 JEI | **All 5 required tests passed**；`build/jei-server-nojei.log` |
| 发布构建 | `build`、`jarJar`、`reobfJarJar`、`sourcesJar` 成功；`build/jei-final-build.log` |
| 发布内容隔离 | 三个新 JAR 均含当前插件、内容同步状态与抽奖状态代码；主包及嵌套依赖中无 JEI 本体、JUnit、验收类或测试结构模板；`build/jei-artifact-audit.json` |
| 原始存档 | 43 个文件，11,421,297 字节；收尾前后文件列表、大小与 SHA-256 一致；最近修改时间早于本轮验收 |
| CurseForge 页面 | 直接访问仍为 HTTP 403；使用本文列出的 GitHub 与作者发布制品证据，不记为页面核验通过 |

真实 JEI 客户端验收使用 Forge 启动的客户端、实际 `IJeiRuntime`、真实渲染帧与集成服务器玩家，已检查：

- 五个来源分类可见且非空，分别在实际 GUI 缩放 **1.0 和 2.0** 完成渲染。每次都断言实际窗口缩放，不将被窗口上限夹回的选项值算成另一档缩放。
- 真实铁剑来源查询、绿宝石用途查询、任务与指南导航、修改后的 JEI 鼠标快捷键，以及查询后返回原指南界面实例。
- 服务器撤销指南授权后，权威快照与 JEI 查询均移除该条目。连续两次真实资源重载使内容 epoch 从 **1 → 2 → 3**，测试任务、商店与奖池条目在快照和 JEI 中各保留一份，已撤销指南不恢复；每轮重载后的交易页面均实际渲染。
- 原生抽奖界面在超时等待、迟到结果滚动与领奖确认阶段持续禁止查询且不发布物品命中区域；显式失败恢复查询，关闭兜底确认只发送一次。此项使用测试源码提供的单调时钟和发包记录器驱动真实界面，向服务器发送的抽奖/确认包为零；服务端抽奖展示和只读语义另由 GameTest 验证。
- 浏览前后服务器玩家库存与总经验相同；断线后目录清空。跨连接旧包、乱序、重复片、错误摘要与 epoch 隔离由自动单元测试补充覆盖。

Java 回归还覆盖标签候选、精确 NBT 与普通物品匹配、动态数量和超过一组堆叠的数量、复合奖励、隐藏/未到达任务阶段、旧存档缺失历史证据、权限撤回、内容应用失败重试及抽奖迟到响应。

客户端验收仅允许使用一次性克隆目录 `run/saves/ArcQ JEI Verification`，运行入口检查世界目录名；服务器 GameTest 使用 `build/jei-gametest`。原始 `run/saves/新的世界` 的 43 个文件 SHA-256 已在收尾复核，清单记录在 `build/jei-original-save-manifest.json`。Windows Graphics Capture 两次捕获失败，因此没有人工截图验收，也没有宣称完成远端多服务器之间的人工切换游玩；上述游戏内结果来自可重复的真实 runtime 验收程序。

### 复现命令

在项目根目录运行以下命令；客户端验收前必须准备上述一次性克隆世界。所有 Gradle 启动串行执行。

```powershell
node --test arc_quest_editor_modular/test/guide-jei.test.js arc_quest_editor_modular/test/document-roundtrip.test.js
.\gradlew.bat test runData --console=plain
.\gradlew.bat -PjeiServerAudit runGameTestServer --console=plain
.\gradlew.bat -PjeiServerAudit -PwithoutJei runGameTestServer --console=plain
.\gradlew.bat -PjeiRuntimeAudit runClient --console=plain
.\gradlew.bat -PjeiRuntimeAudit -PwithoutJei runClient --console=plain
.\gradlew.bat build jarJar reobfJarJar --console=plain
```

项目已在 `gradle.properties` 中跳过 ForgeGradle 附加证书 HEAD 预检查，无需额外命令行参数；实际 HTTPS 证书验证仍启用。数据生成与最后一次发布构建分开执行，确保新生成的语言资源进入 JAR。客户端 `runClient` 验收任务会校验对应的 `PASS` 标记并拒绝 `FAIL`；服务端仍需检查 `All 5 required tests passed`，不能仅凭 Minecraft 正常退出判断成功。

`src/gameTest` 为独立 source set，仅在显式验收启动中加载。发布文件位于 `build/libs`：

- `arc_quest-forge1.20.1-1.0.8-all.jar`：包含项目原有 Ponder、Flywheel 与 MixinExtras 嵌套依赖的发布包，不包含 JEI。SHA-256：`6898357a7757e1b592ae4818736d3723885d9860b624fbf6f9766ed5cc54df5b`。
- `arc_quest-forge1.20.1-1.0.8.jar`：不带上述嵌套依赖的主 JAR。
- `arc_quest-forge1.20.1-1.0.8-sources.jar`：主代码源码，不包含验收夹具。

JEI 仍需由客户端单独安装；当前 ArcQ 协议版本为 16，联机双方须同步更新匹配的 ArcQ 构建。

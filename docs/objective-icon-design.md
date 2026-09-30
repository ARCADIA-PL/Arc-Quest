# ArcQ Objective ICON 详细设计

状态：设计基线已实现。本文最初以 HEAD 11395ef2 为审查基线，现保留原始方案和拟议 API 文字，便于追溯设计取舍。当前实际配置、行为、注册事件和资源包格式请阅读 [Objective 图标使用说明](../OBJECTIVE_ICONS.md)，并以当前源码为准；本文中的“建议”“拟议”或类名不再作为现行 API 合同。构建与客户端运行结果由独立验收记录说明。

本方案优先满足已确认的交互：每个目标可有一个图标槽；左图标、右侧文字与进度；物品目标使用真实物品，Tag 目标自动轮换真实候选；tooltip 使用任务面板现有自绘样式；生物目标使用头颅正面的静态二维头像，或经过逐生物适配的生物纹理脸部裁切；作者可以指定图片；没有可用图标就回到无图布局。禁止完整生物模型预览，新增裁切策略也不实例化完整生物。

## 1. 核心约束与优先级

### 1.1 解析规则

每个 Objective 拥有一个独立的图标描述，默认 AUTO。显式设置只有一个生效来源，Builder 后一次设置覆盖前一次设置。

| 模式 | 行为 | 失败时 |
| --- | --- | --- |
| NONE | 作者明确关闭图标 | 保持无图，不进入自动解析 |
| TEXTURE | 显示指定资源纹理 | 不显示图标；记录可定位的诊断 |
| PROVIDER | 使用作者显式指定的客户端图标解析器 | 解析器不存在或无法产生图标时不显示 |
| AUTO | 使用该目标类型的默认解析器 | 无可用结果则不显示 |

因此，作者的显式选择优先于自动行为。关闭图标属于显式选择，不会被默认解析器重新打开。

### 1.2 AUTO 内置规则

- COLLECT / CRAFT：由真实目标匹配信息解析物品图标。支持标签匹配的物品目标返回候选序列并自动轮换。第一批原生 UI 接入 COLLECT / CRAFT；不会因为显示层支持 Tag 就擅自给仅支持物品 ID 的 CRAFT 增加标签匹配能力。
- KILL：优先应用资源包实体头像规则；没有覆盖规则时，优先按明确的 EntityType → 头颅来源映射生成固定正面的二维头像，其次使用已注册的生物纹理脸部裁切适配。均不可用则 NONE。不为未知生物猜测 UV。
- 其他类型：默认没有自动图标。作者仍可指定图片；附属模组可注册自己的默认解析器。
- 不依据显示文字、资源 ID 名称相似性或继承关系猜测目标。例如流浪者不能因为属于骷髅类就自动使用骷髅头。
- TALK 的 NPC ID、INTERACT 的通用目标 ID 不直接当作实体 ID。必须由明确知道其语义的适配器解析。

没有图标时不画占位符、刷怪蛋、通用问号，也不预留空图标列。隐藏目标先执行可见性判断，不为其生成头像、tooltip 或 JEI 命中区域。

### 1.3 外观和业务语义分离

图标只负责外观，目标的匹配条件、进度、计数语义和 JEI 查询材料仍来自任务数据。

例如：收集橡木原木的目标使用作者绘制的树木图片后，JEI 仍查询橡木原木。击杀僵尸的图标以僵尸头为渲染素材，并不意味着任务要求僵尸头物品，也不自动产生僵尸头的 JEI 查询入口。

## 2. 面向任务作者的 Builder API

### 2.1 常用 API 保持简单

~~~java
// 零配置：自动显示真实目标物品。
ObjectiveBuilder.collect(Items.OAK_LOG, 20);

// 零配置：僵尸有内置头颅映射，生成正面二维头像。
ObjectiveBuilder.kill(EntityType.ZOMBIE, 10);

// 不依赖牛是否有自动头像适配，作者可直接指定图片覆盖。
ObjectiveBuilder.kill(EntityType.COW, 5)
    .iconTexture("my_pack:textures/gui/objectives/cow.png");

// ResourceLocation 版本，适合 Java 附属模组。
ObjectiveBuilder.kill(EntityType.COW, 5)
    .iconTexture(cowPortraitTexture);

// 明确不显示图标。
ObjectiveBuilder.kill(EntityType.ZOMBIE, 10)
    .noIcon();

// 恢复自动选择，适合可复用 Builder 配置方法。
builder.autoIcon();
~~~

建议公开的方法：

~~~java
ObjectiveBuilder iconTexture(String texture);
ObjectiveBuilder iconTexture(ResourceLocation texture);
ObjectiveBuilder icon(ObjectiveIconSpec icon);
ObjectiveBuilder noIcon();
ObjectiveBuilder autoIcon();
~~~

所有方法都返回当前 Builder，支持链式调用。String 重载解析并校验 ResourceLocation；不接受磁盘路径和网络 URL。空字符串不能隐式表示关闭，应使用 noIcon()；传入 null 应给出明确错误。

资源路径示例：my_pack:textures/gui/objectives/cow.png 对应资源包中的 assets/my_pack/textures/gui/objectives/cow.png。通常作者只需要一张透明背景 PNG，不必设置宽高、UV、相机或模型参数。

### 2.2 高级 API 收进图标值对象

~~~java
// 纹理图集中的一个区域：原始图片像素坐标。
builder.icon(ObjectiveIcons.texture(atlasTexture)
    .region(32, 16, 16, 16));

// 指定附属模组注册的解析器。
builder.icon(ObjectiveIcons.provider(customProviderId));
~~~

ObjectiveIconSpec 是不可变、可序列化的公共数据对象；AUTO / NONE 使用规范化的常量值。纹理对象默认使用完整图片、保持宽高比、居中放入图标槽，保留透明通道。region 可选，不要求作者重复填写整张图片尺寸。

不把缩放、偏移、相机参数、客户端回调和渲染器实例全部堆到 ObjectiveBuilder 上。普通作者使用简单入口，高级需求通过图标值对象或注册解析器表达。

### 2.3 旧调用兼容

- ObjectiveEntry 旧构造器继续保留，并默认委托 AUTO；新主构造器增加图标描述。
- ObjectiveBuilder.build()、ObjectiveEntry.withObjectiveId() 等复制路径必须保留图标。
- 未填写 icon 的旧任务定义按 AUTO 处理，不需要批量补字段。
- 本项目 ObjectiveBuilder 当前没有 craft(Item, int) 工厂；实施时可补充该便捷入口，让 CRAFT 与 COLLECT 一样容易配置图标，不改变其事件计数语义。

## 3. JSON 配置和编辑器

### 3.1 统一数据格式

ObjectiveSpec 增加独立的可选 icon 字段，不把显示信息散落在 extraData 的字符串键中。

~~~json
{
  "id": "hunt_cows",
  "type": "arc_quest:kill",
  "targetId": "minecraft:cow",
  "requiredCount": 5,
  "icon": {
    "type": "arc_quest:texture",
    "texture": "my_pack:textures/gui/objectives/cow.png"
  }
}
~~~

约定：

- 省略 icon 或读到 null：AUTO；标准导出时省略默认 AUTO。
- 明确关闭：icon = {"type":"arc_quest:none"}。
- 显式 AUTO 可读：{"type":"arc_quest:auto"}，导出可规范化为省略。
- 纹理裁切：在 texture 对象中增加 region: {"x":32,"y":16,"width":16,"height":16}。
- 指定解析器：{"type":"arc_quest:provider","provider":"my_mod:custom_target_icon"}。

本轮不引入第二个并列的 iconTexture JSON 字段，避免两个字段同时存在时产生优先级歧义。Builder 的 iconTexture() 是生成标准 icon 对象的快捷方法。

固定的数据模式只需承载 AUTO、NONE、TEXTURE、PROVIDER；未来附属模组的新来源通过 provider ID 扩展，不要求每新增一种来源就修改公共网络枚举。provider 使用已有目标及其 extraData 作为上下文，首版不额外开放任意图标脚本或不受约束的 JSON 参数包。

### 3.2 编辑器表单

在 Objective 编辑区增加「目标图标」：

1. 默认显示「自动 / 自定义图片 / 不显示」三种模式。
2. 自定义图片仅必填纹理资源 ID；展示对应 assets 路径提示。
3. 裁切区域和指定 provider 放在高级配置中，不增加普通作者的必填项。
4. 复制目标、导入、导出、保存再打开，图标字段都必须保持。
5. 编辑器在没有游戏资源预览能力时提示「路径合法，资源存在性待客户端验证」，不能伪称资源已验证。实际预览优先复用现有资源服务，不把建立新的资源浏览后台作为第一批前提。
6. 未安装某个 provider 的编辑器仍保留其合法 ID 和原始配置，不能静默删除；未知图标 type 保留原始节点并报告不支持，由目标版本的校验器处理。

## 4. 扩展接口：三种用户，不同入口

### 4.1 任务作者：逐个目标覆盖

使用 iconTexture() 或 JSON icon 字段。任意类型的 objective 均可显式配置图片，不需要给每种类型重新实现 UI。

### 4.2 整合包作者：资源包统一覆盖

建议提供客户端资源规则，使整合包可以为某种生物统一配置头像，而不必编辑每个任务。

拟议规则路径：

~~~text
assets/<entity-namespace>/arc_quest/objective_icons/entities/<entity-path>.json
~~~

例如：

~~~text
assets/minecraft/arc_quest/objective_icons/entities/cow.json
assets/my_pack/textures/gui/objectives/cow.png
~~~

规则文件：

~~~json
{
  "type": "arc_quest:texture",
  "texture": "my_pack:textures/gui/objectives/cow.png"
}
~~~

这些规则只参与 AUTO 的实体头像解析。优先顺序是：目标显式配置 → 资源包实体规则 → 已注册的可用头颅映射 → 已注册的生物纹理裁切适配 → NONE。资源包中指定 NONE 可以关闭某种生物的默认头像；它不会覆盖某个目标作者明确指定的图片。

资源包规则允许 TEXTURE / ENTITY_TEXTURE_PORTRAIT / NONE，避免规则递归调用 provider。其中 ENTITY_TEXTURE_PORTRAIT 是实体资源规则的内部类型，不扩充 ObjectiveIconSpec 的四种公共模式。由资源管理器的包优先级决定同路径覆盖，重载后整体更新索引。显式实体规则解析失败时返回 NONE 并诊断，不悄悄恢复被覆盖的内置头像。

### 4.3 附属模组作者：注册目标解析器和头像来源

设计客户端注册事件，分别提供：

~~~java
// 以下为拟议接口示意，不是现有可编译 API。
event.registerProvider(providerId, provider);
event.bindDefault(objectiveTypeId, providerId);
event.registerHeadPortrait(entityTypeId, headPortraitDefinition);
event.registerTexturePortrait(entityTypeId, texturePortraitDefinition);
~~~

两个独立维度：

- 新目标类型：注册其 AUTO 解析器，不改原生任务界面。
- 新生物头像：注册 EntityType → 头颅来源及正面参数，或 EntityType → 生物纹理裁切定义，不改 KILL 目标解析器。

注册使用 ResourceLocation ID；重复 provider ID 或默认绑定应在注册阶段给出明确冲突，避免依赖模组加载顺序或任意优先级数字。注册表在客户端初始化阶段冻结；资源包覆盖层通过重载建立新快照。

建议客户端 provider 只返回声明式结果，不直接接管目标行渲染：

~~~java
interface ObjectiveIconProvider {
    IconResolution resolve(ObjectiveIconContext context);
}
~~~

IconResolution 为有图或无图的明确结果。有图结果可描述实际物品候选序列、资源纹理区域、待生成的头颅头像或已适配的生物纹理裁切头像。候选是不可变列表，不强制只有一张图。可附带指向真实目标候选的稳定 key，但不能自行授予 JEI 查询权限，也不包含可以通过任务 JSON 下发执行的绘图代码。

ObjectiveIconContext 提供已授权目标定义、稳定目标 key、所需的客户端目标状态及资源代次。默认解析器不依赖每帧时间或随意访问全量任务；复杂 provider 应声明依赖的目标状态，以便有针对性地失效缓存。

新 provider 一般复用 ITEM / TEXTURE / HEAD_PORTRAIT / ENTITY_TEXTURE_PORTRAIT 四种绘制结果即可。特殊模组头颅若需要专用模型适配，可以额外注册客户端 HeadPortraitAdapter，但输入和输出仍受固定正面静态头像这一契约约束。公共图标模式与客户端绘制结果是两层概念，不能混为一套网络枚举。

## 5. 分层结构

~~~text
ObjectiveBuilder / ObjectiveSpec
             ↓
     ObjectiveIconSpec             公共数据；可保存、可同步
             ↓
     ObjectiveIconResolver         显式配置、类型绑定、资源规则
             ↓
       ResolvedIcon                ItemSequence / Texture / HeadPortrait / TexturePortrait / None
             ↓
       IconFrameSelection          本帧候选；图标、tooltip 与查询共享
             ↓
     ObjectiveIconRenderer         绘制；头颅生成、贴图裁切与缓存
             ↓
     ObjectiveRowLayout            有图或无图；标准或紧凑

真实目标匹配描述 + 本帧候选 ──→ 任务自绘 tooltip / 授权 JEI 查询焦点
~~~

最小职责划分：

| 组件 | 职责 |
| --- | --- |
| ObjectiveIconSpec / IconSpec codec | 独立公共值对象、校验、JSON 和网络表达 |
| ObjectiveIconRegistry / Resolver | 注册、默认规则、选择来源；不直接画 UI |
| HeadPortraitRegistry / Cache | 已知实体映射、正面定义、二维头像生成和释放 |
| TexturePortraitRegistry / Composer | 逐实体裁切定义、变体选择、二维图层合成及失效 |
| ObjectiveIconRenderer | 图片、物品、已生成头像的统一绘制 |
| ObjectiveRowLayout / Renderer | 图标、文字、进度与命中区域统一排版 |

不把客户端渲染器或闭包保存进 ObjectiveEntry。服务端只处理声明式数据，不加载 net.minecraft.client 类。JEI 也是可选出口；未安装 JEI 时所有图标和原生提示正常工作。

## 6. Tag 目标：自动候选与轮换

### 6.1 沿用提交面板的体验，抽出共享机制

当前 QuestOfferPanel 已解析标签成员、轮换物品，并让 hoveredStack 指向当前候选。这是新图标组件的体验参考。应抽取真实候选解析与选择逻辑供目标行复用，避免单阶段、并行阶段和提交面板分别维护三套实现。

客户端以已授权的目标定义及已同步物品标签构造候选。解析规则必须与实际任务匹配相同；图标解析器不能仅凭某个 extraData 字段就声称某种目标支持标签。

候选包含真实 ItemStack 的防御性副本及稳定身份。排序优先使用物品注册 ID，存在合法 NBT 差异时将其纳入身份。不要以背包数量每帧重排，防止玩家正在悬停的候选突然变化。

- 0 个有效候选：NONE；不使用含 ItemStack.EMPTY 的占位候选列表。
- 1 个候选：静态显示，不运行轮换。
- 多个候选：自动轮换；不要求作者逐个填写物品，不要求玩家先打开候选面板。

### 6.2 轮换策略

建议默认每 1000 ms 切换一次，作为统一客户端样式参数，而不是每个任务的必填配置。该间隔是设计默认值，实施时可按实际观感微调。

当前提交面板通过 iconCycleTicker 每渲染一帧递增、到 60 切换，因此实际速度受 FPS 影响。新共用控制器使用单调时间或累计 dt 驱动，不照搬帧计数。

建议交互规则：

- 未悬停、未聚焦时自动轮换。
- 悬停或键盘聚焦图标时暂停，保证玩家能读完 tooltip 并查询眼前这个物品。
- 离开后从当前候选继续计时，不立即跳过多个候选。
- 打开 JEI 时保存当前候选；返回仍停留在该候选。如果期间定义或标签已改变，则重新验证，不保留失效物品。
- 不把鼠标滚轮用于手动换图，避免抢占并行卡片原有的目标列表滚动。
- 同一个目标在不同尺寸的视图中使用相同选择规则；以稳定目标 key 管理 UI 状态，不使用一个全局数组下标干扰所有目标。

### 6.3 一帧只选择一次

新增 ItemIconCycleController 输出不可变的 IconFrameSelection，至少包含候选身份、当前展示堆栈、候选序号、候选总数以及定义/资源代次。

同一个选择结果同时交给：

1. 图标绘制。
2. 任务面板 tooltip 请求。
3. JEI 图标命中区域及查询绑定。

输入事件使用最后一个有效绘制帧的选择；不在查询时重新计算当前时间或再从标签里取第一个物品。这可避免显示橡木原木、tooltip 却显示白桦原木、JEI 又查询另一种材料。

Tag 更新时，若当前候选仍存在则尽量保留；否则选择新列表首项并重置轮换。旧内容 epoch、旧资源代次和被遮挡帧的选择结果不能继续响应。

### 6.4 自定义纹理与 Tag 的关系

显式自定义纹理仍优先：它会替代自动轮播画面。目标依然保留标签语义，但画面没有明确展示某个候选，此时 tooltip 使用标签组说明，JEI 查询整组真实候选。

这保证同一个设置只有一种可预测含义：想要自动轮播用 AUTO，想要固定作者图片用 TEXTURE，不在自定义图片背后偷偷轮换查询对象。

## 7. Tooltip：使用任务面板自绘通道

### 7.1 复用现有实现

使用 QuestJournalScreen 的 tooltip 请求、延迟、淡入、尺寸动画和屏幕边界处理，最终由 JournalTooltipRenderer 绘制边框、文字与物品图标。Objective 不直接调用原版 GUI tooltip，也不创建另一种 tooltip 皮肤。

当前屏幕已有 setHoveredRewardTooltip() 和 setHoveredCustomTooltip()。建议在保留这些兼容入口的同时增加通用请求入口，例如 requestTooltip(JournalTooltipRequest)，让目标物品不必借用“奖励”这一命名。

物品本身的描述文本仍沿用现有 ItemStack tooltip 内容收集方式；“使用自绘 tooltip”改变的是布局与绘制通道，不意味着丢弃物品名称、附魔或已有文本内容。

### 7.2 内容组合

| 图标情况 | Tooltip 来源与补充 |
| --- | --- |
| 单一真实物品 | 该目标物品的现有 tooltip 内容；按需补充实际 JEI 按键 |
| 自动轮换 Tag | 本帧物品内容；补充标签组说明、候选 i/N 和查询当前物品的按键提示 |
| 自定义图片覆盖单一物品 | 真实目标物品的信息，不把图片资源 ID 当物品名 |
| 自定义图片覆盖 Tag | 标签组说明、可接受材料数量；查询整组 |
| 生物头像或自定义生物图片 | 生物名称和相关目标条件，不显示头颅物品的 tooltip |
| 非物品自定义目标 | 目标自己的说明或显式适配内容，不猜物品关联 |

标签说明使用真实计数语义：普通数量型可以注明可混合累计；唯一集合型则注明不同种类。循环图标不会改变目标的计数规则。

### 7.3 排版与生命周期

Tooltip 在任务屏幕顶层统一绘制，退出目标列表的 scissor 后再处理自身裁剪；保留任务主题色、边框、透明度和现有动画。悬停命中必须经过卡片滚动、任务界面缩放和模态面板遮挡判断。

tooltip 的身份应包含稳定目标 key 与本帧候选 key，避免缓存上一物品的文本。高级物品提示开关、语言变化和资源重载也需要失效对应测量缓存。对于现有自绘组件未支持的第三方复杂 tooltip 内容，不承诺等同完整原版/Forge tooltip 组件体系。

## 8. 生物二维头像：头颅与生物纹理裁切

### 8.1 明确映射

头颅策略首版内置六组对应关系：骷髅、凋零骷髅、僵尸、苦力怕、末影龙、猪灵，对应其原版头颅物品。没有可用头颅时可尝试第 8.4 节已注册的生物纹理裁切适配；两种来源都不可用时为 NONE。玩家的具体皮肤依赖 profile，不通过通用 EntityType.PLAYER 自动猜测。

头像生成走头颅模型路径，不实例化完整生物，不渲染全身模型。直接把头颅交给普通物品 GUI 绘制不能保证正面，因为外层物品显示变换会影响朝向。

HeadPortraitDefinition 描述：头颅来源、模型/适配器身份、固定正面姿态、正交投影、取景边界、统一光照和静态动画参数。UI 最终只绘制缓存的二维纹理。

### 8.2 扩展头颅

已知原版 skull 模型可复用标准适配器。任意模组返回一个头颅 ItemStack 并不保证自动获得正确正面，必要时必须提供固定正面参数或专用 HeadPortraitAdapter，也可以直接提供图片纹理。

Forge 的 skull model 扩展事件可帮助找到模型，但它不会替 ArcQ 提供 EntityType → 头颅的业务映射，也不会为所有第三方模型定义正面方向。

### 8.3 生成与缓存

- 在客户端资源和模型就绪后，于渲染线程生成；尽量在 screen 正式绘制前准备，不在带有目标列表 scissor 的行循环中随意切换 framebuffer。
- 使用独立模型实例、PoseStack、buffer 和离屏渲染目标，固定动画状态。
- 切换前后 flush；通过 try/finally 恢复 framebuffer、viewport、投影、model-view、shader/color、blend/depth/cull、scissor 与光照状态。
- 使用有界缓存。缓存 key 包含资源代次、模型/适配器身份、头颅及相关 NBT/profile、固定姿态、裁切和分辨率档位。
- 同一种头颅可被多个任务共享；不按任务进度或当前帧重复生成。
- 模型/纹理缺失时报告当前头颅来源不可用，并缓存本代次失败结果，避免每帧重试和刷日志；AUTO 可按第 8.5 节继续尝试已注册纹理适配，显式头颅 provider 则返回 NONE。
- 重载和释放必须销毁 GPU 资源；旧代次的生成任务不能把结果写回新缓存。

### 8.4 新策略：生物纹理脸部裁切

用户新增确认：可以从生物各部位的展开贴图裁切脸部生成头像，不同生物的脸部位置、尺寸和附加部位分别适配。该策略的产物仍是静态二维头像，不渲染完整生物或旋转模型。

这里调整了原先“没有头颅一律不显示”的边界：没有头颅但已有经过验证的纹理适配可以显示；没有头颅且没有纹理适配仍不显示任何额外图形，也不预留空槽。不能从 EntityType 名称、贴图文件名或图片大小推断脸部坐标。

拟议 TexturePortraitDefinition 至少包含：

| 字段 | 含义 |
| --- | --- |
| texture | 来源生物纹理的 ResourceLocation；首版优先静态资源 |
| referenceSize | UV 坐标所依据的原始贴图宽高，必须为正数 |
| canvasSize | 合成头像的逻辑画布宽高；与任务行图标槽尺寸分离 |
| layers | 从后到前排列的有限个矩形图层，至少一个 |
| layer.region | 来源纹理上的脸部、鼻子或附加部位矩形，按 referenceSize 的像素坐标填写 |
| layer.destination | 该部位在头像画布中的位置和尺寸，不等于原贴图位置 |
| layer.flipX / flipY | 可选镜像，默认关闭 |
| layer.tint | 可选显式颜色，默认不染色；不自动从任意目标字段猜测 |

基础生物可以只用一个脸部矩形。需要额外部位时，用多层矩形按指定位置合成，例如先画耳朵或角，再画脸，最后画鼻部或外层。层顺序由适配作者明确指定；不自动重建三维鼻子、几何角或光照。这种取舍需要逐种生物进行实际视觉验收。

解析器完成来源选择后返回 TexturePortrait；ObjectiveIconRenderer 仅绘制其声明的二维区域和图层。单层直接采样纹理，不为每种来源都创建离屏 framebuffer；多层先采用数量有上限的直接二维绘制，确有性能需要时再烘焙合成缓存。

### 8.5 来源优先级和使用入口

默认 AUTO 的实体头像选择顺序：

~~~text
目标显式 icon（TEXTURE / PROVIDER / NONE，具有最终决定权）
    ↓ 仅 AUTO 继续
资源包实体规则（固定图片 / 生物纹理裁切 / NONE）
    ↓ 没有实体规则时继续
已注册且可用的头颅正面头像
    ↓ 确定不可用时继续
已注册且可用的生物纹理脸部裁切
    ↓ 均无有效结果
NONE：无图标，无占位
~~~

头颅仍为默认优先来源，保留此前确认的表现。资源包可为某一种生物指定裁切规则，从而明确优先使用裁切头像。普通任务作者继续使用 AUTO 或 iconTexture()，无需理解 UV。少数任务需要强制另一套头像时，使用已有 icon(provider(...)) 扩展入口或直接指定成品图片，不给 ObjectiveBuilder 再堆叠裁切、鼻子、耳朵等专用 setter。

自动头颅来源只有在确认不支持、资源缺失或生成失败后才进入下一来源。异步准备中的 pending 不视为失败，不逐帧在两种头像之间切换。显式配置或显式资源包覆盖失败仍按前述契约降级 NONE，不绕过作者选择。

拟议资源包规则示例，存于对应实体的 objective_icons/entities 路径：

~~~json
{
  "type": "arc_quest:entity_texture_portrait",
  "texture": "example_mod:textures/entity/example_beast.png",
  "referenceSize": {"width": 64, "height": 64},
  "canvasSize": {"width": 16, "height": 16},
  "layers": [
    {
      "region": {"x": 8, "y": 8, "width": 8, "height": 8},
      "destination": {"x": 2, "y": 2, "width": 12, "height": 12}
    },
    {
      "region": {"x": 24, "y": 8, "width": 4, "height": 2},
      "destination": {"x": 5, "y": 8, "width": 6, "height": 3}
    }
  ]
}
~~~

以上是虚构纹理的格式示例，坐标不对应任何真实原版生物。实施时应检查项目目标版本实际资源与模型 UV，并为每个内置适配提供验证过的规则，不能把示例坐标当作内置映射。

### 8.6 变体、资源包与校验边界

- 首版静态纹理 ID 可完整工作，不依赖世界中存在一只目标生物，也不为查贴图创建实体。
- 多种皮肤或状态的目标只有在任务已有可识别、已授权的变体条件时才选对应规则。仅指定 EntityType 的目标使用适配明确约定的代表头像；不随机换皮肤、不绑定附近实体、不暗示只接受该外观。必要时 tooltip 说明为该类生物的代表图。
- 条件确实要求某变体但当前适配无法准确表达时，不以错误头像暗示支持；该自动适配返回不可用，作者可用显式图片补充。新头像显示能力不新增服务端变体匹配能力。
- 未来特殊模组需要动态纹理来源时，经客户端 provider 返回已解析资源与稳定变体 key；公共任务数据仍是声明式描述。玩家皮肤、异步下载和任意脚本不列入本批实现。
- UV 用 referenceSize 归一化。同布局的高清纹理可按比例采样；资源包改变 UV 排布时，必须同时提供新的实体裁切规则，不能承诺自动兼容所有资源包。
- 校验来源/画布尺寸、正数区域、边界、有限图层数量和显式颜色范围；结构错误或资源缺失使本规则不可用并诊断一次。完整配置通过校验后再绘制，不显示只加载成功的一半脸。
- 像素采样、透明叠加和各图层缩放采用统一 GUI 规则；在不同 GUI 缩放下检查接缝、透明边与裁切越界。
- 解析/合成缓存 key 包含资源代次、实体规则身份及修订、来源纹理、referenceSize、canvasSize、全部图层参数和适用的变体 key；任务进度不参与头像缓存。
- 直接采样的纹理由资源管理器管理；只有实际创建了合成 GPU 资源才由头像缓存释放。资源重载与断线清理失败缓存和派生状态，禁止旧代次结果回填。
- 生物纹理头像的 tooltip 仍由任务面板自绘，显示真实生物目标及条件；不显示贴图文件名，也不自动产生任何头颅物品或其他物品的 JEI 查询入口。

## 9. 标准布局与并行卡片

统一由 ObjectiveRowLayout 返回 iconRect、textRect、progressRect、countRect、rowHeight 和可交互区域，绘制与命中都消费这一份结果。

标准与紧凑模式都保留用户确定的“两行”结构：左侧图标占据文字与进度两行，右侧上文案、下进度条及数量。紧凑模式不是把图标硬塞进当前 14 像素单行。

建议起点是并行模式约 16–20 逻辑像素图标、约 24 像素目标行高；最终值结合真实字号与 GUI 缩放校准。标准模式可适度放大。NONE 的 icon width 和 gap 均为 0，回到无图排版。

并行卡片处理：

- 图标放在各 objective 行，不给整个 phase 随意选择一个代表物品。
- 上方分段进度仍是阶段概览，不能与下方目标列表混为一层。
- 默认容纳两条完整的图标目标，其他目标在卡片内部滚动；同屏 phase 外框高度按统一视口对齐。
- 混合物品、生物和无图目标时，根据实际行高累计内容高度，不能继续使用 total × 14 计算。
- 文字宽度扣除图标、间距、进度数字和滚动条；保留既有长文字 marquee 行为。
- 布局、换行、滚动、tooltip、JEI 命中统一经过相同坐标变换和裁剪。
- 拖拽中的卡片不显示目标 tooltip、不发起 JEI 查询；图标不额外抢占卡片选中和拖拽动作。

## 10. JEI：视觉与查询的对应规则

图标 provider 不能直接把用于绘制的 ItemStack 注册为 JEI 查询材料。查询需要定位原目标，读取当前服务端授权的 catalog inputs，并与选中的真实候选匹配。

建议增加桥接入口，例如 objectiveCandidate(..., objectiveKey, candidateKey, bounds)，由隔离层执行候选授权校验。原生目标组件不 import mezz.jei.*。

| 情况 | 图标上的 JEI 查询 |
| --- | --- |
| AUTO 单物品 | 真实目标物品 |
| AUTO Tag 轮播 | 本帧显示并在悬停时锁定的真实候选 |
| 自定义图片 + 单物品目标 | 真实目标物品 |
| 自定义图片 + Tag 目标 | 整组真实候选，明确提示标签查询 |
| KILL 头颅 / 生物纹理裁切头像 | 显示真实生物语义，不自动建立头颅或其他物品查询 |
| NONE 或无物品语义目标 | 不创建图标查询热点；既有有效的目标文字查询可保留 |

现有目标文字或概览条的整组查询可以继续保留；图标的精确候选热点需要优先于包住它的整行区域，避免轮播图标最终仍触发整组查询。

按键提示读取 JEI 的实际映射，包括玩家改键；不固定写 R/U。跳转与返回继续复用既有协议，恢复 phase 选择、滚动和候选。未安装 JEI 时图标、轮播和自绘 tooltip 均正常工作。

## 11. 数据同步、编辑器与兼容链路

### 11.1 必须贯穿的位置

| 现有位置 | 设计变更 |
| --- | --- |
| ObjectiveBuilder / ObjectiveEntry | 增加独立 icon；默认 AUTO；所有构造和复制保留 |
| PhaseDefinition 自动补 objectiveId | 经 withObjectiveId 复制后 icon 不丢失 |
| ObjectiveSpec / QuestSpecCompiler | icon DTO 与共用编译器；字段组合、ID 和裁切范围校验 |
| QuestSpecValidator / QuestParityComparator | 校验以及 Java / JSON 等价比较包含 icon |
| QuestSpecJsonReader / Writer | 标准 JSON 读写，默认值规范化 |
| Web 编辑器 import/export normalizer | 白名单显式包含 icon，防止保存后丢失 |
| quest-shape-phase nested setter | 支持 icon.type、icon.texture、icon.region 等路径 |
| 编辑器 factory / UI / validators | 默认值、控件、复制以及诊断同步支持 |

客户端的 compileClientPresentation 路径跳过外层 validator，因此 icon 编译器自身必须验证输入，不能仅依赖编辑器或服务端预先检查。

### 11.2 沿现有定义同步，不传图像像素

当前数据包任务定义已经通过 canonical JSON → DatapackContentCodec → ClientDatapackContentApplier 同步。新增可选 DTO 字段可走这条链路；图标资源 ID 随定义传输，图片由客户端模组或资源包提供。

不增加目标进度包字段，不把候选轮播下标写入玩家存档，不通过网络传送生成后的头像像素。定义 hash、内容 epoch 和现有授权/可见性规则照常生效。

Java Builder 任务沿用现有的客户端可见定义机制。当前 ClientQuestSnapshotProjector 只投影 objective 类型描述，并非任意 Java 任务的完整同步器，因此不能宣称“仅在服务端存在的 Java 任务”会因为新增 icon 字段就自动显示在客户端。扩展这一能力需要独立的任务展示同步设计。

默认字段兼容不等于承诺任意旧版客户端都能联机；是否调整内容 schema 或网络协议，应按实际编码变化和现有版本策略判断。本方案不要求为图标另建网络通道。

### 11.3 错误反馈

- 编写阶段检查 ID 格式、类型组合和非负裁切区域，并返回具体字段路径。
- 服务器不验证本地是否有客户端 PNG，也不加载客户端图标注册器。
- 合法 ID 在客户端缺资源、provider 不存在或头像来源不支持时，仅相应图标来源不可用；显式配置降级 NONE，AUTO 按既定来源链尝试后最终降级 NONE。任务本身继续按原逻辑工作。
- 诊断包含 quest / phase / objective key、资源或 provider ID；同一资源代次只报告一次。
- 未知 provider ID 作为数据保留，客户端可诊断为不可用；不能因为当前机器没装附属模组就让编辑器删除配置。

## 12. 缓存与失效

区分四种状态，避免把所有内容放进一个依赖 JEI 的静态缓存：

1. 目标解析缓存：稳定目标 key + 定义修订 + 标签/资源代次 + 声明的语义依赖。
2. 纹理与头像缓存：资源 ID、区域/图层定义、变体、模型/姿态、分辨率和资源代次；包含失败缓存。
3. 轮播会话状态：当前候选、剩余轮换时间、悬停/聚焦和 JEI 暂停状态。
4. Tooltip 测量：候选与文本身份、语言、字体和高级提示设置。

资源重载清理资源、模型和失败缓存；标签更新重建候选；内容 epoch 改变失效目标与命中绑定；断线清理会话与玩家相关信息。旧异步准备结果必须携带代次并在回填时复核。

资源重载接入既有客户端 reload listener。任务定义/epoch 的更新接入 ClientDatapackContentReceiver / Applier 的通用生命周期，不能只挂在 JeiCatalogClient 上，因为用户可能没装 JEI。

每帧只执行布局、轻量候选选择和绘制，不扫描完整物品注册表、读 PNG、解析 JSON、创建生物实例或重新烘焙头颅。

## 13. 前置一致性与范围

已确认的 COLLECT Tag 匹配路径和动态 requiredCount 口径问题仍是验收前置项。轮播不能让玩家误以为“显示出的所有材料都能推进目标”，而服务端实际上只匹配单一 targetId。

这些问题应单独修正并测试，再接入图标层。图标 provider 不修改业务计数，也不自行增加 CRAFT 的烧炼、机器或标签支持。OFFER / DELIVER 的交付流程不作为本轮图标设计的扩展任务；提交面板主要作为候选轮播和视觉风格的复用来源。

## 14. 分批实施与提交建议

以下为后续实施顺序；本轮仅交付设计。

| 批次 | 内容 | 可独立验收的结果 |
| --- | --- | --- |
| 前置修正 | COLLECT Tag 匹配与动态要求一致性 | 可视候选确实计入，显示要求与实际判定一致 |
| 1：公共配置 | IconSpec、Builder、构造/复制、JSON 编译/校验、编辑器保留 | 一行配置可完整保存与回读；旧定义可用 |
| 2：物品目标 | 共用目标行、Tag 候选与时间轮换、自绘 tooltip、两种 phase 布局 | 不安装 JEI 也能完成全部图标体验 |
| 3a：头像基础 | 六类头颅二维生成、NONE、资源包覆盖、客户端 provider 注册、重载缓存 | 头颅与无图规则可验证 |
| 3b：生物纹理策略 | 单层脸部裁切、多层合成、逐生物适配、变体边界与资源包规则 | 已适配的无头颅生物可显示二维头像；未适配仍无图 |
| 4：JEI 与回归 | 本帧候选绑定、改键、返回恢复、遮挡/拖拽、文档和综合回归 | 显示、tooltip 与查询始终一致 |

每批提交包含对应测试和文档，不把互不相关的任务行为修改混入 UI 提交。

## 15. 验收清单

### 配置与扩展

- 无 icon 的旧 JSON、旧 ObjectiveEntry 构造调用得到 AUTO。
- 未设置 objectiveId 时，自动补 ID 后自定义图片仍存在。
- Builder 最后一次设置生效；NONE 不被自动解析覆盖。
- Java / JSON parity、Web 导入导出、嵌套编辑、复制、撤销与重做保留 icon。
- 客户端未注册的 provider 和缺失纹理只导致无图，不刷日志或崩溃。
- 专服启动不触达客户端渲染类；显式纹理和新目标类型可通过公开接口扩展。

### Tag、Tooltip 与 JEI

- 空、单成员和多成员标签分别显示无图、静态图、自动轮播。
- 30 / 60 / 144 FPS 下使用相同时间节奏；悬停/聚焦暂停，离开继续。
- 每次轮换后，图标与任务面板自绘 tooltip 的物品一致。
- tooltip 保持现有主题、动画、物品信息及屏幕边界行为。
- JEI 查询眼前候选；自定义 Tag 图片查询整组；头像不查询头颅物品。
- 标签重载删除当前候选、内容撤销、JEI 返回与断线后均无过期命中。
- 没有 JEI 时轮播和 tooltip 正常；真实按键提示不会误写固定 R/U。

### 生物与布局

- 六类原版头颅均为固定正面静态二维图像；无头颅且无纹理适配的生物不显示任何额外图形。
- 生物纹理策略按各自规则正确裁切脸部，附加部位按声明的二维图层合成，不创建完整生物或渲染全身模型。
- 自定义图片覆盖自动头像；资源包可指定裁切或 NONE；头颅不可用时只尝试已注册纹理适配。
- 单层、多层、缺失纹理、非法区域、未知变体、同布局高清纹理与改变 UV 的资源包均有明确结果。
- 多个目标共享头像资源时不重复每帧解析；头颅准备中不发生来源闪烁，重载后裁切规则和失败缓存更新。
- 单阶段与并行 phase 的图标、文字、进度、计数正确对齐。
- 混合有图/无图、长名称、多目标、不同 GUI 缩放下不重叠。
- 隐藏、被遮挡、滚出视口、拖拽中的目标不响应 tooltip 或 JEI。
- 多次资源重载与窗口调整后，旧 GPU 资源可释放，渲染状态不污染其他界面。

## 16. 源码与官方资料索引

项目源码（当前审查基线）：

- ObjectiveBuilder.java:225 — Builder 输出 ObjectiveEntry。
- ObjectiveEntry.java:29、89 — 兼容构造与 withObjectiveId。
- PhaseDefinition.java:308 — 自动补 objective ID 的复制路径。
- quest/spec/compile/QuestSpecCompiler.java:323 — ObjectiveSpec 编译。
- quest/spec/validate/QuestSpecValidator.java:100 — 目标校验。
- quest/spec/parity/QuestParityComparator.java:50 — 等价比较。
- arc_quest_editor_modular/scripts/core/import-normalizer.js:51、export-normalizer-phase.js:82、quest-shape-phase.js:228 — 编辑器白名单与嵌套设置。
- client/hud/quest/QuestIconRenderer.java:23、88 — 既有 VisualAsset 的物品优先与普通物品绘制。
- client/hud/quest/offer/QuestOfferPanel.java:408、570 — 当前按帧轮换和标签候选解析。
- client/hud/quest/journal/detail/CollectObjectiveTooltip.java:31 — 当前标签 tooltip 取首成员，需要改为使用本帧候选。
- client/hud/quest/journal/QuestJournalScreen.java:480、520、536 — 自绘 tooltip 生命周期与布局。
- client/hud/quest/journal/component/JournalTooltipRenderer.java:11 — 任务面板自绘样式。
- client/hud/quest/journal/detail/JournalDetailParallelPhase.java:375、611 — 固定行高与目标列表。
- client/compat/jei/screen/JeiScreenIngredients.java:92、204 — 授权语义来源与热点选择。
- integration/jei/api/JeiDisplayAdapters.java:114 — 内置目标的物品语义；KILL 不产生物品输入。
- Arc_Quest.java:120 — 客户端资源重载注册。
- client/data/sync/ClientDatapackContentReceiver.java:36、78、110 — epoch、清理和应用生命周期。
- data/sync/ClientQuestSnapshotProjector.java:16 — 类型描述投影边界。

上述 Java 路径均相对于 D:/Arc Quest-master/src/main/java/org/arcadia/arc_quest/，编辑器路径相对于项目根目录。

官方资料：

- Forge 1.20.1 GUI 文档：https://docs.minecraftforge.net/en/1.20.1/gui/screens/
- Forge 1.20.1 逻辑侧与物理侧：https://docs.minecraftforge.net/en/1.20.1/concepts/sides/
- Forge 1.20.1 客户端资源：https://docs.minecraftforge.net/en/1.20.1/resources/client/

## 17. 本轮交付与验证边界

本轮为详细设计及只读源码交叉审查，未修改生产代码，未运行 Gradle，也未宣称上述新增 API 已经可用。方法名、建议像素尺寸、轮换间隔和裁切 JSON 格式属于拟议设计；用户已确定的二维头像、逐生物适配的贴图裁切策略、无图降级、自定义纹理优先、自绘 tooltip 和 Tag 自动轮播属于实现约束。新增策略的适配范围须逐项验证后公布，不能以设计示例宣称已适配所有原版或模组生物。

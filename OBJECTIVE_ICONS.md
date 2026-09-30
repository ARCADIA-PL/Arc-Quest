# Objective 图标使用说明

实现与运行验收见 [Objective ICON 验收记录](docs/objective-icon-verification.md)。

图标是独立的展示配置，不改变任务匹配、数量、进度、提交或 JEI 的真实材料语义。公共配置不依赖客户端类或 JEI；图标资源由客户端模组/资源包提供。

本文描述当前源码中已实现的配置、客户端行为和扩展接口。原始方案保留在 [Objective ICON 详细设计](docs/objective-icon-design.md) 供追溯；其中的拟议名称以本文与源码为准。本文不充当客户端运行验收记录。

## 默认行为与布局

未设置 `icon` 时使用 AUTO。当前默认绑定如下：

| Objective 类型 | 自动图标 | 无有效来源时 |
| --- | --- | --- |
| COLLECT / CRAFT | 真实目标物品；有物品 Tag 时按注册 ID 排序展示真实候选 | 无图；空 Tag 不回退到 `targetId` |
| KILL | 对应实体的资源包覆盖、已注册头颅头像或纹理头像 | 无图；不猜测实体类别或纹理 UV |
| 其他类型 | 默认无图，可显式指定图片/provider，或由附属模组绑定默认 provider | 无图 |

内置 KILL 头颅映射仅有骷髅、凋灵骷髅、僵尸、苦力怕、猪灵、末影龙六种。牛与猪使用各自的原生生物纹理脸部 UV 适配；猪包含鼻部叠层，牛包含角部与脸部。流浪者、尸壳、未知模组实体等不会因为名字或继承关系相似而套用这些头像。玩家头颅/profile 不属于内置支持范围。

头颅由专用头部模型按固定朝向准备为二维缓存，任务行只显示静态图片。牛猪及其他 UV 适配直接采样纹理；两种路径均不创建世界实体、不绘制完整生物模型、不用刷怪蛋替代。

单阶段详情使用标准布局：左侧 24 像素图标、6 像素间距，右侧文案，进度条与计数位于文案下方。并行阶段的紧凑目标行使用 20 像素图标与相同间距，文案保持单行并使用原生滚动文字；卡片内部滚动与外部裁切共同限制图标的显示和命中区域。尺寸是任务面板逻辑坐标，随面板和 GUI 缩放。

`NONE`、资源缺失、未知 provider 和其他无有效图片的结果均不预留空图标列；文字与进度使用完整行宽。隐藏目标直接跳过显示，不产生头像、Tooltip 或 JEI 命中区。

## Tag 轮播、Tooltip 与 JEI

多个真实候选按时间轮播，默认每 1000 毫秒切换一次；只有一个候选时保持静止。鼠标停留在目标行、图标获得键盘焦点或界面暂不可交互时暂停。任务面板中 `Tab` / `Shift+Tab` 在当前可见图标间前进/后退；鼠标移动清除键盘焦点。行滚出视野或页面被暂挂时保存候选并暂停计时，恢复后继续显示原候选。

图标 Tooltip 使用任务面板自绘的小卡片，只显示物品/生物名称；Tag 物品增加一行“任意 Tag 名称”。不显示候选序号、重复目标说明、物品长描述、JEI 快捷键或查询说明，也不重复绘制物品小图。卡片固定锚定图标上方，边缘空间不足时调整位置；只做透明度淡入，避免缩放和追随鼠标抖动。图标悬停/键盘焦点使用轻微底色和细边框。目标文字悬停则保留完整目标说明，方便阅读并行卡片的长标题。

安装 JEI 并获得服务端授权目录后：

- 在自动轮播的物品图标上查询，只查询当前显示的候选；Tag 目标行的其余区域可以查询完整候选组。
- 为 Tag 目标指定自定义图片后，图片保持作者选择的外观，Tooltip 说明候选组，JEI 查询仍针对真实 Tag 材料组。
- 为单物品目标指定自定义图片后，JEI 仍查询真实目标物品；指定的图片不会改变匹配材料。
- KILL 使用僵尸头等素材生成头像，不会产生“需要僵尸头”的虚假 JEI 入口。没有真实物品材料的目标不凭图片创造查询结果。
- 配方/用途查询使用 JEI 当前绑定的键或鼠标按钮；键盘焦点定位到相同图标区域。往返查询恢复原任务面板实例与候选状态。
- 裁切区外、隐藏行、已失效的资源代次和被模态面板遮挡的区域不提供当前任务图标查询。

没有 JEI 时，自动物品图标、Tag 轮播、键盘焦点、图片、实体头像和自绘 Tooltip 仍由 ArcQ 原生客户端运行；不会显示可用的 JEI 查询提示或激活 JEI 目录。

## 任务作者 Java API

```java
import org.arcadia.arc_quest.quest.api.icon.ObjectiveIcons;

ObjectiveBuilder.collect(Items.OAK_LOG, 20); // 默认为 AUTO
ObjectiveBuilder.craft(Items.CRAFTING_TABLE, 1); // 现有 CRAFT 事件语义
ObjectiveBuilder.collectTag(new ResourceLocation("minecraft", "logs"), 20);
ObjectiveBuilder.kill(EntityType.COW, 5)
    .iconTexture("my_pack:textures/gui/objectives/cow.png");

builder.iconTexture(textureId); // ResourceLocation 重载
builder.icon(ObjectiveIcons.texture("my_pack:textures/gui/atlas.png")
    .region(32, 16, 16, 16));
builder.icon(ObjectiveIcons.provider("my_mod:custom_target_icon"));
builder.noIcon();
builder.autoIcon();
```

后一次图标设置覆盖前一次设置。`icon(null)` 和空纹理 ID 是错误，关闭请使用 `noIcon()`。未设置 objectiveId 的目标经阶段自动补 ID 后仍保留图标。旧 `ObjectiveEntry` 构造器继续有效，默认 AUTO。

显式模式不会因失败切换到 AUTO：作者选择 NONE 就保持关闭，指定图片缺失或 provider 不存在则保持无图。`craft(Item, int)` 是 CRAFT 目标的便捷工厂；它不新增烧炼、机器加工等事件监听，也没有隐含的 `craftTag` 工厂。目标材料语义仍取自定义中的物品 ID/Tag。

`quest.api.icon.ObjectiveIconSpec` 是不可变 record，访问器为 `mode()`、`texture()`、`region()`、`provider()`。`Mode` 为 AUTO / NONE / TEXTURE / PROVIDER；嵌套 `Region` 提供 `x()`、`y()`、`width()`、`height()`。`ObjectiveEntry.getIcon()` 永不返回 null。`ObjectiveIcons` 提供规范化 AUTO / NONE 常量工厂，以及 String / ResourceLocation 的 texture 和 provider 工厂。纹理 `.region(...)` 返回新值，不修改原值。

## 任务 JSON

在每个 ObjectiveSpec 中使用唯一的 `icon` 字段：

```json
{
  "type": "arc_quest:kill",
  "targetId": "minecraft:cow",
  "requiredCount": 5,
  "icon": {
    "type": "arc_quest:texture",
    "texture": "my_pack:textures/gui/objectives/cow.png"
  }
}
```

| 配置 | 含义 |
| --- | --- |
| 无 icon、null 或 `{"type":"arc_quest:auto"}` | AUTO；标准导出省略默认字段 |
| `{"type":"arc_quest:none"}` | 明确不显示；不进入自动解析 |
| `{"type":"arc_quest:texture","texture":"my_pack:textures/gui/a.png"}` | 作者指定图片，优先于自动图标 |
| `{"type":"arc_quest:provider","provider":"my_mod:target_icon"}` | 使用指定客户端解析器，未安装时仍保留 ID |

TEXTURE 可增加 `"region":{"x":32,"y":16,"width":16,"height":16}`，坐标为原始图片像素。x/y 非负，width/height 为正整数，边界不能溢出 32 位整数。图片真实尺寸与裁切是否越界由客户端资源解析检查，服务端不读取 PNG。省略 region 表示整张图片。

纹理 ID 对应 `assets/<namespace>/<path>`。禁止磁盘绝对路径、网址及相对路径跳转。模式组合必须明确：AUTO/NONE 不携带来源，TEXTURE 不能同时有 provider，PROVIDER 不能同时有 texture/region。未知模式和拼错字段由 Java JSON 解码器报告具体路径。

使用 `QuestSpecJsonReader` / `QuestSpecJsonWriter` 读写任务，或使用已注册 `ObjectiveIconSpecAdapter` 的 Gson，勿用未注册适配器的 Gson 直接反射序列化 ResourceLocation。定义同步沿用现有 canonical JSON，不增加进度包字段或图像数据。

## 附属模组客户端注册

实际事件是 `org.arcadia.arc_quest.client.hud.quest.icon.RegisterObjectiveIconsEvent`，在客户端 setup 的排队工作中向 **MOD 事件总线**发出一次。注册处理器必须限定 `Dist.CLIENT`；不要从专用服务器入口加载这些客户端类。事件完成后 provider、默认绑定、头像及头部 adapter 注册冻结；同 ID 重复注册或重复绑定会报错。

以下示例中的 `my_addon:inspect`、`my_addon:stone_guard`、`my_addon:forest_pig` 应当是附属模组自身已注册的目标类型/实体；资源 PNG 也由附属模组提供。它们不是 ArcQ 内置 ID。

```java
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.arcadia.arc_quest.client.hud.quest.icon.RegisterObjectiveIconsEvent;
import org.arcadia.arc_quest.client.hud.quest.icon.ResolvedObjectiveIcon;
import org.arcadia.arc_quest.client.hud.quest.icon.TextureObjectiveIcon;
import org.arcadia.arc_quest.client.hud.quest.icon.portrait.HeadPortraitDefinition;
import org.arcadia.arc_quest.client.hud.quest.icon.portrait.TexturePortraitDefinition;
import org.arcadia.arc_quest.quest.api.icon.ObjectiveIcons;
import java.io.IOException;
import java.util.List;

@Mod.EventBusSubscriber(modid = "my_addon", value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.MOD)
public final class MyObjectiveIcons {
    private static ResourceLocation id(String path) {
        return new ResourceLocation("my_addon", path);
    }

    @SubscribeEvent
    public static void register(RegisterObjectiveIconsEvent event) {
        event.registerProvider(id("inspect_icon"), context -> {
            try {
                return ResolvedObjectiveIcon.visual(TextureObjectiveIcon.load(
                        ObjectiveIcons.texture(id("textures/gui/objectives/inspect.png"))));
            } catch (IOException error) {
                // ArcQ catches provider exceptions, reports a diagnostic, and returns no icon.
                throw new IllegalArgumentException("Cannot load inspect icon", error);
            }
        });
        event.bindDefault(id("inspect"), id("inspect_icon"));

        // An explicit choice by this addon: its stone guard uses the vanilla skeleton head.
        event.registerHeadPortrait(id("stone_guard"), HeadPortraitDefinition.skull(
                new ResourceLocation("minecraft", "skeleton_skull"),
                new HeadPortraitDefinition.Frame(-0.30f, -0.05f, 0.30f, 0.55f)));

        // Explicit per-entity UV adaptation; no entity instance is needed.
        event.registerTexturePortrait(id("forest_pig"), new TexturePortraitDefinition(
                new ResourceLocation("minecraft", "textures/entity/pig/pig.png"),
                new TexturePortraitDefinition.Size(64, 32),
                new TexturePortraitDefinition.Size(8, 8),
                List.of(
                        new TexturePortraitDefinition.Layer(
                                new TexturePortraitDefinition.Rect(8, 8, 8, 8),
                                new TexturePortraitDefinition.Rect(0, 0, 8, 8)),
                        new TexturePortraitDefinition.Layer(
                                new TexturePortraitDefinition.Rect(17, 17, 4, 3),
                                new TexturePortraitDefinition.Rect(2, 4, 4, 3)))));
    }
}
```

已绑定的 COLLECT / CRAFT / KILL 默认项不能重复 `bindDefault`。只想覆盖一个目标时使用 `ObjectiveIcons.provider(...)`；新增实体头像使用头像注册或资源包规则，不需要重新绑定整个 KILL 类型。Java 不能重复注册内置头颅或牛猪纹理映射，资源包覆盖是修改这些内置外观的入口。

`ObjectiveIconProvider.resolve(ObjectiveIconContext)` 返回 `ResolvedObjectiveIcon.none()`、`.items(List<ItemStack>)` 或 `.visual(ObjectiveIconVisual)`。context 提供 `questId()`、`phaseId()`、`objectiveIndex()`、`objective()`、`progress()`、`requiredCount()`、`generation()` 和稳定的 `key()`。会话按目标身份、进度、有效需求量与资源代次缓存解析结果；provider 应保持只读，不修改任务、库存或玩家状态，也不要依赖每帧调用。图标 provider 的结果不授予 JEI 材料权限。

`ObjectiveIconVisual` 的实际方法为 `render(GuiGraphics, int x, int y, int size)` 和 `available()`；后者可用于准备中或失效的资源。自定义绘制应恢复自身改动的渲染状态；标准行通过同一 `IconFrameSelection` 驱动图标、Tooltip 和命中区。

### 自定义头部 adapter

`HeadPortraitDefinition` 接收 `headItem`、`adapter`、`Pose(yaw, pitch, animation)`、`Frame(left, bottom, right, top)` 和 `resolution`；分辨率范围为 16–256。`skull(item, frame)` 使用 `arc_quest:skull` adapter、固定 `(180, 0, 0)` 姿态与 64 像素缓存。`Frame` 是正交相机在模型单位下的范围；一个方块对应 16 个模型像素。

内置 skull adapter 只接受能解析到静态头部模型与皮肤的 `AbstractSkullBlock` 物品。新头颅不是仅注册 Item 就能自动适配；可在同一事件调用 `registerHeadAdapter`，再把对应 ID 写入 `HeadPortraitDefinition.adapter`。以下是可用的 adapter 形状，附属模组可替换为自己拥有的头部模型与纹理：

```java
event.registerHeadAdapter(new ResourceLocation("my_addon", "static_skeleton"),
        (definition, models, resources) -> new HeadPortraitAdapter.HeadModel(
                SkullBlockRenderer.createSkullRenderers(models).get(SkullBlock.Types.SKELETON),
                new ResourceLocation("minecraft", "textures/entity/skeleton/skeleton.png")));

event.registerHeadPortrait(new ResourceLocation("my_addon", "another_guard"),
        new HeadPortraitDefinition(
                new ResourceLocation("minecraft", "skeleton_skull"),
                new ResourceLocation("my_addon", "static_skeleton"),
                new HeadPortraitDefinition.Pose(180, 0, 0),
                new HeadPortraitDefinition.Frame(-0.30f, -0.05f, 0.30f, 0.55f),
                64));
```

上段另外需要 `HeadPortraitAdapter`、`net.minecraft.client.renderer.blockentity.SkullBlockRenderer` 和 `net.minecraft.world.level.block.SkullBlock`。adapter 返回的 `HeadModel` 包含自己拥有的 `SkullModelBase` 和纹理 ID，不能创建实体、复用世界 renderer 的可变模型或改变全局渲染状态。ArcQ 负责固定姿态、全亮照明、离屏准备及二维缓存生命周期。

## 资源包实体头像规则

资源包覆盖作用于 **AUTO 的 KILL 头像**。规则路径按目标实体的命名空间和路径决定：

```text
assets/<entity_namespace>/arc_quest/objective_icons/entities/<entity_path>.json
```

例如 `minecraft:pig` 对应 `assets/minecraft/arc_quest/objective_icons/entities/pig.json`，`my_addon:forest_pig` 对应 `assets/my_addon/arc_quest/objective_icons/entities/forest_pig.json`。规则文件放在客户端 `assets` 中，不是服务端 `data` 目录；图片可引用另一个命名空间。实体路径若包含子目录，文件路径保留该子目录。

可用的资源规则只有以下三种：

```json
{ "type": "arc_quest:none" }
```

```json
{
  "type": "arc_quest:texture",
  "texture": "my_pack:textures/gui/portraits/pig.png",
  "region": { "x": 0, "y": 0, "width": 16, "height": 16 }
}
```

`arc_quest:texture` 的 `region` 可以省略；该裁切使用图片实际像素，保持长宽比并在图标槽内居中。第三种 `arc_quest:entity_texture_portrait` 支持逐层 UV，下面完整示例与内置猪的脸部/鼻部组合一致：

```json
{
  "type": "arc_quest:entity_texture_portrait",
  "texture": "minecraft:textures/entity/pig/pig.png",
  "referenceSize": { "width": 64, "height": 32 },
  "canvasSize": { "width": 8, "height": 8 },
  "layers": [
    {
      "region": { "x": 8, "y": 8, "width": 8, "height": 8 },
      "destination": { "x": 0, "y": 0, "width": 8, "height": 8 },
      "flipX": false,
      "flipY": false,
      "tint": "#FFFFFFFF"
    },
    {
      "region": { "x": 17, "y": 17, "width": 4, "height": 3 },
      "destination": { "x": 2, "y": 4, "width": 4, "height": 3 }
    }
  ]
}
```

- `referenceSize` 描述作者编写 UV 时的纹理尺寸；`region` 位于这个坐标系内。
- `canvasSize` 是头像的逻辑画布；`destination` 描述该层在画布上的位置与大小。
- `layers` 按数组顺序从后向前绘制，必须有 1–32 层；画布最大 256×256。所有矩形原点非负、尺寸为正，必须位于对应来源/画布边界内。
- `flipX` / `flipY` 默认 false；`tint` 默认白色，可使用 `#AARRGGBB` 或无符号 32 位 ARGB 数字。
- UV 采用归一化采样。同布局、同宽高比例的高清替换纹理可继续使用规则，例如 64×32 改为 128×64；不要求整数缩放倍数。
- 宽高比例改变会拒绝头像并提示更新规则；即使比例相同，资源包若重排了 UV，也需要作者提供新规则。

内置牛、猪 UV 定义位于 `assets/arc_quest/objective_icons/portraits/cow.json` 与 `pig.json`，也可被资源包替换，但这两个默认定义文件必须保持 `arc_quest:entity_texture_portrait` 类型。更通用的覆盖/关闭方式是上面的实体规则路径。

解析顺序为：目标的显式 icon 配置 → AUTO 下实体资源规则 → 已注册头颅 → 该实体已注册的纹理适配。头颅还在准备时保持暂不可用，不提前切换纹理；头颅确定失败时才尝试该实体明确注册的纹理方案。规则显式 NONE 会关闭头像；JSON 无效、指定资源缺失或裁切越界会保持无图并给出诊断。这些情况不偷偷回到原版头颅。完全没有注册规则的实体也保持无图。

## 资源重载与缓存

客户端资源重载会重新读取图片尺寸、实体覆盖和牛猪默认 UV，重建头像状态并释放旧离屏 GPU 资源；旧头像 handle 随代次切换立即不可用。再次显示时按当前资源重新准备。失败诊断在当前代次去重，资源修复后可通过重载重新尝试。

任务定义同步、客户端收到 Tag 更新和退出世界都会触发相应的会话失效。Tag 候选、Tooltip 和 JEI 命中区不能继续使用旧代次数据；正常 JEI 查询往返则保留当前 Journal 会话及候选。附属模组的注册表只在启动事件中建立，资源重载不会重新开放 Java 注册。

## 编辑器

Web Objective 表单提供自动、自定义图片、不显示以及高级 provider；图片的高级区域支持启用/关闭裁切与四个整数输入。显示资源包路径提示，并明确资源存在性仍待客户端验证。导入、导出、复制文档与嵌套字段编辑保留配置，默认 AUTO 导出时省略。

编辑器保留未知 icon type 的完整原始 JSON 并报告不支持；未知 provider ID 是合法数据，不因当前编辑器未安装附属模组而删除。非法/未编辑完的配置保留并报告错误，不静默改成 AUTO。

游戏内属性编辑器的 `icon` 行可编辑标准 JSON，Enter 提交、Escape 取消；输入不合法时保留原配置并显示错误。任务编辑器的保存、复制、撤销与重做使用标准 JSON 编解码，保持图标信息。

## 实现入口与回归定位

对应回归为 `ObjectiveIconSpecTest`、`ObjectiveIconCompatibilityTest`、`QuestEditorIconHistoryTest` 和 Web 的 `test/objective-icon.test.js`。Java 检查覆盖旧构造、自动 ID 复制、最后设置优先、模式/裁切校验、默认省略、服务端/客户端编译、parity 与撤销重做；Web 检查覆盖表单、嵌套输入、复制与重复往返、未知配置和错误路径。

客户端实现可从以下入口追踪：

| 行为 | 源码入口 |
| --- | --- |
| 默认注册、显式模式、客户端扩展事件 | `client/hud/quest/icon/ObjectiveIconRegistry.java`、`RegisterObjectiveIconsEvent.java` |
| 真实物品/Tag 候选与匹配 | `quest/api/ObjectiveItemResolver.java` |
| 每目标缓存、时间轮播、键盘焦点 | `client/hud/quest/icon/ObjectiveIconSession.java`、`ItemIconCycleController.java` |
| 标准/并行行布局与自绘提示请求 | `client/hud/quest/journal/detail/ObjectiveRowRenderer.java`、`client/hud/quest/icon/ObjectiveRowLayout.java` |
| 实体规则、头颅准备、二维纹理合成 | `client/hud/quest/icon/portrait/EntityPortraits.java`、`HeadPortraitBaker.java`、`PortraitTextureVisual.java` |
| 查询候选授权、裁切与 JEI 返回 | `client/compat/jei/screen/JeiScreenIngredients.java`、`ArcQuestJeiScreenHandlers.java` |
| 资源/Tag/会话失效 | `client/hud/quest/icon/ObjectiveIconsClient.java`、`client/data/sync/ClientDatapackContentReceiver.java` |

表中 Java 路径均相对 `src/main/java/org/arcadia/arc_quest/`。实际客户端验收入口位于 `src/gameTest/java/org/arcadia/arc_quest/client/ObjectiveIconClientAudit.java`，通过专用属性启用，仅操作隔离验收世界并输出截图与 PASS/FAIL；是否通过以及具体环境应以独立的构建/验收记录为准。

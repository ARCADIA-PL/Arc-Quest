# Objective 图标配置

图标是独立的展示配置，不改变任务匹配、数量、进度、提交或 JEI 的真实材料语义。公共配置不依赖客户端类或 JEI；图标资源由客户端模组/资源包提供。

## Java API

```java
import org.arcadia.arc_quest.quest.api.icon.ObjectiveIcons;

ObjectiveBuilder.collect(Items.OAK_LOG, 20); // 默认为 AUTO
ObjectiveBuilder.craft(Items.CRAFTING_TABLE, 1); // 现有 CRAFT 事件语义
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

`quest.api.icon.ObjectiveIconSpec` 是不可变 record，访问器为 `mode()`、`texture()`、`region()`、`provider()`。`Mode` 为 AUTO / NONE / TEXTURE / PROVIDER；嵌套 `Region` 提供 `x()`、`y()`、`width()`、`height()`。`ObjectiveEntry.getIcon()` 永不返回 null。`ObjectiveIcons` 提供规范化 AUTO / NONE 常量工厂，以及 String / ResourceLocation 的 texture 和 provider 工厂。纹理 `.region(...)` 返回新值，不修改原值。

## JSON

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

## 编辑器

Web Objective 表单提供自动、自定义图片、不显示以及高级 provider；图片的高级区域支持启用/关闭裁切与四个整数输入。显示资源包路径提示，并明确资源存在性仍待客户端验证。导入、导出、复制文档与嵌套字段编辑保留配置，默认 AUTO 导出时省略。

编辑器保留未知 icon type 的完整原始 JSON 并报告不支持；未知 provider ID 是合法数据，不因当前编辑器未安装附属模组而删除。非法/未编辑完的配置保留并报告错误，不静默改成 AUTO。

游戏内属性编辑器的 `icon` 行可编辑标准 JSON，Enter 提交、Escape 取消；输入不合法时保留原配置并显示错误。任务编辑器的保存、复制、撤销与重做使用标准 JSON 编解码，保持图标信息。

## 检查

对应回归为 `ObjectiveIconSpecTest`、`ObjectiveIconCompatibilityTest`、`QuestEditorIconHistoryTest` 和 Web 的 `test/objective-icon.test.js`。Java 检查覆盖旧构造、自动 ID 复制、最后设置优先、模式/裁切校验、默认省略、服务端/客户端编译、parity 与撤销重做；Web 检查覆盖表单、嵌套输入、复制与重复往返、未知配置和错误路径。

本文件说明公共配置与编辑器。具体 AUTO 解析覆盖、头颅/生物纹理头像、布局与轮换由客户端实现和对应验收记录定义；CRAFT 便捷工厂不新增 Tag、烧炼或机器制作支持。

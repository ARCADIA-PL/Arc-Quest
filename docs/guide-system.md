# Arc-Quest 指南系统

指南注册只定义内容，不会自动加入玩家列表。玩家数据中的解锁、已读和最后阅读页由服务端保存并同步。
指南列表默认使用 `I` 键打开，也可在 Minecraft 按键设置的 Arc-Quest 分类中重新绑定。
`summary` 会以小号灰色文字显示在指南列表标题下方、列表详情头部和独立指南窗口中；
较长的简介与正文会参与内容高度计算，详情区域支持滚轮和拖动滚动条浏览。
服务端通过 `GuideTriggerService.open` 打开指南时，客户端若没有 Screen，会使用独立
`GuideScreen`；若已有对话或其他 Screen，则使用从底部进入、居中显示的
`GuidePopupOverlay`，不会替换原 Screen。指南 splash 或弹窗存在期间，对话打字机与自动下一句计时会暂停。

## Java 注册

注册必须在 Arc-Quest 冻结 `GuideRegistry` 前完成。附属模组可监听 `ArcQuestRegistrationEvent.Guide`，并调用 `ArcQuestAPI.registerGuide`。

```java
GuideDefinition guide = GuideBuilder.create("example:diamond_guide")
        .category(GuideCategory.BASICS)
        .title("这就是钻石？")
        .summary("第一次获得了钻石")
        .icon(Items.DIAMOND)
        .renderLargeIconOnIntro(true)
        .unlockPopup(true)
        .page(GuidePageBuilder.create()
                .none()
                .description("钻石是个好东西，可以用来巴拉巴拉"))
        .build();
ArcQuestAPI.registerGuide(guide);
```

弹窗背景图为可选项：

```java
.popupBackground(ResourceLocation.parse("example:textures/gui/diamond_guide.png"))
.renderPopupBackground(true)
```

不配置背景图时，解锁弹窗只绘制居中的物品图标，不绘制黑底、面板、边框或文案。

## 指南分组

指南 group 与指南内容分别注册，注册 group 本身不会授予任何指南。附属模组可在
`ArcQuestRegistrationEvent.Guide` 中完成注册与分配：

```java
ResourceLocation groupId = ResourceLocation.parse("example:minerals");
event.registerGroup(new GuideGroupDefinition(
        groupId,
        Component.literal("矿物"),
        0,
        0x55D6E8));
event.assignGuideToGroup("example:diamond_guide", groupId.toString());
```

也可直接使用兼容 API：`ArcQuestAPI.registerGuideGroup`、
`ArcQuestAPI.assignGuideToGroup`、`ArcQuestAPI.getGuideGroup` 和
`ArcQuestAPI.getGuideGroupForGuide`。group 必须先注册，再分配指南；一个指南只能属于一个 group。

## 代码授予

授予必须在服务端执行。该服务负责注册项校验、玩家数据持久化和客户端同步。

```java
boolean newlyGranted = new GuideUnlockService().grant(
        serverPlayer,
        ResourceLocation.parse("example:diamond_guide")
);
```

只打开已授予指南可使用 `GuideTriggerService.openIfUnlocked`。注册指南不会自动授予，空的 `unlockConditions` 也不会改变这一点。

代码驱动的对话选项中，授予和打开是两个独立动作。需要让指南进入玩家列表并立即在对话上方显示时：

```java
choice -> choice
        .unlockGuide("example:diamond_guide")
        .openGuide("example:diamond_guide", 0, false)
        .goTo("finished")
```

`unlockGuide` 在服务端通过 `GuideUnlockService` 完成校验、持久化和同步；单独调用
`openGuide` 只显示指南，不修改玩家的解锁状态。

## 翻阅完成事件

新指南在玩家到达最后一页前始终保持未读红点，下一页按钮也会显示相同红点。服务端只在
已保存的页码到达最后一页后接受完成请求，并在首次完成时发布 `GuideCompletedEvent`：

```java
@SubscribeEvent
public static void onGuideCompleted(GuideCompletedEvent event) {
    ServerPlayer player = event.getPlayer();
    ResourceLocation guideId = event.getGuideId();
    // 发放物品、设置任务变量或执行其他服务端逻辑
}
```

事件发布在 `NeoForge.EVENT_BUS`，每位玩家的每个指南只会在首次完成时触发一次；通过调试命令
重置该指南的已读状态后可以再次触发。

## 数据包指南

指南文件放在 `${arcquest.datapack.dir}/guides/*.json`；未设置系统属性时目录为 `arc_quest/datapack/guides`。完整示例见 [diamond-guide.json](examples/diamond-guide.json)。

新增字段均可省略：

- `summary`：获得提示中的简介。
- `icon`：物品注册 ID。
- `renderLargeIconOnIntro`：介绍页顶部是否显示大物品图。
- `showUnlockPopup`：首次授予时是否显示非阻塞弹窗。
- `popupBackground`：弹窗背景纹理。
- `renderPopupBackground`：是否绘制配置的背景纹理。

## 阶段授予

Java builder：

```java
PhaseBuilder.create("find_diamond")
        .displayName("寻找钻石")
        .objective(...)
        .grantGuideOnEnter("example:mining_basics")
        .grantGuideOnComplete("example:diamond_guide");
```

任务 JSON 的阶段对象支持：

```json
{
  "guidesToGrantOnEnter": ["example:mining_basics"],
  "guidesToGrantOnComplete": ["example:diamond_guide"]
}
```

## 调试命令

命令要求权限等级 2，玩家和指南 ID 都会由 Brigadier 校验，指南 ID 支持补全。

```text
/arcquest guide grant <player> <guide_id>
/arcquest guide status <player>
/arcquest guide list
/arcquest guide reset seen <player> [guide_id]
/arcquest guide reset unlock <player> [guide_id]
```

对话内指南弹窗测试：

```text
/arcquest dialogue start @s arc_quest:diamond_guide_popup_test
```

点击测试对话的“确认”会先授予 `arc_quest:diamond_demo`，再显示非阻塞指南弹窗。

内置调试指南为 `arc_quest:diamond_demo` 和 `arc_quest:gold_ingot_demo`，均属于
`arc_quest:minerals` group，且不会自动授予：

```text
/arcquest guide grant @s arc_quest:diamond_demo
/arcquest guide grant @s arc_quest:gold_ingot_demo
```

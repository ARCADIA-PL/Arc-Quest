# Guide 定义 API

## 注册入口

在 MOD 总线的 `ArcQuestRegistrationEvent.Guide` 中注册 Group、Guide 和归属关系。

```text
@SubscribeEvent
public static void registerGuides(ArcQuestRegistrationEvent.Guide event) {
    ResourceLocation groupId = ResourceLocation.fromNamespaceAndPath(
            ExampleArcQuestAddon.MOD_ID, "tutorials");
    ResourceLocation guideId = ResourceLocation.fromNamespaceAndPath(
            ExampleArcQuestAddon.MOD_ID, "first_steps");

    event.registerGroup(GuideGroupDefinition.translated(
            groupId, "guide_group.example_addon.tutorials"));

    GuideDefinition guide = GuideBuilder.create(guideId)
            .title(Component.translatable("guide.example_addon.first_steps"))
            .summary(Component.translatable("guide.example_addon.first_steps.summary"))
            .icon(Items.BOOK)
            .unlockPopup(true, false)
            .page(GuidePageBuilder.create()
                    .description(Component.translatable("guide.example_addon.first_steps.page_1")))
            .build();
    event.register(guide);
    event.assignGuideToGroup(guideId, groupId);
}
```

## `GuideBuilder`

主要方法：

- `category(GuideCategory)`：分类。
- `title(...)`、`summary(...)`：接受字面文本、`Component` 或 `GuideText`。
- `icon(Item/ItemStack)`：原版物品模型图标。
- `renderLargeIconOnIntro(boolean)`：首页大图标。
- `unlockPopup(...)`：首次解锁弹窗策略。
- `popupBackground(...)`、`renderPopupBackground(boolean)`：弹窗背景。
- `sortOrder(int)`、`hidden()`。
- `repeatablePopup()`：允许后续再次弹出表现；不等同于重复解锁状态。
- `unlockCondition(ICondition)`、`requiresQuest(ResourceLocation)`、`requiresFlag(String)`。
- `page(...)`、`imagePage(...)`、`ponderPage(...)`、`ponderQuestPhasePage(...)`。

`build()` 要求至少有一页。`create(String)` 的无 namespace 回退为 `arc_quest`，附属使用 `ResourceLocation`。

## Popup 参数

- `unlockPopup(false)`：静默加入 Guide 列表。
- `unlockPopup(true)`：启用解锁弹窗，使用默认强制叠加策略。
- `unlockPopup(true, false)`：当前已有 Screen 时延迟，等无 Screen 再打开。
- `unlockPopup(true, true)`：允许在已有 Screen 上强制打开。

该配置只控制解锁时的客户端表现，不影响 Guide 是否写入玩家解锁状态。

## 页面与媒体

`GuidePageBuilder`：

- `description(...)`：页面正文。
- `media(GuideMediaDefinition/GuideMediaBuilder)`。
- `image(ResourceLocation texture, int width, int height)`。
- `ponder(ResourceLocation sceneId)`。
- `ponderScene(String questId, String phaseId)`。
- `none()`：明确无媒体。

图片宽高是展示元数据，仍需提供真实资源。Ponder scene 必须在运行环境存在；附属依赖可选模组时，不要无条件引用缺失 scene。

## `GuideText`

静态翻译文本优先使用 `Component.translatable` 或 `GuideText` 的翻译入口。动态文本 resolver 应能在客户端展示模型中获得稳定结果；不要依赖仅存在于服务端的实体强引用。

## Guide Group

`GuideGroupDefinition` 支持：

- `(ResourceLocation id, Component displayName)`；
- `(ResourceLocation id, Component displayName, int sortOrder, int themeColor)`；
- `literal(...)` 与 `translated(...)` 工厂。

先注册 Group 和 Guide，再 `assignGuideToGroup`。所有 ID 使用附属 namespace。
